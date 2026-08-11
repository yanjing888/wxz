export function parseStructuredData(response) {
  if (response?.data && Object.keys(response.data).length) {
    return response.data
  }
  const text = String(response?.text || '').trim()
  const body = stripCodeFence(text)
  if (!body.startsWith('{') && !body.startsWith('[')) {
    return null
  }
  try {
    return JSON.parse(body)
  } catch {
    return null
  }
}

/** 模型常把 JSON 包在 ```json 代码块里 */
function stripCodeFence(text) {
  const match = text.match(/```(?:json)?\s*([\s\S]*?)```/i)
  return (match ? match[1] : text).trim()
}

export function extractQuestions(payload) {
  if (!payload) return []
  const raw = payload.questions || payload.quiz || payload.items
  if (!Array.isArray(raw)) return []
  return raw
    .map((item, index) => {
      const options = normalizeOptions(item)
      return {
        id: item.id ?? index + 1,
        type: item.type === 'judge' || item.type === 'judgement' ? 'judge' : options.length ? 'choice' : 'text',
        stem: item.stem || item.question || item.title || '',
        options,
        answerIndex: normalizeAnswerIndex(item.answer ?? item.correct, options),
        explain: item.explain || item.explanation || item.analysis || ''
      }
    })
    .filter((item) => item.stem)
}

function normalizeOptions(item) {
  if (Array.isArray(item.options) && item.options.length) {
    return item.options.map(String)
  }
  if (item.type === 'judge' || item.type === 'judgement') {
    return ['正确', '错误']
  }
  return []
}

/** 答案可能是索引、字母或选项原文 */
function normalizeAnswerIndex(answer, options) {
  if (answer == null || !options.length) return -1
  if (typeof answer === 'number' && Number.isInteger(answer)) {
    return answer >= 0 && answer < options.length ? answer : -1
  }
  const raw = String(answer).trim()
  if (/^\d+$/.test(raw)) {
    const n = Number(raw)
    return n >= 0 && n < options.length ? n : -1
  }
  if (/^[A-Za-z]$/.test(raw)) {
    const idx = raw.toUpperCase().charCodeAt(0) - 65
    return idx >= 0 && idx < options.length ? idx : -1
  }
  const hit = options.findIndex((o) => o === raw)
  if (hit >= 0) return hit
  if (['对', '正确', 'true', 'T', '√'].includes(raw)) return 0
  if (['错', '错误', 'false', 'F', '×'].includes(raw)) return 1
  return -1
}

/** 器材清单：{items:[{name,spec,purpose,checkPoint,bySelf}]} */
export function extractEquipment(payload) {
  if (!payload) return []
  const raw = payload.items || payload.equipment || payload.apparatus
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (typeof item === 'string') return { name: item, spec: '', purpose: '', checkPoint: '', bySelf: false }
      return {
        name: String(item.name || item.title || '').trim(),
        spec: String(item.spec || item.model || '').trim(),
        purpose: String(item.purpose || item.usage || '').trim(),
        checkPoint: String(item.checkPoint || item.check || '').trim(),
        bySelf: item.bySelf === true || item.self === true
      }
    })
    .filter((item) => item.name)
}

/** 数据表识别：{headers:[],rows:[[]],warnings:[]} */
export function extractTable(payload) {
  if (!payload) return null
  const headers = Array.isArray(payload.headers) ? payload.headers.map(String) : []
  const rawRows = Array.isArray(payload.rows) ? payload.rows : []
  const rows = rawRows
    .map((row) => (Array.isArray(row) ? row.map((cell) => (cell == null ? '' : String(cell))) : null))
    .filter(Boolean)
  if (!headers.length && !rows.length) return null
  const width = Math.max(headers.length, ...rows.map((r) => r.length), 0)
  return {
    headers: Array.from({ length: width }, (_, i) => headers[i] || `列${i + 1}`),
    rows: rows.map((row) => Array.from({ length: width }, (_, i) => row[i] || '')),
    warnings: Array.isArray(payload.warnings) ? payload.warnings.map(String) : []
  }
}

/**
 * 把 AI 返回的整段文字按小标题切成若干章节。
 * defs: [{ key, label, re }]，re 用于匹配标题行。
 */
export function splitByHeadings(text, defs) {
  const result = Object.fromEntries(defs.map((d) => [d.key, []]))
  let current = ''
  String(text || '')
    .split('\n')
    .forEach((line) => {
      const trimmed = line.trim()
      if (!trimmed) {
        if (current) result[current].push('')
        return
      }
      const heading = trimmed.replace(/^[#*\-\d.、\s]+/, '').replace(/[:：]\s*$/, '')
      const hit = defs.find((d) => d.re.test(heading))
      if (hit && heading.length <= 18) {
        current = hit.key
        const inline = trimmed.split(/[:：]/).slice(1).join('：').trim()
        if (inline) result[current].push(inline)
        return
      }
      if (current) result[current].push(trimmed.replace(/^[-*]\s*/, ''))
    })
  return Object.fromEntries(
    defs.map((d) => [d.key, result[d.key].join('\n').replace(/\n{3,}/g, '\n\n').trim()])
  )
}

export function extractChecklist(payload) {
  if (!payload) return []
  const raw = payload.checklist || payload.objectives || payload.tasks
  return Array.isArray(raw) ? raw.map(String) : []
}

/** 预习卡片：从结构化返回或 markdown 小标题里抽取分块内容 */
export function extractBriefSections(payload, text) {
  const fromPayload = (keys) => {
    for (const key of keys) {
      const v = payload?.[key]
      if (Array.isArray(v) && v.length) return v.map(String)
      if (typeof v === 'string' && v.trim()) return [v.trim()]
    }
    return []
  }

  const sections = {
    objective: fromPayload(['objective', 'objectives', 'goal', 'purpose']),
    checklist: fromPayload(['checklist', 'prepare', 'preparation', 'tasks']),
    formula: fromPayload(['formula', 'formulas', 'keyFormula', 'principle']),
    pitfalls: fromPayload(['pitfalls', 'mistakes', 'cautions', 'warnings'])
  }

  if (Object.values(sections).some((v) => v.length)) return sections

  const headingMap = [
    { key: 'objective', re: /目标|目的/ },
    { key: 'checklist', re: /准备|携带|检查|清单/ },
    { key: 'formula', re: /公式|原理/ },
    { key: 'pitfalls', re: /易错|注意|误区|坑/ }
  ]
  let current = ''
  String(text || '')
    .split('\n')
    .forEach((line) => {
      const trimmed = line.trim()
      if (!trimmed) return
      const heading = trimmed.replace(/^[#*\-\d.、\s]+/, '')
      const hit = headingMap.find((h) => h.re.test(heading) && heading.length <= 16)
      if (hit && (trimmed.startsWith('#') || trimmed.endsWith('：') || trimmed.endsWith(':'))) {
        current = hit.key
        return
      }
      if (current) {
        sections[current].push(trimmed.replace(/^[-*\d.、\s]+/, ''))
      }
    })
  return sections
}
