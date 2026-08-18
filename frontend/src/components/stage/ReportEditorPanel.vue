<template>
  <section class="report-editor flex flex-1 min-h-0 bg-white">
    <div v-if="!sessionId" class="empty-state flex-1 flex items-center justify-center">
      <div class="text-center max-w-md px-6">
        <p class="text-ink-strong font-semibold mb-2">还没有可用的实验记录</p>
        <p class="text-[13px] text-ink-muted">
          请先到「实验台」完成本实验，系统会自动填入步骤、数据与知识要点。
        </p>
      </div>
    </div>

    <template v-else>
      <!-- ===== 左栏：报告编辑 ===== -->
      <div class="report-main flex-1 min-w-0 min-h-0 flex flex-col overflow-hidden">
        <header class="report-header">
          <div class="header-left">
            <h2>{{ experimentName || '实验报告' }}</h2>
          </div>
          <div class="header-actions">
            <button type="button" class="btn-plain" :disabled="!sessionId || generating" @click="generateFromSession">
              {{ generating ? '恢复中…' : '从实验记录填充' }}
            </button>
            <button type="button" class="btn-plain" :disabled="!hasContent" @click="copyFullReport">复制全文</button>
            <button type="button" class="btn-plain" :disabled="!sessionId || exporting" @click="downloadDocx">
              {{ exporting ? '导出中…' : '导出 Word' }}
            </button>
            <button type="button" class="btn-primary" :disabled="!hasContent || reportCompleting" @click="openPreview">
              {{ reportDone ? '已提交' : '提交报告' }}
            </button>
          </div>
        </header>

        <!-- 工具栏 -->
        <div class="rep-toolbar">
          <button type="button" class="tool-btn" title="加粗" @mousedown.prevent="execFmt('bold')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M6 4h8a4 4 0 014 4 4 4 0 01-4 4H6z"/><path d="M6 12h9a4 4 0 014 4 4 4 0 01-4 4H6z"/></svg>
          </button>
          <button type="button" class="tool-btn" title="斜体" @mousedown.prevent="execFmt('italic')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="19" y1="4" x2="10" y2="4"/><line x1="14" y1="20" x2="5" y2="20"/><line x1="15" y1="4" x2="9" y2="20"/></svg>
          </button>
          <button type="button" class="tool-btn" title="下划线" @mousedown.prevent="execFmt('underline')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 3v7a6 6 0 0012 0V3"/><line x1="4" y1="21" x2="20" y2="21"/></svg>
          </button>
          <span class="tool-sep" />
          <button type="button" class="tool-btn" title="上标" @mousedown.prevent="execFmt('superscript')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19l6-6 4 4 6-6"/><text x="14" y="9" font-size="8" fill="currentColor" stroke="none" font-weight="bold">x²</text></svg>
          </button>
          <button type="button" class="tool-btn" title="下标" @mousedown.prevent="execFmt('subscript')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19l6-6 4 4 6-6"/><text x="14" y="14" font-size="8" fill="currentColor" stroke="none" font-weight="bold">x₂</text></svg>
          </button>
          <span class="tool-sep" />
          <button type="button" class="tool-btn" title="插入公式" @mousedown.prevent="startFormula">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M7 3l3 3-3 3M3 7h6M14 17l3-3 3 3M14 21h6M3 21h6M17 7v10"/></svg>
          </button>
          <button type="button" class="tool-btn" title="插入图片" @mousedown.prevent="triggerImageUpload">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/></svg>
          </button>
          <span class="tool-sep" />
          <button type="button" class="tool-btn" title="无序列表" @mousedown.prevent="execFmt('insertUnorderedList')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>
          </button>
        </div>

        <!-- 报告内容 -->
        <div class="report-content custom-scroll flex-1 min-h-0">
          <section
            v-for="def in sectionDefs"
            :id="`sec-${def.key}`"
            :key="def.key"
            class="report-field"
          >
            <label class="rep-label">
              <span>{{ def.label }}</span>
              <em>{{ sectionWordCount(def.key) }} 字</em>
            </label>
            <div
              class="rep-editor"
              contenteditable="true"
              :data-key="def.key"
              :data-placeholder="placeholders[def.key]"
              @input="onEditorInput(def.key, $event)"
              @focus="activeKey = def.key"
              @blur="saveDraft"
            />
          </section>
        </div>
      </div>

      <!-- ===== 右栏：小智辅助（常驻） ===== -->
      <aside class="report-aside border-l border-line-soft flex flex-col bg-surface-soft/30">
        <div class="aside-header">
          <svg class="aside-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 2a7 7 0 017 7c0 2.5-1.5 4.5-3 6v2H8v-2c-1.5-1.5-3-3.5-3-6a7 7 0 017-7zM9 19h6M10 22h4" />
          </svg>
          <span>小智辅助</span>
        </div>
        <div class="aside-body flex-1 min-h-0 flex flex-col">
          <AskPanel
            title="小智辅助"
            desc="实验报告写作助手"
            tool-code="report-assist"
            :experiment-code="experimentCode"
            :experiment-name="experimentName"
            :suggestions="reportAssistantSuggestions"
          />
        </div>
      </aside>
    </template>

    <!-- 公式输入弹窗 -->
    <div v-if="formulaOpen" class="formula-overlay" @click.self="formulaOpen = false">
      <div class="formula-dialog">
        <p class="formula-title">插入公式</p>
        <input
          ref="formulaInputRef"
          v-model="formulaText"
          class="formula-input"
          placeholder="输入 LaTeX 公式，如 E=mc^2 或 \\frac{1}{2}mv^2"
          @keydown.enter="confirmFormula"
          @keydown.escape="formulaOpen = false"
        />
        <div class="formula-preview" v-if="formulaText">
          <span class="formula-text">{{ formulaText }}</span>
        </div>
        <div class="formula-actions">
          <button type="button" class="btn-plain" @click="formulaOpen = false">取消</button>
          <button type="button" class="btn-primary" @click="confirmFormula">插入</button>
        </div>
      </div>
    </div>

    <!-- 隐藏的图片上传 -->
    <input
      ref="imageInputRef"
      type="file"
      accept="image/*"
      class="hidden"
      @change="onImageSelected"
    />

    <!-- 报告预览弹窗 -->
    <div v-if="previewOpen" class="preview-overlay" @click.self="previewOpen = false">
      <div class="preview-wrapper">
        <div class="preview-topbar">
          <span class="preview-topbar-title">报告预览</span>
          <div class="preview-topbar-actions">
            <button type="button" class="btn-plain" @click="previewOpen = false">取消</button>
            <button type="button" class="btn-primary" :disabled="reportCompleting || reportDone" @click="confirmSubmit">
              {{ reportCompleting ? '提交中…' : reportDone ? '已提交' : '确认提交' }}
            </button>
          </div>
        </div>
        <div class="preview-scroll custom-scroll">
          <div class="preview-page">
            <div class="doc-header">
              <h1 class="doc-title">{{ experimentName || '实验报告' }}</h1>
              <div class="doc-subtitle">大学物理实验报告</div>
              <table class="doc-info-table">
                <tr>
                  <td class="doc-info-label">姓名</td>
                  <td class="doc-info-value">{{ studentName }}</td>
                  <td class="doc-info-label">日期</td>
                  <td class="doc-info-value">{{ todayStr }}</td>
                </tr>
              </table>
            </div>
            <div class="doc-body">
              <section v-for="def in sectionDefs" :key="def.key" class="doc-section">
                <h2 class="doc-heading">{{ def.label }}</h2>
                <div
                  v-if="stripHtml(form[def.key] || '').trim()"
                  class="doc-content"
                  v-html="form[def.key] || ''"
                />
                <p v-else class="doc-empty">（未填写）</p>
              </section>
            </div>
          </div>
        </div>
        <p v-if="completionMessage" class="preview-msg">{{ completionMessage }}</p>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import AskPanel from './AskPanel.vue'
