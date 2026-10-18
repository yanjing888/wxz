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
    // Markdown 表格行不做「短行标题」升格，避免破坏表格
    if (/^\|/.test(trimmed)) return line
    if (/^[\s|:-]+$/.test(trimmed) && trimmed.includes('-')) return line
    // 短行、无句末标点，像「图片分析摘要」「发现的问题」这类小节标题
    if (trimmed.length <= 24 && !/[。！？；，,.!?;:：]$/.test(trimmed)) {
      return line.replace(trimmed, `### ${trimmed}`)
    }
    return line
  }).join('\n')
}

/** 修复 LLM 常见 Markdown 表格错误：单元格内竖线、分隔行断行等 */
export function repairChatMarkdownTables(text) {
  if (!text || !text.includes('|')) return text || ''

  const lines = text.split('\n')
  const out = []

  for (let i = 0; i < lines.length; i++) {
    let line = sanitizeTableRowPipes(lines[i])
    const trimmed = line.trim()

    if (!trimmed.startsWith('|')) {
      out.push(line)
      continue
    }

    // 表头后分隔行被模型拆成多行 → 合并并重建标准分隔行
    if (i + 1 < lines.length) {
      const nextTrim = lines[i + 1].trim()
      const nextIsSep = /^[\s|:-]+$/.test(nextTrim) && nextTrim.includes('-')
      const rowAfterNext = lines[i + 2]?.trim() ?? ''
      const looksLikeHeaderThenSep = nextIsSep && (
        rowAfterNext.startsWith('|') || rowAfterNext === '' || rowAfterNext.startsWith('—') || rowAfterNext.startsWith('| —')
      )
      if (looksLikeHeaderThenSep || (nextIsSep && !/^[\s|:-]+$/.test(trimmed))) {
        out.push(line)
        i++
        while (i + 1 < lines.length && /^[\s|:-]+$/.test(lines[i + 1].trim()) && !lines[i + 1].trim().startsWith('|')) {
          i++
        }
        out.push(buildTableSeparator(countTableColumns(line)))
        continue
      }
    }

    // 孤立的断行分隔符片段，跳过（已在上一段重建）
    if (/^[\s|:-]+$/.test(trimmed) && trimmed.includes('-') && !trimmed.startsWith('|')) {
      continue
    }

    out.push(line)
  }

  return out.join('\n')
}

function sanitizeTableRowPipes(line) {
  if (!line.includes('|')) return line
  return line
    .replace(/D_m\s*=\s*\|[^|\n]+\|/gi, 'Dm（右读−左读）')
    .replace(/\|\s*右\s*[-−]\s*左\s*\|/g, '（右−左）')
    .replace(/=\s*\|([^|\n]+)\|/g, '=$1')
}

function countTableColumns(headerRow) {
  const trimmed = headerRow.trim()
  if (!trimmed.startsWith('|')) return Math.max(1, trimmed.split('|').length)
  const cells = trimmed.split('|').filter((cell) => cell.trim().length > 0)
  return Math.max(1, cells.length)
}

function buildTableSeparator(columnCount) {
  return `|${Array(columnCount).fill(' --- ').join('|')}|`
}

export function renderChatMarkdown(text, { normalize = true } = {}) {
  let source = repairChatMarkdownTables(text || '')
  source = normalize ? normalizeAiMarkdown(source) : source
  return marked.parse(renderMathInMarkdown(source))
}
