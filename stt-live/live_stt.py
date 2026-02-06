#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, List, Tuple


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def ensure_dir(p: Path) -> None:
    p.mkdir(parents=True, exist_ok=True)


def run_cmd(cmd: List[str], *, timeout: int | None = None) -> None:
    proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=timeout)
    if proc.returncode != 0:
        raise RuntimeError(f"Command failed: {' '.join(cmd)}\nSTDERR:\n{proc.stderr[-2000:]}")


def capture_chunk(url: str, out_wav: Path, seconds: int) -> None:
    # Use yt-dlp to stream best audio to stdout, then ffmpeg to cut a chunk.
    # 16kHz mono wav works well for whisper.
    ytdlp = ["yt-dlp", "-f", "bestaudio", "-o", "-", url]
    ffmpeg = [
        "ffmpeg",
        "-hide_banner",
        "-loglevel",
        "error",
        "-i",
        "pipe:0",
        "-t",
        str(seconds),
        "-ac",
        "1",
        "-ar",
        "16000",
        str(out_wav),
    ]

    with subprocess.Popen(ytdlp, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL) as p1:
        assert p1.stdout is not None
        with subprocess.Popen(ffmpeg, stdin=p1.stdout, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True) as p2:
            _, err = p2.communicate()
            p1.kill()
            if p2.returncode != 0:
                raise RuntimeError(f"ffmpeg failed: {err[-2000:]}")


def split_sentences_ko(text: str) -> List[str]:
    # Very simple splitter for Korean transcripts
    text = re.sub(r"\s+", " ", text).strip()
    if not text:
        return []
    # Split on ., !, ?, or '다.' boundaries by punctuation, also keep long lines.
    parts = re.split(r"(?<=[\.\!\?])\s+", text)
    # Fallback: split on '다 ' as a boundary sometimes
    out: List[str] = []
    for p in parts:
        p = p.strip()
        if not p:
            continue
        if len(p) > 180:
            out.extend([x.strip() for x in re.split(r"(?<=다)\s+", p) if x.strip()])
        else:
            out.append(p)
    # De-dup while preserving order
    seen = set()
    uniq = []
    for s in out:
        if s in seen:
            continue
        seen.add(s)
        uniq.append(s)
    return uniq


def heuristic_summary(text: str, max_bullets: int = 6) -> List[str]:
    sents = split_sentences_ko(text)
    if not sents:
        return ["(전사 텍스트가 부족합니다)"]

    # Score sentences by keyword hits + length preference
    keywords = [
        "결론",
        "요약",
        "핵심",
        "중요",
        "정리",
        "따라서",
        "그래서",
        "왜냐하면",
        "예를 들어",
        "데이터",
        "AI",
        "스마트",
        "공정",
        "효율",
        "OEE",
        "품질",
    ]

    scored: List[Tuple[float, str]] = []
    for s in sents:
        score = 0.0
        for kw in keywords:
            if kw.lower() in s.lower():
                score += 2.0
        # prefer mid-length sentences
        L = len(s)
        if 40 <= L <= 140:
            score += 1.5
        elif L < 25:
            score -= 0.5
        else:
            score += 0.3
        scored.append((score, s))

    scored.sort(key=lambda x: x[0], reverse=True)

    bullets: List[str] = []
    for _, s in scored:
        if s in bullets:
            continue
        bullets.append(s)
        if len(bullets) >= max_bullets:
            break

    # If still too few, top from original order
    if len(bullets) < min(3, max_bullets):
        for s in sents:
            if s in bullets:
                continue
            bullets.append(s)
            if len(bullets) >= max_bullets:
                break

    return bullets


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", required=True)
    ap.add_argument("--chunk-seconds", type=int, default=300)
    ap.add_argument("--model", default="small", help="tiny|base|small|medium|large-v3")
    ap.add_argument("--device", default="cpu", help="cpu|cuda")
    ap.add_argument("--compute-type", default="int8", help="cpu: int8 recommended; cuda: float16 recommended")
    ap.add_argument("--language", default="ko")
    args = ap.parse_args()

    try:
        from faster_whisper import WhisperModel
    except Exception as e:
        print("Missing faster-whisper. Install: pip install faster-whisper", file=sys.stderr)
        raise

    out_dir = Path(__file__).resolve().parent / "out"
    chunks_dir = out_dir / "chunks"
    ensure_dir(chunks_dir)

    transcript_path = out_dir / "transcript.ndjson"
    summary_path = out_dir / "summary.ndjson"

    model = WhisperModel(args.model, device=args.device, compute_type=args.compute_type)

    print(f"[live_stt] start {utc_now_iso()} url={args.url} chunk={args.chunk_seconds}s model={args.model} device={args.device}")

    i = 0
    while True:
        i += 1
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        wav_path = chunks_dir / f"chunk_{ts}.wav"

        try:
            capture_chunk(args.url, wav_path, args.chunk_seconds)
        except Exception as e:
            print(f"[live_stt] capture failed: {e}", file=sys.stderr)
            time.sleep(5)
            continue

        # Transcribe
        try:
            segments, info = model.transcribe(str(wav_path), language=args.language, vad_filter=True)
            texts = []
            for s in segments:
                if s.text:
                    texts.append(s.text.strip())
            full_text = " ".join(texts).strip()
        except Exception as e:
            print(f"[live_stt] transcribe failed: {e}", file=sys.stderr)
            full_text = ""

        rec = {
            "at": utc_now_iso(),
            "chunk": wav_path.name,
            "seconds": args.chunk_seconds,
            "text": full_text,
        }
        transcript_path.parent.mkdir(parents=True, exist_ok=True)
        with open(transcript_path, "a", encoding="utf-8") as f:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")

        bullets = heuristic_summary(full_text, max_bullets=6)
        sumrec = {
            "at": utc_now_iso(),
            "chunk": wav_path.name,
            "range": f"{ts} ~ (+{args.chunk_seconds}s)",
            "bullets": bullets,
        }
        with open(summary_path, "a", encoding="utf-8") as f:
            f.write(json.dumps(sumrec, ensure_ascii=False) + "\n")

        # Print to stdout
        print("\n" + "=" * 70)
        print(f"[5분 요약] {sumrec['range']}")
        for b in bullets:
            print(f"- {b}")
        print("=" * 70 + "\n")

        # loop continues


if __name__ == "__main__":
    main()
