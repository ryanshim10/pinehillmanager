# Personal Plex 🧠

> "NVIDIA Personal Plex" 스타일의 로컬 AI 에이전트
> 
> ChatGPT 없이 집에서 AI를 돌리고, pinehill-manager(원룸 관리), 비트코인 트레이딩 봇 등과 연동

## 🎯 주요 기능

- **로컬 LLM**: Llama 3.1 8B (한국어 지원, 개인정보 외부 유출 없음)
- **웹 인터페이스**: Open WebUI (ChatGPT 스타일 채팅)
- **Pinehill 연동**: 원룸 19세대 현황, 납부, 지출 데이터 조회
- **확장 가능**: 비트코인 봇, 구글시트 등 추가 연동 가능

## 🏗️ 아키텍처

```
┌─────────────────────────────────────────┐
│  Open WebUI (localhost:3000)            │
│  - 채팅 인터페이스                       │
│  - Pinehill 데이터 조회                  │
├─────────────────────────────────────────┤
│  Ollama (localhost:11434)               │
│  - Llama 3.1 8B 모델                     │
│  - 로컬 추론 엔진                        │
├─────────────────────────────────────────┤
│  Pinehill Bridge (localhost:8001)       │
│  - REST API                             │
│  - SQLite/Sheets 연동                    │
└─────────────────────────────────────────┘
```

## 🚀 설치 방법

### 사전 요구사항
- Linux (Ubuntu 20.04+ 권장)
- Docker & Docker Compose
- (선택) NVIDIA GPU + 8GB VRAM 이상

### 설치 (3단계)

```bash
# 1. 클론
git clone https://github.com/ryanshim10/pinehillmanager.git
cd pinehillmanager/personal-plex

# 2. 설치 스크립트 실행
./scripts/install.sh

# 3. 테스트
./scripts/test.sh
```

## 🧪 테스트

```bash
# 전체 테스트 실행
./scripts/test.sh

# 수동 테스트
# 1. Ollama API 확인
curl http://localhost:11434/api/tags

# 2. WebUI 확인 (브라우저)
open http://localhost:3000

# 3. Pinehill Bridge 확인
curl http://localhost:8001/health
```

## 💬 사용법

### 1. AI 채팅 시작
브라우저에서 http://localhost:3000 열고 새 채팅 시작

### 2. Pinehill 데이터 조회 예시
```
"이번 달 미납 세대 알려줘"
"PINE-201 현황 보여줘"
"1월 지출 내역 정리해줘"
```

### 3. API 직접 호출
```bash
# 모든 세대 조회
curl http://localhost:8001/api/units

# 월별 요약 (2026-01)
curl http://localhost:8001/api/summary/2026-01
```

## 🔧 문제 해결

| 문제 | 해결책 |
|-----|--------|
| GPU 인식 안 됨 | `.env`에서 `USE_GPU=false` 설정 |
| Ollama 응답 없음 | `docker-compose restart ollama` |
| 모델 다운로드 실패 | `docker exec personal-plex-ollama ollama pull llama3.1:8b` |
| WebUI 접속 안 됨 | `docker-compose logs open-webui` 확인 |

## 📁 디렉토리 구조

```
personal-plex/
├── docker-compose.yml      # 서비스 정의
├── .env.example            # 환경 설정 예시
├── config/
│   ├── bridge.py           # Pinehill API 서버
│   └── Dockerfile.bridge   # Bridge 빌드 파일
├── scripts/
│   ├── install.sh          # 설치 스크립트
│   └── test.sh             # 테스트 스크립트
└── data/                   # 데이터 저장소 (Git 제외)
    ├── ollama/             # AI 모델 파일
    └── open-webui/         # WebUI 데이터
```

## 🔒 보안

- 모든 AI 추론은 **로컬에서만** 실행 (외부 서버 없음)
- pinehill-manager DB 파일은 로컬에만 저장
- `.env`와 `data/` 디렉토리는 `.gitignore`에 포함

## 📋 로드맵

- [ ] GPU 가속 최적화
- [ ] RAG (문서 검색) 기능
- [ ] 비트코인 봇 연동
- [ ] 음성 인식 (STT)
- [ ] 텔레그램 봇 연동

## 📝 라이선스

MIT License

## 🙏 감사

- [Ollama](https://ollama.com)
- [Open WebUI](https://github.com/open-webui/open-webui)
- [Llama](https://llama.meta.com)
