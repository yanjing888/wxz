/**
 * 实验实训闭环（对齐法学实训：任务 → 文书 → 实验台 → 复盘）。
 * AI 能力嵌入各节点，不再按「数据处理」等功能墙导航。
 */

export const LOOP_NODES = [
  {
    key: 'task',
    label: '实验任务',
    short: '任务',
    lead: '看步骤、问 AI，尽快进入实验台',
    route: (code) => ({ name: 'loop-task', params: { code } })
  },
  {
    key: 'lab',
    label: '实验台',
    short: '实训',
    lead: '课上主战场：指导纠错、拍照读数、逐步完成操作',
    route: (code) => ({ name: 'lab', query: { exp: code } })
  },
  {
    key: 'documents',
    label: '实验文书',
    short: '文书',
    lead: '下课前后撰写报告；预习报告为可选',
    route: (code) => ({ name: 'loop-documents', params: { code } })
  },
  {
    key: 'review',
    label: '评价复盘',
    short: '复盘',
    lead: '实验结束后回顾与思考题',
    route: (code) => ({ name: 'loop-review', params: { code } })
  }
]

export const LOOP_NODE_KEYS = LOOP_NODES.map((n) => n.key)

export function findLoopNode(key) {
  return LOOP_NODES.find((n) => n.key === key) || LOOP_NODES[0]
}

/** 课上任务页：一步概览 + 按需展开，不堆页签 */
export const LOOP_TABS = {
  task: [],
  documents: [
    { key: 'report', label: '实验报告', tool: 'report-assist' },
    { key: 'prep', label: '预习报告（可选）', tool: 'prep-report' }
  ],
  review: [
    { key: 'recap', label: '实验复盘', tool: 'lab-recap' },
    { key: 'think', label: '思考题', tool: 'think-questions' }
  ]
}

/** 折叠在任务页「课前预习」里，不占上课时间 */
export const PRE_CLASS_TABS = [
  { key: 'equipment', label: '器材核对', tool: 'equipment-check' },
  { key: 'brief', label: '预习要点', tool: 'pre-lab' },
  { key: 'quiz', label: '预习自测', tool: 'practice-quiz' }
]

/** @deprecated 保留给尚未迁移的引用，勿再用于主导航 */
export const STAGES = LOOP_NODES
export const STAGE_KEYS = LOOP_NODE_KEYS
export const STAGE_TABS = LOOP_TABS
export function findStage(key) {
  return findLoopNode(normalizeLegacyNode(key))
}

export const FILE_CATEGORIES = [
  { key: 'raw_data', label: '原始数据', stage: 'lab' },
  { key: 'photo', label: '实验照片', stage: 'lab' },
  { key: 'chart', label: '数据图表', stage: 'lab' },
  { key: 'prep', label: '预习材料', stage: 'task' },
  { key: 'report', label: '实验报告', stage: 'documents' },
  { key: 'other', label: '其他资料', stage: 'lab' }
]

export function categoryLabel(key) {
  return FILE_CATEGORIES.find((c) => c.key === key)?.label || '其他资料'
}

function stepOf(progress, key) {
  return (progress?.steps || []).find((s) => s.key === key)
}

/** 闭环节点完成状态：available | in_progress | done */
export function loopStatus(progress, nodeKey) {
  if (!progress) return 'available'
  const lab = stepOf(progress, 'lab')
  const report = stepOf(progress, 'report')
  const recap = stepOf(progress, 'recap')

  switch (nodeKey) {
    case 'task':
      return lab && lab.status !== 'available' ? 'done' : 'available'
    case 'documents':
      if (report?.status === 'done') return 'done'
      if (lab?.status === 'done' || lab?.status === 'in_progress') return 'in_progress'
      return 'available'
    case 'lab':
      return lab?.status || 'available'
    case 'review':
      if (recap?.status === 'done') return 'done'
      if (report?.status === 'done') return 'in_progress'
      return 'available'
    default:
      return 'available'
  }
}

/** @deprecated */
export function stageStatus(progress, stageKey) {
  const legacy = { prepare: 'task', data: 'documents', report: 'documents' }
  return loopStatus(progress, legacy[stageKey] || stageKey)
}

const LAST_EXPERIMENT_KEY = 'wxz_last_experiment'
const LAST_LOOP_PREFIX = 'wxz_last_loop:'
const LAST_FLOW_LOOP_PREFIX = 'wxz_last_flow_loop:'

const LEGACY_LOOP_PREFIX = 'wxz_last_stage:'
const LEGACY_FLOW_PREFIX = 'wxz_last_flow_stage:'

function normalizeLegacyNode(value) {
  const map = {
    prepare: 'task',
    data: 'documents',
    report: 'documents',
    recap: 'review'
  }
  const key = String(value || '').trim()
  if (LOOP_NODE_KEYS.includes(key)) return key
  return map[key] || 'task'
}

export function rememberVisit(code, nodeKey) {
  const node = normalizeLegacyNode(nodeKey)
  if (!code || !LOOP_NODE_KEYS.includes(node)) return
  try {
    localStorage.setItem(LAST_EXPERIMENT_KEY, code)
    localStorage.setItem(LAST_LOOP_PREFIX + code, node)
    if (node !== 'lab') {
      localStorage.setItem(LAST_FLOW_LOOP_PREFIX + code, node)
    }
  } catch {
    // ignore
  }
}

export function lastExperiment() {
  try {
    return localStorage.getItem(LAST_EXPERIMENT_KEY) || ''
  } catch {
    return ''
  }
}

export function lastLoopNode(code) {
  return readLoopNode(LAST_LOOP_PREFIX + code)
}

/** 实验台返回时，回到闭环壳内最近一次停留的节点 */
export function lastFlowNode(code) {
  const node = readLoopNode(LAST_FLOW_LOOP_PREFIX + code)
  return node === 'lab' ? 'task' : node
}

/** @deprecated */
export function lastStage(code) {
  return lastLoopNode(code)
}

/** @deprecated */
export function lastFlowStage(code) {
  return lastFlowNode(code)
}

function readLoopNode(primaryKey) {
  try {
    let value = localStorage.getItem(primaryKey)
    if (!value) {
      const legacyKey = primaryKey.replace('wxz_last_loop:', LEGACY_LOOP_PREFIX)
        .replace('wxz_last_flow_loop:', LEGACY_FLOW_PREFIX)
      value = localStorage.getItem(legacyKey)
    }
    return normalizeLegacyNode(value)
  } catch {
    return 'task'
  }
}

export function formatBytes(bytes) {
  const size = Number(bytes) || 0
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
