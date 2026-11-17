export const EXPERIMENT_FILTERS = [
  { key: 'all', label: '全部实验' }, { key: 'current', label: '当前实验' },
  { key: 'queued', label: '待开始' }, { key: 'report', label: '待交报告' },
  { key: 'submitted', label: '待批阅' }, { key: 'done', label: '已完成' }
]
export function experimentState(item) {
  if (item.labCompleted) {
    if (!item.reportCompleted) return 'report'
    return item.gradingCompleted ? 'done' : 'submitted'
  }
  return item.currentTask ? 'current' : 'queued'
}
export function experimentAction(item) {
  const state = experimentState(item)
  if (state === 'queued') return null
  if (state !== 'current') return {
    label: state === 'report' ? '继续写报告' : state === 'done' ? '查看评价' : '查看报告',
    to: { name: 'after-center', params: { code: item.experimentCode } }
  }
  if (item.simulationRequired && !item.simulationCompleted) return {
    label: '开始仿真预习', to: { name: 'prep-simulation', params: { code: item.experimentCode } }
  }
  return { label: item.activeSessionId ? '继续实验' : '开始实验', to: { name: 'lab', query: { exp: item.experimentCode } } }
}
export function orderedExperiments(items) {
  const priority = { current: 0, report: 1, queued: 2, submitted: 3, done: 4 }
  return [...items].sort((a, b) => priority[experimentState(a)] - priority[experimentState(b)])
}
