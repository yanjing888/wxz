import { marked } from 'marked'
import katex from 'katex'

marked.setOptions({
  breaks: true,
  gfm: true
})

const CODE_PLACEHOLDER = '\u0000CODEBLOCK_'

/**
 * 将 Markdown/LaTeX 中的数学公式渲染为 HTML（KaTeX）。
 * 支持：$...$、$$...$$、\\(...\\)、\\[...\\]
 * 会先保护 ``` 与 ` 代码块，避免误渲染。
 */
export function renderMathInMarkdown(text) {
  if (!text) return ''

  const codeBlocks = []
  let protectedText = text

  protectedText = protectedText.replace(/```[\s\S]*?```/g, (block) => {
    const index = codeBlocks.length
    codeBlocks.push(block)
    return `${CODE_PLACEHOLDER}${index}\u0000`
  })

  protectedText = protectedText.replace(/`[^`\n]+`/g, (block) => {
    const index = codeBlocks.length
    codeBlocks.push(block)
    return `${CODE_PLACEHOLDER}${index}\u0000`
  })

  const renderTex = (tex, displayMode) => {
    try {
      return katex.renderToString(tex.trim(), {
        displayMode,
        throwOnError: false,
        strict: 'ignore',
        trust: false
      })
    } catch {
      return displayMode ? `$$${tex}$$` : `$${tex}$`
    }
  }

  // 块级公式（先匹配，避免与行内 $ 冲突）
  protectedText = protectedText.replace(/\$\$([\s\S]+?)\$\$/g, (_, tex) => renderTex(tex, true))
  protectedText = protectedText.replace(/\\\[([\s\S]+?)\\\]/g, (_, tex) => renderTex(tex, true))

  // 行内公式
  protectedText = protectedText.replace(/\$([^$\n]+?)\$/g, (_, tex) => renderTex(tex, false))
  protectedText = protectedText.replace(/\\\(([\s\S]+?)\\\)/g, (_, tex) => renderTex(tex, false))

  return protectedText.replace(
    new RegExp(`${CODE_PLACEHOLDER}(\\d+)\u0000`, 'g'),
    (_, index) => codeBlocks[Number(index)] || ''
  )
}

/**
 * 轻量规范化：不固定模板，只把常见「单行标题」补成 Markdown 标题，便于加粗显示。
 * 已有 ### / ** 的内容不会被改动。
 */
export function normalizeAiMarkdown(text) {
  if (!text) return ''
  if (text.includes('welcome-guide')) return text
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
  return marked.parse(renderMathInMarkdown(source))
}
