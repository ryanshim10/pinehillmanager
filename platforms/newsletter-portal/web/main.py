import os
import logging
from datetime import datetime
from typing import Optional, List

from fastapi import FastAPI, Depends, HTTPException, Query, Body
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, EmailStr
from sqlmodel import Session, select, func

from shared.db import make_engine, init_db
from shared.models import (
    User, TempPassword, Source, Keyword, Item, Newsletter, SendLog, Schedule,
    UserRole, SourceType, KeywordBucket, NewsletterStatus
)
from shared.auth import (
    hash_password, verify_password, create_temp_password, verify_temp_password,
    record_login_failure, reset_login_failures, is_account_locked, unlock_account,
    create_token, parse_token
)
from shared.mail import get_mailer, Mailer
from shared.llm import NewsletterGenerator

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Initialize FastAPI
app = FastAPI(
    title="현대위아 뉴스레터 포탈 API",
    description="AI 기반 뉴스레터 생성 및 발송 시스템",
    version="1.0.0"
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Configuration
DATABASE_URL = os.environ["DATABASE_URL"]
SESSION_SECRET = os.environ.get("SESSION_SECRET", "")
BOOTSTRAP_ADMIN_EMAIL = os.environ.get("BOOTSTRAP_ADMIN_EMAIL", "")

# Database engine
engine = make_engine(DATABASE_URL)

# Security
security = HTTPBearer(auto_error=False)


def get_session():
    """Get database session"""
    with Session(engine) as session:
        yield session


def get_mailer_instance() -> Mailer:
    """Get mailer instance"""
    return get_mailer()


def _require_user(
    creds: Optional[HTTPAuthorizationCredentials] = Depends(security),
    session: Session = Depends(get_session),
) -> User:
    """Require authenticated user"""
    if not creds:
        raise HTTPException(status_code=401, detail="인증이 필요합니다")
    
    email = parse_token(creds.credentials)
    if not email:
        raise HTTPException(status_code=401, detail="유효하지 않은 토큰입니다")
    
    user = session.exec(select(User).where(User.email == email)).first()
    if not user:
        raise HTTPException(status_code=401, detail="사용자를 찾을 수 없습니다")
    
    if not user.enabled:
        raise HTTPException(status_code=403, detail="비활성화된 계정입니다")
    
    if is_account_locked(user):
        raise HTTPException(status_code=403, detail="계정이 잠겼습니다. 관리자에게 문의하세요.")
    
    return user


def _require_admin(user: User = Depends(_require_user)) -> User:
    """Require admin user"""
    if user.role != UserRole.ADMIN:
        raise HTTPException(status_code=403, detail="관리자 권한이 필요합니다")
    return user


def _bootstrap_admin(session: Session):
    """Create initial admin user"""
    if not BOOTSTRAP_ADMIN_EMAIL:
        return
    
    admin = session.exec(
        select(User).where(User.email == BOOTSTRAP_ADMIN_EMAIL)
    ).first()
    
    if admin:
        return
    
    logger.info(f"Creating bootstrap admin: {BOOTSTRAP_ADMIN_EMAIL}")
    
    admin = User(
        email=BOOTSTRAP_ADMIN_EMAIL,
        role=UserRole.ADMIN,
        enabled=True,
        password_hash=""
    )
    session.add(admin)
    session.commit()
    session.refresh(admin)
    
    # Generate temp password
    code = create_temp_password(session, admin)
    
    # Send email
    mailer = get_mailer()
    result = mailer.send_temp_password(admin.email, code)
    
    if result["success"]:
        logger.info(f"Temp password sent to {admin.email}")
    else:
        logger.warning(f"Failed to send temp password: {result['error']}")


@app.on_event("startup")
def on_startup():
    """Initialize on startup"""
    init_db(engine)
    
    with Session(engine) as session:
        _bootstrap_admin(session)


# ============== Auth Routes ==============

class LoginRequest(BaseModel):
    email: str
    password: str


class LoginResponse(BaseModel):
    token: str
    role: str
    email: str


@app.post("/auth/login", response_model=LoginResponse)
def login(
    req: LoginRequest,
    session: Session = Depends(get_session)
):
    """Login with email and password"""
    user = session.exec(select(User).where(User.email == req.email)).first()
    
    # Don't leak whether user exists
    if not user:
        raise HTTPException(status_code=401, detail="이메일 또는 비밀번호가 올바르지 않습니다")
    
    if not user.enabled:
        raise HTTPException(status_code=401, detail="이메일 또는 비밀번호가 올바르지 않습니다")
    
    if is_account_locked(user):
        raise HTTPException(status_code=403, detail="계정이 잠겼습니다. 임시비밀번호를 요청하세요.")
    
    # Check password (could be regular or temp)
    is_valid = verify_password(req.password, user.password_hash)
    
    # Also check recent temp password
    if not is_valid:
        is_valid = verify_temp_password(session, user.id, req.password)
    
    if not is_valid:
        is_locked = record_login_failure(session, user)
        if is_locked:
            raise HTTPException(status_code=403, detail="5회 실패로 계정이 잠겼습니다. 임시비밀번호를 요청하세요.")
        raise HTTPException(status_code=401, detail="이메일 또는 비밀번호가 올바르지 않습니다")
    
    # Success
    reset_login_failures(session, user)
    
    return LoginResponse(
        token=create_token(user),
        role=user.role,
        email=user.email
    )


@app.post("/auth/request-temp-password")
def request_temp_password(
    email: str = Body(..., embed=True),
    session: Session = Depends(get_session),
    mailer: Mailer = Depends(get_mailer_instance)
):
    """Request temporary password"""
    user = session.exec(select(User).where(User.email == email)).first()
    
    if user and user.enabled:
        code = create_temp_password(session, user)
        mailer.send_temp_password(email, code)
        logger.info(f"Temp password requested for {email}")
    
    # Always return same response to prevent user enumeration
    return {"ok": True, "message": "이메일이 발송되었습니다 (존재하는 경우)"}


class SetPasswordRequest(BaseModel):
    new_password: str


@app.post("/auth/set-password")
def set_password(
    req: SetPasswordRequest,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """Set new password (must be logged in)"""
    if len(req.new_password) < 8:
        raise HTTPException(status_code=400, detail="비밀번호는 8자 이상이어야 합니다")
    
    user.password_hash = hash_password(req.new_password)
    user.updated_at = datetime.utcnow()
    session.add(user)
    session.commit()
    
    logger.info(f"Password changed for {user.email}")
    return {"ok": True}


@app.get("/me")
def me(user: User = Depends(_require_user)):
    """Get current user info"""
    return {
        "id": user.id,
        "email": user.email,
        "role": user.role,
        "enabled": user.enabled,
        "created_at": user.created_at
    }


# ============== User Management (Admin) ==============

class CreateUserRequest(BaseModel):
    email: EmailStr
    role: str = "user"


class UpdateUserRequest(BaseModel):
    enabled: Optional[bool] = None
    role: Optional[str] = None


@app.post("/admin/users", response_model=dict)
def create_user(
    req: CreateUserRequest,
    admin: User = Depends(_require_admin),
    session: Session = Depends(get_session),
    mailer: Mailer = Depends(get_mailer_instance)
):
    """Create new user (admin only)"""
    # Check if email exists
    existing = session.exec(select(User).where(User.email == req.email)).first()
    if existing:
        raise HTTPException(status_code=400, detail="이미 등록된 이메일입니다")
    
    # Validate role
    if req.role not in [UserRole.ADMIN, UserRole.USER]:
        raise HTTPException(status_code=400, detail="유효하지 않은 역할입니다")
    
    user = User(
        email=req.email,
        role=req.role,
        enabled=True,
        password_hash=""
    )
    session.add(user)
    session.commit()
    session.refresh(user)
    
    # Send temp password
    code = create_temp_password(session, user)
    mailer.send_temp_password(user.email, code)
    
    logger.info(f"User created by {admin.email}: {user.email}")
    return {"ok": True, "id": user.id, "email": user.email}


@app.get("/admin/users")
def list_users(
    skip: int = 0,
    limit: int = 100,
    admin: User = Depends(_require_admin),
    session: Session = Depends(get_session)
):
    """List all users (admin only)"""
    users = session.exec(
        select(User).offset(skip).limit(limit)
    ).all()
    
    return {
        "users": [
            {
                "id": u.id,
                "email": u.email,
                "role": u.role,
                "enabled": u.enabled,
                "locked_at": u.locked_at,
                "failed_login_count": u.failed_login_count,
                "created_at": u.created_at
            }
            for u in users
        ],
        "total": session.exec(select(func.count(User.id))).one()
    }


@app.get("/admin/users/{user_id}")
def get_user(
    user_id: int,
    admin: User = Depends(_require_admin),
    session: Session = Depends(get_session)
):
    """Get user details (admin only)"""
    user = session.get(User, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다")
    
    return {
        "id": user.id,
        "email": user.email,
        "role": user.role,
        "enabled": user.enabled,
        "locked_at": u.locked_at,
        "failed_login_count": user.failed_login_count,
        "created_at": user.created_at,
        "updated_at": user.updated_at
    }


@app.patch("/admin/users/{user_id}")
def update_user(
    user_id: int,
    req: UpdateUserRequest,
    admin: User = Depends(_require_admin),
    session: Session = Depends(get_session)
):
    """Update user (admin only)"""
    user = session.get(User, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다")
    
    if req.enabled is not None:
        user.enabled = req.enabled
    
    if req.role is not None:
        if req.role not in [UserRole.ADMIN, UserRole.USER]:
            raise HTTPException(status_code=400, detail="유효하지 않은 역할입니다")
        user.role = req.role
    
    user.updated_at = datetime.utcnow()
    session.add(user)
    session.commit()
    
    logger.info(f"User {user_id} updated by {admin.email}")
    return {"ok": True}


@app.post("/admin/users/{user_id}/unlock")
def unlock_user(
    user_id: int,
    admin: User = Depends(_require_admin),
    session: Session = Depends(get_session)
):
    """Unlock locked user account (admin only)"""
    user = session.get(User, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다")
    
    unlock_account(session, user)
    logger.info(f"User {user_id} unlocked by {admin.email}")
    return {"ok": True}


@app.delete("/admin/users/{user_id}")
def delete_user(
    user_id: int,
    admin: User = Depends(_require_admin),
    session: Session = Depends(get_session)
):
    """Delete user (admin only)"""
    user = session.get(User, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다")
    
    # Prevent self-deletion
    if user.id == admin.id:
        raise HTTPException(status_code=400, detail="자신을 삭제할 수 없습니다")
    
    session.delete(user)
    session.commit()
    
    logger.info(f"User {user_id} deleted by {admin.email}")
    return {"ok": True}


# ============== Source Management ==============

class CreateSourceRequest(BaseModel):
    type: str
    name: str
    url: str
    enabled: bool = True
    config: Optional[str] = None


class UpdateSourceRequest(BaseModel):
    name: Optional[str] = None
    url: Optional[str] = None
    enabled: Optional[bool] = None
    config: Optional[str] = None


@app.post("/sources")
def create_source(
    req: CreateSourceRequest,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """Create new source"""
    # Validate type
    valid_types = [t.value for t in SourceType]
    if req.type not in valid_types:
        raise HTTPException(status_code=400, detail=f"유효하지 않은 소스 타입입니다. 사용 가능: {valid_types}")
    
    source = Source(
        user_id=user.id,
        type=req.type,
        name=req.name,
        url=req.url,
        enabled=req.enabled,
        config=req.config
    )
    session.add(source)
    session.commit()
    session.refresh(source)
    
    logger.info(f"Source created by {user.email}: {source.id}")
    return {"ok": True, "id": source.id}


@app.get("/sources")
def list_sources(
    type: Optional[str] = None,
    enabled: Optional[bool] = None,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """List user's sources"""
    query = select(Source).where(Source.user_id == user.id)
    
    if type:
        query = query.where(Source.type == type)
    if enabled is not None:
        query = query.where(Source.enabled == enabled)
    
    sources = session.exec(query.order_by(Source.created_at.desc())).all()
    
    return {
        "sources": [
            {
                "id": s.id,
                "type": s.type,
                "name": s.name,
                "url": s.url,
                "enabled": s.enabled,
                "last_run_at": s.last_run_at,
                "last_status": s.last_status,
                "fetch_count": s.fetch_count,
                "created_at": s.created_at
            }
            for s in sources
        ]
    }


@app.get("/sources/{source_id}")
def get_source(
    source_id: int,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """Get source details"""
    source = session.get(Source, source_id)
    if not source or source.user_id != user.id:
        raise HTTPException(status_code=404, detail="소스를 찾을 수 없습니다")
    
    return {
        "id": source.id,
        "type": source.type,
        "name": source.name,
        "url": source.url,
        "enabled": source.enabled,
        "config": source.config,
        "last_run_at": source.last_run_at,
        "last_status": source.last_status,
        "last_error": source.last_error,
        "fetch_count": source.fetch_count,
        "created_at": source.created_at
    }


@app.patch("/sources/{source_id}")
def update_source(
    source_id: int,
    req: UpdateSourceRequest,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """Update source"""
    source = session.get(Source, source_id)
    if not source or source.user_id != user.id:
        raise HTTPException(status_code=404, detail="소스를 찾을 수 없습니다")
    
    if req.name is not None:
        source.name = req.name
    if req.url is not None:
        source.url = req.url
    if req.enabled is not None:
        source.enabled = req.enabled
    if req.config is not None:
        source.config = req.config
    
    source.updated_at = datetime.utcnow()
    session.add(source)
    session.commit()
    
    return {"ok": True}


@app.delete("/sources/{source_id}")
def delete_source(
    source_id: int,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """Delete source"""
    source = session.get(Source, source_id)
    if not source or source.user_id != user.id:
        raise HTTPException(status_code=404, detail="소스를 찾을 수 없습니다")
    
    session.delete(source)
    session.commit()
    
    logger.info(f"Source {source_id} deleted by {user.email}")
    return {"ok": True}


@app.post("/sources/{source_id}/test")
def test_source(
    source_id: int,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """Test source connection"""
    from shared.connectors import get_connector
    
    source = session.get(Source, source_id)
    if not source or source.user_id != user.id:
        raise HTTPException(status_code=404, detail="소스를 찾을 수 없습니다")
    
    try:
        config = {}
        if source.config:
            import json
            config = json.loads(source.config)
        
        connector = get_connector(source.type, source.id, source.url, config)
        result = connector.test_connection()
        
        return result
        
    except Exception as e:
        logger.error(f"Source test error: {e}")
        return {"success": False, "error": str(e)}


# ============== Keyword Management ==============

class CreateKeywordRequest(BaseModel):
    bucket: str
    text: str
    weight: float = 1.0


class UpdateKeywordRequest(BaseModel):
    bucket: Optional[str] = None
    text: Optional[str] = None
    weight: Optional[float] = None


@app.post("/keywords")
def create_keyword(
    req: CreateKeywordRequest,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """Create new keyword"""
    # Validate bucket
    valid_buckets = [b.value for b in KeywordBucket]
    if req.bucket not in valid_buckets:
        raise HTTPException(status_code=400, detail=f"유효하지 않은 버킷입니다. 사용 가능: {valid_buckets}")
    
    keyword = Keyword(
        user_id=user.id,
        bucket=req.bucket,
        text=req.text,
        weight=req.weight
    )
    session.add(keyword)
    session.commit()
    session.refresh(keyword)
    
    return {"ok": True, "id": keyword.id}


@app.get("/keywords")
def list_keywords(
    bucket: Optional[str] = None,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """List user's keywords"""
    query = select(Keyword).where(Keyword.user_id == user.id)
    
    if bucket:
        query = query.where(Keyword.bucket == bucket)
    
    keywords = session.exec(query.order_by(Keyword.bucket, Keyword.text)).all()
    
    return {
        "keywords": [
            {
                "id": k.id,
                "bucket": k.bucket,
                "text": k.text,
                "weight": k.weight,
                "created_at": k.created_at
            }
            for k in keywords
        ]
    }


@app.patch("/keywords/{keyword_id}")
def update_keyword(
    keyword_id: int,
    req: UpdateKeywordRequest,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """Update keyword"""
    keyword = session.get(Keyword, keyword_id)
    if not keyword or keyword.user_id != user.id:
        raise HTTPException(status_code=404, detail="키워드를 찾을 수 없습니다")
    
    if req.bucket is not None:
        valid_buckets = [b.value for b in KeywordBucket]
        if req.bucket not in valid_buckets:
            raise HTTPException(status_code=400, detail="유효하지 않은 버킷입니다")
        keyword.bucket = req.bucket
    
    if req.text is not None:
        keyword.text = req.text
    
    if req.weight is not None:
        keyword.weight = req.weight
    
    keyword.updated_at = datetime.utcnow()
    session.add(keyword)
    session.commit()
    
    return {"ok": True}


@app.delete("/keywords/{keyword_id}")
def delete_keyword(
    keyword_id: int,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """Delete keyword"""
    keyword = session.get(Keyword, keyword_id)
    if not keyword or keyword.user_id != user.id:
        raise HTTPException(status_code=404, detail="키워드를 찾을 수 없습니다")
    
    session.delete(keyword)
    session.commit()
    
    return {"ok": True}


# ============== Item Management ==============

@app.get("/items")
def list_items(
    source_id: Optional[int] = None,
    unread_only: bool = False,
    skip: int = 0,
    limit: int = 50,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """List items from user's sources"""
    query = select(Item).where(Item.user_id == user.id)
    
    if source_id:
        query = query.where(Item.source_id == source_id)
    
    if unread_only:
        query = query.where(Item.is_processed == False)
    
    query = query.order_by(Item.published_at.desc()).offset(skip).limit(limit)
    items = session.exec(query).all()
    
    return {
        "items": [
            {
                "id": item.id,
                "source_id": item.source_id,
                "title": item.title,
                "url": item.url,
                "author": item.author,
                "published_at": item.published_at,
                "content_text": item.content_text[:500] if item.content_text else "",
                "summary": item.summary,
                "relevance_score": item.relevance_score,
                "is_processed": item.is_processed,
                "created_at": item.created_at
            }
            for item in items
        ]
    }


# ============== Newsletter Management ==============

class CreateNewsletterRequest(BaseModel):
    subject: str
    html: str
    text: str
    item_ids: List[int] = []


class GenerateNewsletterRequest(BaseModel):
    item_ids: List[int]
    subject_template: str = "[뉴스레터] 주간 업계 동향"


@app.post("/newsletters")
def create_newsletter(
    req: CreateNewsletterRequest,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """Create manual newsletter draft"""
    newsletter = Newsletter(
        user_id=user.id,
        status=NewsletterStatus.DRAFT,
        subject=req.subject,
        html=req.html,
        text=req.text,
        item_count=len(req.item_ids),
        generated_by="manual"
    )
    session.add(newsletter)
    session.commit()
    session.refresh(newsletter)
    
    # Link items
    from shared.models import NewsletterItem
    for order, item_id in enumerate(req.item_ids):
        link = NewsletterItem(
            newsletter_id=newsletter.id,
            item_id=item_id,
            order=order
        )
        session.add(link)
    
    session.commit()
    
    return {"ok": True, "id": newsletter.id}


@app.post("/newsletters/generate")
def generate_newsletter(
    req: GenerateNewsletterRequest,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """Generate newsletter using AI"""
    # Get items
    items = []
    for item_id in req.item_ids:
        item = session.get(Item, item_id)
        if item and item.user_id == user.id:
            items.append({
                "id": item.id,
                "title": item.title,
                "url": item.url,
                "content_text": item.content_text,
                "published_at": item.published_at
            })
    
    if not items:
        raise HTTPException(status_code=400, detail="선택된 아이템이 없습니다")
    
    # Generate
    generator = NewsletterGenerator()
    result = generator.generate(items, req.subject_template)
    
    # Save as recommended
    newsletter = Newsletter(
        user_id=user.id,
        status=NewsletterStatus.RECOMMENDED,
        subject=result["subject"],
        html=result["html"],
        text=result["text"],
        item_count=len(items),
        generated_by="ai"
    )
    session.add(newsletter)
    session.commit()
    session.refresh(newsletter)
    
    # Link items
    from shared.models import NewsletterItem
    for order, item in enumerate(items):
        link = NewsletterItem(
            newsletter_id=newsletter.id,
            item_id=item["id"],
            order=order
        )
        session.add(link)
    
    session.commit()
    
    return {
        "ok": True,
        "id": newsletter.id,
        "subject": result["subject"],
        "preview": result["text"][:500] if result["text"] else ""
    }


@app.get("/newsletters")
def list_newsletters(
    status: Optional[str] = None,
    skip: int = 0,
    limit: int = 50,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """List newsletters"""
    query = select(Newsletter).where(Newsletter.user_id == user.id)
    
    if status:
        query = query.where(Newsletter.status == status)
    
    query = query.order_by(Newsletter.created_at.desc()).offset(skip).limit(limit)
    newsletters = session.exec(query).all()
    
    return {
        "newsletters": [
            {
                "id": n.id,
                "status": n.status,
                "subject": n.subject,
                "item_count": n.item_count,
                "generated_by": n.generated_by,
                "sent_at": n.sent_at,
                "sent_count": n.sent_count,
                "created_at": n.created_at
            }
            for n in newsletters
        ]
    }


@app.get("/newsletters/{newsletter_id}")
def get_newsletter(
    newsletter_id: int,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """Get newsletter details"""
    newsletter = session.get(Newsletter, newsletter_id)
    if not newsletter or newsletter.user_id != user.id:
        raise HTTPException(status_code=404, detail="뉴스레터를 찾을 수 없습니다")
    
    return {
        "id": newsletter.id,
        "status": newsletter.status,
        "subject": newsletter.subject,
        "html": newsletter.html,
        "text": newsletter.text,
        "item_count": newsletter.item_count,
        "generated_by": newsletter.generated_by,
        "sent_at": newsletter.sent_at,
        "sent_count": newsletter.sent_count,
        "error_count": newsletter.error_count,
        "created_at": newsletter.created_at
    }


class SendNewsletterRequest(BaseModel):
    to_emails: List[EmailStr]


@app.post("/newsletters/{newsletter_id}/send")
def send_newsletter(
    newsletter_id: int,
    req: SendNewsletterRequest,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session),
    mailer: Mailer = Depends(get_mailer_instance)
):
    """Send newsletter to recipients"""
    newsletter = session.get(Newsletter, newsletter_id)
    if not newsletter or newsletter.user_id != user.id:
        raise HTTPException(status_code=404, detail="뉴스레터를 찾을 수 없습니다")
    
    sent_count = 0
    error_count = 0
    
    for email in req.to_emails:
        log = SendLog(
            newsletter_id=newsletter.id,
            recipient_email=email,
            status=SendStatus.PENDING
        )
        session.add(log)
        session.commit()
        
        # Send
        result = mailer.send_newsletter(
            to_email=email,
            subject=newsletter.subject,
            html_content=newsletter.html,
            text_content=newsletter.text
        )
        
        if result["success"]:
            log.status = SendStatus.OK
            log.sent_at = datetime.utcnow()
            sent_count += 1
        else:
            log.status = SendStatus.ERROR
            log.error = result["error"]
            error_count += 1
        
        session.add(log)
        session.commit()
    
    # Update newsletter
    newsletter.status = NewsletterStatus.SENT
    newsletter.sent_at = datetime.utcnow()
    newsletter.sent_count += sent_count
    newsletter.error_count += error_count
    session.add(newsletter)
    session.commit()
    
    logger.info(f"Newsletter {newsletter_id} sent by {user.email}: {sent_count} ok, {error_count} errors")
    
    return {
        "ok": True,
        "sent_count": sent_count,
        "error_count": error_count
    }


@app.delete("/newsletters/{newsletter_id}")
def delete_newsletter(
    newsletter_id: int,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """Delete newsletter"""
    newsletter = session.get(Newsletter, newsletter_id)
    if not newsletter or newsletter.user_id != user.id:
        raise HTTPException(status_code=404, detail="뉴스레터를 찾을 수 없습니다")
    
    session.delete(newsletter)
    session.commit()
    
    return {"ok": True}


# ============== Schedule Management ==============

class CreateScheduleRequest(BaseModel):
    name: str
    cron_expression: str = "0 9 * * 1"
    recipient_emails: str
    subject_template: str = "[뉴스레터] 주간 업계 동향"
    auto_generate: bool = True
    max_items: int = 10
    enabled: bool = True


@app.post("/schedules")
def create_schedule(
    req: CreateScheduleRequest,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """Create schedule"""
    schedule = Schedule(
        user_id=user.id,
        name=req.name,
        cron_expression=req.cron_expression,
        recipient_emails=req.recipient_emails,
        subject_template=req.subject_template,
        auto_generate=req.auto_generate,
        max_items=req.max_items,
        enabled=req.enabled
    )
    session.add(schedule)
    session.commit()
    session.refresh(schedule)
    
    return {"ok": True, "id": schedule.id}


@app.get("/schedules")
def list_schedules(
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """List schedules"""
    schedules = session.exec(
        select(Schedule).where(Schedule.user_id == user.id)
    ).all()
    
    return {
        "schedules": [
            {
                "id": s.id,
                "name": s.name,
                "cron_expression": s.cron_expression,
                "enabled": s.enabled,
                "recipient_emails": s.recipient_emails,
                "auto_generate": s.auto_generate,
                "last_run_at": s.last_run_at,
                "created_at": s.created_at
            }
            for s in schedules
        ]
    }


@app.delete("/schedules/{schedule_id}")
def delete_schedule(
    schedule_id: int,
    user: User = Depends(_require_user),
    session: Session = Depends(get_session)
):
    """Delete schedule"""
    schedule = session.get(Schedule, schedule_id)
    if not schedule or schedule.user_id != user.id:
        raise HTTPException(status_code=404, detail="스케줄을 찾을 수 없습니다")
    
    session.delete(schedule)
    session.commit()
    
    return {"ok": True}


# ============== Health Check ==============

@app.get("/health")
def health_check():
    """Health check endpoint"""
    return {"status": "ok", "timestamp": datetime.utcnow()}
