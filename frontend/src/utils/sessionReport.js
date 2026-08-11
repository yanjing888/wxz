/** 从实验会话报告数据构建学生可用的结构化内容 */

export function parseSessionRows(byStep) {
  if (!byStep || typeof byStep !== 'object') return []
  return Object.values(byStep).map((entry) => ({
    stepId: entry.stepId,
    stepTitle: entry.stepTitle || '步骤',
    values: entry.values && typeof entry.values === 'object' ? entry.values : {},
    validation: entry.validation || null,
    feedback: entry.feedback || ''
  }))
}

export function findNumericSeries(rows) {
  const map = new Map()
  rows.forEach((row) => {
    Object.entries(row.values).forEach(([key, val]) => {
      const num = Number(val)
      if (val !== '' && val != null && Number.isFinite(num)) {
        if (!map.has(key)) {
          map.set(key, { key, label: key, unit: '', values: [] })
        }
        map.get(key).values.push(num)
      }
    })
  })
  return [...map.values()].filter((s) => s.values.length > 0)
}

export function formatDataLogTable(entries) {
  if (!entries?.length) return '（本次实验暂无提交的结构化数据，请在实验工作台各步骤提交测量值。）'
  return entries
    .map((row, i) => {
      const title = row.stepTitle || `步骤 ${i + 1}`
      const data = row.valuesSummary || '—'
      const check = row.validationSummary || '—'
      return `${title}\n  数据：${data}\n  校验：${check}`
    })
    .join('\n\n')
}

export function buildReportSections(report) {
  if (!report) return defaultSections()
  const name = report.experimentName || '本次实验'
  const steps = report.stepSummaries || []
  const knowledge = report.reportKnowledge || []
  const path = report.reportPath || []

  const purpose =
    steps.length > 0
      ? `通过「${name}」实验，掌握${steps.map((s) => s.title).slice(0, 2).join('、')}等操作技能，理解相关物理规律并完成数据处理。`
      : `完成「${name}」规定的测量与数据处理，验证相关物理规律。`

  const principle =
    knowledge.length > 0
      ? knowledge.join('\n')
      : steps.find((s) => s.desc)?.desc || '（请补充本实验所依据的物理原理与核心公式。）'

  const apparatus = steps.length
    ? `本实验主要涉及以下环节与仪器：\n${steps.map((s, i) => `${i + 1}. ${s.title}`).join('\n')}`
    : '（请列出主要仪器名称与型号。）'

  const procedure = steps.length
    ? steps.map((s, i) => `${i + 1}. ${s.title}${s.desc ? '：' + s.desc : ''}`).join('\n')
    : '（请按实际操作顺序简述步骤。）'

  const data = formatDataLogTable(report.dataLogEntries)

  const results = report.dataLogEntries?.length
    ? '（请在「数据处理」模块完成不确定度计算后，将结果填入此处。）'
    : '（暂无测量数据，请先在实验工作台提交数据。）'

  const discussion =
    path.length > 0
      ? `后续可深入：\n${path.map((t, i) => `${i + 1}. ${t}`).join('\n')}`
      : '（请结合误差来源分析结果合理性，并提出改进措施。）'

  return {
    purpose,
    principle,
    apparatus,
    procedure,
    data,
    results,
    discussion
  }
}

export function defaultSections() {
  return {
    purpose: '',
    principle: '',
    apparatus: '',
    procedure: '',
    data: '',
    results: '',
    discussion: ''
  }
}

export const REPORT_SECTION_DEFS = [
  { key: 'purpose', label: '1. 实验目的' },
  { key: 'principle', label: '2. 实验原理' },
  { key: 'apparatus', label: '3. 实验仪器' },
  { key: 'procedure', label: '4. 实验步骤' },
  { key: 'data', label: '5. 数据与处理' },
  { key: 'results', label: '6. 实验结果' },
  { key: 'discussion', label: '7. 分析与讨论' }
]

export function sectionsToFullText(sections, experimentName) {
  const title = experimentName ? `${experimentName} — 实验报告\n\n` : ''
  const body = REPORT_SECTION_DEFS.map((def) => {
    const text = (sections[def.key] || '').trim()
    return text ? `## ${def.label.replace(/^\d+\.\s*/, '')}\n\n${text}` : ''
  })
    .filter(Boolean)
    .join('\n\n')
  return title + body
}

export function extractRecapIssues(report) {
  const issues = []
  const corrections = report?.corrections || []
  corrections.forEach((c) => {
    issues.push({
      type: '操作',
      severity: 'high',
      title: c.stepTitle || '操作纠错',
      detail: c.detail || c.feedback || '',
      tag: c.errorType || '操作问题'
    })
  })

  ;(report?.dataLogEntries || []).forEach((row) => {
    const v = row.validationSummary || ''
    if (v && v !== '通过' && v !== '—') {
      issues.push({
        type: '数据',
        severity: 'medium',
        title: row.stepTitle || '数据校验',
        detail: v,
        tag: '数据校验'
      })
    }
  })

  if ((report?.tutViewCount ?? 0) >= 3) {
    issues.push({
      type: '学习',
      severity: 'low',
      title: '教程查阅较多',
      detail: `本次实验查阅教程 ${report.tutViewCount} 次，说明部分步骤操作尚不熟练。`,
      tag: '操作熟练度'
    })
  }

  if ((report?.helpCount ?? 0) >= 8) {
    issues.push({
      type: '学习',
      severity: 'medium',
      title: '问答求助较多',
      detail: `共提问/纠错 ${report.helpCount} 次，建议复盘时重点回顾这些环节。`,
      tag: '求助频率'
    })
  }

  return issues
}

export function downloadTextFile(filename, content) {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
