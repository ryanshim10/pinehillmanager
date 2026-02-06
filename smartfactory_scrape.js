const { chromium } = require('playwright');

async function run() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  await page.goto('https://www.smart-factory.kr/', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(2000);

  // Try find links that look like notices
  const links = await page.$$eval('a', as => as.map(a => ({ text: (a.innerText||'').trim(), href: a.href })).filter(x => x.text));
  const interesting = links.filter(l => /공지|공고|사업|모집|지원/.test(l.text)).slice(0,50);

  console.log(JSON.stringify({ url: page.url(), items: interesting }, null, 2));
  await browser.close();
}

run().catch(e => { console.error(e); process.exit(1); });
