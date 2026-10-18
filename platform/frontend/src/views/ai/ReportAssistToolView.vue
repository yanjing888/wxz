<template>
  <AiToolShell :tool="tool" fill>
    <div class="report-workspace flex flex-col flex-1 min-h-0">
      <header class="ws-header shrink-0">
        <div class="flex-1 min-w-0">
          <h2 class="ws-title">实验报告撰写</h2>
          <p class="ws-lead">从实验记录一键生成报告框架，编辑后导出。对标「文书生成」，辅助撰写，非教师批改。</p>
        </div>
        <div class="flex flex-wrap gap-2 shrink-0">
          <button type="button" class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold" :disabled="!sessionId || generating" @click="generateFromSession">
            {{ generating ? '生成中…' : '从实验记录生成' }}
          </button>
          <button type="button" class="btn-ghost px-4 py-2 rounded-xl text-sm font-semibold" :disabled="!hasContent" @click="copyFullReport">复制全文</button>
          <button type="button" class="btn-ghost px-4 py-2 rounded-xl text-sm font-semibold" :disabled="!sessionId" @click="downloadReport">下载 .txt</button>
        </div>
      </header>

      <div class="session-bar shrink-0">
        <select v-model="sessionId" class="field-input" @change="onSessionChange">
          <option value="">选择已完成的实验会话</option>
          <option v-for="item in sessions" :key="item.sessionId" :value="item.sessionId">
            {{ item.experimentName }} · {{ formatSessionTime(item.endTime) }}
          </option>
        </select>
        <p v-if="!sessions.length" class="hint">请先在实验工作台完成实验并点击「生成报告」结束会话。</p>
      </div>

      <div v-if="!sessionId" class="empty-state flex-1 flex items-center justify-center">
        <div class="text-center max-w-md px-6">
          <p class="text-ink-strong font-semibold mb-2">选择实验会话开始撰写</p>
          <p class="text-[13px] text-ink-muted">系统将自动填入实验步骤、测量数据与知识要点，你只需补充结果与讨论。</p>
        </div>
      </div>

      <div v-else class="ws-body flex-1 min-h-0 flex overflow-hidden">
        <nav class="section-nav custom-scroll shrink-0">
          <button
            v-for="def in sectionDefs"
            :key="def.key"
            type="button"
            class="nav-item"
            :class="{ 'nav-item--active': activeSection === def.key, 'nav-item--filled': (form[def.key] || '').trim().length >= 10 }"
            @click="activeSection = def.key"
          >
            {{ def.label }}
          </button>
          <div class="nav-divider" />
          <button type="button" class="nav-item nav-item--check" :class="{ 'nav-item--active': activeSection === 'check' }" @click="activeSection = 'check'">
            规范检查
          </button>
        </nav>

        <main class="editor-pane custom-scroll flex-1 min-h-0">
          <template v-if="activeSection !== 'check'">
            <div class="editor-head">
              <h3>{{ currentSectionDef?.label }}</h3>
              <button
                type="button"
                class="text-[12px] font-semibold text-brand-600 hover:text-brand-700 disabled:opacity-40"
                :disabled="polishLoading || !(form[activeSection] || '').trim()"
                @click="polishSection(activeSection)"
              >
                {{ polishLoading ? '润色中…' : 'AI 润色本段' }}
              </button>
            </div>
            <textarea
              v-model="form[activeSection]"
              class="editor-area"
              :placeholder="placeholders[activeSection]"
              @input="saveDraft"
            />
            <p v-if="activeSection === 'results'" class="editor-tip">
              提示：在「数据处理 → 不确定度计算器」中计算后，复制段落粘贴到此处。
            </p>
          </template>

          <template v-else>
            <h3 class="editor-head-title">报告规范检查</h3>
            <ul class="check-list">
              <li v-for="item in checkItems" :key="item.key" class="check-row" :class="item.ok ? 'check-row--ok' : 'check-row--miss'">
                <span>{{ item.ok ? '✓' : '○' }}</span>
                <div>
                  <p class="check-name">{{ item.label }}</p>
                  <p class="check-msg">{{ item.message }}</p>
                </div>
              </li>
            </ul>
            <button type="button" class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold mt-4" :disabled="checkLoading || !hasContent" @click="runAiCheck">
              {{ checkLoading ? '检查中…' : 'AI 深度检查' }}
            </button>
            <div v-if="checkResult" class="check-result chat-md mt-4" v-html="renderMd(checkResult)" />
          </template>
        </main>

        <aside class="preview-pane custom-scroll shrink-0 hidden lg:block">
          <h4 class="preview-title">报告预览</h4>
          <div class="preview-doc">
            <p class="preview-doc-title">{{ reportMeta.experimentName || '实验报告' }}</p>
            <div v-for="def in sectionDefs" :key="def.key" class="preview-section">
              <p class="preview-h">{{ def.label }}</p>
              <p class="preview-p">{{ (form[def.key] || '（未填写）').slice(0, 200) }}{{ (form[def.key] || '').length > 200 ? '…' : '' }}</p>
            </div>
          </div>
        </aside>
      </div>
    </div>
  </AiToolShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AiToolShell from '../../components/ai/AiToolShell.vue'
