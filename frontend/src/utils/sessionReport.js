/** 从实验会话报告数据构建学生可用的结构化内容 */

function mapSessionDataRow(entry, row) {
  const values = row?.values ?? entry?.values
  return {
    id: row?.id ?? entry?.id ?? null,
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
      id: row.id ?? null,
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
        id: r.id ?? null,
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

function buildEmptyDataTable(stepTitle, fields, rowCount = 3) {
  if (!fields.length) return ''
  const head = fields.map((f) => `<th>${f.label}</th>`).join('')
  const emptyRow = fields.map(() => '<td>&nbsp;</td>').join('')
  const body = Array.from({ length: rowCount }, (_, i) =>
    `<tr><td>${i + 1}</td>${emptyRow}</tr>`
  ).join('')
  return `<p><strong>${stepTitle}</strong>（可在下方直接填写或修改）</p>
<table class="report-data-table">
<thead><tr><th>序号</th>${head}</tr></thead>
<tbody>${body}</tbody>
</table>`
}

function buildDataProcessingAppendix(report) {
  const guide = report?.reportFillSections?.dataProcessing
  const text = typeof guide === 'string' ? guide.trim() : ''
  if (!text) return ''
  const lines = text.split('\n').map((line) => line.trim()).filter(Boolean)
  const html = lines.map((line) => {
    if (line.startsWith('```')) return ''
    if (line.startsWith('- ') || line.startsWith('• ')) {
      return `<p>${line.replace(/^[-•]\s*/, '• ')}</p>`
    }
    if (/^\d+\./.test(line)) return `<p>${line}</p>`
    return `<p>${line}</p>`
  }).join('')
  return `<h4>数据处理说明（参考，请补充具体计算过程）</h4>${html}`
}

export function buildExperimentDataHtml(report) {
  const stepSchemas = report?.stepSchemas || {}
  const rows = parseDataLogRows(report)
  const parts = []

  const schemaStepIds = Object.keys(stepSchemas)
    .map((k) => Number(k))
    .filter((n) => Number.isFinite(n))
    .sort((a, b) => a - b)

  const grouped = new Map()
  rows.forEach((row) => {
    const sid = row.stepId
    if (!grouped.has(sid)) grouped.set(sid, [])
    grouped.get(sid).push(row)
  })

  if (schemaStepIds.length) {
    for (const stepId of schemaStepIds) {
      const dataRows = grouped.get(stepId) || []
      const schema = stepSchemas[String(stepId)] || stepSchemas[stepId]
      const fields = resolveStepDataFields(stepId, stepSchemas)
      const stepTitle = schema?.stepTitle || dataRows[0]?.stepTitle || `步骤 ${stepId}`
      if (dataRows.length) {
        parts.push(buildStepDataTable(
          stepTitle,
          fields.length ? fields : inferFieldsFromRows(dataRows),
          dataRows
        ))
      } else if (fields.length) {
        parts.push(buildEmptyDataTable(stepTitle, fields))
      }
    }
  } else if (rows.length) {
    grouped.forEach((dataRows, stepId) => {
      const fields = inferFieldsFromRows(dataRows)
      if (!fields.length) return
      parts.push(buildStepDataTable(dataRows[0].stepTitle || `步骤 ${stepId}`, fields, dataRows))
    })
  }

  if (!parts.length && schemaStepIds.length) {
    for (const stepId of schemaStepIds) {
      const schema = stepSchemas[String(stepId)] || stepSchemas[stepId]
      const fields = resolveStepDataFields(stepId, stepSchemas)
      if (!fields.length) continue
      const stepTitle = schema?.stepTitle || `步骤 ${stepId}`
      parts.push(buildEmptyDataTable(stepTitle, fields))
    }
  }

  if (!parts.length) {
    parts.push('<p>（暂无自动填入的数据表，请根据实验步骤自行录入原始数据与计算过程。）</p>')
  } else {
    parts.push('<p><em>说明：带数据的表格由实验记录自动整理；空白表格可自行填写。下方可补充计算过程与不确定度。</em></p>')
  }

  const processing = buildDataProcessingAppendix(report)
  if (processing) parts.push(processing)

  return parts.join('\n')
}

export function buildResultsSection(report, experimentCode) {
  const code = experimentCode || report?.experimentCode || ''
  const rows = parseDataLogRows(report)
  const hints = []

  if (rows.length) {
    const lastCalc = [...rows].reverse().find((r) => {
      const v = r.values || {}
      return v.average_R_mm != null || v.average_thickness_um != null || v.average_length_mm != null
        || v.radius_R_mm != null || v.thickness_um != null || v.length_mm != null
    })
    const v = lastCalc?.values || {}
    if (code === 'newton_rings' && (v.average_R_mm != null || v.radius_R_mm != null)) {
      hints.push(`（参考：工作台计算值 R ≈ ${v.average_R_mm ?? v.radius_R_mm} mm，请在此给出正式结果与不确定度。）`)
    } else if (code === 'air_wedge_thickness' && (v.average_thickness_um != null || v.thickness_um != null)) {
      hints.push(`（参考：工作台计算值 d ≈ ${v.average_thickness_um ?? v.thickness_um} μm，请在此给出正式结果与不确定度。）`)
    } else if (code === 'microscope_length_measurement' && (v.average_length_mm != null || v.length_mm != null)) {
      hints.push(`（参考：工作台计算值 L̄ ≈ ${v.average_length_mm ?? v.length_mm} mm，请在此给出正式结果与不确定度。）`)
    }
  }

  const hint = hints[0] || '（请根据「数据与处理」中的计算，写出最终测量结果及不确定度表示，如 R = (数值 ± ΔR) 单位。）'
  return hint
}

function buildProcedureFromSteps(steps = []) {
  if (!steps.length) return ''
  return steps.map((s, i) => {
    const lines = [`步骤 ${s.stepNo || i + 1}：${s.title || ''}`]
    if (s.desc) lines.push(`【目标】${s.desc}`)
    if (Array.isArray(s.tutSteps) && s.tutSteps.length) {
      lines.push('【操作指引】')
      s.tutSteps.forEach((item, idx) => lines.push(`  ${idx + 1}. ${item}`))
    }
    if (Array.isArray(s.tutWarnings) && s.tutWarnings.length) {
      lines.push('【注意事项】')
      s.tutWarnings.forEach((item) => lines.push(`  • ${item}`))
    }
    return lines.join('\n')
  }).join('\n\n')
}

export function buildReportSections(report, experimentCode) {
  if (!report) return defaultSections()
  const code = experimentCode || report.experimentCode || ''
  const name = report.experimentName || '本次实验'
  const steps = report.stepSummaries || []
  const fill = report.reportFillSections || {}
  const knowledge = report.reportKnowledge || []

  const purpose = (fill.purpose || '').trim()
    || (steps.length > 0
      ? `通过「${name}」实验，掌握${steps.map((s) => s.title).slice(0, 3).join('、')}等操作技能，理解相关物理规律并完成数据处理。`
      : `完成「${name}」规定的测量与数据处理，验证相关物理规律。`)

  const principle = (fill.principle || '').trim()
    || (knowledge.length > 0
      ? knowledge.join('\n')
      : steps.find((s) => s.desc)?.desc || '（请补充本实验所依据的物理原理与核心公式。）')

  const apparatus = (fill.apparatus || '').trim()
    || (steps.length
      ? `本实验主要使用以下仪器与材料（请核对型号并补充）：\n${steps.map((s, i) => `${i + 1}. ${s.title}`).join('\n')}`
      : '（请列出主要仪器名称与型号。）')

  const procedure = (fill.procedure || '').trim()
    || buildProcedureFromSteps(steps)
    || '（请按实际操作顺序简述各步骤、操作要点与注意事项。）'

  const data = buildExperimentDataHtml(report)
  const results = buildResultsSection(report, code)

  const discussion = '（请结合本次实验数据，撰写误差来源分析、结果合理性讨论、实验中的问题与改进措施，以及个人思考与反思。）'

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
  { key: 'purpose', label: '1. 实验目的', autoFill: true },
  { key: 'principle', label: '2. 实验原理', autoFill: true },
  { key: 'apparatus', label: '3. 实验仪器', autoFill: true },
  { key: 'procedure', label: '4. 实验步骤', autoFill: true },
  { key: 'data', label: '5. 数据与处理', autoFill: true },
  { key: 'results', label: '6. 实验结果', autoFill: false },
  { key: 'discussion', label: '7. 分析与讨论', autoFill: false }
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
