/**
 * 学生端智能体目录（对齐法小智式「选智能体 → 进工作区」）。
 * 每项都是明确的 AI 能力入口，而不是埋在工具面板里的按钮。
 */

export const STUDENT_AGENT_GROUPS = [
  {
    key: 'pre',
    label: '课前',
    lead: '进实验室前先就绪'
  },
  {
    key: 'lab',
    label: '课上',
    lead: '操作、读数与数据可信'
  },
  {
    key: 'after',
    label: '课后',
    lead: '报告、复盘与思考'
  }
]

/** @typedef {{
 *  code: string,
 *  name: string,
 *  group: 'pre'|'lab'|'after',
 *  blurb: string,
 *  aiLabel: string,
 *  kind: 'panel'|'lab'|'route',
 *  panel?: string,
 *  routeName?: string,
 *  labTab?: string,
 *  labAction?: string
 * }} StudentAgent */

/** @type {StudentAgent[]} */
export const STUDENT_AGENTS = [
  {
    code: 'pre-lab',
    name: '预习要点',
    group: 'pre',
    blurb: '目标、公式、易错点，一屏生成课前就绪包。',
    aiLabel: 'AI 生成',
    kind: 'panel',
    panel: 'brief'
  },
  {
    code: 'practice-quiz',
    name: '预习自测',
    group: 'pre',
    blurb: '围绕本实验出题、即时判分；通过即标记进门就绪。',
    aiLabel: 'AI 出题',
    kind: 'panel',
    panel: 'quiz'
  },
  {
    code: 'equipment-check',
    name: '器材核对',
    group: 'pre',
    blurb: '列出仪器清单与检查要点，逐项核对。',
    aiLabel: 'AI 清单',
    kind: 'panel',
    panel: 'equipment'
  },
  {
    code: 'lab-assist',
    name: '现场助教',
    group: 'lab',
    blurb: '步骤指导、拍照视觉纠错，边做边问。',
    aiLabel: 'AI 对话',
    kind: 'lab',
    labTab: 'guide'
  },
  {
    code: 'instrument-reading',
    name: '读数助手',
    group: 'lab',
    blurb: '拍刻度识别读数并讲解过程，确认后才入库。',
    aiLabel: 'AI 识读',
    kind: 'lab',
    labTab: 'guide',
    labAction: 'reading'
  },
  {
    code: 'data-doctor',
    name: '数据体检',
    group: 'lab',
    blurb: '本地统计 + AI 判断物理合理性，拦住离谱数据。',
    aiLabel: 'AI 诊断',
    kind: 'lab',
    labTab: 'data'
  },
  {
    code: 'error-trace',
    name: '误差溯源',
    group: 'lab',
    blurb: '结合全过程操作与数据，反推结果偏差最可能来自哪一步。',
    aiLabel: 'AI 溯源',
    kind: 'panel',
    panel: 'trace'
  },
  {
    code: 'report-assist',
    name: '报告教练',
    group: 'after',
    blurb: '基于真实数据起稿、润色与查缺漏（不代写思考题）。',
    aiLabel: 'AI 教练',
    kind: 'route',
    routeName: 'after-report'
  },
  {
    code: 'lab-recap',
    name: '个性复盘',
    group: 'after',
    blurb: '基于纠错与数据记录：薄弱点、误差假设、下次行动。',
    aiLabel: 'AI 复盘',
    kind: 'route',
    routeName: 'after-review'
  },
  {
    code: 'think-questions',
    name: '思考题助手',
    group: 'after',
    blurb: '只给思路支架与常见误区，不给可照抄答案。',
    aiLabel: 'AI 思路',
    kind: 'panel',
    panel: 'think'
  },
  {
    code: 'explore',
    name: '原理答疑',
    group: 'after',
    blurb: '围绕原理、公式与仪器结构的多轮问答。',
    aiLabel: 'AI 问答',
    kind: 'panel',
    panel: 'ask'
  }
]

export function findStudentAgent(code) {
  return STUDENT_AGENTS.find((a) => a.code === code) || null
}

export function agentsByGroup(groupKey) {
  return STUDENT_AGENTS.filter((a) => a.group === groupKey)
}