import { useAuthStore } from '../../stores/auth'
import { sessionApi, studentExperimentApi } from '../../api'
import {
  REPORT_SECTION_DEFS,
  buildReportSections,
  defaultSections,
  sectionsToFullText
} from '../../utils/sessionReport'

const props = defineProps({
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' },
  sessionId: { type: [Number, String], default: null }
})

const auth = useAuthStore()

const sectionDefs = REPORT_SECTION_DEFS
const placeholders = {
  purpose: '本实验要验证什么物理规律…',
  principle: '核心公式与物理意义…',
  apparatus: '主要仪器名称与型号…',
  procedure: '按实际操作顺序简述…',
  data: '原始数据表、必要计算过程、单位和有效数字…',
  results: '最终测量结果及不确定度表示…',
  discussion: '误差分析、结果合理性、改进措施…'
}

const form = reactive(defaultSections())
const generating = ref(false)
const exporting = ref(false)
const reportCompleting = ref(false)
const reportDone = ref(false)
const completionMessage = ref('')
const sourceReport = ref(null)
const activeKey = ref('')
const formulaOpen = ref(false)
const formulaText = ref('')
const formulaInputRef = ref(null)
const imageInputRef = ref(null)
const previewOpen = ref(false)

const hasContent = computed(() => sectionDefs.some((d) => stripHtml(form[d.key] || '').trim()))
const studentName = computed(() => auth.displayName || auth.username || '—')
const todayStr = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
})

