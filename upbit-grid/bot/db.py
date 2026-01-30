from __future__ import annotations

from datetime import datetime
from typing import Optional

from sqlmodel import SQLModel, Field, create_engine


class BotState(SQLModel, table=True):
    id: int = Field(default=1, primary_key=True)
    enabled: bool = Field(default=False)
    first_entry_price: Optional[float] = None
    slices_bought: int = Field(default=0)
    updated_at: datetime = Field(default_factory=datetime.utcnow)


class Lot(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    buy_price: float
    buy_qty: float
    buy_krw: int
    sell_target_price: float
    status: str = Field(default="OPEN")  # OPEN|SOLD
    buy_order_id: Optional[str] = None
    sell_order_id: Optional[str] = None
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)


def get_engine(db_url: str):
    connect_args = {"check_same_thread": False} if db_url.startswith("sqlite") else {}
    return create_engine(db_url, echo=False, connect_args=connect_args)


def init_db(engine):
    SQLModel.metadata.create_all(engine)
