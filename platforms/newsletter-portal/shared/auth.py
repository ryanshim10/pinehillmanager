import secrets
import hashlib
from datetime import datetime, timedelta
from typing import Optional

from passlib.context import CryptContext
from sqlmodel import Session, select

from shared.models import User, TempPassword

# Password hashing
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

# Constants
MAX_LOGIN_ATTEMPTS = 5
LOCKOUT_DURATION_MINUTES = 30
TEMP_PASSWORD_EXPIRY_MINUTES = 30


def hash_password(password: str) -> str:
    """Hash a password using bcrypt"""
    return pwd_context.hash(password)


def verify_password(password: str, hashed: str) -> bool:
    """Verify a password against its hash"""
    try:
        return pwd_context.verify(password, hashed)
    except Exception:
        return False


def generate_temp_code() -> str:
    """Generate a 6-digit numeric code"""
    return f"{secrets.randbelow(1_000_000):06d}"


def create_temp_password(session: Session, user: User) -> str:
    """Create and store a temporary password for a user"""
    code = generate_temp_code()
    expires = datetime.utcnow() + timedelta(minutes=TEMP_PASSWORD_EXPIRY_MINUTES)
    
    tp = TempPassword(
        user_id=user.id,
        code_hash=hash_password(code),
        expires_at=expires
    )
    session.add(tp)
    
    # Reset lock status
    user.failed_login_count = 0
    user.locked_at = None
    user.updated_at = datetime.utcnow()
    session.add(user)
    
    session.commit()
    return code


def verify_temp_password(session: Session, user_id: int, code: str) -> bool:
    """Verify a temporary password"""
    # Get the most recent unused temp password
    temp_pass = session.exec(
        select(TempPassword)
        .where(TempPassword.user_id == user_id)
        .where(TempPassword.used_at == None)
        .where(TempPassword.expires_at > datetime.utcnow())
        .order_by(TempPassword.created_at.desc())
    ).first()
    
    if not temp_pass:
        return False
    
    if not verify_password(code, temp_pass.code_hash):
        return False
    
    # Mark as used
    temp_pass.used_at = datetime.utcnow()
    session.add(temp_pass)
    session.commit()
    
    return True


def record_login_failure(session: Session, user: User) -> bool:
    """
    Record a failed login attempt.
    Returns True if account is now locked.
    """
    user.failed_login_count += 1
    user.updated_at = datetime.utcnow()
    
    if user.failed_login_count >= MAX_LOGIN_ATTEMPTS:
        user.locked_at = datetime.utcnow()
        session.add(user)
        session.commit()
        return True
    
    session.add(user)
    session.commit()
    return False


def reset_login_failures(session: Session, user: User):
    """Reset login failure count after successful login"""
    user.failed_login_count = 0
    user.locked_at = None
    user.updated_at = datetime.utcnow()
    session.add(user)
    session.commit()


def is_account_locked(user: User) -> bool:
    """Check if account is currently locked"""
    if user.locked_at is None:
        return False
    
    # Check if lockout duration has passed
    unlock_time = user.locked_at + timedelta(minutes=LOCKOUT_DURATION_MINUTES)
    if datetime.utcnow() >= unlock_time:
        return False
    
    return True


def unlock_account(session: Session, user: User):
    """Manually unlock an account"""
    user.locked_at = None
    user.failed_login_count = 0
    user.updated_at = datetime.utcnow()
    session.add(user)
    session.commit()


# ============== Simple Token System ==============

def create_token(user: User) -> str:
    """Create a simple token for a user"""
    return f"user:{user.email}"


def parse_token(token: str) -> Optional[str]:
    """Parse token and return email"""
    if not token or not token.startswith("user:"):
        return None
    return token.split(":", 1)[1]


def generate_secure_token(length: int = 32) -> str:
    """Generate a cryptographically secure random token"""
    return secrets.token_urlsafe(length)
