<template>
  <StagePanel
    layout="single"
    title="器材清单核对"
    desc="进实验室后照着清单逐项确认，避免开做后才发现仪器不对。"
  >
    <template #actions>
      <button
        type="button"
        class="btn-brand px-4 py-1.5 rounded-lg text-[13px] font-semibold"
        :disabled="loading || !experimentCode"
        @click="generate"
      >
        {{ loading ? '生成中…' : items.length ? '重新生成' : '生成清单' }}
      </button>
    </template>

    <p v-if="error" class="err-text">{{ error }}</p>

    <div v-if="!items.length && !loading" class="empty-card">
      <p class="empty-title">还没有器材清单</p>
      <p class="empty-desc">点击右上角「生成清单」，AI 会依据本实验的步骤列出需要的仪器与检查要点。</p>
    </div>

    <template v-if="items.length">
      <div class="progress-card">
        <div class="min-w-0">
          <p class="progress-num">{{ checkedCount }} / {{ items.length }}</p>
          <p class="progress-label">{{ checkedCount === items.length ? '全部确认完毕，可以开始实验' : '逐项确认，勾选后会自动记住' }}</p>
        </div>
        <button type="button" class="btn-ghost px-4 py-2 rounded-xl text-[13px]" @click="clearChecks">重置勾选</button>
      </div>

      <ul class="item-list">
        <li v-for="(item, index) in items" :key="index" class="item-row" :class="{ 'item-row--done': checked[index] }">
          <label class="item-check">
            <input type="checkbox" :checked="checked[index]" @change="toggle(index)" />
          </label>
          <div class="min-w-0 flex-1">
            <p class="item-name">
              {{ item.name }}
              <span v-if="item.spec" class="item-spec">{{ item.spec }}</span>
              <span v-if="item.bySelf" class="item-tag">需自带</span>
            </p>
            <p v-if="item.purpose" class="item-purpose">用途：{{ item.purpose }}</p>
            <p v-if="item.checkPoint" class="item-check-point">检查：{{ item.checkPoint }}</p>
          </div>
        </li>
      </ul>

      <details v-if="rawText" class="raw-box">
        <summary>查看 AI 原始输出</summary>
        <div class="chat-md mt-3" v-html="renderMd(rawText)" />
      </details>
    </template>
  </StagePanel>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import StagePanel from './StagePanel.vue'
import { useAgentTool } from '../../composables/useAgentTool'
import { extractEquipment, parseStructuredData } from '../../utils/aiTool'
import { renderChatMarkdown } from '../../utils/markdown'

const props = defineProps({
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' }
})

const { loading, error, invoke } = useAgentTool('equipment-check', () => props.experimentCode)

const items = ref([])
const checked = ref([])
const rawText = ref('')

const checkedCount = computed(() => checked.value.filter(Boolean).length)

watch(() => props.experimentCode, restore, { immediate: true })

function storageKey() {
  return props.experimentCode ? `wxz_equipment_${props.experimentCode}` : ''
}

function restore() {
  items.value = []
  checked.value = []
  rawText.value = ''
  const key = storageKey()
  if (!key) return
  try {
    const saved = JSON.parse(localStorage.getItem(key) || 'null')
    if (saved?.items?.length) {
      items.value = saved.items
      checked.value = saved.checked || saved.items.map(() => false)
      rawText.value = saved.rawText || ''
    }
  } catch {
    // 忽略损坏的本地缓存
  }
}

function persist() {
  const key = storageKey()
  if (!key) return
  localStorage.setItem(key, JSON.stringify({
    items: items.value,
    checked: checked.value,
    rawText: rawText.value
  }))
}

async function generate() {
  try {
    const data = await invoke('equipment', { experimentName: props.experimentName })
    rawText.value = data.text || ''
    const parsed = extractEquipment(parseStructuredData(data))
    items.value = parsed
    checked.value = parsed.map(() => false)
    persist()
  } catch {
    // error 已由 composable 记录
  }
}

function toggle(index) {
  checked.value[index] = !checked.value[index]
  persist()
}

function clearChecks() {
  checked.value = items.value.map(() => false)
  persist()
}

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}
</script>

<style scoped>
.err-text { @apply text-[13px] text-rose-600; }
.empty-card { @apply rounded-2xl border border-dashed border-line-soft bg-white p-10 text-center; }
.empty-title { @apply text-[15px] font-semibold text-ink-strong; }
.empty-desc { @apply text-[13px] text-ink-muted mt-1.5 max-w-md mx-auto; }

.progress-card { @apply flex flex-wrap items-center justify-between gap-4 rounded-2xl bg-brand-50 border border-brand-100 p-5; }
.progress-num { @apply text-[22px] font-bold text-ink-strong leading-none; }
.progress-label { @apply text-[13px] text-ink-muted mt-1.5; }

.item-list { @apply space-y-2; }
.item-row { @apply flex gap-3 rounded-2xl border border-line-soft bg-white p-4; }
.item-row--done { @apply border-emerald-100 bg-emerald-50/40; }
.item-check { @apply shrink-0 pt-0.5; }
.item-check input { @apply w-4 h-4 accent-emerald-500 cursor-pointer; }
.item-name { @apply text-[14.5px] font-bold text-ink-strong; }
.item-spec { @apply ml-2 text-[12px] font-normal text-ink-muted; }
.item-tag { @apply ml-2 text-[11px] px-1.5 py-0.5 rounded bg-amber-50 text-amber-700 border border-amber-100 font-medium; }
.item-purpose { @apply text-[12.5px] text-ink-muted mt-1; }
.item-check-point { @apply text-[12.5px] text-brand-600 mt-0.5; }

.raw-box { @apply rounded-2xl border border-line-soft bg-white p-4 text-[13px] text-ink-muted; }
.raw-box summary { @apply cursor-pointer font-medium; }
</style>
