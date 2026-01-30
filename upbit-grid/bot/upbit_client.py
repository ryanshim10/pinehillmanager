from __future__ import annotations

import time
from dataclasses import dataclass
from typing import Optional

import pyupbit


@dataclass
class Ticker:
    price: float


class UpbitClient:
    def __init__(self, access_key: str, secret_key: str, dry_run: bool = True):
        self.dry_run = dry_run
        self._upbit = None if dry_run else pyupbit.Upbit(access_key, secret_key)

    def get_price(self, market: str) -> Ticker:
        price = float(pyupbit.get_current_price(market))
        return Ticker(price=price)

    def get_balance(self, currency: str) -> float:
        if self.dry_run:
            return 0.0
        return float(self._upbit.get_balance(currency))

    def buy_market(self, market: str, krw: int) -> dict:
        if self.dry_run:
            return {"dry_run": True, "type": "buy_market", "market": market, "krw": krw, "id": f"dry-buy-{time.time()}"}
        return self._upbit.buy_market_order(market, krw)

    def sell_limit(self, market: str, price: float, qty: float) -> dict:
        if self.dry_run:
            return {"dry_run": True, "type": "sell_limit", "market": market, "price": price, "qty": qty, "id": f"dry-sell-{time.time()}"}
        return self._upbit.sell_limit_order(market, price, qty)

    def cancel_order(self, uuid: str) -> dict:
        if self.dry_run:
            return {"dry_run": True, "type": "cancel", "id": uuid}
        return self._upbit.cancel_order(uuid)

    def get_order(self, uuid: str) -> Optional[dict]:
        if self.dry_run:
            return None
        return self._upbit.get_order(uuid)
