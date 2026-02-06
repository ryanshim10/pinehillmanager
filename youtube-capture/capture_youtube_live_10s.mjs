import fs from 'node:fs/promises';
import path from 'node:path';
import jpeg from 'jpeg-js';
import { chromium } from 'playwright';

// Usage:
//   node capture_youtube_live_10s.mjs <youtube_live_url> [outDir]
//
// Behavior:
// - Captures a JPG screenshot every 10 seconds
// - Detects presenter/person change (simple aHash on right-side region)
//   and when change is detected, it starts a NEW segment folder automatically.

const url = process.argv[2];
if (!url) {
  console.error('Usage: node capture_youtube_live_10s.mjs <youtube_live_url> [outDir]');
  process.exit(2);
}

const outBase = process.argv[3] || '/home/ironshim/.openclaw/workspace/shared/youtube_live_capture';

function getId(u){
  const m1 = u.match(/[?&]v=([^&]+)/);
  if (m1) return m1[1];
  const m2 = u.match(/youtu\.be\/([^?&/]+)/);
  if (m2) return m2[1];
  const m3 = u.match(/\/live\/([^?&/]+)/);
  if (m3) return m3[1];
  return 'live';
}

function stamp(){
  return new Date().toISOString().replace(/[-:]/g,'').replace(/\..+/, '').replace('T','_');
}

function popcount64(x) {
  // x is BigInt
  let c = 0n;
  while (x) { x &= (x - 1n); c++; }
  return Number(c);
}

function hamming(a, b){
  return popcount64(a ^ b);
}

function aHashRightRegion(jpgBuf){
  const decoded = jpeg.decode(jpgBuf, { useTArray: true });
  const { width: w, height: h, data } = decoded;

  // region: right 30% of the frame
  const x0 = Math.floor(w * 0.70);
  const x1 = w;
  const y0 = 0;
  const y1 = h;

  const gw = 16;
  const gh = 16;
  const gray = new Array(gw * gh).fill(0);

  for (let gy = 0; gy < gh; gy++) {
    for (let gx = 0; gx < gw; gx++) {
      const sx = Math.floor(x0 + (gx + 0.5) * (x1 - x0) / gw);
      const sy = Math.floor(y0 + (gy + 0.5) * (y1 - y0) / gh);
      const i = (sy * w + sx) * 4;
      const r = data[i], g = data[i + 1], b = data[i + 2];
      const L = 0.2126 * r + 0.7152 * g + 0.0722 * b;
      gray[gy * gw + gx] = L;
    }
  }

  const mean = gray.reduce((a,c)=>a+c,0) / gray.length;
  let bits = 0n;
  for (let i = 0; i < gray.length; i++) {
    if (gray[i] >= mean) bits |= (1n << BigInt(i));
  }
  return bits;
}

async function makeSegment(){
  const vid = getId(url);
  const runDir = path.join(outBase, `${vid}_${stamp()}`);
  const framesDir = path.join(runDir, 'frames_10s');
  await fs.mkdir(framesDir, { recursive: true });
  console.log(JSON.stringify({ ok:true, url, runDir, framesDir }, null, 2));
  return { runDir, framesDir };
}

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1280, height: 720 } });
const page = await context.newPage();

await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 120000 });

// Try to start playback if paused (best-effort)
try {
  await page.waitForTimeout(3000);
  await page.keyboard.press('k');
} catch {}

let seg = await makeSegment();

// person-change detection state
let baselineHash = null;
let pendingChange = 0;
const HAMMING_THRESHOLD = 22;      // 0..256, higher = less sensitive
const STABLE_FRAMES = 2;           // require N consecutive frames to confirm

while (true) {
  const name = stamp();
  const outPath = path.join(seg.framesDir, `${name}.jpg`);

  let buf;
  try {
    buf = await page.screenshot({ type: 'jpeg', quality: 80, fullPage: false });
    await fs.writeFile(outPath, buf);
  } catch (e) {
    console.error(`[frame_error] ${String(e).slice(0, 300)}`);
    await page.waitForTimeout(10_000);
    continue;
  }

  // detect person change
  try {
    const h = aHashRightRegion(buf);
    if (baselineHash === null) {
      baselineHash = h;
    } else {
      const d = hamming(baselineHash, h);
      if (d >= HAMMING_THRESHOLD) {
        pendingChange++;
      } else {
        pendingChange = Math.max(0, pendingChange - 1);
      }

      if (pendingChange >= STABLE_FRAMES) {
        console.log(`[person_change] detected (hamming>=${HAMMING_THRESHOLD}) -> rotate segment`);
        // rotate
        seg = await makeSegment();
        baselineHash = h;
        pendingChange = 0;
      }
    }
  } catch (e) {
    console.error(`[hash_error] ${String(e).slice(0, 300)}`);
  }

  console.log(`[frame] ${outPath}`);
  await page.waitForTimeout(10_000);
}
