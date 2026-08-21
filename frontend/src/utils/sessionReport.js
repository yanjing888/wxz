/** 从实验会话报告数据构建学生可用的结构化内容 */

function mapSessionDataRow(entry, row) {
  const values = row?.values ?? entry?.values
  return {
    stepId: entry?.stepId ?? row?.stepId,
    stepTitle: entry?.stepTitle || row?.stepTitle || '步骤',
    values: values && typeof values === 'object' ? values : {},
    validation: row?.validation ?? entry?.validation ?? null,
    feedback: row?.feedback ?? entry?.feedback ?? '',
    createdAt: row?.createdAt ?? entry?.createdAt ?? ''
  }
}

/** 将 GET /sessions/:id/data 响应展开为扁平记录列表（支持同一步多笔） */
export function parseSessionDataResponse(data) {
  if (!data || typeof data !== 'object') return []
  if (Array.isArray(data.logs) && data.logs.length) {
    return data.logs.map((row) => mapSessionDataRow(row, row))
  }
  const byStep = data.byStep
  if (!byStep || typeof byStep !== 'object') return []
  const rows = []
  for (const entry of Object.values(byStep)) {
    if (Array.isArray(entry?.rows) && entry.rows.length) {
      entry.rows.forEach((row) => rows.push(mapSessionDataRow(entry, row)))
      continue
    }
    rows.push(mapSessionDataRow(entry, entry))
  }
  return rows.sort((a, b) => {
    const stepDiff = Number(a.stepId) - Number(b.stepId)
    if (stepDiff !== 0) return stepDiff
    return String(a.createdAt || '').localeCompare(String(b.createdAt || ''))
  })
}

/** 规范化 byStep 结构，保证每步含 rows 数组 */
export function normalizeSessionDataByStep(data) {
  const byStep = {}
  const rows = parseSessionDataResponse(data)
  for (const row of rows) {
    const key = String(row.stepId)
    if (!byStep[key]) {
      byStep[key] = {
        stepId: row.stepId,
        stepTitle: row.stepTitle,
        rows: []
      }
    }
    byStep[key].rows.push({
      values: row.values,
      validation: row.validation,
      feedback: row.feedback,
      createdAt: row.createdAt
    })
  }
  for (const entry of Object.values(byStep)) {
    const latest = entry.rows[entry.rows.length - 1]
    if (latest) {
      entry.values = latest.values
      entry.validation = latest.validation
      entry.feedback = latest.feedback
      entry.createdAt = latest.createdAt
    }
  }
  return byStep
}

export function countSessionDataLogs(byStep) {
  if (!byStep || typeof byStep !== 'object') return 0
  let total = 0
  for (const entry of Object.values(byStep)) {
    if (Array.isArray(entry?.rows)) total += entry.rows.length
    else if (entry?.values && Object.keys(entry.values).length) total += 1
  }
  return total
}

/** 从后端 stepSchemas（manifest dataFields）解析某步骤的表格列 */
export function resolveStepDataFields(stepId, stepSchemas = null) {
  const schema = stepSchemas?.[String(stepId)] || stepSchemas?.[stepId]
  if (Array.isArray(schema?.fields) && schema.fields.length) {
    return schema.fields.map((f) => ({
      key: f.key,
      label: f.label || f.key
    }))
  }
  return []
}

function inferFieldsFromRows(rows) {
  const keys = new Set()
  rows.forEach((r) => Object.keys(r.values || {}).forEach((k) => keys.add(k)))
  return [...keys].map((k) => ({ key: k, label: k }))
}

