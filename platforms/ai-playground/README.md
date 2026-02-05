# AI Playground (사내용)

사내에서 간단히 **프롬프트 실험/템플릿 관리/결과 공유**를 할 수 있는 최소 기능 Playground 스캐폴드입니다.

## 목표
- 사내 SSO 연동 시, `X-SSO-EMPID`(사번) 또는 `X-SSO-LOGINID`(로그인ID) 헤더로 사용자 식별
- 기본 기능: 프롬프트 실행(Stub), 실행 기록 저장(선택), 템플릿 관리(선택)

## 실행(로컬)
```bash
docker compose up --build
```
- UI: http://localhost:8501
- API: http://localhost:8001

## 환경변수
- `PUBLIC_BASE_URL` (선택)
- `SSO_HEADER_EMPID` (기본: `X-SSO-EMPID`)
- `SSO_HEADER_LOGINID` (기본: `X-SSO-LOGINID`)

> 실제 LLM 연동(OpenAI/Bedrock/사내모델)은 운영 환경에 맞게 추가하세요.
