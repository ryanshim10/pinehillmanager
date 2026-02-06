const { chromium } = require('playwright');

async function run() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  await page.goto('https://www.smart-factory.kr/usr/bg/ba/ma/bsnsPbanc', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(2000);
  const title = await page.title();
  const url = page.url();
  const links = await page.$$eval('a', as => as.map(a => ({ text: (a.innerText||'').trim().replace(/\s+/g,' '), href: a.href })).filter(x => x.text && /공고|모집|지원|사업/.test(x.text)));
  const uniq=[]; const seen=new Set();
  for (const l of links) { const k=l.href+'|'+l.text; if(seen.has(k)) continue; seen.add(k); uniq.push(l);} 
  console.log(JSON.stringify({title,url,count:uniq.length,items:uniq.slice(0,30)},null,2));
  await browser.close();
}
run().catch(e=>{console.error(e);process.exit(1);});
