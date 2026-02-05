import os
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel

app = FastAPI(title="AI Playground API", version="0.1.0")

SSO_HEADER_EMPID = os.getenv("SSO_HEADER_EMPID", "X-SSO-EMPID")
SSO_HEADER_LOGINID = os.getenv("SSO_HEADER_LOGINID", "X-SSO-LOGINID")


class RunRequest(BaseModel):
    prompt: str


def _get_user(empid: str | None, loginid: str | None) -> dict:
    # 사내 SSO 연동 시 프록시가 아래 헤더 중 하나(또는 둘 다)를 내려주는 걸 전제로 함
    if loginid:
        return {"loginid": loginid, "empid": empid}
    if empid:
        return {"loginid": None, "empid": empid}
    raise HTTPException(status_code=401, detail="Missing SSO headers")


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/run")
def run(
    body: RunRequest,
    x_sso_empid: str | None = Header(default=None, convert_underscores=False, alias=SSO_HEADER_EMPID),
    x_sso_loginid: str | None = Header(default=None, convert_underscores=False, alias=SSO_HEADER_LOGINID),
):
    user = _get_user(x_sso_empid, x_sso_loginid)

    # TODO: 실제 LLM 호출(사내/외부 모델)로 교체
    return {
        "user": user,
        "input": body.prompt,
        "output": f"(stub) you said: {body.prompt}",
    }
