from __future__ import annotations

import os
from dataclasses import dataclass


def _f(name: str, default: float) -> float:
    v = os.getenv(name)
    return default if v is None or v == "" else float(v)


def _i(name: str, default: int) -> int:
    v = os.getenv(name)
    return default if v is None or v == "" else int(v)


def _s(name: str, default: str) -> str:
    v = os.getenv(name)
    return default if v is None else v


@dataclass
class Settings:
    market: str = _s("MARKET", "KRW-BTC")
    total_krw: int = _i("TOTAL_KRW", 2_000_000)
    slices: int = _i("SLICES", 50)
    buy_step_pct: float = _f("BUY_STEP_PCT", 2.0)  # each level down from first entry
    sell_tp_pct: float = _f("SELL_TP_PCT", 3.0)    # take profit per-lot
    dry_run: bool = _s("DRY_RUN", "1") not in ("0", "false", "False")

    upbit_access_key: str = _s("UPBIT_ACCESS_KEY", "")
    upbit_secret_key: str = _s("UPBIT_SECRET_KEY", "")

    db_url: str = _s("DB_URL", "sqlite:///./db/grid.db")

    @property
    def slice_krw(self) -> int:
        return max(5_000, int(self.total_krw // max(1, self.slices)))
