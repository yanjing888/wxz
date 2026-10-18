/**
 * 将仪器/表单读数格式化为对话附件与消息摘要。
 */
export function formatDataValuesSummary(fields = [], values = {}) {
  const lines = []
  for (const field of fields) {
    const raw = values[field.key]
    if (raw == null || String(raw).trim() === '') continue
    const unit = field.unit ? ` ${field.unit}` : ''
    lines.push(`${resolveFieldLabel(field, field.key)}: ${raw}${unit}`)
  }
  if (!lines.length) {
    for (const [key, raw] of Object.entries(values || {})) {
      if (raw == null || String(raw).trim() === '') continue
      lines.push(`${resolveFieldLabel(null, key)}: ${raw}`)
    }
  }
  return lines
}

/**
 * 已知字段 key → 中文标签（commonDataFields 未加载时的兜底）。
 */
const FIELD_LABEL_FALLBACK = {
  focus_lift_position_mm: '调焦升降位置',
  stage_lateral_position_mm: '横向居中位置'
}

function resolveFieldLabel(field, key) {
  if (field?.label) return field.label
  return FIELD_LABEL_FALLBACK[key] || key
}

/** 本次提交是否仅为刻度/读数记录（无计算字段等） */
export function isScaleReadingOnlyCheck(fields = [], values = {}) {
  const activeFields = fields.filter((field) => {
    const raw = values[field.key]
    return raw != null && String(raw).trim() !== ''
  })
  return activeFields.length > 0 && activeFields.every((field) => field.scaleReading)
}

/**
 * 读数/数据检查发给智能体的问题（中间对话区展示用，与后端 query 语义一致）。
 */
export function buildDataCorrectionPrompt({
  stepTitle = '',
  fields = [],
  values = {},
  fromDevice = false,
  officialData = false,
  extraMessage = ''
} = {}) {
  const bulletLines = []
  for (const field of fields) {
    const raw = values[field.key]
    if (raw == null || String(raw).trim() === '') continue
    const unit = field.unit ? ` ${field.unit}` : ''
    const label = resolveFieldLabel(field, field.key)
    bulletLines.push(
      field.scaleReading
        ? `- ${label}（刻度读数）：${raw}${unit}`
        : `- ${label}：${raw}${unit}`
    )
  }
  if (!bulletLines.length) {
    for (const [key, raw] of Object.entries(values || {})) {
      if (raw == null || String(raw).trim() === '') continue
      bulletLines.push(`- ${resolveFieldLabel(null, key)}：${raw}`)
    }
  }

  if (fromDevice) {
    const summary = formatDataValuesSummary(fields, values).join('，')
    return `【仪器读数核对】${stepTitle || '当前步骤'}\n${summary || '(空)'}`
  }

  const stepPart = stepTitle ? `「${stepTitle}」` : '当前步骤'
  const scaleOnly = isScaleReadingOnlyCheck(fields, values)
  const intent = officialData
    ? '请检查以下正式实验数据是否合理、计算与记录是否正确：'
    : scaleOnly
      ? '请帮我核对以下刻度读数是否合理、记录是否规范；若合理请简要确认，如有疑问请说明：'
      : '请检查以下测量读数是否合理、记录是否正确；若合理请确认，如有问题请说明：'

  let text = scaleOnly
    ? `我在实验步骤${stepPart}记录了以下刻度读数。\n${intent}\n\n${bulletLines.join('\n') || '- (空)'}`
    : `我在实验步骤${stepPart}中填写了以下数据。\n${intent}\n\n${bulletLines.join('\n') || '- (空)'}`
  const note = String(extraMessage || '').trim()
  if (note) text += `\n\n补充说明：${note}`
  return text
}

export function buildComposerDataAttachment({ stepId, stepTitle, fields, values, fromDevice = true }) {
  const lines = formatDataValuesSummary(fields, values)
  const body = lines.join('\n')
  const previewSource = lines[0] || body
  const title = previewSource.length > 48 ? `${previewSource.slice(0, 47)}…` : previewSource

  return {
    stepId,
    stepTitle: stepTitle || '',
    fromDevice,
    values: { ...values },
    title: title || '仪器读数',
    body,
    label: fromDevice ? '仪器读数' : '实验数据'
  }
}
