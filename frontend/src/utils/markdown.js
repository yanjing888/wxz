import { marked } from 'marked'

marked.setOptions({
  breaks: true,
  gfm: true
})

/**
 * 轻量规范化：不固定模板，只把常见「单行标题」补成 Markdown 标题，便于加粗显示。
 * 已有 ### / ** 的内容不会被改动。
 */
export function normalizeAiMarkdown(text) {
  if (!text) return ''
  const lines = text.split('\n')
  return lines.map((line) => {
    const trimmed = line.trim()
    if (!trimmed) return line
    if (/^#{1,6}\s/.test(trimmed)) return line
    if (/^\*\*.+\*\*$/.test(trimmed)) return line
    if (/^[-*•]\s/.test(trimmed)) return line
    if (/^\d+[.)]\s/.test(trimmed)) return line
    if (/^>\s/.test(trimmed)) return line
    // 短行、无句末标点，像「图片分析摘要」「发现的问题」这类小节标题
    if (trimmed.length <= 24 && !/[。！？；，,.!?;:：]$/.test(trimmed)) {
      return line.replace(trimmed, `### ${trimmed}`)
    }
    return line
  }).join('\n')
}

export function renderChatMarkdown(text, { normalize = true } = {}) {
  const source = normalize ? normalizeAiMarkdown(text) : (text || '')
  return marked.parse(source)
}
