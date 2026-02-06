#!/usr/bin/env python3
import os, sys, json, subprocess, datetime, pathlib, textwrap

def sh(cmd, check=True):
    return subprocess.run(cmd, check=check, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)

VIDEO_URL = os.environ.get("YOUTUBE_URL", "https://www.youtube.com/watch?v=ePc4Az15IOQ")
OUT_DIR = pathlib.Path(os.environ.get("OUT_DIR", "/home/ironshim/.openclaw/workspace/shared/youtube_summaries/ePc4Az15IOQ"))
DURATION = int(os.environ.get("DURATION", "300"))
MODEL = os.environ.get("WHISPER_MODEL", "base")

# Use venv python if available
VENV_PY = pathlib.Path(__file__).resolve().parent / ".venv" / "bin" / "python"
PY = str(VENV_PY) if VENV_PY.exists() else sys.executable

OUT_DIR.mkdir(parents=True, exist_ok=True)
(OUT_DIR / "audio").mkdir(exist_ok=True)
(OUT_DIR / "transcripts").mkdir(exist_ok=True)

now = datetime.datetime.utcnow()
stamp = now.strftime("%Y%m%d_%H%M%S")
audio_wav = OUT_DIR / "audio" / f"{stamp}.wav"

# 1) Get direct audio URL via yt-dlp
cmd_get = [PY, "-m", "yt_dlp", "-f", "bestaudio", "-g", VIDEO_URL]
res = sh(cmd_get)
stream_url = res.stdout.strip().splitlines()[-1].strip() if res.stdout.strip() else ""
if not stream_url.startswith("http"):
    print(json.dumps({"ok": False, "error": "Failed to resolve stream URL", "yt_dlp_output": res.stdout[-2000:]}, ensure_ascii=False))
    sys.exit(2)

# 2) Record 5 minutes of audio
cmd_ff = [
    "ffmpeg", "-y",
    "-i", stream_url,
    "-t", str(DURATION),
    "-vn",
    "-ac", "1",
    "-ar", "16000",
    "-c:a", "pcm_s16le",
    str(audio_wav),
]
res2 = sh(cmd_ff, check=False)
if res2.returncode != 0 or not audio_wav.exists() or audio_wav.stat().st_size < 50_000:
    print(json.dumps({"ok": False, "error": "ffmpeg capture failed", "ffmpeg_output": res2.stdout[-4000:]}, ensure_ascii=False))
    sys.exit(3)

# 3) Transcribe with faster-whisper
try:
    from faster_whisper import WhisperModel
except Exception as e:
    print(json.dumps({"ok": False, "error": f"Failed to import faster_whisper: {e}"}, ensure_ascii=False))
    sys.exit(4)

model = WhisperModel(MODEL, device="cpu", compute_type="int8")
segments, info = model.transcribe(str(audio_wav), vad_filter=True)
text_parts = []
for seg in segments:
    t = (seg.text or "").strip()
    if t:
        text_parts.append(t)

full_text = " ".join(text_parts).strip()
transcript_path = OUT_DIR / "transcripts" / f"{stamp}.txt"
transcript_path.write_text(full_text + "\n", encoding="utf-8")

payload = {
    "ok": True,
    "stampUtc": stamp,
    "durationSec": DURATION,
    "model": MODEL,
    "language": getattr(info, "language", None),
    "audioPath": str(audio_wav),
    "transcriptPath": str(transcript_path),
    "text": full_text,
}
print(json.dumps(payload, ensure_ascii=False))
