# AI Playground

사내에서 **Streamlit 앱 업로드, 공유, 실행**을 할 수 있는 AI Playground 플랫폼입니다.

## 주요 기능

- **앱 폴더 업로드**: ZIP 파일로 Streamlit 앱 업로드
- **조직 기반 공유**: 앱을 조직 내에서 공유
- **협업자 관리**: 소유자/관리자/뷰어 역할로 협업
- **서버 사이드 실행**: 격리된 환경에서 Streamlit 앱 실행

## 아키텍처

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│     UI      │────▶│     API     │────▶│   Runner    │
│  (8501)     │     │   (8001)    │     │ (9000-9100) │
└─────────────┘     └─────────────┘     └─────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │  Database   │
                    │  (SQLite)   │
                    └─────────────┘
```

## 실행 (Docker Compose)

```bash
docker compose up --build
```

- UI: http://localhost:8501
- API: http://localhost:8001

## 서비스 구성

| 서비스 | 포트 | 설명 |
|--------|------|------|
| api | 8001 | FastAPI 백엔드 |
| ui | 8501 | Streamlit 프론트엔드 |
| runner | 9000-9100 | Streamlit 앱 실행기 |

## 환경변수

```bash
# 인증
AUTH_MODE=dev                    # dev 또는 sso
DEV_BOOTSTRAP_EMAIL=dev@local
DEV_BOOTSTRAP_PASSWORD=devpass

# SSO (sso 모드 사용 시)
SSO_HEADER_EMPID=X-SSO-EMPID
SSO_HEADER_LOGINID=X-SSO-LOGINID
SSO_ALLOWED_COMPANY_CODE=1000

# 데이터베이스
DATABASE_URL=sqlite:///./playground.db

# 러너 서비스
RUNNER_PORT_START=9000
RUNNER_PORT_END=9100
RUNNER_MAX_APPS=50
RUNNER_IDLE_TIMEOUT=3600
```

## 앱 업로드 형식

앱은 다음 구조의 ZIP 파일로 업로드합니다:

```
my_app/
├── app.py           # 메인 Streamlit 앱 (필수)
├── requirements.txt # 의존성 (선택)
└── ...              # 기타 파일
```

### 예제 app.py

```python
import streamlit as st

st.title("Hello from My App!")
name = st.text_input("Your name", "World")
st.write(f"Hello, {name}!")
```

## API 엔드포인트

### 인증
- `POST /auth/login` - 로그인 (dev 모드)
- `GET /me` - 현재 사용자 정보

### 앱 관리
- `GET /apps` - 앱 목록 조회
- `POST /apps` - 새 앱 생성
- `GET /apps/{app_id}` - 앱 상세 조회
- `PUT /apps/{app_id}` - 앱 수정
- `DELETE /apps/{app_id}` - 앱 삭제
- `POST /apps/{app_id}/upload` - ZIP 파일 업로드
- `POST /apps/{app_id}/deploy` - 앱 배포

### 협업자 관리
- `GET /apps/{app_id}/collaborators` - 협업자 목록
- `POST /apps/{app_id}/collaborators` - 협업자 추가
- `DELETE /apps/{app_id}/collaborators/{user_id}` - 협업자 삭제

### 실행
- `POST /apps/{app_id}/run` - 앱 실행 (포트 할당)
- `POST /apps/{app_id}/stop` - 앱 중지
- `GET /apps/{app_id}/status` - 실행 상태 조회

## 권한 모델

| 역할 | 설명 | 권한 |
|------|------|------|
| owner | 소유자 | 모든 권한 (수정, 삭제, 협업자 관리) |
| collaborator | 협업자 | 실행, 조회 (수정/삭제 불가) |
| viewer | 뷰어 | 조회만 가능 |

## 보안

- **최소 보안 모델**: 조직 멤버는 공유된 앱 사용 가능
- **협업자 관리**: 소유자만 협업자 추가/제거
- **실행 격리**: 각 앱은 별도 포트/프로세스에서 실행

## 개발 (로컬)

```bash
cd platforms/ai-playground

# API
cd api
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8001

# UI (다른 터미널)
cd ui
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
streamlit run app.py

# Runner (다른 터미널)
cd runner
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python main.py
```
