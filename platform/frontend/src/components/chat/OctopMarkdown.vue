<template>
  <div class="octop-markdown" @click="copyBlock">
    <div v-html="html" />
    <span class="sr-only" role="status">{{ copyStatus }}</span>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { renderExpertMarkdown } from '../../utils/expertMarkdown'

const props = defineProps({ text: String })
const copyStatus = ref('')
const copyIcon = '<svg aria-hidden="true" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/></svg>'

const html = computed(() => {
  const template = document.createElement('template')
  template.innerHTML = renderExpertMarkdown(props.text)
  for (const code of template.content.querySelectorAll('pre > code')) {
    const pre = code.parentElement
    const block = document.createElement('div')
    block.className = 'octop-code'
    const header = document.createElement('div')
    header.className = 'octop-code-header'
    const language = document.createElement('span')
    language.textContent = [...code.classList].find((name) => name.startsWith('language-'))?.slice(9).toLowerCase() || 'text'
    const button = document.createElement('button')
    button.type = 'button'
    button.className = 'octop-code-copy'
    button.setAttribute('aria-label', '复制代码块')
    button.title = '复制'
    button.innerHTML = copyIcon
    header.append(language, button)
    pre.replaceWith(block)
    block.append(header, pre)
  }
  return template.innerHTML
})

async function copyBlock(event) {
  const button = event.target.closest('button.octop-code-copy')
  if (!button) return
  const text = button.closest('.octop-code')?.querySelector('pre > code')?.textContent.replace(/\n$/, '')
  if (!text) return

  copyStatus.value = ''
  const copied = await copyText(text)
  const label = copied ? '已复制' : '复制失败'
  copyStatus.value = label
  if (button.isConnected) {
    button.title = label
    button.textContent = label
  }
}

async function copyText(text) {
  try {
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    // Try the selection-based fallback on browsers that deny Clipboard API access.
  }

  const input = document.createElement('textarea')
  const previousFocus = document.activeElement
  input.value = text
  input.readOnly = true
  input.style.cssText = 'position:fixed;top:0;left:0;opacity:0'
  document.body.append(input)
  try {
    input.select()
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    input.remove()
    previousFocus?.focus({ preventScroll: true })
  }
}
</script>

<style scoped>
.octop-markdown :deep(.katex-display) {
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0.35em 0;
}
.octop-markdown :deep(.octop-code) {
  margin: 0.75em 0;
  overflow: hidden;
  border: 1px solid #e8eaee;
  border-radius: 8px;
  background: #f8f8f9;
}
.octop-markdown :deep(.octop-code-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  padding: 4px 12px;
  border-bottom: 1px solid #e8eaee;
  background: #eef0f2;
  color: #687385;
  font: 12px Consolas, monospace;
}
.octop-markdown :deep(.octop-code-copy) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  min-height: 24px;
  padding: 2px 5px;
  border-radius: 4px;
  color: #566070;
}
.octop-markdown :deep(.octop-code-copy:hover),
.octop-markdown :deep(.octop-code-copy:focus-visible) {
  background: #e1e5e9;
  color: #222a38;
}
.octop-markdown :deep(.octop-code pre) {
  margin: 0;
  padding: 12px 16px;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: #343b46;
  overflow-x: auto;
  white-space: pre;
  font: 13px/1.6 Consolas, "Liberation Mono", monospace;
}
.octop-markdown :deep(.octop-code pre code) {
  background: transparent;
  color: inherit;
  font: inherit;
}
</style>
