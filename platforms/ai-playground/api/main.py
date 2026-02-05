import os
import shutil
import zipfile
import uuid
from datetime import datetime
from typing import Optional, List
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException, Depends, UploadFile, File, Form, BackgroundTasks
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel
from passlib.context import CryptContext

from sqlmodel import SQLModel, Field, Session, create_engine, select, Relationship
import requests

app = FastAPI(title="AI Playground API", version="0.3.0")

# -------------------- Config --------------------
AUTH_MODE = os.getenv("AUTH_MODE", "sso")  # sso|dev

# dev bootstrap (email/password)
DEV_BOOTSTRAP_EMAIL = os.getenv("DEV_BOOTSTRAP_EMAIL", "dev@local")
DEV_BOOTSTRAP_PASSWORD = os.getenv("DEV_BOOTSTRAP_PASSWORD", "devpass")

# SSO headers
SSO_HEADER_EMPID = os.getenv("SSO_HEADER_EMPID", "X-SSO-EMPID")
SSO_HEADER_LOGINID = os.getenv("SSO_HEADER_LOGINID", "X-SSO-LOGINID")
SSO_ALLOWED_COMPANY_CODE = os.getenv("SSO_ALLOWED_COMPANY_CODE", "1000")

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./playground.db")
RUNNER_BASE_URL = os.getenv("RUNNER_BASE_URL", "http://runner:8000")
UPLOADS_DIR = Path(os.getenv("UPLOADS_DIR", "./uploads"))

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
security = HTTPBearer(auto_error=False)

# -------------------- Models --------------------

class IFOrgUser(SQLModel, table=True):
    __tablename__ = "if_org_user"

    code: str = Field(primary_key=True, index=True)  # empid
    loginid: Optional[str] = Field(default=None, index=True)
    email: Optional[str] = Field(default=None, index=True)
    name: Optional[str] = Field(default=None)
    company_code: Optional[str] = Field(default=None, index=True)


class User(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    email: str = Field(index=True, unique=True)
    password_hash: str = Field(default="")
    created_at: datetime = Field(default_factory=datetime.utcnow)


class App(SQLModel, table=True):
    id: Optional[str] = Field(default=None, primary_key=True)
    name: str = Field(index=True)
    description: Optional[str] = Field(default=None)
    owner_id: str = Field(index=True)  # loginid or email
    org_code: Optional[str] = Field(default=None, index=True)  # company_code for org sharing
    is_shared: bool = Field(default=False)  # Share with org members
    entry_file: str = Field(default="app.py")  # Main entry point
    status: str = Field(default="draft")  # draft, active, archived
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)


