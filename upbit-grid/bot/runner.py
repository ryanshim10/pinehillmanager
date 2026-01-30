from __future__ import annotations

import os
import time
from datetime import datetime

from dotenv import load_dotenv
from sqlmodel import Session, select

from bot.config import Settings
from bot.db import BotState, Lot, get_engine, init_db
from bot.strategy import decide_next
from bot.upbit_client import UpbitClient


def ensure_state(session: Session) -> BotState:
    state = session.get(BotState, 1)
    if not state:
        state = BotState(id=1, enabled=False)
        session.add(state)
        session.commit()
    return state


def main():
    load_dotenv()
    s = Settings()
    engine = get_engine(s.db_url)
    init_db(engine)

    client = UpbitClient(s.upbit_access_key, s.upbit_secret_key, dry_run=s.dry_run)

    poll_sec = float(os.getenv("POLL_SEC", "2.0"))

    while True:
        try:
            with Session(engine) as session:
                state = ensure_state(session)
                ticker = client.get_price(s.market)

                # strategy decision
                first_entry, plan = decide_next(
                    enabled=state.enabled,
                    cur_price=ticker.price,
                    first_entry_price=state.first_entry_price,
                    slices_bought=state.slices_bought,
                    slices_total=s.slices,
                    slice_krw=s.slice_krw,
                    buy_step_pct=s.buy_step_pct,
                    sell_tp_pct=s.sell_tp_pct,
                )

                # update anchor if was None
                if state.first_entry_price is None and first_entry is not None:
                    state.first_entry_price = float(first_entry)

                # execute buy + place sell
                if plan.should_buy:
                    buy_res = client.buy_market(s.market, plan.buy_krw)
                    # approximate qty for dry-run / early stage: use current price
                    qty = (plan.buy_krw / ticker.price) if ticker.price > 0 else 0.0
                    lot = Lot(
                        buy_price=ticker.price,
                        buy_qty=qty,
                        buy_krw=plan.buy_krw,
                        sell_target_price=plan.sell_price,
                        buy_order_id=str(buy_res.get("uuid") or buy_res.get("id")),
                        status="OPEN",
                        updated_at=datetime.utcnow(),
                    )
                    session.add(lot)
                    state.slices_bought += 1

                    if plan.should_place_sell and qty > 0:
                        sell_res = client.sell_limit(s.market, plan.sell_price, qty)
                        lot.sell_order_id = str(sell_res.get("uuid") or sell_res.get("id"))
                        lot.updated_at = datetime.utcnow()

                state.updated_at = datetime.utcnow()
                session.add(state)
                session.commit()

        except Exception as e:
            print("[bot] error:", repr(e))

        time.sleep(poll_sec)


if __name__ == "__main__":
    main()