/** 将会话数据记录整理为「步骤 → 表格」结构 */
export function buildSessionDataStepTables({ experimentName, entries, stepSchemas = null }) {
  const grouped = new Map()
  for (const entry of entries || []) {
    const sid = Number(entry.stepId)
    if (!grouped.has(sid)) grouped.set(sid, [])
    grouped.get(sid).push(entry)
  }

  const tables = []
  for (const stepId of [...grouped.keys()].sort((a, b) => a - b)) {
    const rows = grouped.get(stepId) || []
    const schema = stepSchemas?.[String(stepId)] || stepSchemas?.[stepId]
    const stepTitle = schema?.stepTitle || rows[0]?.stepTitle || `步骤 ${stepId}`
    const fields = resolveStepDataFields(stepId, stepSchemas)
    const columns = fields.length ? fields : inferFieldsFromRows(rows)
    tables.push({
      stepId,
      stepTitle,
      fields: columns,
      rows: rows.map((r, i) => ({
        index: i + 1,
        values: r.values && typeof r.values === 'object' ? r.values : {},
        createdAt: r.createdAt || ''
      }))
    })
  }

  return {
    experimentName: experimentName || '本次实验',
    tables
  }
}

export function formatSessionDataCell(values, key) {
  const v = values?.[key]
  if (v === null || v === undefined || v === '') return '—'
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}

/** @deprecated 请优先使用 parseSessionDataResponse */
export function parseSessionRows(byStep) {
  if (!byStep || typeof byStep !== 'object') return []
  if (Array.isArray(byStep.logs) || byStep.byStep) {
    return parseSessionDataResponse(byStep)
  }
  return parseSessionDataResponse({ byStep })
}

export function parseDataLogRows(report) {
  const logs = report?.dataLogs || []
  return logs.map((log) => {
    let values = log.values
    if (!values || typeof values !== 'object') {
      try {
        values = JSON.parse(log.valuesJson || '{}')
      } catch {
        values = {}
      }
    }
    return {
      stepId: log.stepId,
      stepTitle: log.stepTitle || `步骤 ${log.stepId}`,
      submittedAt: log.createdAt || log.submittedAt || '',
      values: values && typeof values === 'object' ? values : {},
      validationSummary: log.validationSummary || ''
    }
  })
}

function cellVal(values, key) {
  const v = values?.[key]
  if (v === null || v === undefined || v === '') return '—'
  return String(v)
}

function buildStepDataTable(stepTitle, fields, rows) {
  if (!rows.length) return ''
  const head = fields.map((f) => `<th>${f.label}</th>`).join('')
  const body = rows
    .map((row, i) => {
      const cols = fields.map((f) => `<td>${cellVal(row.values, f.key)}</td>`).join('')
      return `<tr><td>${i + 1}</td>${cols}</tr>`
    })
    .join('')
  return `<p><strong>${stepTitle}</strong></p>
<table class="report-data-table">
<thead><tr><th>序号</th>${head}</tr></thead>
<tbody>${body}</tbody>
</table>`
}

export function buildExperimentDataHtml(report) {
  const stepSchemas = report?.stepSchemas || {}
  const rows = parseDataLogRows(report)
  if (!rows.length) {
    return '<p>（本次实验暂无提交的结构化数据，请在实验工作台各步骤提交测量值。）</p>'
  }

  const parts = []
  const grouped = new Map()
  rows.forEach((row) => {
    const sid = row.stepId
    if (!grouped.has(sid)) grouped.set(sid, [])
    grouped.get(sid).push(row)
  })

  const schemaStepIds = Object.keys(stepSchemas)
    .map((k) => Number(k))
    .filter((n) => Number.isFinite(n))
    .sort((a, b) => a - b)

  for (const stepId of schemaStepIds) {
    const dataRows = grouped.get(stepId) || []
    if (!dataRows.length) continue
    const schema = stepSchemas[String(stepId)] || stepSchemas[stepId]
    const fields = resolveStepDataFields(stepId, stepSchemas)
    const stepTitle = schema?.stepTitle || dataRows[0].stepTitle || `步骤 ${stepId}`
    parts.push(buildStepDataTable(
      stepTitle,
      fields.length ? fields : inferFieldsFromRows(dataRows),
      dataRows
    ))
  }

  grouped.forEach((dataRows, stepId) => {
    if (schemaStepIds.includes(Number(stepId))) return
    const fields = inferFieldsFromRows(dataRows)
    if (!fields.length) return
    parts.push(buildStepDataTable(dataRows[0].stepTitle || `步骤 ${stepId}`, fields, dataRows))
  })

  if (!parts.length) {
    return '<p>（数据已提交，但未能解析为表格；请检查实验工作台的数据格式。）</p>'
  }

  parts.push('<p><em>说明：上表由本次实验会话自动整理，可在下方补充计算过程与不确定度。</em></p>')
  return parts.join('\n')
}

