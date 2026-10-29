import { UNITS, QUESTIONS } from './student-course.js';

export const reportTitle = '牛顿环实验仿真预习报告';
const time = value => {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '未记录' : date.toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false });
};
const choices = (q, values) => (values || []).map(i => `${String.fromCharCode(65 + i)}. ${q.options[i] || ''}`).join('；') || '未作答';
export function reportSections(data) {
  const result = data.challenge?.result;
  if (!result) throw new Error('请完成预习题和成像挑战后生成报告');
  const o = result.optics;
  const number = (value, unit = '') => Number.isFinite(Number(value)) && value != null ? `${Number(value).toFixed(2)}${unit}` : '未记录';
  const sections = [
    { title: '一 学生与实验信息', paragraphs: [
      `姓名 / 学号：${data.student || '未填写'}${data.studentClass ? `    班级：${data.studentClass}` : ''}`,
      '实验名称：牛顿环法测定平凸透镜曲率半径',
      `预习开始时间：${time(data.startedAt)}`,
      `报告记录时间：${time(data.finishedAt)}（北京时间）`,
    ] },
    { title: '二 预习结果汇总', paragraphs: [
      `预习结论：${data.passed ? '通过' : '尚未通过，需继续练习'}。通过标准为总分不低于 80 分且成像挑战通过。`,
    ], table: { headers: ['评价项目', '得分', '满分'], widths: [5400, 1800, 1800], rows: [
      ['预习题', String(data.knowledgeScore ?? 0), '40'],
      ['成像挑战', String(result.score ?? 0), '60'],
      ['合计', String(data.totalScore ?? 0), '100'],
    ] } },
    { title: '三 装配与引导调节', paragraphs: [`引导装调：${data.guidePassed ? '已通过' : '未通过'}`], table: {
      headers: ['组件名称', '安装结果'], widths: [6300, 2700],
      rows: UNITS.filter(u => u.install).map(u => [u.name, (data.installed || []).includes(u.id) ? '已安装' : '未安装']),
    } },
    { title: '四 预习题作答分析', questions: QUESTIONS.map((q, i) => {
      const a = (data.answers || []).find(a => a.id === q.id);
      return { title: `${i + 1}. ${q.question}`, paragraphs: [
        `学生答案：${choices(q, a?.selected)}`,
        `判定结果：${a?.correct ? '正确，得 5 分' : '未得分'}`,
        `正确答案：${choices(q, q.answers)}`,
        `解析：${q.explanation}`,
      ] };
    }) },
    { title: '五 成像挑战评价', paragraphs: [
      `挑战用时：${result.durationSeconds ?? '未记录'} 秒    调节次数：${result.edits ?? '未记录'} 次`,
    ], table: { headers: ['考核项目', '评价结果', '得分 / 满分'], widths: [5400, 1800, 1800], rows: (result.rubric || []).map(g => [g.label, g.ok ? '通过' : '需继续调节', `${g.ok ? g.points : 0} / ${g.points}`]) } },
  ];
  if (/^data:image\/(png|jpeg);base64,[A-Za-z0-9+/=]+$/.test(result.snapshot || '') && result.snapshot.length < 10_000_000) {
    sections.push({ title: '成像提交记录', image: result.snapshot });
  }
  if (o) sections.push({ title: '六 提交时的光学参数', table: { headers: ['参数名称', '记录值'], widths: [5400, 3600], rows: [
    ['光源位置', number(o.lampPosition)], ['半反镜倾角', number(o.mirrorAngle, '°')],
    ['目镜视度', number(o.reticle)], ['物镜高度', number(o.focus)], ['CCD 镜头调焦', number(o.cameraFocus)],
    ['相机曝光', number(o.exposure, '%')], ['孔径', number(o.aperture, '%')], ['鼓轮读数', number(o.reading, ' mm')],
    ['底座反光镜', o.baseMirrorFolded ? '已收起' : '未收起'],
  ] } });
  sections.push({ title: '实验模型说明', paragraphs: [
    '本报告记录仿真预习题和独立成像挑战的评价结果。仿真中的亮度、模糊程度和部分调节参数为教学相对量，不能作为真实仪器的测量结果。',
    '牛顿环视场中的十字丝固定，转动读数鼓轮时环纹相对十字丝移动。真实实验的数据记录与误差分析应在实验台完成。',
  ] });
  return sections;
}
