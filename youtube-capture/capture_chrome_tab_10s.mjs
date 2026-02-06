import fs from 'node:fs/promises';
import path from 'node:path';
import jpeg from 'jpeg-js';
import { chromium } from 'playwright';

// Usage:
//   node capture_chrome_tab_10s.mjs <cdpWsUrl> <matchUrlSubstr> [outBase]
// Example:
//   node capture_chrome_tab_10s.mjs "ws://127.0.0.1:18792/cdp" "youtube.com/live/XyfwcjXvbII" \
//     /home/ironshim/.openclaw/workspace/shared/youtube_live_capture

const cdpWsUrl = process.argv[2];
const matchSubstr = process.argv[3];
if (!cdpWsUrl || !matchSubstr) {
  console.error('Usage: node capture_chrome_tab_10s.mjs <cdpWsUrl> <matchUrlSubstr> [outBase]');
  process.exit(2);
}
const outBase = process.argv[4] || '/home/ironshim/.openclaw/workspace/shared/youtube_live_capture';

function stamp(){
  return new Date().toISOString().replace(/[-:]/g,'').replace(/\..+/, '').replace('T','_');
}

function popcount64(x) {
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

  const x0 = Math.floor(w * 0.70);
  const x1 = w;

  const gw = 16;
  const gh = 16;
  const gray = new Array(gw * gh).fill(0);

  for (let gy = 0; gy < gh; gy++) {
    for (let gx = 0; gx < gw; gx++) {
      const sx = Math.floor(x0 + (gx + 0.5) * (x1 - x0) / gw);
      const sy = Math.floor((gy + 0.5) * h / gh);
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

function getVidFromUrl(url){
  const m1 = url.match(/[?&]v=([^&]+)/);
  if (m1) return m1[1];
  const m2 = url.match(/youtu\.be\/([^?&/]+)/);
  if (m2) return m2[1];
  const m3 = url.match(/\/live\/([^?&/]+)/);
  if (m3) return m3[1];
  return 'live';
}

async function makeSegment(vid){
  const runDir = path.join(outBase, `${vid}_${stamp()}`);
  const framesDir = path.join(runDir, 'frames_10s');
  await fs.mkdir(framesDir, { recursive: true });
  console.log(JSON.stringify({ ok:true, runDir, framesDir }, null, 2));
  return { runDir, framesDir };
}

const browser = await chromium.connectOverCDP(cdpWsUrl);

// Find the page
let page = null;
for (const ctx of browser.contexts()) {
  for (const p of ctx.pages()) {
    const u = p.url();
    if (u && u.includes(matchSubstr)) {
      page = p;
      break;
    }
  }
  if (page) break;
}

if (!page) {
  console.error(`No page matched substring: ${matchSubstr}`);
  process.exit(3);
}

await page.bringToFront();

const vid = getVidFromUrl(page.url());
let seg = await makeSegment(vid);

let baselineHash = null;
let pendingChange = 0;
const HAMMING_THRESHOLD = 22;
const STABLE_FRAMES = 2;

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
      if (d >= HAMMING_THRESHOLD) pendingChange++;
      else pendingChange = Math.max(0, pendingChange - 1);

      if (pendingChange >= STABLE_FRAMES) {
        console.log(`[person_change] detected (hamming=${d}) -> rotate segment`);
        seg = await makeSegment(vid);
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
