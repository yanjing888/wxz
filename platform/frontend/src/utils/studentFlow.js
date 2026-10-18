export function formatSessionTime(value) {
  if (!value) return '—'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString('zh-CN')
}

export function formatDataLogsAsText(entries) {
  if (!entries?.length) return ''
  return entries
    .map((row, index) => {
      const parts = [`#${index + 1}`]
      if (row.stepTitle) parts.push(row.stepTitle)
      if (row.fieldName) parts.push(row.fieldName)
      if (row.value != null) parts.push(String(row.value))
      if (row.unit) parts.push(row.unit)
      return parts.join(' · ')
    })
    .join('\n')
}
