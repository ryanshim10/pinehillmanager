import ExcelJS from 'exceljs';
import fs from 'node:fs/promises';
import path from 'node:path';

// Usage:
//   node make_glossary_xlsx.mjs [outPath] [sourceJsonPath]
// Defaults:
//   outPath:      ./AI_DX_Glossary_Manufacturing.xlsx
//   sourceJson:   ../glossary-webapp/data/glossary.json

const outPath = process.argv[2]
  || path.resolve('./AI_DX_Glossary_Manufacturing.xlsx');

const sourceJsonPath = process.argv[3]
  || path.resolve('../glossary-webapp/data/glossary.json');

const raw = await fs.readFile(sourceJsonPath, 'utf-8');
/** @type {Array<{kr:string,en?:string,category?:string,oneLine?:string,example?:string,kpi?:string[],confusions?:string[],ask?:string[]}>} */
const entries = JSON.parse(raw);

const rows = entries.map(e => ({
  kr: e.kr || '',
  en: e.en || '',
  cat: e.category || '',
  def: e.oneLine || '',
  example: e.example || '',
  kpi: Array.isArray(e.kpi) ? e.kpi.join(', ') : '',
  confusions: Array.isArray(e.confusions) ? e.confusions.join(', ') : '',
}));

const wb = new ExcelJS.Workbook();
wb.creator = 'OpenClaw';
wb.created = new Date();

const ws = wb.addWorksheet('Glossary');

ws.columns = [
  { header: '용어(KR)', key: 'kr', width: 22 },
  { header: '약어/EN', key: 'en', width: 22 },
  { header: '분류', key: 'cat', width: 12 },
  { header: '한줄 정의', key: 'def', width: 70 },
  { header: '예시', key: 'example', width: 60 },
  { header: 'KPI', key: 'kpi', width: 25 },
  { header: '혼동되는 용어', key: 'confusions', width: 30 },
];

ws.addRows(rows);

ws.getRow(1).font = { bold: true };
ws.views = [{ state: 'frozen', ySplit: 1 }];
ws.autoFilter = {
  from: { row: 1, column: 1 },
  to: { row: 1, column: ws.columns.length },
};

for (let i = 1; i <= ws.columns.length; i++) {
  ws.getColumn(i).alignment = { vertical: 'top', wrapText: true };
}

await fs.mkdir(path.dirname(outPath), { recursive: true });
await wb.xlsx.writeFile(outPath);
console.log(`Wrote: ${outPath}`);
console.log(`From:  ${sourceJsonPath}`);