import { aiToolApi, sessionApi } from '../../api'
import { formatSessionTime } from '../../utils/studentFlow'
import { renderChatMarkdown } from '../../utils/markdown'
import {
  REPORT_SECTION_DEFS,
  buildReportSections,
  defaultSections,
  sectionsToFullText,
  downloadTextFile
} from '../../utils/sessionReport'

const props = defineProps({ tool: { type: Object, required: true } })
const route = useRoute()

const sectionDefs = REPORT_SECTION_DEFS
const placeholders = {
  purpose: '本实验要验证什么物理规律…',
  principle: '核心公式与物理意义…',
  apparatus: '主要仪器名称与型号…',
  procedure: '按实际操作顺序简述…',
  data: '原始数据表、计算过程（可从数据处理模块复制）…',
  results: '最终测量结果及不确定度表示…',
  discussion: '误差分析、结果合理性、改进措施…'
}

const form = reactive(defaultSections())
const sessions = ref([])
const sessionId = ref('')
const activeSection = ref('purpose')
const reportMeta = ref({})
const generating = ref(false)
const polishLoading = ref(false)
const checkLoading = ref(false)
const checkResult = ref('')

const currentSectionDef = computed(() => sectionDefs.find((d) => d.key === activeSection.value))
const hasContent = computed(() => sectionDefs.some((d) => (form[d.key] || '').trim()))

const checkItems = computed(() => {
  const items = sectionDefs.map((d) => {
    const len = (form[d.key] || '').trim().length
    return {
      key: d.key,
      label: d.label,
      ok: len >= 15,
      message: len >= 15 ? `已填写 ${len} 字` : len ? '内容偏短' : '未填写'
    }
  })
  const dataOk = /误差|不确定|±|标准差|相对/.test((form.data || '') + (form.results || ''))
  items.push({
    key: 'uncertainty',
    label: '含不确定度/误差',
    ok: dataOk,
    message: dataOk ? '数据或结果段已涉及误差分析' : '建议在数据/结果段补充不确定度'
  })
  return items
})

onMounted(async () => {
  const { data } = await aiToolApi.recapSessions()
  sessions.value = data || []
  preselectSession()
})

watch(() => route.query.exp, preselectSession)

function preselectSession() {
  const exp = String(route.query.exp || '').trim()
  if (!exp || sessionId.value) return
  const hit = sessions.value.find((s) => s.experimentCode === exp)
  if (hit) {
    sessionId.value = hit.sessionId
    onSessionChange()
  }
}

function draftKey() {
  return sessionId.value ? `wxz_report_draft_${sessionId.value}` : ''
}

function saveDraft() {
  const key = draftKey()
  if (!key) return
  localStorage.setItem(key, JSON.stringify({ ...form }))
}

function loadDraft() {
  const key = draftKey()
  if (!key) return false
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return false
    const saved = JSON.parse(raw)
    Object.keys(form).forEach((k) => {
      if (saved[k]) form[k] = saved[k]
    })
    return true
  } catch {
    return false
  }
}

async function onSessionChange() {
  checkResult.value = ''
  if (!sessionId.value) return
  if (loadDraft()) return
  await generateFromSession()
}

async function generateFromSession() {
  if (!sessionId.value) return
  generating.value = true
  try {
    const { data: report } = await sessionApi.report(sessionId.value)
    reportMeta.value = report || {}
    const built = buildReportSections(report)
    Object.keys(form).forEach((k) => {
      form[k] = built[k] || ''
    })
    saveDraft()

    const session = sessions.value.find((s) => String(s.sessionId) === String(sessionId.value))
    if (session && !form.results.includes('数据处理')) {
      try {
        const { data } = await aiToolApi.invoke(props.tool.code, {
          action: 'draft',
          inputs: {
            sessionId: Number(sessionId.value),
            experimentCode: session.experimentCode,
            experimentName: session.experimentName,
            ...form
          }
        })
        applyAiDraft(data.text || '')
      } catch {
        // local draft is enough
      }
    }
  } finally {
    generating.value = false
  }
}

