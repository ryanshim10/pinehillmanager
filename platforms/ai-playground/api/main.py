import os
from typing import Optional

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel

from sqlmodel import SQLModel, Field, Session, create_engine, select

app = FastAPI(title="AI Playground API", version="0.1.0")

SSO_HEADER_EMPID = os.getenv("SSO_HEADER_EMPID", "X-SSO-EMPID")
SSO_HEADER_LOGINID = os.getenv("SSO_HEADER_LOGINID", "X-SSO-LOGINID")
DATABASE_URL = os.getenv("DATABASE_URL", "")
SSO_ALLOWED_COMPANY_CODE = os.getenv("SSO_ALLOWED_COMPANY_CODE", "1000")


class IFOrgUser(SQLModel, table=True):
    __tablename__ = "if_org_user"

    code: str = Field(primary_key=True, index=True)
    loginid: Optional[str] = Field(default=None, index=True)
    email: Optional[str] = Field(default=None, index=True)
    name: Optional[str] = Field(default=None)
    company_code: Optional[str] = Field(default=None, index=True)


_engine = create_engine(DATABASE_URL, pool_pre_ping=True) if DATABASE_URL else None


class RunRequest(BaseModel):
    prompt: str


def _get_user(empid: Optional[str], loginid: Optional[str]) -> dict:
    # 1) 로그인ID 헤더가 있으면 그걸 최우선 사용
    if loginid:
        return {"loginid": loginid, "empid": empid}

    # 2) 사번만 있으면(현 환경) 디렉토리 테이블에서 loginid를 찾는다
    if empid and _engine:
        with Session(_engine) as s:
            du = s.get(IFOrgUser, empid)
            if du and du.company_code and SSO_ALLOWED_COMPANY_CODE and du.company_code != SSO_ALLOWED_COMPANY_CODE:
                raise HTTPException(status_code=403, detail="Forbidden company code")
            if du and du.loginid:
                return {"loginid": du.loginid, "empid": empid, "email": du.email, "name": du.name}

    # 3) DB가 없으면 최소한 사번만이라도 표시
    if empid:
        return {"loginid": None, "empid": empid}

    raise HTTPException(status_code=401, detail="Missing SSO headers")


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/run")
def run(
    body: RunRequest,
    x_sso_empid: Optional[str] = Header(default=None, convert_underscores=False, alias=SSO_HEADER_EMPID),
    x_sso_loginid: Optional[str] = Header(default=None, convert_underscores=False, alias=SSO_HEADER_LOGINID),
):
    user = _get_user(x_sso_empid, x_sso_loginid)

    # TODO: 실제 LLM 호출(사내/외부 모델)로 교체
    return {
        "user": user,
        "input": body.prompt,
        "output": f"(stub) you said: {body.prompt}",
    }
