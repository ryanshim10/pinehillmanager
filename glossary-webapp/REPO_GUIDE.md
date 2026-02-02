# 레포 저장/배포 안내 (Repo Guide)

이 레포는 현재 `/home/ironshim/.openclaw/workspace` 아래에 여러 프로젝트가 섞여 있습니다.
용어집 웹앱은 아래 경로가 **정본**입니다:

- `glossary-webapp/`

## GitHub 레포로 저장(권장 구조)

### 1) 새 레포 생성(권장)
GitHub에서 새 레포 예: `manufacturing-glossary-webapp` 생성 후,
로컬에서 `glossary-webapp`만 따로 레포로 만들면 관리가 깔끔합니다.

### 2) 최소 포함 파일
- `glossary-webapp/app/`
- `glossary-webapp/templates/`
- `glossary-webapp/static/`
- `glossary-webapp/data/glossary.json`
- `glossary-webapp/requirements.txt`
- `glossary-webapp/.env.example` (실제 `.env`는 절대 커밋 금지)
- `glossary-webapp/INSTALL_MANUAL.md`

### 3) 커밋/푸시 예시
```bash
cd glossary-webapp

git init

git add .
git commit -m "Initial: glossary webapp"

git branch -M main
# HTTPS 대신 SSH나 GitHub CLI 권장
# git remote add origin git@github.com:{계정}/{레포}.git
# git push -u origin main
```

## 보안 주의
- `.env` / API Key / 토큰은 절대 레포에 올리지 마세요.
- 실수로 올렸으면 즉시 토큰 폐기 + git 히스토리 정리 필요.