const reportAssistantSuggestions = computed(() => [
  '帮我检查报告还缺什么',
  '根据本次实验记录，帮我整理误差分析思路',
  '帮我把测量数据整理成表格'
])

watch(() => props.sessionId, onSessionChange, { immediate: true })

// ===== 工具函数 =====

function stripHtml(html) {
  if (!html) return ''
  const tmp = document.createElement('div')
  tmp.innerHTML = html
  return tmp.textContent || tmp.innerText || ''
}

function escapeHtml(text) {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

// ===== 富文本编辑 =====

function onEditorInput(key, event) {
  form[key] = event.target.innerHTML
}

function setEditorContent(key, content) {
  const el = document.querySelector(`.rep-editor[data-key="${key}"]`)
  if (el) {
    if (content && content.includes('<')) {
      el.innerHTML = content
    } else {
      el.innerText = content || ''
    }
    form[key] = el.innerHTML
  }
}

function execFmt(command) {
  document.execCommand(command, false, null)
  const el = document.querySelector(`.rep-editor[data-key="${activeKey.value}"]`)
  if (el) form[activeKey.value] = el.innerHTML
}

function getSelectionRange() {
  const sel = window.getSelection()
  if (!sel.rangeCount) return null
  const range = sel.getRangeAt(0)
  const container = range.commonAncestorContainer
  const editor = container.nodeType === 3
    ? container.parentElement?.closest('.rep-editor')
    : container.closest?.('.rep-editor')
  if (!editor) return null
  return { range, editor }
}

// ===== 公式插入 =====

function startFormula() {
  formulaText.value = ''
  formulaOpen.value = true
  nextTick(() => formulaInputRef.value?.focus())
}

function confirmFormula() {
  if (!formulaText.value.trim()) {
    formulaOpen.value = false
    return
  }
  const el = document.querySelector(`.rep-editor[data-key="${activeKey.value}"]`)
  if (!el) {
    formulaOpen.value = false
    return
  }
  el.focus()
  const formulaHtml = `<span class="inline-formula" contenteditable="false">$${escapeHtml(formulaText.value.trim())}$</span>&nbsp;`
  document.execCommand('insertHTML', false, formulaHtml)
  form[activeKey.value] = el.innerHTML
  saveDraft()
  formulaOpen.value = false
}

// ===== 图片插入 =====

function triggerImageUpload() {
  imageInputRef.value?.click()
}

function onImageSelected(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (file.size > 3 * 1024 * 1024) {
    completionMessage.value = '图片不能超过 3MB，请压缩后插入。'
    setTimeout(() => { if (completionMessage.value.startsWith('图片')) completionMessage.value = '' }, 3000)
    event.target.value = ''
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    const el = document.querySelector(`.rep-editor[data-key="${activeKey.value}"]`)
    if (!el) return
    el.focus()
    const imgHtml = `<img src="${reader.result}" alt="${escapeHtml(file.name)}" style="max-width:100%;border-radius:6px;margin:4px 0" />`
    document.execCommand('insertHTML', false, imgHtml)
    form[activeKey.value] = el.innerHTML
    saveDraft()
  }
  reader.readAsDataURL(file)
  event.target.value = ''
}

// ===== 草稿 =====

function draftKey() {
  return props.sessionId ? `wxz_report_draft_${props.sessionId}` : ''
}

function saveDraft() {
  const key = draftKey()
  if (key) localStorage.setItem(key, JSON.stringify({ ...form }))
}

function loadDraft() {
  const key = draftKey()
  if (!key) return false
  try {
    const saved = JSON.parse(localStorage.getItem(key) || 'null')
    if (!saved) return false
    Object.keys(form).forEach((k) => {
      form[k] = saved[k] || ''
    })
    return true
  } catch {
    return false
  }
}

async function onSessionChange() {
  completionMessage.value = ''
  reportDone.value = false
  sourceReport.value = null
  Object.keys(form).forEach((k) => { form[k] = '' })
  if (!props.sessionId) return
  if (loadDraft()) {
    await nextTick()
    syncEditorsFromForm()
    await loadSourceReport()
    return
  }
  await generateFromSession()
}

function syncEditorsFromForm() {
  sectionDefs.forEach((def) => {
    setEditorContent(def.key, form[def.key] || '')
  })
}

async function loadSourceReport() {
  if (!props.sessionId) return
  const { data: report } = await sessionApi.report(props.sessionId)
  sourceReport.value = report || null
}

async function generateFromSession() {
  if (!props.sessionId) return
  generating.value = true
  try {
    await loadSourceReport()
    const built = buildReportSections(sourceReport.value)
    Object.keys(form).forEach((k) => { form[k] = escapeHtml(built[k] || '').replace(/\n/g, '<br>') })
    await nextTick()
    syncEditorsFromForm()
    saveDraft()
  } finally {
    generating.value = false
  }
}

async function markReportComplete({ silent = false } = {}) {
  if (!props.experimentCode || reportCompleting.value || !hasContent.value) return
  reportCompleting.value = true
  if (!silent) completionMessage.value = ''
  try {
    await studentExperimentApi.completeReport(props.experimentCode)
    reportDone.value = true
    if (!silent) completionMessage.value = '报告状态已同步，教师端可在报告评阅中查看。'
  } finally {
    reportCompleting.value = false
  }
}

function fullText() {
  const plain = {}
  Object.keys(form).forEach((k) => { plain[k] = stripHtml(form[k]) })
  return sectionsToFullText(plain, props.experimentName)
}

function copyFullReport() {
  navigator.clipboard?.writeText(fullText()).catch(() => {})
}

async function downloadDocx() {
  if (!props.sessionId) return
  exporting.value = true
  try {
    const sections = sectionDefs.map((def) => ({ label: def.label, content: stripHtml(form[def.key] || '') }))
    const { data } = await sessionApi.studentReportDocx(props.sessionId, { sections })
    const url = URL.createObjectURL(data)
    const a = document.createElement('a')
    a.href = url
    a.download = `${props.experimentName || '实验'}-实验报告.docx`
    a.click()
    URL.revokeObjectURL(url)
    await markReportComplete({ silent: true })
  } finally {
    exporting.value = false
  }
}

function sectionWordCount(key) {
  return stripHtml(form[key] || '').trim().length
}

function openPreview() {
  saveDraft()
  completionMessage.value = ''
  previewOpen.value = true
}

async function confirmSubmit() {
  if (reportDone.value) {
    previewOpen.value = false
    return
  }
  await markReportComplete()
  if (reportDone.value) {
    setTimeout(() => { previewOpen.value = false }, 1200)
  }
}
</script>

<style scoped>
.report-editor { @apply overflow-hidden bg-white; }

/* ===== 左栏 ===== */
.report-main { @apply bg-white; }

.report-header {
  @apply flex items-center justify-between gap-3 px-4 py-1.5 border-b border-line-soft shrink-0;
}
.header-left h2 { @apply text-[13px] font-bold text-ink-strong; }
.header-left span { @apply text-[10px] text-ink-faint ml-1.5; }
.header-actions { @apply flex items-center gap-1; }

.btn-plain {
  @apply border border-line-soft bg-white px-2 py-0.5 text-[11px] font-semibold text-ink-muted
    hover:text-ink-strong hover:border-brand-200 disabled:opacity-40 disabled:cursor-not-allowed;
}
.btn-primary {
  @apply bg-brand-600 px-2 py-0.5 text-[11px] font-semibold text-white
    hover:bg-brand-700 disabled:opacity-40 disabled:cursor-not-allowed;
}

/* ===== 工具栏 ===== */
.rep-toolbar {
  @apply flex items-center gap-0.5 px-4 py-0.5 border-b border-line-soft bg-surface-soft/30 shrink-0;
}
.tool-btn {
  @apply w-6 h-6 flex items-center justify-center rounded text-ink-muted
    hover:bg-white hover:text-brand-600 hover:shadow-sm transition-all;
}
.tool-btn svg { @apply w-3.5 h-3.5; }
.tool-sep { @apply w-px h-3.5 bg-line-soft mx-1; }

/* ===== 报告内容 ===== */
.report-content {
  @apply px-4 py-3;
  overflow-y: auto;
  flex: 1 1 0;
  min-height: 0;
}
.report-field { @apply mb-5; }
.rep-label {
  @apply flex items-center justify-between mb-1.5;
}
.rep-label span { @apply text-[14px] font-bold text-ink-strong; }
.rep-label em { @apply text-[12px] text-ink-faint not-italic; }

.rep-editor {
  @apply w-full min-h-[100px] border border-line-soft px-3 py-2 text-[14px] leading-7
    text-ink-strong outline-none focus:border-brand-300 focus:bg-brand-50/20 rounded-sm;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
.rep-editor:empty::before {
  content: attr(data-placeholder);
  color: var(--ink-faint, #94a3b8);
  font-size: 14px;
  pointer-events: none;
}
.rep-editor :deep(img) { max-width: 100%; border-radius: 6px; margin: 4px 0; }
.rep-editor :deep(.inline-formula) {
  background: #eef2ff;
  color: #4338ca;
  padding: 1px 5px;
  border-radius: 3px;
  font-family: "Cambria Math", "Latin Modern Math", serif;
  font-size: 0.95em;
  margin: 0 2px;
  user-select: all;
}

/* ===== 右栏：小智辅助 ===== */
.report-aside {
  width: 340px;
  flex-shrink: 0;
}
.aside-header {
  @apply flex items-center gap-2 px-4 py-3 border-b border-line-soft bg-white;
}
.aside-icon { @apply w-4 h-4 text-brand-600; }
.aside-header span { @apply text-[13px] font-bold text-ink-strong; }
.aside-body { @apply bg-white; }
.aside-body :deep(.stage-panel) { @apply h-full border-0; }
.aside-body :deep(.panel-head) { @apply px-4 py-2.5; }
.aside-body :deep(.panel-actions) { @apply hidden; }
.aside-body :deep(.max-w-\[960px\]) { @apply max-w-none; }

/* ===== 公式弹窗 ===== */
.formula-overlay {
  @apply fixed inset-0 z-50 flex items-center justify-center;
  background: rgba(0,0,0,0.25);
}
.formula-dialog {
  @apply bg-white rounded-lg shadow-xl p-5 w-[420px] max-w-[90vw];
}
.formula-title { @apply text-[14px] font-bold text-ink-strong mb-3; }
.formula-input {
  @apply w-full border border-line-soft px-3 py-2 text-[14px] outline-none
    focus:border-brand-300 rounded-sm;
  font-family: "Cambria Math", "Consolas", monospace;
}
.formula-preview {
  @apply mt-2 px-3 py-2 bg-surface-soft/40 rounded-sm border border-line-soft;
}
.formula-text {
  font-family: "Cambria Math", "Latin Modern Math", serif;
  font-size: 15px;
  color: #4338ca;
}
.formula-actions { @apply flex gap-2 mt-3 justify-end; }

.empty-state { @apply text-sm text-ink-muted bg-surface-soft/30; }

/* ===== 报告预览弹窗 ===== */
.preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.35);
  backdrop-filter: blur(2px);
}
.preview-wrapper {
  display: flex;
  flex-direction: column;
  width: 820px;
  max-width: 94vw;
  height: 90vh;
  max-height: 900px;
  background: #f1f5f9;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  overflow: hidden;
}
.preview-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid var(--border-soft, #e4e9f3);
  flex-shrink: 0;
}
.preview-topbar-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-strong, #0f172a);
}
.preview-topbar-actions {
  display: flex;
  gap: 8px;
}
.preview-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
}
.preview-msg {
  padding: 8px 16px;
  font-size: 12px;
  font-weight: 600;
  color: #059669;
  background: #ecfdf5;
  border-top: 1px solid #a7f3d0;
  flex-shrink: 0;
}

