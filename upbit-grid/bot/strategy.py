from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import datetime
from typing import Optional, Tuple


@dataclass
class Plan:
    should_buy: bool
    buy_krw: int
    buy_reason: str
    should_place_sell: bool
    sell_price: float


def _round_price_upbit(price: float) -> float:
    # Simplified KRW price unit rules for Upbit (approx). For production, use exact tick rules.
    if price < 10:
        unit = 0.01
    elif price < 100:
        unit = 0.1
    elif price < 1_000:
        unit = 1
    elif price < 10_000:
        unit = 5
    elif price < 100_000:
        unit = 10
    elif price < 500_000:
        unit = 50
    elif price < 1_000_000:
        unit = 100
    elif price < 2_000_000:
        unit = 500
    else:
        unit = 1_000
    return math.floor(price / unit) * unit


def decide_next(
    *,
    enabled: bool,
    cur_price: float,
    first_entry_price: Optional[float],
    slices_bought: int,
    slices_total: int,
    slice_krw: int,
    buy_step_pct: float,
    sell_tp_pct: float,
) -> Tuple[Optional[float], Plan]:
    if not enabled:
        return first_entry_price, Plan(False, 0, "bot_disabled", False, 0.0)

    # If no first entry yet: anchor at current price (but do not auto-buy unless configured elsewhere).
    if first_entry_price is None:
        return cur_price, Plan(False, 0, "set_first_entry_anchor_only", False, 0.0)

    # Buy rule: every buy_step_pct drop from first entry for next slice.
    next_level = slices_bought + 1  # 1-based
    if next_level > slices_total:
        return first_entry_price, Plan(False, 0, "all_slices_used", False, 0.0)

    target_buy_price = first_entry_price * (1.0 - (buy_step_pct / 100.0) * (next_level - 1))

    if cur_price <= target_buy_price:
        sell_price = _round_price_upbit(cur_price * (1.0 + sell_tp_pct / 100.0))
        return first_entry_price, Plan(True, slice_krw, f"price<=target_level({next_level})", True, sell_price)

    return first_entry_price, Plan(False, 0, "waiting_for_next_level", False, 0.0)
