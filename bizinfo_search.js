const { chromium } = require('playwright');

async function run() {
  const queries = [
    'AI팩토리',
    '지능형 제조혁신',
    '스마트공장',
    '제조AI',
    '자율제조'
  ];

  const base = 'https://www.bizinfo.go.kr/sii/siia/selectSIIA200View.do';

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  const results = [];
  for (const q of queries) {
    const url = `${base}?searchCnd=1&searchWrd=${encodeURIComponent(q)}`;
    console.error('fetch', url);
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60000 });

    // Wait for table
    await page.waitForTimeout(1500);

    // Extract rows (best effort)
    const rows = await page.$$eval('table tbody tr', trs => trs.slice(0, 15).map(tr => {
      const tds = Array.from(tr.querySelectorAll('td'));
      if (tds.length < 7) return null;
      const link = tr.querySelector('a');
      return {
        no: tds[0].innerText.trim(),
        field: tds[1].innerText.trim(),
        title: link ? link.innerText.trim() : tds[2].innerText.trim(),
        href: link ? link.getAttribute('href') : null,
        period: tds[3].innerText.trim(),
        org: tds[4].innerText.trim(),
        agency: tds[5].innerText.trim(),
        date: tds[6].innerText.trim(),
      };
    }).filter(Boolean));

    for (const r of rows) {
      if (r.href && r.href.startsWith('/')) r.url = 'https://www.bizinfo.go.kr' + r.href;
      results.push({ query: q, ...r });
    }
  }

  await browser.close();

  // Deduplicate by title+date
  const seen = new Set();
  const dedup = [];
  for (const r of results) {
    const k = `${r.title}|${r.date}`;
    if (seen.has(k)) continue;
    seen.add(k);
    dedup.push(r);
  }

  console.log(JSON.stringify({ fetchedAt: new Date().toISOString(), items: dedup }, null, 2));
}

run().catch(e => {
  console.error(e);
  process.exit(1);
});