export function buildResultsSection(report, experimentCode) {
  const code = experimentCode || report?.experimentCode || ''
  const rows = parseDataLogRows(report)
  if (!rows.length) return '（暂无测量数据，请先在实验工作台提交数据。）'

  const lastCalc = [...rows].reverse().find((r) => {
    const v = r.values || {}
    return v.average_R_mm != null || v.average_thickness_um != null || v.average_length_mm != null
      || v.radius_R_mm != null || v.thickness_um != null || v.length_mm != null
  })
  const v = lastCalc?.values || rows[rows.length - 1]?.values || {}

  if (code === 'newton_rings') {
    const R = v.average_R_mm ?? v.radius_R_mm
    if (R != null && R !== '') {
      return `由直径平方差法计算得平凸透镜曲率半径：\nR = ${R} mm\n\n（请补充不确定度评定与结果表示，如 R = (${R} ± ΔR) mm。）`
    }
    return '（请在「曲率半径计算」步骤提交 R 或平均曲率半径，并在此给出最终结果及不确定度。）'
  }
  if (code === 'air_wedge_thickness') {
    const d = v.average_thickness_um ?? v.thickness_um
    if (d != null && d !== '') {
      return `由空气劈尖干涉测得薄片厚度：\nd = ${d} μm\n\n（请补充不确定度评定与结果表示。）`
    }
    return '（请在「厚度计算」步骤提交厚度结果，并在此给出最终结果及不确定度。）'
  }
  if (code === 'microscope_length_measurement') {
    const L = v.average_length_mm ?? v.length_mm
    if (L != null && L !== '') {
      return `读数显微镜测得被测长度：\nL̄ = ${L} mm\n\n（请补充 A 类/B 类不确定度合成与结果表示。）`
    }
    return '（请在「数据处理」步骤提交平均长度，并在此给出最终结果及不确定度。）'
  }

  return '（请根据本次实验数据给出最终测量结果及不确定度表示。）'
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

export function buildReportSections(report, experimentCode) {
  if (!report) return defaultSections()
  const code = experimentCode || report.experimentCode || ''
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

  const data = buildExperimentDataHtml(report)
  const results = buildResultsSection(report, code)

  const discussion =
    path.length > 0
      ? `建议从以下方面展开误差分析与讨论：\n${path.map((t, i) => `${i + 1}. ${t}`).join('\n')}`
      : '（请结合误差来源分析结果合理性，并提出改进措施。）'

  return {
    purpose,
    principle,
    apparatus,
    procedure,
    data,
    results,
    discussion,
    _htmlKeys: ['data']
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

/** 将编辑器 HTML 转为预览/导出用 HTML（公式 span → KaTeX 由调用方处理） */
export function sectionsToExportPayload(sectionDefs, form, stripHtmlFn) {
  return sectionDefs.map((def) => {
    const raw = form[def.key] || ''
    const plain = stripHtmlFn(raw)
    return {
      label: def.label,
      content: plain,
      contentHtml: raw.includes('<') ? raw : plain.replace(/\n/g, '<br>')
    }
  })
}

export function buildReportAssistContext(form, sectionDefs, stripHtmlFn) {
  const sections = {}
  sectionDefs.forEach((def) => {
    sections[def.key] = stripHtmlFn(form[def.key] || '')
  })
  return {
    sections,
    filledKeys: sectionDefs.filter((d) => (sections[d.key] || '').trim()).map((d) => d.key),
    wordCounts: Object.fromEntries(
      sectionDefs.map((d) => [d.key, (sections[d.key] || '').trim().length])
    )
  }
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
