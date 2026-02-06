const { chromium } = require('playwright');

async function run() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  await page.goto('https://www.smart-factory.kr/', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(1500);

  // Try go to notices list by clicking any notice link
  // If main page has notices list, capture first 10 notice links
  const noticeLinks = await page.$$eval('a[href*="notice"]', as => {
    return as
      .map(a => ({ text: (a.innerText||'').trim().replace(/\s+/g,' '), href: a.href }))
      .filter(x => x.text && /\d{4}-\d{2}-\d{2}/.test(x.text) || /공고|모집|안내|고시/.test(x.text));
  });

  // Keep unique by href+text
  const seen = new Set();
  const uniq = [];
  for (const l of noticeLinks) {
    const k = l.href + '|' + l.text;
    if (seen.has(k)) continue;
    seen.add(k);
    uniq.push(l);
  }

  const items = [];
  for (const l of uniq.slice(0, 8)) {
    // open link in same page
    await page.goto(l.href, { waitUntil: 'domcontentloaded', timeout: 60000 });
    await page.waitForTimeout(800);
    items.push({ title: l.text.slice(0,200), url: page.url() });
  }

  await browser.close();
  console.log(JSON.stringify({ fetchedAt: new Date().toISOString(), items }, null, 2));
}

run().catch(e => { console.error(e); process.exit(1); });
