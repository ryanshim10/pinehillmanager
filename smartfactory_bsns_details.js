const { chromium } = require('playwright');

async function run() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  await page.goto('https://www.smart-factory.kr/usr/bg/ba/ma/bsnsPbanc', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(2000);

  // get candidate anchors to detail
  const anchors = await page.$$('a[href*="bsnsPbancDtl"]');
  const items = [];

  for (let i=0;i<Math.min(10, anchors.length);i++) {
    const a = anchors[i];
    const text = (await a.innerText()).trim().replace(/\s+/g,' ');
    // click with navigation
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 60000 }),
      a.click()
    ]);
    await page.waitForTimeout(1000);

    const url = page.url();
    // try extract some meta from page (h3 or title)
    const h = await page.textContent('h3').catch(()=>null);
    items.push({ text, url, h3: h? h.trim().slice(0,120): null });

    // go back
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 60000 }),
      page.goBack()
    ]);
    await page.waitForTimeout(800);
  }

  await browser.close();
  console.log(JSON.stringify({ fetchedAt: new Date().toISOString(), items }, null, 2));
}

run().catch(e => { console.error(e); process.exit(1); });
