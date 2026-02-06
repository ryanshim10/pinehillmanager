#!/usr/bin/env bash
set -euo pipefail

URL="${1:-}"
if [ -z "$URL" ]; then
  echo "Usage: ./run.sh <youtube_live_url>" >&2
  exit 1
fi

python3 -m venv .venv
source .venv/bin/activate
pip install -U pip >/dev/null
pip install yt-dlp faster-whisper >/dev/null

# ffmpeg is required
command -v ffmpeg >/dev/null || { echo "ffmpeg missing. Install: sudo apt-get install -y ffmpeg" >&2; exit 1; }

python live_stt.py --url "$URL" --chunk-seconds 300 --model small --device cpu --compute-type int8 --language ko
