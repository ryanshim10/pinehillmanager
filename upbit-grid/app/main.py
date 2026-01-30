from __future__ import annotations

import os
from datetime import datetime

from dotenv import load_dotenv
from fastapi import FastAPI, Depends, HTTPException, Request, Response
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from pydantic import BaseModel
from sqlmodel import Session, select

from bot.config import Settings
from bot.db import BotState, Lot, get_engine, init_db
from app.security import verify_user, create_token, decode_token

load_dotenv()

app = FastAPI(title="Upbit Grid Dashboard")
app.mount("/static", StaticFiles(directory="app/static"), name="static")

templates = Jinja2Templates(directory="app/templates")

settings = Settings()
engine = get_engine(settings.db_url)
init_db(engine)


def require_auth(request: Request):
    token = request.cookies.get("token")
    if not token:
        raise HTTPException(status_code=401)
    try:
        decode_token(token)
    except Exception:
        raise HTTPException(status_code=401)


class LoginBody(BaseModel):
    username: str
    password: str


class ConfigPatch(BaseModel):
    enabled: bool | None = None
    first_entry_price: float | None = None


@app.get("/", response_class=HTMLResponse)
def home(request: Request, _=Depends(require_auth)):
    with Session(engine) as session:
        state = session.get(BotState, 1)
        lots = session.exec(select(Lot).order_by(Lot.id.desc()).limit(50)).all()
    return templates.TemplateResponse(
        "index.html",
        {
            "request": request,
            "state": state,
            "lots": lots,
            "settings": settings,
            "now": datetime.utcnow(),
        },
    )


@app.get("/login", response_class=HTMLResponse)
def login_page(request: Request):
    return templates.TemplateResponse("login.html", {"request": request})


@app.post("/login")
def login(body: LoginBody, response: Response):
    if not verify_user(body.username, body.password):
        raise HTTPException(status_code=401, detail="bad credentials")
    token = create_token(body.username)
    resp = RedirectResponse(url="/", status_code=302)
    resp.set_cookie("token", token, httponly=True, samesite="lax")
    return resp


@app.post("/logout")
def logout():
    resp = RedirectResponse(url="/login", status_code=302)
    resp.delete_cookie("token")
    return resp


@app.get("/api/state")
def api_state(_=Depends(require_auth)):
    with Session(engine) as session:
        state = session.get(BotState, 1)
        lots = session.exec(select(Lot).order_by(Lot.id.desc()).limit(200)).all()
    return {"state": state, "lots": lots, "settings": settings.__dict__}


@app.post("/api/state")
def api_patch(patch: ConfigPatch, _=Depends(require_auth)):
    with Session(engine) as session:
        state = session.get(BotState, 1)
        if not state:
            state = BotState(id=1)
            session.add(state)
        if patch.enabled is not None:
            state.enabled = patch.enabled
        if patch.first_entry_price is not None:
            state.first_entry_price = float(patch.first_entry_price)
        state.updated_at = datetime.utcnow()
        session.add(state)
        session.commit()
        return {"ok": True, "state": state}
