from __future__ import annotations

import os
from datetime import datetime, timedelta, timezone

import jwt
from passlib.context import CryptContext

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def _env(name: str, default: str = "") -> str:
    v = os.getenv(name)
    return default if v is None else v


def verify_user(username: str, password: str) -> bool:
    u = _env("APP_USER", "ryan")
    p = _env("APP_PASS", "")
    return username == u and password == p


def create_token(username: str) -> str:
    secret = _env("APP_SECRET", "change-me")
    now = datetime.now(timezone.utc)
    payload = {
        "sub": username,
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(hours=24)).timestamp()),
    }
    return jwt.encode(payload, secret, algorithm="HS256")


def decode_token(token: str) -> dict:
    secret = _env("APP_SECRET", "change-me")
    return jwt.decode(token, secret, algorithms=["HS256"])
