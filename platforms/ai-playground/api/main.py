import os
from datetime import datetime
from typing import Optional

from fastapi import FastAPI, Header, HTTPException, Depends
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel
from passlib.context import CryptContext

from sqlmodel import SQLModel, Field, Session, create_engine, select

app = FastAPI(title="AI Playground API", version="0.2.0")

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


_engine = create_engine(DATABASE_URL, pool_pre_ping=True)


# -------------------- Auth helpers --------------------
class LoginRequest(BaseModel):
    email: str
    password: str


class LoginResponse(BaseModel):
    token: str
    email: str


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
):
    # dev: email/password 토큰만 허용
    if AUTH_MODE == "dev":
        if not creds:
            raise HTTPException(status_code=401, detail="Need Authorization token")
        email = parse_token(creds.credentials)
        if not email:
            raise HTTPException(status_code=401, detail="Invalid token")
        u = session.exec(select(User).where(User.email == email)).first()
        if not u:
            raise HTTPException(status_code=401, detail="User not found")
        return {"email": u.email}

    # sso: 헤더 우선, 없으면 토큰도 허용
    if creds:
        email = parse_token(creds.credentials)
        if email:
            return {"email": email}

    # loginid 헤더가 있으면 최우선
    if x_sso_loginid:
        return {"loginid": x_sso_loginid, "empid": x_sso_empid}

    # 사번만 있으면 디렉토리에서 loginid 조회
    if x_sso_empid:
        du = session.get(IFOrgUser, x_sso_empid)
        if not du:
            raise HTTPException(status_code=403, detail="Empid not found in if_org_user")
        if du.company_code and SSO_ALLOWED_COMPANY_CODE and du.company_code != SSO_ALLOWED_COMPANY_CODE:
            raise HTTPException(status_code=403, detail="Forbidden company")
        return {"loginid": du.loginid, "empid": du.code, "email": du.email, "name": du.name}

    raise HTTPException(status_code=401, detail="Missing SSO headers")


@app.on_event("startup")
def on_startup():
    SQLModel.metadata.create_all(_engine)

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


@app.post("/auth/login", response_model=LoginResponse)
def login(req: LoginRequest, session: Session = Depends(get_session)):
    if AUTH_MODE != "dev":
        raise HTTPException(status_code=404, detail="/auth/login is dev-only in playground")

    u = session.exec(select(User).where(User.email == req.email)).first()
    if not u or not verify_password(req.password, u.password_hash):
        raise HTTPException(status_code=401, detail="Invalid email/password")

    return LoginResponse(token=create_token(u.email), email=u.email)


class RunRequest(BaseModel):
    prompt: str


@app.post("/run")
def run(body: RunRequest, user=Depends(require_user)):
    return {
        "user": user,
        "input": body.prompt,
        "output": f"(stub) you said: {body.prompt}",
    }
