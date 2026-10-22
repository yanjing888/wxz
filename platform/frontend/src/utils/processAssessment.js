export const processStatus = {
  NOT_STARTED: '未完成', PENDING: '已提交，待评价', REVISION: '待整改复查',
  PASSED: '教师已通过', COMPLETED: '已完成'
}
export function parseProcessJson(value, fallback = []) {
  if (value && typeof value === 'object') return value
  try { return value ? JSON.parse(value) : fallback } catch { return fallback }
}
export function processImages(value) {
  const images = Array.isArray(value) ? value : []
  const seen = new Set()
  return images.flatMap(image => {
    const url = String(typeof image === 'string' ? image : image?.url || '').trim()
    if (!url || seen.has(url) || !/^(\/uploads\/|https?:\/\/|blob:|data:image\/)/i.test(url)) return []
    seen.add(url)
    return [{ url, name: typeof image === 'object' ? image.name || '' : '' }]
  })
}
export function validProcessTable(table) {
  if (!Array.isArray(table) || table.length < 2 || table.length > 50 || !Array.isArray(table[0])) return false
  const columns = table[0].length
  return columns >= 1 && columns <= 20 && table.every(row => Array.isArray(row) && row.length === columns && row.every(cell => typeof cell === 'string' && cell.length <= 2000)) && table[0].every(cell => cell.trim()) && table.slice(1).some(row => row.some(cell => cell.trim()))
}
