# Glossary WebApp (Manufacturing AI/DX)

Simple glossary webapp:
- Search terms locally (fast)
- If not found, can call an LLM endpoint to generate a **draft** entry (optional)

## Quick start (local)

```bash
cd glossary-webapp
python3 -m venv .venv
. .venv/bin/activate
pip install -U pip
pip install -r requirements.txt

# run
uvicorn app.main:app --host 0.0.0.0 --port 8080
```

Open: http://localhost:8080

## Config (optional LLM)
Copy `.env.example` to `.env` and fill if you want auto-draft generation.

- `LLM_MODE=off|azure_openai|hchat`
- `LLM_ENDPOINT=...`
- `LLM_API_KEY=...`
- `LLM_DEPLOYMENT=...` (for Azure OpenAI)
- `LLM_API_VERSION=...` (for Azure OpenAI)

If `LLM_MODE=off`, the app will only search local glossary.

## Data
- `data/glossary.json` is the source.
- You can edit it manually or extend it.

## Export (Excel)
- GUI button: **엑셀 다운로드**
- Endpoint: `GET /api/export.xlsx`

## Notes
This is intentionally minimal: one Python service + one HTML page.