class Collaborator(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    app_id: str = Field(index=True, foreign_key="app.id")
    user_id: str = Field(index=True)  # loginid or email
    role: str = Field(default="viewer")  # owner, collaborator, viewer
    added_at: datetime = Field(default_factory=datetime.utcnow)
    added_by: Optional[str] = Field(default=None)


class AppRun(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    app_id: str = Field(index=True, foreign_key="app.id")
    user_id: str = Field(index=True)
    port: Optional[int] = Field(default=None)
    runner_url: Optional[str] = Field(default=None)
    status: str = Field(default="starting")  # starting, running, stopped, error
    started_at: datetime = Field(default_factory=datetime.utcnow)
    stopped_at: Optional[datetime] = Field(default=None)
    error_message: Optional[str] = Field(default=None)


_engine = create_engine(DATABASE_URL, pool_pre_ping=True)


# -------------------- Auth helpers --------------------
class LoginRequest(BaseModel):
    email: str
    password: str


class LoginResponse(BaseModel):
    token: str
    email: str


class UserInfo(BaseModel):
    email: Optional[str] = None
    loginid: Optional[str] = None
    empid: Optional[str] = None
    name: Optional[str] = None
    org_code: Optional[str] = None


def create_token(email: str) -> str:
    return f"user:{email}"


def parse_token(token: str) -> Optional[str]:
    if not token or not token.startswith("user:"):
        return None
    return token.split(":", 1)[1]


def hash_password(pw: str) -> str:
    return pwd_context.hash(pw)


def verify_password(pw: str, hashed: str) -> bool:
    try:
        return pwd_context.verify(pw, hashed)
    except Exception:
        return False


def get_session():
    with Session(_engine) as s:
        yield s


def require_user(
    creds: Optional[HTTPAuthorizationCredentials] = Depends(security),
    session: Session = Depends(get_session),
    x_sso_empid: Optional[str] = Header(default=None, convert_underscores=False, alias=SSO_HEADER_EMPID),
    x_sso_loginid: Optional[str] = Header(default=None, convert_underscores=False, alias=SSO_HEADER_LOGINID),
) -> UserInfo:
    # dev: email/password token only
    if AUTH_MODE == "dev":
        if not creds:
            raise HTTPException(status_code=401, detail="Need Authorization token")
        email = parse_token(creds.credentials)
        if not email:
            raise HTTPException(status_code=401, detail="Invalid token")
        u = session.exec(select(User).where(User.email == email)).first()
        if not u:
            raise HTTPException(status_code=401, detail="User not found")
        return UserInfo(email=u.email, loginid=u.email)

    # sso: headers first, fallback to token
    if creds:
        email = parse_token(creds.credentials)
        if email:
            return UserInfo(email=email, loginid=email)

    # loginid header has priority
    if x_sso_loginid:
        # Lookup org info
        org_user = session.exec(
            select(IFOrgUser).where(IFOrgUser.loginid == x_sso_loginid)
        ).first()
        return UserInfo(
            loginid=x_sso_loginid,
            empid=x_sso_empid,
            email=org_user.email if org_user else None,
            name=org_user.name if org_user else None,
            org_code=org_user.company_code if org_user else None
        )

    # empid only - lookup in directory
    if x_sso_empid:
        du = session.get(IFOrgUser, x_sso_empid)
        if not du:
            raise HTTPException(status_code=403, detail="Empid not found in if_org_user")
        if du.company_code and SSO_ALLOWED_COMPANY_CODE and du.company_code != SSO_ALLOWED_COMPANY_CODE:
            raise HTTPException(status_code=403, detail="Forbidden company")
        return UserInfo(
            loginid=du.loginid,
            empid=du.code,
            email=du.email,
            name=du.name,
            org_code=du.company_code
        )

    raise HTTPException(status_code=401, detail="Missing SSO headers")


def get_user_id(user: UserInfo) -> str:
    """Get unique user identifier."""
    return user.loginid or user.email or "anonymous"


def check_app_permission(app: App, user: UserInfo, session: Session, min_role: str = "viewer") -> bool:
    """Check if user has permission to access app."""
    user_id = get_user_id(user)
    
    # Owner always has access
    if app.owner_id == user_id:
        return True
    
    # Check collaborators
    collab = session.exec(
        select(Collaborator).where(
            Collaborator.app_id == app.id,
            Collaborator.user_id == user_id
        )
    ).first()
    
    if collab:
        role_hierarchy = {"viewer": 1, "collaborator": 2, "admin": 3, "owner": 4}
        if role_hierarchy.get(collab.role, 0) >= role_hierarchy.get(min_role, 1):
            return True
    
    # Check org sharing
    if app.is_shared and user.org_code and app.org_code == user.org_code:
        return min_role == "viewer"  # Org members can only view
    
    return False


@app.on_event("startup")
def on_startup():
    SQLModel.metadata.create_all(_engine)
    UPLOADS_DIR.mkdir(parents=True, exist_ok=True)

    # dev bootstrap user
    if AUTH_MODE == "dev" and DEV_BOOTSTRAP_EMAIL:
        with Session(_engine) as s:
            u = s.exec(select(User).where(User.email == DEV_BOOTSTRAP_EMAIL)).first()
            if not u:
                u = User(email=DEV_BOOTSTRAP_EMAIL, password_hash=hash_password(DEV_BOOTSTRAP_PASSWORD))
                s.add(u)
                s.commit()


# -------------------- Routes --------------------

@app.get("/health")
def health():
    return {"ok": True, "mode": AUTH_MODE}


@app.get("/me")
def get_me(user: UserInfo = Depends(require_user)):
    return user


@app.post("/auth/login", response_model=LoginResponse)
def login(req: LoginRequest, session: Session = Depends(get_session)):
    if AUTH_MODE != "dev":
        raise HTTPException(status_code=404, detail="/auth/login is dev-only")

    u = session.exec(select(User).where(User.email == req.email)).first()
    if not u or not verify_password(req.password, u.password_hash):
        raise HTTPException(status_code=401, detail="Invalid email/password")

    return LoginResponse(token=create_token(u.email), email=u.email)


# -------------------- App Routes --------------------

class CreateAppRequest(BaseModel):
    name: str
    description: Optional[str] = None
    is_shared: bool = False


class AppResponse(BaseModel):
    id: str
    name: str
    description: Optional[str]
    owner_id: str
    org_code: Optional[str]
    is_shared: bool
    entry_file: str
    status: str
    created_at: datetime
    updated_at: datetime
    can_manage: bool = False


@app.get("/apps", response_model=List[AppResponse])
def list_apps(
    session: Session = Depends(get_session),
    user: UserInfo = Depends(require_user)
):
    """List apps accessible to the user."""
    user_id = get_user_id(user)
    
    # Get apps owned by user
    owned = session.exec(select(App).where(App.owner_id == user_id)).all()
    
    # Get apps where user is collaborator
    collabs = session.exec(
        select(App).join(Collaborator).where(Collaborator.user_id == user_id)
    ).all()
    
    # Get shared org apps
    shared = []
    if user.org_code:
        shared = session.exec(
            select(App).where(
                App.is_shared == True,
                App.org_code == user.org_code,
                App.owner_id != user_id  # Exclude already owned
            )
        ).all()
    
    # Combine and deduplicate
    all_apps = {app.id: app for app in owned + collabs + shared}
    
    result = []
    for app in all_apps.values():
        can_manage = app.owner_id == user_id
        if not can_manage:
            collab = session.exec(
                select(Collaborator).where(
                    Collaborator.app_id == app.id,
                    Collaborator.user_id == user_id
                )
            ).first()
            if collab and collab.role in ["admin", "owner"]:
                can_manage = True
        
        app_data = AppResponse(
            id=app.id,
            name=app.name,
            description=app.description,
            owner_id=app.owner_id,
            org_code=app.org_code,
            is_shared=app.is_shared,
            entry_file=app.entry_file,
            status=app.status,
            created_at=app.created_at,
            updated_at=app.updated_at,
            can_manage=can_manage
        )
        result.append(app_data)
    
    return result


@app.post("/apps", response_model=AppResponse)
def create_app(
    req: CreateAppRequest,
    session: Session = Depends(get_session),
    user: UserInfo = Depends(require_user)
):
    """Create a new app."""
    user_id = get_user_id(user)
    
    app = App(
        id=str(uuid.uuid4()),
        name=req.name,
        description=req.description,
        owner_id=user_id,
        org_code=user.org_code,
        is_shared=req.is_shared,
        status="draft"
    )
    session.add(app)
    session.commit()
    session.refresh(app)
    
    # Create app directory
    app_dir = UPLOADS_DIR / app.id
    app_dir.mkdir(parents=True, exist_ok=True)
    
    # Create default app.py
    default_app = '''import streamlit as st

st.title("My New App")
st.write("Edit app.py to customize your application!")
'''
    (app_dir / "app.py").write_text(default_app)
    
    return AppResponse(
        id=app.id,
        name=app.name,
        description=app.description,
        owner_id=app.owner_id,
        org_code=app.org_code,
        is_shared=app.is_shared,
        entry_file=app.entry_file,
        status=app.status,
        created_at=app.created_at,
        updated_at=app.updated_at,
        can_manage=True
    )


@app.get("/apps/{app_id}", response_model=AppResponse)
def get_app(
    app_id: str,
    session: Session = Depends(get_session),
    user: UserInfo = Depends(require_user)
):
    """Get app details."""
    app = session.get(App, app_id)
    if not app:
        raise HTTPException(status_code=404, detail="App not found")
    
    if not check_app_permission(app, user, session):
        raise HTTPException(status_code=403, detail="Access denied")
    
    user_id = get_user_id(user)
    can_manage = app.owner_id == user_id
    
    return AppResponse(
        id=app.id,
        name=app.name,
        description=app.description,
        owner_id=app.owner_id,
        org_code=app.org_code,
        is_shared=app.is_shared,
        entry_file=app.entry_file,
        status=app.status,
        created_at=app.created_at,
        updated_at=app.updated_at,
        can_manage=can_manage
    )


@app.put("/apps/{app_id}", response_model=AppResponse)
def update_app(
    app_id: str,
    req: CreateAppRequest,
    session: Session = Depends(get_session),
    user: UserInfo = Depends(require_user)
):
    """Update app metadata."""
    app = session.get(App, app_id)
    if not app:
        raise HTTPException(status_code=404, detail="App not found")
    
    user_id = get_user_id(user)
    if app.owner_id != user_id:
        # Check if admin collaborator
        collab = session.exec(
            select(Collaborator).where(
                Collaborator.app_id == app_id,
                Collaborator.user_id == user_id
            )
        ).first()
        if not collab or collab.role not in ["admin", "owner"]:
            raise HTTPException(status_code=403, detail="Only owner can update app")
    
    app.name = req.name
    app.description = req.description
    app.is_shared = req.is_shared
    app.updated_at = datetime.utcnow()
    
    session.add(app)
    session.commit()
    session.refresh(app)
    
    return AppResponse(
        id=app.id,
        name=app.name,
        description=app.description,
        owner_id=app.owner_id,
        org_code=app.org_code,
        is_shared=app.is_shared,
        entry_file=app.entry_file,
        status=app.status,
        created_at=app.created_at,
        updated_at=app.updated_at,
        can_manage=True
    )


@app.delete("/apps/{app_id}")
def delete_app(
    app_id: str,
    session: Session = Depends(get_session),
    user: UserInfo = Depends(require_user)
):
    """Delete app and all associated data."""
    app = session.get(App, app_id)
    if not app:
        raise HTTPException(status_code=404, detail="App not found")
    
    user_id = get_user_id(user)
    if app.owner_id != user_id:
        raise HTTPException(status_code=403, detail="Only owner can delete app")
    
    # Stop running instance
    try:
        requests.post(f"{RUNNER_BASE_URL}/stop", json={"app_id": app_id}, timeout=10)
    except Exception:
        pass
    
    # Delete app directory
    app_dir = UPLOADS_DIR / app_id
    if app_dir.exists():
        shutil.rmtree(app_dir)
    
    # Delete from database
    session.delete(app)
    session.commit()
    
    return {"ok": True, "message": f"App {app_id} deleted"}


@app.post("/apps/{app_id}/upload")
def upload_app_files(
    app_id: str,
    file: UploadFile = File(...),
    session: Session = Depends(get_session),
    user: UserInfo = Depends(require_user)
):
    """Upload app files as ZIP."""
    app = session.get(App, app_id)
    if not app:
        raise HTTPException(status_code=404, detail="App not found")
    
    user_id = get_user_id(user)
    if app.owner_id != user_id:
        collab = session.exec(
            select(Collaborator).where(
                Collaborator.app_id == app_id,
                Collaborator.user_id == user_id
            )
        ).first()
        if not collab or collab.role not in ["admin", "owner", "collaborator"]:
            raise HTTPException(status_code=403, detail="Access denied")
    
    if not file.filename.endswith('.zip'):
        raise HTTPException(status_code=400, detail="Only ZIP files allowed")
    
    app_dir = UPLOADS_DIR / app_id
    app_dir.mkdir(parents=True, exist_ok=True)
    
    # Clear existing files (except keep backups)
    for item in app_dir.iterdir():
        if item.is_file():
            item.unlink()
        elif item.is_dir():
            shutil.rmtree(item)
    
    # Save and extract ZIP
    zip_path = app_dir / "upload.zip"
    with open(zip_path, "wb") as f:
        shutil.copyfileobj(file.file, f)
    
    try:
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            zip_ref.extractall(app_dir)
        
        # Clean up zip
        zip_path.unlink()
        
        # Find entry file (app.py or main.py)
        entry_candidates = ["app.py", "main.py", "streamlit_app.py"]
        entry_file = None
        for candidate in entry_candidates:
            if (app_dir / candidate).exists():
                entry_file = candidate
                break
        
        if entry_file:
            app.entry_file = entry_file
            app.status = "active"
            app.updated_at = datetime.utcnow()
            session.add(app)
            session.commit()
        else:
            raise HTTPException(status_code=400, detail="No app.py or main.py found in ZIP")
        
        return {
            "ok": True,
            "message": "Files uploaded successfully",
            "entry_file": app.entry_file,
            "files": [f.name for f in app_dir.iterdir()]
        }
    except zipfile.BadZipFile:
        raise HTTPException(status_code=400, detail="Invalid ZIP file")


# -------------------- Collaborator Routes --------------------

class CollaboratorResponse(BaseModel):
    id: int
    user_id: str
    role: str
    added_at: datetime
    added_by: Optional[str]


class AddCollaboratorRequest(BaseModel):
    user_id: str  # loginid or email
    role: str = "viewer"  # collaborator, viewer, admin


@app.get("/apps/{app_id}/collaborators", response_model=List[CollaboratorResponse])
def list_collaborators(
    app_id: str,
    session: Session = Depends(get_session),
    user: UserInfo = Depends(require_user)
):
    """List collaborators for an app."""
    app = session.get(App, app_id)
    if not app:
        raise HTTPException(status_code=404, detail="App not found")
    
    if not check_app_permission(app, user, session):
        raise HTTPException(status_code=403, detail="Access denied")
    
    collabs = session.exec(
        select(Collaborator).where(Collaborator.app_id == app_id)
    ).all()
    
    return [
        CollaboratorResponse(
            id=c.id,
            user_id=c.user_id,
            role=c.role,
            added_at=c.added_at,
            added_by=c.added_by
        )
        for c in collabs
    ]


@app.post("/apps/{app_id}/collaborators")
def add_collaborator(
    app_id: str,
    req: AddCollaboratorRequest,
    session: Session = Depends(get_session),
    user: UserInfo = Depends(require_user)
):
    """Add a collaborator to an app."""
    app = session.get(App, app_id)
    if not app:
        raise HTTPException(status_code=404, detail="App not found")
    
    user_id = get_user_id(user)
    
    # Only owner or admin can add collaborators
    if app.owner_id != user_id:
        collab = session.exec(
            select(Collaborator).where(
                Collaborator.app_id == app_id,
                Collaborator.user_id == user_id
            )
        ).first()
        if not collab or collab.role not in ["admin", "owner"]:
            raise HTTPException(status_code=403, detail="Only owner or admin can manage collaborators")
    
    # Check if already collaborator
    existing = session.exec(
        select(Collaborator).where(
            Collaborator.app_id == app_id,
            Collaborator.user_id == req.user_id
        )
    ).first()
    
    if existing:
        existing.role = req.role
        session.add(existing)
        session.commit()
        return {"ok": True, "message": "Collaborator role updated"}
    
    new_collab = Collaborator(
        app_id=app_id,
        user_id=req.user_id,
        role=req.role,
        added_by=user_id
    )
    session.add(new_collab)
    session.commit()
    
    return {"ok": True, "message": f"Added {req.user_id} as {req.role}"}


@app.delete("/apps/{app_id}/collaborators/{collab_user_id}")
def remove_collaborator(
    app_id: str,
    collab_user_id: str,
    session: Session = Depends(get_session),
    user: UserInfo = Depends(require_user)
):
    """Remove a collaborator from an app."""
    app = session.get(App, app_id)
    if not app:
        raise HTTPException(status_code=404, detail="App not found")
    
    user_id = get_user_id(user)
    
    # Only owner or admin can remove collaborators
    if app.owner_id != user_id:
        collab = session.exec(
            select(Collaborator).where(
                Collaborator.app_id == app_id,
                Collaborator.user_id == user_id
            )
        ).first()
        if not collab or collab.role not in ["admin", "owner"]:
            raise HTTPException(status_code=403, detail="Only owner or admin can manage collaborators")
    
    # Cannot remove owner
    if collab_user_id == app.owner_id:
        raise HTTPException(status_code=400, detail="Cannot remove owner")
    
    to_remove = session.exec(
        select(Collaborator).where(
            Collaborator.app_id == app_id,
            Collaborator.user_id == collab_user_id
        )
    ).first()
    
    if not to_remove:
        raise HTTPException(status_code=404, detail="Collaborator not found")
    
    session.delete(to_remove)
    session.commit()
    
    return {"ok": True, "message": f"Removed {collab_user_id}"}


# -------------------- Run Routes --------------------

class RunAppResponse(BaseModel):
    app_id: str
    status: str
    port: Optional[int]
    url: Optional[str]
    started_at: datetime


@app.post("/apps/{app_id}/run", response_model=RunAppResponse)
def run_app(
    app_id: str,
    background_tasks: BackgroundTasks,
    session: Session = Depends(get_session),
    user: UserInfo = Depends(require_user)
):
    """Start running an app."""
    app = session.get(App, app_id)
    if not app:
        raise HTTPException(status_code=404, detail="App not found")
    
    if not check_app_permission(app, user, session, min_role="viewer"):
        raise HTTPException(status_code=403, detail="Access denied")
    
    if app.status != "active":
        raise HTTPException(status_code=400, detail="App is not active")
    
    user_id = get_user_id(user)
    
    # Check if already running
    existing_run = session.exec(
        select(AppRun).where(
            AppRun.app_id == app_id,
            AppRun.status.in_(["starting", "running"])
        )
    ).first()
    
    if existing_run:
        # Check with runner
        try:
            resp = requests.get(f"{RUNNER_BASE_URL}/status/{app_id}", timeout=10)
            if resp.status_code == 200:
                status_data = resp.json()
                if status_data.get("status") == "running":
                    return RunAppResponse(
                        app_id=app_id,
                        status="running",
                        port=status_data.get("port"),
                        url=f"{RUNNER_BASE_URL.replace(':8000', '')}:{status_data.get('port')}/app/{app_id}",
                        started_at=existing_run.started_at
                    )
        except Exception:
            pass
        
        # Mark as stopped
        existing_run.status = "stopped"
        existing_run.stopped_at = datetime.utcnow()
        session.add(existing_run)
        session.commit()
    
    # Start via runner service
    try:
        resp = requests.post(
            f"{RUNNER_BASE_URL}/run",
            json={
                "app_id": app_id,
                "app_path": app.entry_file
            },
            timeout=30
        )
        
        if resp.status_code != 200:
            raise HTTPException(status_code=500, detail=f"Runner error: {resp.text}")
        
        run_data = resp.json()
        
        # Record run
        app_run = AppRun(
            app_id=app_id,
            user_id=user_id,
            port=run_data.get("port"),
            runner_url=RUNNER_BASE_URL,
            status="running"
        )
        session.add(app_run)
        session.commit()
        
        # Build URL
        runner_host = RUNNER_BASE_URL.replace("http://", "").replace("https://", "").split(":")[0]
        url = f"http://{runner_host}:{run_data.get('port')}/app/{app_id}"
        
        return RunAppResponse(
            app_id=app_id,
            status="running",
            port=run_data.get("port"),
            url=url,
            started_at=app_run.started_at
        )
    
    except requests.RequestException as e:
        raise HTTPException(status_code=503, detail=f"Runner service unavailable: {str(e)}")


@app.post("/apps/{app_id}/stop")
def stop_app(
    app_id: str,
    session: Session = Depends(get_session),
    user: UserInfo = Depends(require_user)
):
    """Stop a running app."""
    app = session.get(App, app_id)
    if not app:
        raise HTTPException(status_code=404, detail="App not found")
    
    if not check_app_permission(app, user, session, min_role="viewer"):
        raise HTTPException(status_code=403, detail="Access denied")
    
    # Stop via runner
    try:
        resp = requests.post(
            f"{RUNNER_BASE_URL}/stop",
            json={"app_id": app_id},
            timeout=10
        )
    except Exception:
        pass
    
    # Update run records
    runs = session.exec(
        select(AppRun).where(
            AppRun.app_id == app_id,
            AppRun.status.in_(["starting", "running"])
        )
    ).all()
    
    for run in runs:
        run.status = "stopped"
        run.stopped_at = datetime.utcnow()
        session.add(run)
    
    session.commit()
    
    return {"ok": True, "message": f"App {app_id} stopped"}


@app.get("/apps/{app_id}/status")
def get_app_status(
    app_id: str,
    session: Session = Depends(get_session),
    user: UserInfo = Depends(require_user)
):
    """Get app execution status."""
    app = session.get(App, app_id)
    if not app:
        raise HTTPException(status_code=404, detail="App not found")
    
    if not check_app_permission(app, user, session, min_role="viewer"):
        raise HTTPException(status_code=403, detail="Access denied")
    
    # Check with runner
    try:
        resp = requests.get(f"{RUNNER_BASE_URL}/status/{app_id}", timeout=10)
        if resp.status_code == 200:
            return resp.json()
    except Exception:
        pass
    
    return {
        "app_id": app_id,
        "status": "stopped",
        "port": None,
        "url": None
    }


# Legacy stub route (keep for compatibility)
class RunRequest(BaseModel):
    prompt: str


@app.post("/run")
def run_legacy(body: RunRequest, user=Depends(require_user)):
    return {
        "user": user.dict(),
        "input": body.prompt,
        "output": f"(stub) you said: {body.prompt}",
        "note": "Use /apps/{app_id}/run for full app execution"
    }
