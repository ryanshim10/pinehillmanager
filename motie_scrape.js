const { chromium } = require('playwright');

async function run() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  await page.goto('https://www.motir.go.kr/', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(2000);

  // Try click "사업공고" menu if exists
  const link = page.getByRole('link', { name: '사업공고' });
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 60000 }).catch(()=>{}),
    link.click().catch(()=>{})
  ]);
  await page.waitForTimeout(2000);

  const url = page.url();

  // Extract list items/rows containing dates
  const items = await page.$$eval('a', as => as.map(a => ({ text: (a.innerText||'').trim().replace(/\s+/g,' '), href: a.href }))
    .filter(x => x.text && /\d{4}\.\d{2}\.\d{2}/.test(x.text) || /공고|사업/.test(x.text))
  );

  // Dedup
  const seen=new Set();
  const uniq=[];
  for (const it of items) {
    const k=it.href+'|'+it.text;
    if(seen.has(k)) continue;
    seen.add(k);
    uniq.push(it);
  }

  console.log(JSON.stringify({ landing: url, items: uniq.slice(0,80) }, null, 2));
  await browser.close();
}

run().catch(e => { console.error(e); process.exit(1); });
