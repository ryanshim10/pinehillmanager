# YouTube Live STT (faster-whisper) + 5-min summaries

Target URL:
- https://www.youtube.com/watch?v=ePc4Az15IOQ

## Install
```bash
sudo apt-get update
sudo apt-get install -y ffmpeg

python3 -m venv .venv
source .venv/bin/activate
pip install -U pip
pip install yt-dlp faster-whisper
```

## Run
```bash
source .venv/bin/activate
python live_stt.py --url "https://www.youtube.com/watch?v=ePc4Az15IOQ" --chunk-seconds 300 --model small --device cpu
```

Outputs:
- `out/transcript.ndjson` : chunk transcripts
- `out/summary.ndjson` : 5-min summaries (local heuristic)
- `out/chunks/*.wav` : raw 5-min audio chunks

## Notes
- This pipeline uses **local** STT (faster-whisper). No paid APIs.
- Summaries are heuristic (no LLM). If you want LLM summaries, we can pipe summaries into Shimplex or send to Telegram.
