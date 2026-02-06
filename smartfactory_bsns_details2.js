const { chromium } = require('playwright');

async function run() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  await page.goto('https://www.smart-factory.kr/usr/bg/ba/ma/bsnsPbanc', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(2000);

  const titles = await page.$$eval('a[href*="bsnsPbancDtl"]', as => as.map(a => (a.innerText||'').trim().replace(/\s+/g,' ')).filter(t=>t && t!=='사업안내'));
  const uniq=[]; const seen=new Set();
  for (const t of titles) { if(seen.has(t)) continue; seen.add(t); uniq.push(t);} 

  const items=[];
  for (const t of uniq.slice(0,10)) {
    const link = page.getByRole('link', { name: t });
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 60000 }),
      link.click()
    ]);
    await page.waitForTimeout(1200);
    const url = page.url();

    // attempt to extract hidden inputs that may contain ids
    const hidden = await page.$$eval('input[type="hidden"]', ins => ins.slice(0,20).map(i=>({name:i.name||i.id||'', value:i.value||''})).filter(x=>x.name && x.value));
    const heading = await page.textContent('h3').catch(()=>null);

    items.push({ title: t, url, heading: heading?heading.trim().slice(0,120):null, hidden });

    await Promise.all([
      page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 60000 }),
      page.goBack()
    ]);
    await page.waitForTimeout(800);
  }

  await browser.close();
  console.log(JSON.stringify({ fetchedAt: new Date().toISOString(), items }, null, 2));
}

run().catch(e=>{console.error(e);process.exit(1);});
