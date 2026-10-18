export const learningState = state => ({ CURRENT: '进行中', QUEUED: '待开始', REPORT_PENDING: '待交报告', SUBMITTED: '待批阅', GRADED: '已批阅' })[state] || '待开始'
export const learningDate = value => value ? value.replace('T', ' ').slice(0, 16) : '—'
export const pendingLearning = records => records.filter(r => r.currentTask || (r.finished && !r.submitted) || (r.graded && !r.reflectionCurrent))
export const feedbackLearning = records => records.filter(r => r.graded)
export const recordRoute = record => record.finished ? { name: 'after-center', params: { code: record.code }, query: { tab: 'report' } } : { name: 'lab', query: { exp: record.code } }
