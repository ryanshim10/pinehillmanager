const { chromium } = require('playwright');

async function run(){
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  const url='https://www.motir.go.kr/kor/article/ATCL2826a2625';
  await page.goto(url,{waitUntil:'domcontentloaded',timeout:60000});
  await page.waitForTimeout(1500);

  // Try to locate rows in table
  const rows = await page.$$eval('table tbody tr', trs => trs.slice(0,20).map(tr=>{
    const tds=Array.from(tr.querySelectorAll('td'));
    const th=tr.querySelector('th');
    const a=tr.querySelector('a');
    return {
      gonggoNo: th ? th.innerText.trim() : null,
      title: a ? a.innerText.trim().replace(/\s+/g,' ') : (tds[0]?.innerText||'').trim(),
      href: a ? a.href : null,
      dept: tds[1]?.innerText?.trim(),
      date: tds[2]?.innerText?.trim(),
      attach: tr.querySelector('a[href*="/attach/down/"]')?.href || null,
    };
  }).filter(r=>r.gonggoNo));

  console.log(JSON.stringify({source:url, items: rows}, null, 2));
  await browser.close();
}
run().catch(e=>{console.error(e);process.exit(1);});
