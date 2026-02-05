# 현대위아 뉴스레터 포탈 - 개발 가이드

## 빠른 시작

### 1. 저장소 복제 및 환경 설정

```bash
cd wia-newsletter-portal
cp .env.example .env
# .env 파일을 편집하여 실제 값 입력
```

### 2. 개발 모드로 실행 (MailHog 포함)

```bash
docker-compose --profile dev up --build
```

서비스:
- 웹 애플리케이션: http://localhost:8088
- API 문서: http://localhost:8088/docs
- MailHog (이메일 테스트): http://localhost:8025

### 3. 초기 관리자 로그인

1. `BOOTSTRAP_ADMIN_EMAIL`에 설정된 이메일 확인
2. MailHog에서 임시 비밀번호 확인 (http://localhost:8025)
3. `/auth/login`으로 로그인
4. `/auth/set-password`로 비밀번호 변경

## 환경 변수 설정

필수 설정:
```bash
POSTGRES_PASSWORD=strong-password
SESSION_SECRET=long-random-string-min-32-chars
BOOTSTRAP_ADMIN_EMAIL=admin@yourcompany.com
```

SMTP 설정 (Outlook/Office 365):
```bash
SMTP_HOST=smtp.office365.com
SMTP_PORT=587
SMTP_STARTTLS=true
SMTP_SSL=false
SMTP_USER=your-email@company.com
SMTP_PASS=your-app-password
SMTP_FROM=newsletter@company.com
```

LLM 설정 (Azure OpenAI):
```bash
LLM_MODE=chat_completions
LLM_CHAT_URL=https://your-resource.openai.azure.com/...
LLM_AUTH_TYPE=api-key
LLM_API_KEY=your-api-key
```

## API 사용 예시

### 로그인
```bash
curl -X POST http://localhost:8088/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@example.com", "password": "your-password"}'
```

### 소스 추가 (RSS)
```bash
curl -X POST http://localhost:8088/sources \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer user:admin@example.com" \
  -d '{
    "type": "rss",
    "name": "Tech News",
    "url": "https://techcrunch.com/feed/"
  }'
```

### 키워드 추가
```bash
curl -X POST http://localhost:8088/keywords \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer user:admin@example.com" \
  -d '{
    "bucket": "top",
    "text": "AI",
    "weight": 2.0
  }'
```

## 테스트

### 로컬 테스트 (SQLite)
```bash
cd tests
pip install -r requirements.txt
cd ..
python -m pytest tests/ -v
```

### Docker 통합 테스트 (PostgreSQL + MailHog)
```bash
docker-compose -f docker-compose.yml -f docker-compose.test.yml up --build --abort-on-container-exit
```

### 커버리지 리포트
```bash
python -m pytest tests/ --cov=shared --cov=web --cov-report=html
```

## 프로덕션 배포

### 1. 환경 변수 준비
```bash
cp .env.example .env.production
# 프로덕션 값으로 편집
```

### 2. 프로덕션 빌드
```bash
docker-compose -f docker-compose.yml up -d --build
```

### 3. 데이터 백업
```bash
# PostgreSQL 백업
docker exec wia-newsletter-portal_db_1 pg_dump -U newsletter newsletter > backup.sql

# 복원
docker exec -i wia-newsletter-portal_db_1 psql -U newsletter newsletter < backup.sql
```

## 소스 커넥터 가이드

### RSS 피드
- URL: RSS/Atom 피드 URL
- 예: `https://news.ycombinator.com/rss`

### YouTube 채널
- URL: 채널 URL
- 예: `https://www.youtube.com/channel/UCxxx` 또는 `https://www.youtube.com/@handle`

### 웹사이트
- URL: 웹사이트 URL
- 선택적 설정 (JSON):
  ```json
  {
    "selector": "article h2 a",
    "max_pages": 5,
    "timeout": 30
  }
  ```

### LinkedIn
- URL: 프로필 또는 회사 페이지 URL
- 쿠키 파일 필요 (JSON 형식):
  ```json
  {
    "li_at": "your_li_at_cookie",
    "JSESSIONID": "your_jsessionid"
  }
  ```
- 설정: `{"cookie_file": "/app/secrets/linkedin_cookies.json"}`

**주의**: LinkedIn은 스크래핑을 엄격히 제한합니다. 프로덕션에서는 LinkedIn API 사용을 권장합니다.

## 트러블슈팅

### 데이터베이스 연결 오류
```bash
# PostgreSQL이 준비될 때까지 대기
docker-compose logs -f db
```

### 이메일 발송 오류
- MailHog가 실행 중인지 확인 (개발 모드)
- SMTP 설정 확인 (프로덕션)

### 워커가 실행되지 않음
```bash
# 워커 로그 확인
docker-compose logs -f worker
```

### 패키지 설치 문제
```bash
# 캐시 없이 재빌드
docker-compose build --no-cache
```

## 개발 가이드라인

### 코드 스타일
- PEP 8 준수
- 타입 힌트 사용 권장
- docstring 작성

### 테스트 작성
- 모든 새 기능에 테스트 추가
- `tests/test_<module>.py`에 테스트 작성
- `conftest.py`에 fixture 정의

### 커밋 메시지
- 명확하고 설명적인 메시지 작성
- 변경 사항 요약

## 라이선스

남용 금지
