import { reportSections, reportTitle } from './student-report-content.js';
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const css = `*{box-sizing:border-box}body{margin:0;padding:24px;background:#e9edf2;color:#111}.report-document{width:210mm;max-width:100%;margin:0 auto;padding:24mm 25mm;background:white;box-shadow:0 2px 14px #17203318;font:11pt/1.65 'SimSun','宋体',serif}.report-document h1{font:700 20pt/1.4 'SimHei','黑体',sans-serif;text-align:center;margin:0 0 24pt;color:#000;border:0}.report-document h2{font-size:14pt;color:#000;margin:20pt 0 10pt;border:0;break-after:avoid}.report-document h3{font-size:11pt;margin:12pt 0 6pt;break-after:avoid}.report-document p{margin:5pt 0 8pt;overflow-wrap:anywhere}.report-document table{width:100%;border-collapse:collapse;margin:10pt 0 16pt;table-layout:fixed;font-size:10.5pt}.report-document td,.report-document th{border:1px solid #8b929a;padding:7pt 8pt;text-align:left;overflow-wrap:anywhere}.report-document th{background:#edeff2;font-weight:bold;color:#000}.report-document tr,.question{break-inside:avoid}.report-document thead{display:table-header-group}@page{size:A4;margin:24mm 25mm}@media print{body{background:white;padding:0}.report-document{width:auto;max-width:none;padding:0;box-shadow:none}}@media(max-width:650px){body{padding:8px}.report-document{padding:22px}}`;
function markup(data) {
  return `<article class="report-document"><h1>${reportTitle}</h1>${reportSections(data).map(section => `<section><h2>${esc(section.title)}</h2>${(section.paragraphs || []).map(p => `<p>${esc(p)}</p>`).join('')}${section.table ? `<table><colgroup>${section.table.widths.map(width => `<col style="width:${width / 90}%">`).join('')}</colgroup><thead><tr>${section.table.headers.map(h => `<th>${esc(h)}</th>`).join('')}</tr></thead><tbody>${section.table.rows.map(row => `<tr>${row.map(cell => `<td>${esc(cell)}</td>`).join('')}</tr>`).join('')}</tbody></table>` : ''}${section.image ? `<img src="${esc(section.image)}" alt="成像挑战提交记录" style="max-width:100%;height:auto">` : ''}${(section.questions || []).map(q => `<div class="question"><h3>${esc(q.title)}</h3>${q.paragraphs.map(p => `<p>${esc(p)}</p>`).join('')}</div>`).join('')}</section>`).join('')}</article>`;
}
export function reportHtml(data) {
  return `<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${reportTitle}</title><style>${css}</style></head><body>${markup(data)}</body></html>`;
}
export function openReport(state, lab, data) {
  const host = document.getElementById('reportBody');
  host.innerHTML = '<iframe title="仿真预习报告" sandbox="allow-modals allow-same-origin" style="width:100%;height:68vh;border:0"></iframe>';
  host.firstElementChild.srcdoc = reportHtml(data);
  document.getElementById('reportDialog').showModal();
}
function download(blob, extension, data) {
  const url = URL.createObjectURL(blob), link = document.createElement('a');
  const student = String(data.student || '学生').replace(/[\\/:*?"<>|]/g, '_').slice(0, 60);
  link.href = url; link.download = `${reportTitle}-${student}.${extension}`;
  link.click(); setTimeout(() => URL.revokeObjectURL(url), 1500);
}
export function downloadData(data) {
  download(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json;charset=utf-8' }), 'json', data);
}
export async function downloadReport(data) {
  const { createReportDocx } = await import('./student-report-docx.js');
  download(await createReportDocx(data), 'docx', data);
}
