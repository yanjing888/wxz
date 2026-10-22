/**
 * 学生端智能体目录（前端唯一来源）
 * code 需与后端 application.yml 的 wuxiaozhi.ai-tools.items[].code 一致
 */

export const AGENT_GROUPS = [
  { key: 'before', label: '课前准备', desc: '进实验室前用 10 分钟做好准备' },
  { key: 'during', label: '课中辅助', desc: '操作卡住、读数不准时随时调用' },
  { key: 'after', label: '课后处理', desc: '数据处理、报告撰写与问题复盘' },
  { key: 'extend', label: '拓展学习', desc: '把不懂的原理彻底搞明白' }
]

export const AGENTS = [
  {
    code: 'pre-lab',
    name: '实验预习',
    tagline: '进实验室前先看一眼',
    desc: '本次实验目标、必带物品、核心公式与两个易错点，一屏看完。',
    group: 'before',
    icon: '预',
    tone: 'sky',
    needsSession: false
  },
  {
    code: 'practice-quiz',
    name: '预习自测',
    tagline: '5 道题检验准备情况',
    desc: '选择与判断题，做完即时判分并给出解析，可反复练习。',
    group: 'before',
    icon: '测',
    tone: 'sky',
    needsSession: false
  },
  {
    code: 'data-doctor',
    name: '数据体检',
    tagline: '这组数据合理吗',
    desc: '量级、离群值、有效位数本地体检，再由 AI 判断物理合理性。',
    group: 'during',
    icon: '检',
    tone: 'violet',
    badge: 'NEW',
    needsSession: false
  },
  {
    code: 'data-lab',
    name: '数据处理',
    tagline: '不确定度 / 逐差法 / 作图',
    desc: '本地精确计算并生成可直接写进报告的段落与图表。',
    group: 'after',
    icon: '算',
    tone: 'emerald',
    needsSession: false
  },
  {
    code: 'report-assist',
    name: '报告助手',
    tagline: '从实验记录生成报告',
    desc: '自动填充七段结构，逐段润色与规范检查，可导出 Word。',
    group: 'after',
    icon: '报',
    tone: 'emerald',
    needsSession: true
  },
  {
    code: 'error-trace',
    name: '误差溯源',
    tagline: '结果偏差是哪一步造成的',
    desc: '结合你的全过程操作记录反推误差来源，并给出验证方法。',
    group: 'after',
    icon: '溯',
    tone: 'amber',
    badge: 'NEW',
    needsSession: true
  },
  {
    code: 'lab-recap',
    name: '实验复盘',
    tagline: '这次哪里做得不好',
    desc: '基于真实纠错与校验记录列出问题清单与改进建议。',
    group: 'after',
    icon: '盘',
    tone: 'amber',
    needsSession: true
  },
  {
    code: 'explore',
    name: '原理答疑',
    tagline: '把原理问到懂为止',
    desc: '围绕实验原理、公式推导与仪器结构的多轮问答。',
    group: 'extend',
    icon: '问',
    tone: 'rose',
    needsSession: false
  }
]

export const AGENT_CODES = AGENTS.map((a) => a.code)

export function findAgent(code) {
  return AGENTS.find((a) => a.code === code) || null
}

export function agentsByGroup(groupKey) {
  return AGENTS.filter((a) => a.group === groupKey)
}

/** 大厅与各工作区共用的实验上下文（记住上次选择） */
const EXP_KEY = 'wxz_agent_exp'

export function readAgentExperiment() {
  return localStorage.getItem(EXP_KEY) || ''
}

export function saveAgentExperiment(code) {
  if (code) localStorage.setItem(EXP_KEY, code)
  else localStorage.removeItem(EXP_KEY)
}
