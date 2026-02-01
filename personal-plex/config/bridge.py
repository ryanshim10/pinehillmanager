from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import sqlite3
import json
from typing import Optional, List
import os

app = FastAPI(title="Pinehill Bridge", version="1.0.0")

def get_db_connection():
    db_path = os.getenv("DATABASE_URL", "/data/pinehill.db")
    return sqlite3.connect(db_path)

class UnitStatus(BaseModel):
    unitId: str
    roomNo: int
    floor: int
    status: str
    roomType: Optional[str]
    targetPrice: Optional[str]

class PaymentInfo(BaseModel):
    unitId: str
    month: str
    status: str
    amount: int
    paidAt: Optional[str]

@app.get("/health")
def health_check():
    return {"status": "ok", "service": "pinehill-bridge"}

@app.get("/api/units", response_model=List[UnitStatus])
def get_units():
    """모든 세대 현황 조회"""
    try:
        conn = get_db_connection()
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
        cursor.execute("SELECT unitId, roomNo, floor, status, roomType, targetPrice FROM units")
        rows = cursor.fetchall()
        conn.close()
        
        return [UnitStatus(
            unitId=row["unitId"],
            roomNo=row["roomNo"],
            floor=row["floor"],
            status=row["status"],
            roomType=row["roomType"],
            targetPrice=row["targetPrice"]
        ) for row in rows]
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/units/{unit_id}/status")
def get_unit_status(unit_id: str):
    """특정 세대 상세 조회"""
    try:
        conn = get_db_connection()
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM units WHERE unitId = ?", (unit_id,))
        row = cursor.fetchone()
        conn.close()
        
        if not row:
            raise HTTPException(status_code=404, detail="Unit not found")
        
        return dict(row)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/payments/{month}", response_model=List[PaymentInfo])
def get_payments_by_month(month: str):
    """월별 납부 현황 조회 (YYYY-MM)"""
    try:
        conn = get_db_connection()
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
        cursor.execute("""
            SELECT unitId, month, status, amount, paidAt 
            FROM payments 
            WHERE month = ?
        """, (month,))
        rows = cursor.fetchall()
        conn.close()
        
        return [PaymentInfo(
            unitId=row["unitId"],
            month=row["month"],
            status=row["status"],
            amount=row["amount"],
            paidAt=row["paidAt"]
        ) for row in rows]
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/summary/{month}")
def get_monthly_summary(month: str):
    """월별 요약 통계"""
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        # 납부 통계
        cursor.execute("""
            SELECT 
                COUNT(CASE WHEN status = 'PAID' THEN 1 END) as paid_count,
                COUNT(CASE WHEN status = 'PENDING' THEN 1 END) as pending_count,
                COUNT(CASE WHEN status = 'UNPAID' THEN 1 END) as unpaid_count,
                SUM(amount) as total_paid
            FROM payments 
            WHERE month = ?
        """, (month,))
        payment_stats = cursor.fetchone()
        
        # 지출 통계
        cursor.execute("""
            SELECT SUM(amount) as total_expense, COUNT(*) as expense_count
            FROM expenses 
            WHERE month = ?
        """, (month,))
        expense_stats = cursor.fetchone()
        
        conn.close()
        
        return {
            "month": month,
            "payments": {
                "paid": payment_stats[0],
                "pending": payment_stats[1],
                "unpaid": payment_stats[2],
                "totalAmount": payment_stats[3] or 0
            },
            "expenses": {
                "totalAmount": expense_stats[0] or 0,
                "count": expense_stats[1]
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
