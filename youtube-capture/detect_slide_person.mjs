import fs from 'node:fs';
import { PNG } from 'pngjs';

// Usage: node detect_slide_person.mjs <imagePath>
const imgPath = process.argv[2];
if (!imgPath) {
  console.error('Usage: node detect_slide_person.mjs <imagePath>');
  process.exit(2);
}

const buf = fs.readFileSync(imgPath);
const png = PNG.sync.read(buf);
const { width: w, height: h, data } = png;

function regionStats(x0, y0, x1, y1) {
  let n = 0;
  let white = 0;
  let dark = 0;
  let skin = 0;
  let sumL = 0;
  let sumL2 = 0;

  for (let y = y0; y < y1; y++) {
    for (let x = x0; x < x1; x++) {
      const i = (y * w + x) * 4;
      const r = data[i], g = data[i + 1], b = data[i + 2];

      // luminance
      const L = 0.2126 * r + 0.7152 * g + 0.0722 * b;
      sumL += L;
      sumL2 += L * L;

      // near-white
      if (r > 235 && g > 235 && b > 235) white++;
      // dark
      if (r < 40 && g < 40 && b < 40) dark++;

      // very rough skin-tone heuristic (works best for presenter box)
      // Common simple rules: R>95, G>40, B>20, max-min>15, |R-G|>15, R>G, R>B
      const max = Math.max(r, g, b);
      const min = Math.min(r, g, b);
      if (r > 95 && g > 40 && b > 20 && (max - min) > 15 && Math.abs(r - g) > 15 && r > g && r > b) {
        skin++;
      }

      n++;
    }
  }

  const meanL = sumL / n;
  const varL = Math.max(0, sumL2 / n - meanL * meanL);
  const stdL = Math.sqrt(varL);

  return {
    n,
    whiteRatio: white / n,
    darkRatio: dark / n,
    skinRatio: skin / n,
    meanL,
    stdL,
  };
}

// Heuristic split: left is likely slide area, right is likely presenter box area in the conference layout.
const left = regionStats(0, 0, Math.floor(w * 0.68), h);
const right = regionStats(Math.floor(w * 0.70), 0, w, h);

// Slide-present heuristic:
// - slides often have large white/light background + text/graphics => higher whiteRatio and moderate std
// Presenter-present heuristic:
// - presenter box tends to have human skin pixels + non-white background
const hasSlide = (left.whiteRatio > 0.18 && left.stdL > 35) || (left.whiteRatio > 0.28);
const hasPerson = (right.skinRatio > 0.008 && right.whiteRatio < 0.35) || (right.skinRatio > 0.015);

const ok = hasSlide && hasPerson;

// Print machine-friendly line
console.log(JSON.stringify({
  ok,
  width: w,
  height: h,
  left,
  right,
  hasSlide,
  hasPerson,
}));

process.exit(ok ? 0 : 1);
