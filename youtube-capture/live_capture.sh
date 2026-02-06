#!/usr/bin/env bash
set -euo pipefail

URL="$1"
OUT_BASE="${2:-/home/ironshim/.openclaw/workspace/shared/youtube_live_capture}"

# derive id
VID="$(python3 - "$URL" <<'PY'
import sys,re
u=sys.argv[1]
m=re.search(r"[?&]v=([^&]+)",u)
if m:
  print(m.group(1)); sys.exit(0)
m=re.search(r"youtu\.be/([^?&/]+)",u)
if m:
  print(m.group(1)); sys.exit(0)
m=re.search(r"/live/([^?&/]+)",u)
if m:
  print(m.group(1)); sys.exit(0)
print("live")
PY
)"

STAMP="$(date -u +%Y%m%d_%H%M%S)"
OUT_DIR="$OUT_BASE/${VID}_${STAMP}"
FRAMES_DIR="$OUT_DIR/frames_10s"
SUM_DIR="$OUT_DIR/summaries_5min"

mkdir -p "$FRAMES_DIR" "$SUM_DIR"

echo "[live_capture] url=$URL"
echo "[live_capture] out=$OUT_DIR"

# Resolve direct stream URLs once (may expire; we refresh on failure)
resolve_urls() {
  STREAM_JSON=$(~/.local/bin/yt-dlp -f "bestvideo+bestaudio/best" -g --print-json "$URL" 2>/dev/null | head -n 1 || true)
  if [ -z "$STREAM_JSON" ]; then
    # fallback without --print-json
    VIDEO_URL=$(~/.local/bin/yt-dlp -f "bestvideo" -g "$URL" | tail -n 1)
    AUDIO_URL=$(~/.local/bin/yt-dlp -f "bestaudio" -g "$URL" | tail -n 1)
  else
    VIDEO_URL=$(python3 - <<PY
import json,sys
j=json.loads(sys.stdin.read())
print(j.get('url',''))
PY
<<<"$STREAM_JSON")
    AUDIO_URL="$VIDEO_URL"
  fi
  if [[ "$VIDEO_URL" != http* ]]; then
    echo "[live_capture] failed to resolve stream url" >&2
    return 1
  fi
  return 0
}

resolve_urls

# 1) frames every 10 seconds (0.1 fps)
(
  while true; do
    if ! ffmpeg -hide_banner -loglevel error \
      -i "$VIDEO_URL" \
      -vf "fps=1/10,scale=1280:-2" \
      -q:v 3 \
      -strftime 1 \
      "$FRAMES_DIR/%Y%m%d_%H%M%S.jpg"; then
      echo "[frames] ffmpeg failed; refreshing stream url" >&2
      resolve_urls || sleep 5
    fi
    # ffmpeg above keeps running; if it exits, loop retries
    sleep 2
  done
) &
FRAMES_PID=$!

# 2) 5-minute rolling transcript + lightweight summary
(
  while true; do
    TS=$(date -u +%Y%m%d_%H%M%S)
    export YOUTUBE_URL="$URL"
    export OUT_DIR="$SUM_DIR"
    export DURATION=300
    export WHISPER_MODEL=base

    python3 /home_HOME_FIX_=1 /home/ironshim/.openclaw/workspace/yt-stt/transcribe_5min.py > "$SUM_DIR/$TS.json" 2>/dev/null || true

    # create a short summary (heuristic) from transcript
    TXT_PATH=$(python3 - <<PY
import json,sys,glob,os
p=sys.argv[1]
try:
  j=json.load(open(p,'r',encoding='utf-8'))
  print(j.get('transcriptPath',''))
except Exception:
  print('')
PY
"$SUM_DIR/$TS.json")

    if [ -n "$TXT_PATH" ] && [ -f "$TXT_PATH" ]; then
      python3 - <<'PY' "$TXT_PATH" "$SUM_DIR/$TS.summary.txt"
import sys,re
src=sys.argv[1]
out=sys.argv[2]
text=open(src,'r',encoding='utf-8').read().strip()
# very lightweight summarizer: first 2-3 sentences + keyword hints
sents=[s.strip() for s in re.split(r"[\.\?!]\s+|\n+", text) if s.strip()]
core=sents[:3]
# keywords: top frequent tokens (korean/english)
words=re.findall(r"[가-힣A-Za-z0-9]{2,}", text)
stop=set(['그리고','그래서','하지만','그런데','이것','저것','지금','그냥','합니다','하는','있습니다','있어요','되면','해서','하는거','여기','저기','우리','이제','그거','그게'])
from collections import Counter
kw=[w for w in words if w not in stop]
keys=[w for w,_ in Counter(kw).most_common(8)]
with open(out,'w',encoding='utf-8') as f:
  f.write('요약(5분)\n')
  for i,s in enumerate(core,1):
    f.write(f'- {s}\n')
  if keys:
    f.write('\n키워드: ' + ', '.join(keys) + '\n')
PY
    fi

    sleep 300
  done
) &
SUM_PID=$!

echo "[live_capture] frames_pid=$FRAMES_PID summaries_pid=$SUM_PID"
echo "[live_capture] stop: kill $FRAMES_PID $SUM_PID"

wait
