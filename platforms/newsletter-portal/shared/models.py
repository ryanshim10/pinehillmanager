from __future__ import annotations

from datetime import datetime
from typing import Optional, List
from enum import Enum

from sqlmodel import SQLModel, Field, Relationship


class UserRole(str, Enum):
    ADMIN = "admin"
    USER = "user"


class SourceType(str, Enum):
    LINKEDIN_PROFILE = "linkedin_profile"
    RSS = "rss"
    WEBSITE = "website"
    YOUTUBE_CHANNEL = "youtube_channel"
    EMAIL_NEWSLETTER = "email_newsletter"


class KeywordBucket(str, Enum):
    TOP = "top"
    IMPORTANT = "important"
    NORMAL = "normal"
    EXCLUDE = "exclude"


class NewsletterStatus(str, Enum):
    DRAFT = "draft"
    RECOMMENDED = "recommended"
    SENT = "sent"


class SendStatus(str, Enum):
    PENDING = "pending"
    OK = "ok"
    ERROR = "error"


# ============== User Models ==============

class User(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    email: str = Field(index=True, unique=True)
    role: str = Field(default="user", index=True)  # admin|user
    enabled: bool = Field(default=True)

    password_hash: str = Field(default="")

    failed_login_count: int = Field(default=0)
    locked_at: Optional[datetime] = Field(default=None)

    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)

    # Relationships
    sources: List["Source"] = Relationship(back_populates="user")
    keywords: List["Keyword"] = Relationship(back_populates="user")
    newsletters: List["Newsletter"] = Relationship(back_populates="user")


class TempPassword(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    user_id: int = Field(index=True, foreign_key="user.id")

    code_hash: str
    expires_at: datetime
    used_at: Optional[datetime] = Field(default=None)

    created_at: datetime = Field(default_factory=datetime.utcnow)


# ============== Source Models ==============

class Source(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    user_id: int = Field(index=True, foreign_key="user.id")

    type: str = Field(index=True)  # linkedin_profile|rss|website|youtube_channel|email_newsletter
    name: str = Field(default="")  # 사용자 지정 이름
    url: str
    enabled: bool = Field(default=True)
    config: Optional[str] = Field(default=None)  # JSON 설정 (LinkedIn cookies 등)

    last_run_at: Optional[datetime] = Field(default=None)
    last_status: Optional[str] = Field(default=None)
    last_error: Optional[str] = Field(default=None)
    fetch_count: int = Field(default=0)

    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)

    # Relationships
    user: Optional[User] = Relationship(back_populates="sources")
    items: List["Item"] = Relationship(back_populates="source")


# ============== Keyword Models ==============

class Keyword(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    user_id: int = Field(index=True, foreign_key="user.id")

    bucket: str = Field(index=True)  # top|important|normal|exclude
    text: str = Field(index=True)
    weight: float = Field(default=1.0)  # 가중치

    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)

    # Relationships
    user: Optional[User] = Relationship(back_populates="keywords")


# ============== Item Models ==============

class Item(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    source_id: int = Field(index=True, foreign_key="source.id")
    user_id: int = Field(index=True, foreign_key="user.id")  # 소스 소유자

    title: str
    url: str = Field(index=True)
    author: Optional[str] = Field(default=None)
    published_at: Optional[datetime] = Field(default=None, index=True)

    content_text: str = Field(default="")
    content_html: Optional[str] = Field(default=None)
    content_hash: str = Field(index=True)
    summary: Optional[str] = Field(default=None)  # AI 요약

    relevance_score: float = Field(default=0.0)  # 키워드 매칭 점수
    is_processed: bool = Field(default=False)  # 처리 여부

    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)

    # Relationships
    source: Optional[Source] = Relationship(back_populates="items")


# ============== Newsletter Models ==============

class Newsletter(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    user_id: int = Field(index=True, foreign_key="user.id")

    status: str = Field(default="draft")  # draft|recommended|sent
    subject: str
    html: str
    text: str
    
    # 메타데이터
    item_count: int = Field(default=0)
    generated_by: str = Field(default="manual")  # manual|ai
    
    # 발송 정보
    sent_at: Optional[datetime] = Field(default=None)
    sent_count: int = Field(default=0)
    error_count: int = Field(default=0)

    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)

    # Relationships
    user: Optional[User] = Relationship(back_populates="newsletters")
    send_logs: List["SendLog"] = Relationship(back_populates="newsletter")


class NewsletterItem(SQLModel, table=True):
    """뉴스레터에 포함된 아이템 연결 테이블"""
    id: Optional[int] = Field(default=None, primary_key=True)
    newsletter_id: int = Field(index=True, foreign_key="newsletter.id")
    item_id: int = Field(index=True, foreign_key="item.id")
    order: int = Field(default=0)
    included_at: datetime = Field(default_factory=datetime.utcnow)


# ============== SendLog Models ==============

class SendLog(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    newsletter_id: int = Field(index=True, foreign_key="newsletter.id")
    recipient_email: str = Field(index=True)

    status: str = Field(index=True)  # pending|ok|error
    error: Optional[str] = Field(default=None)
    retry_count: int = Field(default=0)

    sent_at: Optional[datetime] = Field(default=None)
    created_at: datetime = Field(default_factory=datetime.utcnow)

    # Relationships
    newsletter: Optional[Newsletter] = Relationship(back_populates="send_logs")


# ============== Schedule Models ==============

class Schedule(SQLModel, table=True):
    """뉴스레터 발송 스케줄 설정"""
    id: Optional[int] = Field(default=None, primary_key=True)
    user_id: int = Field(index=True, foreign_key="user.id")

    name: str
    enabled: bool = Field(default=True)
    
    # CRON 표현식 (예: "0 9 * * 1" = 매주 월요일 9시)
    cron_expression: str = Field(default="0 9 * * 1")
    
    # 발송 설정
    recipient_emails: str = Field(default="")  # 쉼표 구분
    subject_template: str = Field(default="[뉴스레터] 주간 업계 동향")
    
    # AI 생성 설정
    auto_generate: bool = Field(default=True)
    max_items: int = Field(default=10)
    
    last_run_at: Optional[datetime] = Field(default=None)
    next_run_at: Optional[datetime] = Field(default=None)
    
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)


# ============== System Log Models ==============

class SystemLog(SQLModel, table=True):
    """시스템 로그"""
    id: Optional[int] = Field(default=None, primary_key=True)
    level: str = Field(index=True)  # info|warning|error
    component: str = Field(index=True)  # web|worker|connector|etc
    message: str
    details: Optional[str] = Field(default=None)
    created_at: datetime = Field(default_factory=datetime.utcnow)
