# wia-newsletter-portal

현대위아 뉴스레터 포탈 - AI 기반 뉴스레터 생성 및 발송 시스템

## 아키텍처

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Web (FastAPI) │────▶│   PostgreSQL    │◀────│ Worker (APScheduler)│
│   포트 8088     │     │                 │     │   백그라운드 작업   │
└─────────────────┘     └─────────────────┘     └─────────────────┘
         │                                               │
         ▼                                               ▼
   ┌─────────────┐                               ┌─────────────┐
   │   SMTP      │                               │  Connectors │
   │  (이메일)    │                               │ RSS/YouTube │
   └─────────────┘                               │ /Web/LinkedIn│
                                                 └─────────────┘
```

## 기능

- **인증**: bcrypt 패스워드 해싱, 5회 실패 시 계정 잠금, 6자리 임시 비밀번호 이메일 발송
- **사용자 관리**: 관리자/일반 사용자 CRUD
- **소스 관리**: LinkedIn, RSS, 웹사이트, YouTube 채널 소스 등록 및 관리
- **키워드 버킷**: 상위/중요/일반/제외 키워드 관리
- **뉴스레터 생성**: AI 기반 추천 뉴스레터 및 수동 드래프트
- **발송 관리**: SMTP HTML/Text 발송, 발송 로그
- **워커**: APScheduler 기반 주기적 수집

## 빠른 시작

### 1. 환경 설정

```bash
cp .env.example .env
# .env 파일을 편집하여 실제 값 입력
```

### 2. 실행

```bash
docker-compose up --build
```

### 3. 개발 모드 (MailHog 포함)

```bash
docker-compose --profile dev up --build
```

MailHog UI: http://localhost:8025

### 4. 테스트

```bash
docker-compose -f docker-compose.yml -f docker-compose.test.yml up --build --abort-on-container-exit
```

## API 문서

- Swagger UI: http://localhost:8088/docs
- ReDoc: http://localhost:8088/redoc

## 초기 관리자 설정

`BOOTSTRAP_ADMIN_EMAIL`에 설정된 이메일로 임시 비밀번호가 발송됩니다.

## 환경 변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `POSTGRES_DB` | 데이터베이스 이름 | newsletter |
| `POSTGRES_USER` | DB 사용자 | newsletter |
| `POSTGRES_PASSWORD` | DB 비밀번호 | required |
| `SESSION_SECRET` | 세션 암호화 키 | required |
| `BOOTSTRAP_ADMIN_EMAIL` | 초기 관리자 이메일 | required |
| `SMTP_HOST` | SMTP 서버 | required |
| `SMTP_PORT` | SMTP 포트 | 587 |
| `SMTP_USER` | SMTP 사용자 | required |
| `SMTP_PASS` | SMTP 비밀번호 | required |
| `WORKER_TICK_SECONDS` | 워커 실행 간격 | 300 |
| `LLM_*` | LLM API 설정 | - |

## 디렉토리 구조

```
wia-newsletter-portal/
├── docker-compose.yml      # 프로덕션 구성
├── docker-compose.test.yml # 테스트 구성
├── .env.example            # 환경 변수 예시
├── README.md               # 이 파일
├── web/                    # FastAPI 웹 애플리케이션
│   ├── Dockerfile
│   ├── requirements.txt
│   └── main.py
├── worker/                 # 백그라운드 워커
│   ├── Dockerfile
│   ├── requirements.txt
│   └── main.py
├── shared/                 # 공유 모듈
│   ├── models.py           # SQLModel 모델
│   ├── db.py               # 데이터베이스 유틸
│   ├── auth.py             # 인증 유틸
│   ├── mail.py             # 이메일 유틸
│   └── connectors/         # 소스 커넥터
│       ├── __init__.py
│       ├── base.py
│       ├── rss.py
│       ├── youtube.py
│       ├── website.py
│       └── linkedin.py
└── tests/                  # pytest 테스트
    ├── conftest.py
    ├── test_auth.py
    ├── test_users.py
    ├── test_sources.py
    ├── test_newsletters.py
    └── test_worker.py
```

## 라이선스

남용 금지
