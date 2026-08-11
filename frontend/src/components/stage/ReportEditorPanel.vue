<template>
  <StagePanel layout="chat" title="报告教练" desc="基于真实实验记录起稿、润色与查缺漏；思考题结论请自己完成。">
    <template #actions>
      <button
        type="button"
        class="btn-ghost px-3 py-1.5 rounded-lg text-[13px]"
        :disabled="!sessionId || generating"
        @click="generateFromSession"
      >
        {{ generating ? '填充中…' : '重新填充' }}
      </button>
      <button type="button" class="btn-ghost px-3 py-1.5 rounded-lg text-[13px]" :disabled="!hasContent" @click="copyFullReport">
        复制全文
      </button>
      <button type="button" class="btn-brand px-4 py-1.5 rounded-lg text-[13px] font-semibold" :disabled="!sessionId || exporting" @click="downloadDocx">
        {{ exporting ? '导出中…' : '导出 Word' }}
      </button>
      <button type="button" class="btn-ghost px-3 py-1.5 rounded-lg text-[13px]" :disabled="!hasContent || archiving" @click="saveToLibrary">
        {{ archiving ? '存档中…' : '存入资料库' }}
      </button>
    </template>

    <div v-if="!sessionId" class="empty-state flex-1 flex items-center justify-center">
      <div class="text-center max-w-md px-6">
        <p class="text-ink-strong font-semibold mb-2">还没有可用的实验记录</p>
        <p class="text-[13px] text-ink-muted">
          请先到「实验操作」完成本实验并点击生成报告结束会话，系统会自动填入步骤、数据与知识要点。
        </p>
      </div>
    </div>

    <div v-else class="ws-body flex-1 min-h-0 flex overflow-hidden">
      <nav class="section-nav custom-scroll shrink-0">
        <button
          v-for="def in sectionDefs"
          :key="def.key"
          type="button"
          class="nav-item"
          :class="{
            'nav-item--active': activeSection === def.key,
            'nav-item--filled': (form[def.key] || '').trim().length >= 10
          }"
          @click="activeSection = def.key"
        >
          {{ def.label }}
        </button>
        <div class="nav-divider" />
        <button
          type="button"
          class="nav-item nav-item--check"
          :class="{ 'nav-item--active': activeSection === 'check' }"
          @click="activeSection = 'check'"
        >
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
            提示：在「数据处理 → 计算与作图」中算完后，复制生成的段落粘贴到此处。
          </p>
        </template>

        <template v-else>
          <h3 class="editor-head-title">报告规范检查</h3>
          <ul class="check-list">
            <li
              v-for="item in checkItems"
              :key="item.key"
              class="check-row"
              :class="item.ok ? 'check-row--ok' : 'check-row--miss'"
            >
              <span>{{ item.ok ? '✓' : '○' }}</span>
              <div>
                <p class="check-name">{{ item.label }}</p>
                <p class="check-msg">{{ item.message }}</p>
              </div>
            </li>
          </ul>
          <button
            type="button"
            class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold mt-4"
            :disabled="checkLoading || !hasContent"
            @click="runAiCheck"
          >
            {{ checkLoading ? '检查中…' : 'AI 深度检查' }}
          </button>
          <div v-if="checkResult" class="check-result chat-md mt-4" v-html="renderMd(checkResult)" />
        </template>
      </main>

      <aside class="preview-pane custom-scroll shrink-0 hidden lg:block">
        <h4 class="preview-title">报告预览</h4>
        <div class="preview-doc">
          <p class="preview-doc-title">{{ experimentName || '实验报告' }}</p>
          <div v-for="def in sectionDefs" :key="def.key" class="preview-section">
            <p class="preview-h">{{ def.label }}</p>
            <p class="preview-p">
              {{ (form[def.key] || '（未填写）').slice(0, 200) }}{{ (form[def.key] || '').length > 200 ? '…' : '' }}
            </p>
          </div>
        </div>
      </aside>
    </div>
  </StagePanel>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import StagePanel from './StagePanel.vue'
import { aiToolApi, sessionApi, studentFileApi } from '../../api'
import { renderChatMarkdown } from '../../utils/markdown'
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
const emit = defineEmits(['saved'])

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
const activeSection = ref('purpose')
const generating = ref(false)
const exporting = ref(false)
const archiving = ref(false)
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

watch(() => props.sessionId, onSessionChange, { immediate: true })

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
      if (saved[k]) form[k] = saved[k]
    })
    return true
  } catch {
    return false
  }
}

async function onSessionChange() {
  checkResult.value = ''
  Object.keys(form).forEach((k) => { form[k] = '' })
  if (!props.sessionId) return
  if (loadDraft()) return
  await generateFromSession()
}

async function generateFromSession() {
  if (!props.sessionId) return
  generating.value = true
  try {
    const { data: report } = await sessionApi.report(props.sessionId)
    const built = buildReportSections(report)
    Object.keys(form).forEach((k) => { form[k] = built[k] || '' })
    saveDraft()
  } finally {
    generating.value = false
  }
}

async function polishSection(key) {
  polishLoading.value = true
  try {
    const { data } = await aiToolApi.invoke('report-assist', {
      action: 'polish',
      inputs: {
        sessionId: Number(props.sessionId),
        experimentCode: props.experimentCode,
        experimentName: props.experimentName,
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
    const summary = checkItems.value.map((i) => `${i.ok ? '✓' : '✗'} ${i.label}`).join('\n')
    const { data } = await aiToolApi.invoke('report-assist', {
      action: 'check',
      inputs: {
        sessionId: Number(props.sessionId),
        experimentCode: props.experimentCode,
        experimentName: props.experimentName,
        localCheckSummary: summary,
        ...form
      }
    })
    checkResult.value = data.text || ''
  } finally {
    checkLoading.value = false
  }
}

function fullText() {
  return sectionsToFullText(form, props.experimentName)
}

function copyFullReport() {
  navigator.clipboard?.writeText(fullText()).catch(() => {})
}

async function downloadDocx() {
  if (!props.sessionId) return
  exporting.value = true
  try {
    const sections = sectionDefs.map((def) => ({ label: def.label, content: form[def.key] || '' }))
    const { data } = await sessionApi.studentReportDocx(props.sessionId, { sections })
    const url = URL.createObjectURL(data)
    const a = document.createElement('a')
    a.href = url
    a.download = `${props.experimentName || '实验'}-实验报告.docx`
    a.click()
    URL.revokeObjectURL(url)
  } finally {
    exporting.value = false
  }
}

async function saveToLibrary() {
  archiving.value = true
  try {
    const name = `${props.experimentName || '实验'}-实验报告.txt`
    const file = new File([fullText()], name, { type: 'text/plain' })
    await studentFileApi.upload(file, {
      experimentCode: props.experimentCode,
      category: 'report',
      stage: 'report',
      sessionId: props.sessionId || '',
      note: '实验报告'
    })
    emit('saved')
  } finally {
    archiving.value = false
  }
}

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}
</script>

<style scoped>
.ws-body { @apply min-h-0 bg-surface-soft/30; }
.section-nav { @apply w-40 shrink-0 border-r border-line-soft bg-white py-3 overflow-y-auto; }
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

.preview-pane { @apply w-64 border-l border-line-soft bg-surface-soft/50 p-4; }
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

.empty-state { @apply text-sm text-ink-muted bg-surface-soft/30; }
</style>
