# 제조 AI/DX 용어집 웹앱 설치/운영 매뉴얼 (Manual)

이 문서는 **용어집 웹앱(glossary-webapp)** 을 사내/개인 PC에서 설치하고 운영하는 방법을 **매뉴얼 수준**으로 정리한 것입니다.

---

## 0. 구성 요약

- **웹앱(검색/필터/업로드/엑셀 다운로드/수정)**: `glossary-webapp`
- **데이터 정본(JSON)**: `glossary-webapp/data/glossary.json`
- **기본 예제 100개 포함**: 설치 후 바로 검색 가능
- **LLM(선택)**: Azure OpenAI 등 설정 시 “검색 결과 없으면 생성” 기능 사용

---

## 1. 설치 전 준비사항

### 1) 필수 소프트웨어
- **Python 3.10+** 권장 (최소 3.9 이상 권장)
- (선택) Git

### 2) 네트워크/방화벽
- 로컬 PC에서만 쓰면: `localhost` 접속
- 사내 서버에서 띄우면: 해당 포트(기본 8080)를 사내망에서 접근 가능하도록 방화벽 설정

---

## 2. 설치(로컬 PC 기준)

### 2.1. 프로젝트 폴더로 이동
```bash
cd /home/ironshim/.openclaw/workspace/glossary-webapp
```

### 2.2. 가상환경 생성/활성화
```bash
python3 -m venv .venv
source .venv/bin/activate
```

### 2.3. 패키지 설치
```bash
pip install -U pip
pip install -r requirements.txt
```

### 2.4. 서버 실행
```bash
uvicorn app.main:app --host 0.0.0.0 --port 8080
```

### 2.5. 접속
- 브라우저에서: `http://localhost:8080`

---

## 3. 기능 사용법(GUI)

### 3.1 검색
- 검색창에 키워드 입력 후 **검색**
- 결과 카드가 아래 리스트로 표시됨
- 카드 클릭 → 하단(또는 데스크탑 우측)에 상세 표시

### 3.2 카테고리 필터
- “전체 카테고리” 드롭다운에서 카테고리 선택
- 검색어 없이도 카테고리만으로 조회 가능

### 3.3 엑셀 다운로드
- 상단 **엑셀 다운로드** 버튼 클릭
- 현재 `glossary.json` 내용을 `.xlsx`로 내려받음

### 3.4 용어 수정
- 상세 패널 오른쪽 아래 **수정** 버튼 클릭
- 수정 후 **저장** → 즉시 `data/glossary.json`에 반영

### 3.5 일괄 업로드(xlsx)
- “파일 선택”으로 `.xlsx` 파일 선택
- **일괄 업로드(xlsx)** 클릭
- 결과(추가/업데이트/스킵/총 용어수)가 화면에 표시됨

> 주의: 업로드는 `glossary.json`을 직접 수정하는 기능이므로, 운영 환경에서는 **백업 후 업로드**를 권장합니다.

---

## 4. 데이터(정본) 관리

### 4.1 정본 파일
- `glossary-webapp/data/glossary.json`

### 4.2 백업 방법(권장)
```bash
cp glossary-webapp/data/glossary.json glossary-webapp/data/glossary.backup.$(date +%Y%m%d_%H%M%S).json
```

### 4.3 예제 100개
- 기본 100개 용어가 `glossary.json`에 포함되어 있어 설치 직후 바로 검색 가능

---

## 5. LLM 기능(선택) — Azure OpenAI 연동

> 토큰/키는 절대 채팅/문서에 노출하지 말고, 서버의 `.env`에만 보관하세요.

### 5.1 `.env` 설정
1) 예시 파일 복사
```bash
cd glossary-webapp
cp .env.example .env
```

2) `.env` 편집(예시)
```env
LLM_MODE=azure_openai
LLM_ENDPOINT=https://{리소스이름}.openai.azure.com
LLM_API_KEY=*****
LLM_DEPLOYMENT={배포이름}
LLM_API_VERSION=2024-02-15-preview
LLM_API_KEY_HEADER=api-key
```

### 5.2 동작
- LLM이 켜져 있으면:
  - 검색 결과가 없을 때 **“없음 → 초안 생성”** 버튼이 활성화됨
  - (옵션) 자동생성 체크 시 검색 결과 없으면 바로 생성

---

## 6. 업로드 엑셀(xlsx) 형식

### 6.1 필수
- 1행은 헤더
- 최소 **용어(KR)** 컬럼이 있어야 함

### 6.2 인식하는 헤더(대표)
- `용어(KR)` 또는 `kr`
- `약어/EN` 또는 `en`
- `분류` 또는 `category`
- `한줄 정의` 또는 `oneLine`
- `예시` 또는 `example`
- `KPI` 또는 `kpi` (쉼표/줄바꿈 가능)
- `혼동되는 용어` 또는 `confusions` (쉼표/줄바꿈 가능)

---

## 7. 문제 해결(Troubleshooting)

### 7.1 접속이 안 됨
- 서버가 떠 있는지 확인
- 포트(8080) 방화벽/보안그룹 허용 확인

### 7.2 패키지 설치 오류
- 아래 순서로 재시도
```bash
source .venv/bin/activate
pip install -U pip
pip install -r requirements.txt
```

---

## 8. 운영 권장사항(사내)

- 외부망 차단 환경이면 LLM/이미지 생성이 동작하지 않을 수 있음
- 사내 배포 시:
  - 리버스 프록시(nginx) + 사내 인증(SSO/OIDC 등) 권장
  - 정본 파일 백업/버전관리(Git) 권장

---

## 9. 빠른 체크리스트

- [ ] `uvicorn` 실행됨
- [ ] 브라우저 `http://localhost:8080` 접속됨
- [ ] 검색 결과가 뜸(기본 100개)
- [ ] 엑셀 다운로드 동작
- [ ] 업로드(xlsx) 동작
- [ ] 수정 후 저장이 반영됨
