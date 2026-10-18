import { parseNumbers } from './physicsCalc'

export function emptyTable(cols = 3, rows = 5) {
  return {
    headers: Array.from({ length: cols }, (_, i) => `列${i + 1}`),
    rows: Array.from({ length: rows }, () => Array.from({ length: cols }, () => ''))
  }
}

export function normalizeTable(table) {
  if (!table?.headers?.length) return emptyTable()
  const width = table.headers.length
  return {
    headers: table.headers.map((h, i) => String(h || `列${i + 1}`)),
    rows: (table.rows || []).map((row) =>
      Array.from({ length: width }, (_, i) => (row?.[i] == null ? '' : String(row[i])))
    )
  }
}

export function addRow(table) {
  table.rows.push(table.headers.map(() => ''))
}

export function addColumn(table) {
  table.headers.push(`列${table.headers.length + 1}`)
  table.rows.forEach((row) => row.push(''))
}

export function removeRow(table, index) {
  if (table.rows.length > 1) table.rows.splice(index, 1)
}

export function removeColumn(table, index) {
  if (table.headers.length <= 1) return
  table.headers.splice(index, 1)
  table.rows.forEach((row) => row.splice(index, 1))
}

export function isTableEmpty(table) {
  return !table?.rows?.some((row) => row.some((cell) => String(cell).trim()))
}

/** 把某一列取成数值序列，供不确定度、拟合等计算使用 */
export function columnValues(table, index) {
  if (!table?.rows) return []
  return parseNumbers(table.rows.map((row) => row[index]).join(','))
}

/** 所有含 2 个以上数值的列，用作计算工具的导入候选 */
export function numericColumns(table) {
  if (!table?.headers) return []
  return table.headers
    .map((header, index) => ({ header, index, values: columnValues(table, index) }))
    .filter((col) => col.values.length >= 2)
}

export function tableToCsv(table) {
  const escape = (cell) => {
    const value = String(cell ?? '')
    return /[",\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value
  }
  return [table.headers, ...table.rows].map((row) => row.map(escape).join(',')).join('\r\n')
}

export function tableToText(table) {
  return [table.headers, ...table.rows].map((row) => row.join('\t')).join('\n')
}

/** 从实验台各步提交的数据构造表格 */
export function tableFromSessionRows(rows) {
  const columns = []
  rows.forEach((row) => {
    Object.keys(row.values || {}).forEach((key) => {
      if (!columns.includes(key)) columns.push(key)
    })
  })
  if (!columns.length) return null
  return {
    headers: ['步骤', ...columns],
    rows: rows.map((row) => [row.stepTitle || '', ...columns.map((key) => String(row.values?.[key] ?? ''))])
  }
}
