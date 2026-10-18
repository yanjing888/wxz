<template>
  <StagePanel
    layout="single"
    title="预习报告"
    desc="生成六段式框架后自行补充，草稿会自动保存在本机。"
  >
    <template #actions>
      <button
        type="button"
        class="btn-brand px-4 py-1.5 rounded-lg text-[13px] font-semibold"
        :disabled="loading || !experimentCode"
        @click="generate"
      >
        {{ loading ? '生成中…' : hasContent ? '重新生成初稿' : '生成初稿' }}
      </button>
      <button type="button" class="btn-ghost px-3 py-1.5 rounded-lg text-[13px]" :disabled="!hasContent" @click="copyAll">
        复制全文
      </button>
      <button type="button" class="btn-ghost px-3 py-1.5 rounded-lg text-[13px]" :disabled="!hasContent" @click="download">
        下载 .txt
      </button>
      <button type="button" class="btn-ghost px-3 py-1.5 rounded-lg text-[13px]" :disabled="!hasContent || saving" @click="saveToLibrary">
        {{ saving ? '存档中…' : '存入资料库' }}
      </button>
    </template>

    <p v-if="error" class="err-text">{{ error }}</p>
    <p v-if="notice" class="ok-text">{{ notice }}</p>

    <div v-if="!hasContent && !loading" class="empty-card">
      <p class="empty-title">还没有预习报告</p>
      <p class="empty-desc">
        点击「生成初稿」得到目的、原理、仪器、步骤、数据表设计与注意事项六段框架，
        再按自己的理解改写——直接照抄不会被认可。
      </p>
    </div>

    <section v-for="def in SECTIONS" :key="def.key" class="section-card">
      <div class="section-head">
        <h3 class="section-title">{{ def.label }}</h3>
        <span class="section-count">{{ (form[def.key] || '').length }} 字</span>
      </div>
      <textarea
        v-model="form[def.key]"
        class="section-area"
        :placeholder="def.placeholder"
        @input="persist"
      />
    </section>
  </StagePanel>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import StagePanel from './StagePanel.vue'
import { useAgentTool } from '../../composables/useAgentTool'
import { studentFileApi } from '../../api'
import { splitByHeadings } from '../../utils/aiTool'
import { downloadTextFile } from '../../utils/sessionReport'

const props = defineProps({
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' }
})
const emit = defineEmits(['saved'])

const SECTIONS = [
  { key: 'purpose', label: '实验目的', re: /目的|目标/, placeholder: '本次实验要验证或测量什么…' },
  { key: 'principle', label: '实验原理', re: /原理/, placeholder: '核心公式及各符号含义…' },
  { key: 'apparatus', label: '实验仪器', re: /仪器|器材|装置/, placeholder: '主要仪器名称、型号与精度…' },
  { key: 'procedure', label: '实验步骤', re: /步骤|过程|操作/, placeholder: '按操作顺序列出关键步骤…' },
  { key: 'dataTable', label: '数据记录表设计', re: /数据记录|记录表|数据表/, placeholder: '表头、单位与预计行数…' },
  { key: 'cautions', label: '注意事项', re: /注意|安全|事项/, placeholder: '容易出错或有安全风险的地方…' }
]

const { loading, error, invoke } = useAgentTool('prep-report', () => props.experimentCode)

const form = reactive(Object.fromEntries(SECTIONS.map((s) => [s.key, ''])))
const saving = ref(false)
const notice = ref('')

const hasContent = computed(() => SECTIONS.some((s) => (form[s.key] || '').trim()))

watch(() => props.experimentCode, restore, { immediate: true })

function storageKey() {
  return props.experimentCode ? `wxz_prep_report_${props.experimentCode}` : ''
}

function restore() {
  SECTIONS.forEach((s) => {
    form[s.key] = ''
  })
  notice.value = ''
  const key = storageKey()
  if (!key) return
  try {
    const saved = JSON.parse(localStorage.getItem(key) || 'null')
    if (saved) SECTIONS.forEach((s) => { form[s.key] = saved[s.key] || '' })
  } catch {
    // 忽略损坏的本地缓存
  }
}

function persist() {
  const key = storageKey()
  if (key) localStorage.setItem(key, JSON.stringify({ ...form }))
}

async function generate() {
  notice.value = ''
  try {
    const data = await invoke('prepReport', { experimentName: props.experimentName })
    const parsed = splitByHeadings(data.text || '', SECTIONS)
    const filled = SECTIONS.filter((s) => parsed[s.key])
    if (!filled.length) {
      form.purpose = data.text || ''
    } else {
      SECTIONS.forEach((s) => { form[s.key] = parsed[s.key] || form[s.key] })
    }
    persist()
  } catch {
    // error 已由 composable 记录
  }
}

function fullText() {
  const title = `${props.experimentName || '实验'} 预习报告`
  const body = SECTIONS.map((s) => `【${s.label}】\n${(form[s.key] || '').trim() || '（未填写）'}`).join('\n\n')
  return `${title}\n\n${body}\n`
}

function copyAll() {
  navigator.clipboard?.writeText(fullText()).catch(() => {})
  notice.value = '已复制到剪贴板'
}

function download() {
  downloadTextFile(`${props.experimentName || '实验'}-预习报告.txt`, fullText())
}

async function saveToLibrary() {
  saving.value = true
  notice.value = ''
  try {
    const name = `${props.experimentName || '实验'}-预习报告.txt`
    const file = new File([fullText()], name, { type: 'text/plain' })
    await studentFileApi.upload(file, {
      experimentCode: props.experimentCode,
      category: 'prep',
      stage: 'prepare',
      note: '预习报告'
    })
    notice.value = '已存入实验资料库'
    emit('saved')
  } catch (e) {
    error.value = e.message || '存档失败'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.err-text { @apply text-[13px] text-rose-600; }
.ok-text { @apply text-[13px] text-emerald-600; }
.empty-card { @apply rounded-2xl border border-dashed border-line-soft bg-white p-8 text-center; }
.empty-title { @apply text-[15px] font-semibold text-ink-strong; }
.empty-desc { @apply text-[13px] text-ink-muted mt-1.5 max-w-lg mx-auto leading-relaxed; }

.section-card { @apply rounded-2xl border border-line-soft bg-white p-4; }
.section-head { @apply flex items-center justify-between mb-2; }
.section-title { @apply text-[14px] font-bold text-ink-strong; }
.section-count { @apply text-[11.5px] text-ink-faint; }
.section-area {
  @apply w-full min-h-[110px] rounded-xl border border-line-soft px-3.5 py-2.5 text-[14px] leading-relaxed resize-y;
}
</style>
