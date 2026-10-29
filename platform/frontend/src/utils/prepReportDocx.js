import { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, WidthType, HeadingLevel, AlignmentType, Footer, PageNumber, TableLayoutType, ImageRun } from 'docx';
import { reportSections, reportTitle } from '../../public/newton-rings-simulation/student-report-content.js';

const font = { ascii: 'Times New Roman', hAnsi: 'Times New Roman', eastAsia: '宋体' };
const paragraph = (text, options = {}) => new Paragraph({
  spacing: { after: 100, line: 320 }, ...options,
  children: [new TextRun({ text: String(text), font, size: 22, color: '000000', ...options.run })],
});
function table({ headers, rows, widths }) {
  return new Table({
    width: { size: 9000, type: WidthType.DXA }, columnWidths: widths, layout: TableLayoutType.FIXED,
    rows: [headers, ...rows].map((row, index) => new TableRow({
      tableHeader: index === 0, cantSplit: true,
      children: row.map((text, i) => new TableCell({
        width: { size: widths[i], type: WidthType.DXA },
        shading: index === 0 ? { fill: 'EDEFF2' } : undefined,
        margins: { top: 100, bottom: 100, left: 120, right: 120 },
        children: [paragraph(text, { spacing: { after: 0, line: 280 }, run: { bold: index === 0, size: 21 } })],
      })),
    })),
  });
}
export async function createReportDocx(data) {
  const children = [paragraph(reportTitle, { heading: HeadingLevel.TITLE, alignment: AlignmentType.CENTER, spacing: { after: 300 }, run: { bold: true, size: 36, font: { ...font, eastAsia: '黑体' } } })];
  for (const section of reportSections(data)) {
    children.push(paragraph(section.title, { heading: HeadingLevel.HEADING_1, keepNext: true, spacing: { before: 240, after: 140 }, run: { bold: true, size: 26 } }));
    for (const text of section.paragraphs || []) children.push(paragraph(text));
    if (section.table) children.push(table(section.table), paragraph(''));
    if (section.image) {
      const binary = Uint8Array.from(atob(section.image.split(',')[1]), c => c.charCodeAt(0));
      children.push(new Paragraph({ alignment: AlignmentType.CENTER, children: [new ImageRun({ type: section.image.startsWith('data:image/png') ? 'png' : 'jpg', data: binary, transformation: { width: 560, height: 315 }, altText: { title: '成像挑战提交记录', description: '学生提交成像挑战时的仿真场景', name: '成像记录' } })] }));
    }
    for (const question of section.questions || []) {
      children.push(paragraph(question.title, { keepNext: true, spacing: { before: 160, after: 100 }, run: { bold: true } }));
      question.paragraphs.forEach((text, i) => children.push(paragraph(text, { keepNext: i < question.paragraphs.length - 1 })));
    }
  }
  const doc = new Document({
    creator: '物小智', title: reportTitle, description: '牛顿环实验仿真预习题与成像挑战结果',
    styles: { default: { document: { run: { font, size: 22, color: '000000' }, paragraph: { spacing: { line: 320 } } } } },
    sections: [{
      properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1400, bottom: 1400, left: 1453, right: 1453 } } },
      footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: '第 ', size: 18 }), new TextRun({ children: [PageNumber.CURRENT], size: 18 }), new TextRun({ text: ' 页', size: 18 })] })] }) },
      children,
    }],
  });
  return Packer.toBlob(doc);
}