/* Word 文档页面 */
.preview-page {
  width: 680px;
  max-width: 100%;
  margin: 0 auto;
  background: #fff;
  padding: 56px 60px 48px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  min-height: 800px;
  font-family: "SimSun", "宋体", "Times New Roman", serif;
  color: #1a1a1a;
  line-height: 1.9;
}
.doc-header {
  text-align: center;
  margin-bottom: 28px;
  padding-bottom: 16px;
  border-bottom: 2px solid #1a1a1a;
}
.doc-title {
  font-size: 22px;
  font-weight: 700;
  font-family: "SimHei", "黑体", "Microsoft YaHei", sans-serif;
  margin: 0 0 4px;
  letter-spacing: 2px;
}
.doc-subtitle {
  font-size: 14px;
  color: #555;
  font-family: "SimHei", "黑体", "Microsoft YaHei", sans-serif;
  letter-spacing: 1px;
}
.doc-info-table {
  width: 100%;
  margin-top: 14px;
  border-collapse: collapse;
  font-size: 13px;
}
.doc-info-table td {
  padding: 2px 6px;
  border: none;
}
.doc-info-label {
  text-align: right;
  font-weight: 600;
  white-space: nowrap;
  width: 60px;
  color: #333;
}
.doc-info-value {
  text-align: left;
  border-bottom: 1px solid #999;
  min-width: 120px;
  color: #1a1a1a;
}
.doc-body {
  font-size: 14px;
}
.doc-section {
  margin-bottom: 18px;
}
.doc-heading {
  font-size: 15px;
  font-weight: 700;
  font-family: "SimHei", "黑体", "Microsoft YaHei", sans-serif;
  margin: 0 0 6px;
  color: #1a1a1a;
}
.doc-content {
  font-size: 14px;
  line-height: 2;
  text-indent: 2em;
  word-break: break-word;
}
.doc-content :deep(img) {
  max-width: 100%;
  border-radius: 4px;
  margin: 6px 0;
}
.doc-content :deep(.inline-formula) {
  background: #f0f4ff;
  color: #4338ca;
  padding: 1px 5px;
  border-radius: 3px;
  font-family: "Cambria Math", "Latin Modern Math", serif;
  font-size: 0.95em;
  margin: 0 2px;
}
.doc-empty {
  color: #999;
  font-style: italic;
  font-size: 13px;
  text-indent: 2em;
}

@media (max-width: 1024px) {
  .report-aside { width: 280px; }
}
@media (max-width: 768px) {
  .report-editor { @apply flex-col; }
  .report-aside { width: 100%; height: 300px; border-left: 0; border-top: 1px solid var(--line-soft, #e4e9f3); }
  .report-header { @apply flex-col items-start gap-2; }
  .header-actions { @apply w-full; }
  .report-content { @apply px-4; }
  .preview-wrapper { width: 100vw; max-width: 100vw; height: 100vh; max-height: 100vh; border-radius: 0; }
  .preview-page { width: 100%; padding: 24px 20px; }
}
</style>
