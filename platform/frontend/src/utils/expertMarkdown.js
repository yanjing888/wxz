import { Marked } from 'marked'
import DOMPurify from 'dompurify'
import katex from 'katex'

const delimiters = [
  ['$$', '$$', true], ['\\[', '\\]', true],
  ['\\(', '\\)', false], ['$', '$', false]
]

export function renderExpertMarkdown(text) {
  const formulas = []
  const extension = (name, level) => ({
    name, level,
    start: source => level === 'block'
      ? source.search(/(?:^|\n)(?:\$\$|\\\[)/)
      : source.search(/\$|\\\[|\\\(/),
    tokenizer(source) {
      for (const [left, right, display] of delimiters) {
        if (!source.startsWith(left) || (level === 'block' && !display)) continue
        let end = source.indexOf(right, left.length)
        while (end >= 0 && source[end - 1] === '\\') end = source.indexOf(right, end + right.length)
        if (end < 0) continue
        const tex = source.slice(left.length, end)
        if (!tex.trim() || (!display && tex.includes('\n'))) continue
        return { type: name, raw: source.slice(0, end + right.length), tex, display }
      }
    },
    renderer(token) {
      const index = formulas.push(token) - 1
      return `<span data-expert-math="${index}"></span>`
    }
  })
  const parser = new Marked({ gfm: true, breaks: false,
    extensions: [extension('expertMathBlock', 'block'), extension('expertMath', 'inline')] })
  const container = document.createElement('div')
  container.innerHTML = DOMPurify.sanitize(parser.parse(text || ''), {
    FORBID_TAGS: ['img', 'video', 'audio', 'form', 'button'],
    FORBID_ATTR: ['style']
  })
  // Sanitize model HTML first; generate trusted KaTeX markup afterwards so its
  // layout styles survive. Never enable TeX HTML/URL commands from model output.
  for (const placeholder of container.querySelectorAll('[data-expert-math]')) {
    const formula = formulas[Number(placeholder.dataset.expertMath)]
    if (!formula) { placeholder.remove(); continue }
    katex.render(formula.tex, placeholder, {
      displayMode: formula.display, throwOnError: false, strict: 'ignore', trust: false
    })
    placeholder.removeAttribute('data-expert-math')
  }
  return container.innerHTML
}
