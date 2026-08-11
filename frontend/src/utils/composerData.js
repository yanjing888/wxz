/**
 * 将仪器/表单读数格式化为对话附件与消息摘要。
 */
export function formatDataValuesSummary(fields = [], values = {}) {
  const lines = []
  for (const field of fields) {
    const raw = values[field.key]
    if (raw == null || String(raw).trim() === '') continue
    const unit = field.unit ? ` ${field.unit}` : ''
    lines.push(`${field.label || field.key}: ${raw}${unit}`)
  }
  if (!lines.length) {
    for (const [key, raw] of Object.entries(values || {})) {
      if (raw == null || String(raw).trim() === '') continue
      lines.push(`${key}: ${raw}`)
    }
  }
  return lines
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
