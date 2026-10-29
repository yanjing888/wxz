import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { unzipSync, strFromU8 } from 'fflate';
import { createReportDocx } from '../public/newton-rings-simulation/student-report-docx.js';
import { reportHtml } from '../public/newton-rings-simulation/student-report.js';
import { QUESTIONS, UNITS } from '../public/newton-rings-simulation/student-course.js';

const data = {
  student: '示例学生 / 20260001', studentClass: '物理实验示例班', startedAt: 1793232000000, finishedAt: 1793235600000,
  installed: UNITS.filter(u => u.install).map(u => u.id), guidePassed: true,
  answers: QUESTIONS.map(q => ({ id: q.id, selected: q.answers, correct: true })),
  knowledgeScore: 40, totalScore: 100, passed: true,
  challenge: { result: { score: 60, durationSeconds: 125, edits: 18,
    rubric: ['CCD 供电、接线和屏幕', '光源与半反镜照明', '十字丝清晰', '物镜高度与环纹焦点', '底座反光镜收起', 'CCD 图像质量', '暗斑居中'].map((label, i) => ({ label, points: i === 4 || i === 5 ? 5 : 10, ok: true })),
    optics: { lampPosition: 0, mirrorAngle: 45, reticle: 50, focus: 50, cameraFocus: 50, exposure: 100, aperture: 70, reading: 25, baseMirrorFolded: true },
  } },
};
const blob = await createReportDocx(data);
assert.equal(blob.type, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
const bytes = Buffer.from(await blob.arrayBuffer());
assert.equal(bytes.subarray(0, 2).toString(), 'PK');
const zip = unzipSync(bytes);
assert.ok(zip['[Content_Types].xml']);
const xml = strFromU8(zip['word/document.xml']);
for (const text of ['牛顿环实验仿真预习报告', data.student, data.studentClass, '预习题作答分析', '成像挑战评价', '25.00 mm']) assert.ok(xml.includes(text), text);
assert.ok(xml.includes('w:tblHeader'));
assert.ok(xml.includes('w:pgSz'));
assert.ok(zip['word/footer1.xml']);
await assert.rejects(createReportDocx({ ...data, challenge: null }));
const malicious = { ...data, student: '<script>alert(1)</script>' };
assert.ok(!reportHtml(malicious).includes('<script>alert(1)</script>'));
const escaped = unzipSync(new Uint8Array(await (await createReportDocx(malicious)).arrayBuffer()));
assert.ok((strFromU8(escaped['word/document.xml'])).includes('&lt;script&gt;'));
if (process.argv.includes('--sample')) {
  const dir = new URL('../../../.codex-logs/prep-report-qa/', import.meta.url);
  await mkdir(dir, { recursive: true });
  await writeFile(new URL('prep-report.docx', dir), bytes);
}
console.log('PASS: real DOCX archive, formal report sections, Chinese content, repeating headers, page footer, report gating and escaping');
