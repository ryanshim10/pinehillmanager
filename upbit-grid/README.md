# Upbit BTC Grid Bot + Web Dashboard (Linux)

> ⚠️ 투자 손실 위험이 있습니다. 이 프로젝트는 자동매매 실행을 포함할 수 있으며, **출금 권한은 절대 부여하지 마세요.**

## 목표(ryan 요구사항)
- 거래소: Upbit
- 시드: 2,000,000 KRW
- 분할: 50 (기본 40,000 KRW/분할)
- 매수: 1번째 분할(첫 진입가)을 기준으로 **2% 하락할 때마다** 다음 분할 매수
- 매도: 각 분할 매수 체결가 대비 **+3%** 에 지정가 매도(개별 lot 관리)
- 웹앱: 휴대폰으로 현황 확인 + 파라미터 조정 + 봇 On/Off

## 구성
- `bot/` : 전략/주문 실행/상태 저장
- `app/` : FastAPI 웹앱(모바일 대응 UI)
- `db/`  : SQLite DB

## 안전장치(기본)
- 기본은 `DRY_RUN=1` (실주문 OFF)
- 출금 권한 사용 금지
- API 키는 `.env` 로컬 환경변수로만 관리(채팅에 공유 금지)

## 빠른 시작
```bash
cd upbit-grid
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# .env에 UPBIT_ACCESS_KEY / UPBIT_SECRET_KEY 입력

# 1) 웹 대시보드
uvicorn app.main:app --host 0.0.0.0 --port 8000

# 2) 봇 실행(별도 터미널)
python -m bot.runner
```

휴대폰에서: `http://<서버IP>:8000`

## 다음 확인 필요(ryan 답변 요청)
- 첫 매수(1번째 분할)는 **언제/어떻게** 들어갈까?
  - A) 봇 시작 즉시 시장가 1회 매수(권장: 시장가)
  - B) 지정가로 첫 진입가를 사용자가 입력
- 50분할 모두 사용 후 더 하락하면?
  - A) 추가 매수 중단
  - B) 비율 유지로 재설정(리밸런싱)
- 강한 상승장(+3% 매도 후) 재진입 규칙
  - A) 매도된 그리드 칸은 다시 같은 규칙으로 재매수 대기
  - B) 다른 방식
