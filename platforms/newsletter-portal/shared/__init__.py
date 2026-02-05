"""
현대위아 뉴스레터 포탈 - 공유 모듈
"""

from .db import make_engine, init_db, get_session_context
from .auth import (
    hash_password, verify_password, create_temp_password, verify_temp_password,
    record_login_failure, reset_login_failures, is_account_locked, unlock_account,
    create_token, parse_token
)
from .mail import Mailer, MailConfig, get_mailer
from .llm import LLMClient, LLMConfig, NewsletterGenerator

__all__ = [
    # DB
    "make_engine",
    "init_db",
    "get_session_context",
    # Auth
    "hash_password",
    "verify_password",
    "create_temp_password",
    "verify_temp_password",
    "record_login_failure",
    "reset_login_failures",
    "is_account_locked",
    "unlock_account",
    "create_token",
    "parse_token",
    # Mail
    "Mailer",
    "MailConfig",
    "get_mailer",
    # LLM
    "LLMClient",
    "LLMConfig",
    "NewsletterGenerator",
]