function applyAiDraft(text) {
  if (!text) return
  const blocks = text.split(/\n(?=##\s)/)
  const labelMap = {
    实验目的: 'purpose',
    实验原理: 'principle',
    数据与处理: 'data',
    实验结果: 'results',
    分析与讨论: 'discussion',
    讨论与拓展: 'discussion'
  }
  blocks.forEach((block) => {
    const match = block.match(/^##\s*(.+?)\s*\n([\s\S]*)/)
    if (!match) return
    const key = labelMap[match[1].trim()]
    if (key && !(form[key] || '').trim()) form[key] = match[2].trim()
  })
  saveDraft()
}

async function polishSection(key) {
  polishLoading.value = true
  try {
    const session = sessions.value.find((s) => String(s.sessionId) === String(sessionId.value))
    const { data } = await aiToolApi.invoke(props.tool.code, {
      action: 'polish',
      inputs: {
        sessionId: Number(sessionId.value),
        experimentCode: session?.experimentCode || '',
        experimentName: session?.experimentName || '',
        section: key,
        sectionLabel: sectionDefs.find((d) => d.key === key)?.label || key,
        text: form[key]
      }
    })
    const polished = (data.text || '').replace(/^#+\s*.*\n+/m, '').trim()
    if (polished) {
      form[key] = polished
      saveDraft()
    }
  } finally {
    polishLoading.value = false
  }
}

async function runAiCheck() {
  checkLoading.value = true
  checkResult.value = ''
  try {
    const session = sessions.value.find((s) => String(s.sessionId) === String(sessionId.value))
    const summary = checkItems.value.map((i) => `${i.ok ? '✓' : '✗'} ${i.label}`).join('\n')
    const { data } = await aiToolApi.invoke(props.tool.code, {
      action: 'check',
      inputs: {
        sessionId: Number(sessionId.value),
        experimentCode: session?.experimentCode || '',
        experimentName: session?.experimentName || '',
        localCheckSummary: summary,
        ...form
      }
    })
    checkResult.value = data.text || ''
  } finally {
    checkLoading.value = false
  }
}

function copyFullReport() {
  const text = sectionsToFullText(form, reportMeta.value.experimentName)
  navigator.clipboard?.writeText(text).catch(() => {})
}

function downloadReport() {
  const name = reportMeta.value.experimentName || '实验报告'
  downloadTextFile(`${name}-报告.txt`, sectionsToFullText(form, name))
}

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}
</script>

<style scoped>
.report-workspace { @apply bg-surface-soft/30; }
.ws-header { @apply flex flex-wrap items-start justify-between gap-4 px-5 py-4 border-b border-line-soft bg-white; }
.ws-title { @apply text-[18px] font-bold text-ink-strong; }
.ws-lead { @apply text-[13px] text-ink-muted mt-1; }
.session-bar { @apply px-5 py-3 bg-white border-b border-line-soft; }
.field-input { @apply w-full max-w-lg rounded-xl border border-line-soft px-3 py-2 text-[14px]; }
.hint { @apply text-[13px] text-ink-faint mt-2; }

.ws-body { @apply min-h-0; }
.section-nav { @apply w-44 shrink-0 border-r border-line-soft bg-white py-3 overflow-y-auto; }
.nav-item {
  @apply block w-full text-left px-4 py-2.5 text-[13px] text-ink-muted hover:bg-surface-soft transition-colors;
}
.nav-item--active { @apply text-brand-600 bg-brand-50 font-semibold border-r-2 border-brand-600; }
.nav-item--filled:not(.nav-item--active)::before { content: '● '; @apply text-emerald-500 text-[10px]; }
.nav-item--check { @apply text-ink-faint; }
.nav-divider { @apply my-2 border-t border-line-soft mx-3; }

.editor-pane { @apply flex-1 min-w-0 p-5 bg-white; }
.editor-head { @apply flex items-center justify-between mb-3; }
.editor-head h3 { @apply text-[15px] font-bold text-ink-strong; }
.editor-head-title { @apply text-[15px] font-bold text-ink-strong mb-4; }
.editor-area {
  @apply w-full min-h-[320px] rounded-xl border border-line-soft px-4 py-3 text-[14px] leading-relaxed resize-y;
}
.editor-tip { @apply text-[12px] text-brand-600 mt-2; }

.preview-pane { @apply w-72 border-l border-line-soft bg-surface-soft/50 p-4; }
.preview-title { @apply text-[12px] font-bold text-ink-muted uppercase mb-3; }
.preview-doc { @apply bg-white rounded-xl border border-line-soft p-4 text-[12px]; }
.preview-doc-title { @apply font-bold text-ink-strong mb-3 pb-2 border-b border-line-soft; }
.preview-section { @apply mb-3; }
.preview-h { @apply font-semibold text-ink-muted mb-0.5; }
.preview-p { @apply text-ink-faint leading-relaxed; }

.check-list { @apply space-y-2; }
.check-row { @apply flex gap-3 rounded-xl border px-4 py-3 text-[14px]; }
.check-row--ok { @apply border-emerald-100 bg-emerald-50/60; }
.check-row--miss { @apply border-amber-100 bg-amber-50/50; }
.check-name { @apply font-semibold text-ink-strong; }
.check-msg { @apply text-[12px] text-ink-muted; }
.check-result { @apply rounded-xl border border-line-soft p-4 bg-surface-soft/50; }

.empty-state { @apply text-sm text-ink-muted; }
</style>
