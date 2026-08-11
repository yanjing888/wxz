<template>
  <StagePanel
    title="思考题"
    desc="报告末尾的思考题，先看思路再自己作答——这里不会给可以照抄的答案。"
    empty-title="把思考题贴进来"
    empty-hint="左侧输入讲义或报告末尾的思考题，会得到考查点、分析思路与常见错误答法。"
  >
    <template #input>
      <section>
        <label class="field-label">思考题题目</label>
        <textarea
          v-model="question"
          class="field-textarea"
          rows="6"
          placeholder="如：牛顿环实验中，若平凸透镜与平板玻璃之间有灰尘，对测量结果有何影响？"
        />
        <p class="field-hint">一次贴一道题，思路会更聚焦。</p>
      </section>

      <section>
        <label class="field-label">你目前的想法（可选）</label>
        <textarea
          v-model="myThought"
          class="field-textarea"
          rows="3"
          placeholder="写下你已经想到的部分，回答会针对性地补充你缺的环节"
        />
      </section>

      <button
        type="button"
        class="btn-brand w-full py-2.5 rounded-xl text-sm font-semibold"
        :disabled="loading || !question.trim()"
        @click="ask"
      >
        {{ loading ? '分析中…' : '给我思路' }}
      </button>
      <p v-if="error" class="err-text">{{ error }}</p>

      <section v-if="historyItems.length" class="history">
        <div class="flex items-center justify-between mb-2">
          <h3 class="field-label mb-0">本实验已问过（{{ historyItems.length }}）</h3>
          <button type="button" class="link-btn" @click="clearHistory">清空</button>
        </div>
        <ul class="history-list">
          <li v-for="(item, i) in historyItems" :key="i">
            <button type="button" class="history-btn" @click="restore(item)">{{ item.question }}</button>
          </li>
        </ul>
      </section>
    </template>

    <template v-if="answer" #result>
      <h3 class="result-title">解题思路</h3>
      <div class="chat-md" v-html="renderMd(answer)" />
      <div class="answer-box">
        <label class="field-label">我的作答</label>
        <textarea
          v-model="myAnswer"
          class="field-textarea"
          rows="6"
          placeholder="按上面的思路写出你自己的回答，写完可复制到报告里"
        />
        <button type="button" class="btn-ghost px-4 py-2 rounded-lg text-[13px] mt-2" :disabled="!myAnswer.trim()" @click="copyAnswer">
          复制我的作答
        </button>
      </div>
    </template>
  </StagePanel>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import StagePanel from './StagePanel.vue'
import { useAgentTool } from '../../composables/useAgentTool'
import { renderChatMarkdown } from '../../utils/markdown'

const props = defineProps({
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' },
  sessionId: { type: [Number, String], default: null }
})

const { loading, error, invoke } = useAgentTool('think-questions', () => props.experimentCode)

const question = ref('')
const myThought = ref('')
const myAnswer = ref('')
const answer = ref('')
const historyItems = ref([])

const storageKey = computed(() => (props.experimentCode ? `wxz_think_${props.experimentCode}` : ''))

watch(() => props.experimentCode, restoreHistory, { immediate: true })

function restoreHistory() {
  historyItems.value = []
  question.value = ''
  answer.value = ''
  myAnswer.value = ''
  if (!storageKey.value) return
  try {
    historyItems.value = JSON.parse(localStorage.getItem(storageKey.value) || '[]')
  } catch {
    historyItems.value = []
  }
}

function persistHistory() {
  if (storageKey.value) localStorage.setItem(storageKey.value, JSON.stringify(historyItems.value.slice(0, 20)))
}

async function ask() {
  answer.value = ''
  try {
    const data = await invoke('think', {
      experimentName: props.experimentName,
      sessionId: props.sessionId ? Number(props.sessionId) : null,
      question: question.value.trim(),
      studentThought: myThought.value.trim()
    })
    answer.value = data.text || ''
    const entry = { question: question.value.trim(), answer: answer.value, myAnswer: myAnswer.value }
    historyItems.value = [entry, ...historyItems.value.filter((i) => i.question !== entry.question)]
    persistHistory()
  } catch {
    // error 已由 composable 记录
  }
}

function restore(item) {
  question.value = item.question
  answer.value = item.answer
  myAnswer.value = item.myAnswer || ''
}

function clearHistory() {
  historyItems.value = []
  persistHistory()
}

function copyAnswer() {
  navigator.clipboard?.writeText(myAnswer.value).catch(() => {})
}

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}
</script>

<style scoped>
.field-label { @apply block text-[13px] font-bold text-ink-strong mb-2; }
.field-textarea { @apply w-full rounded-lg border border-line-soft px-3 py-2 text-[14px] leading-relaxed; }
.field-hint { @apply text-[12px] text-ink-faint mt-1.5; }
.err-text { @apply text-[12px] text-rose-600; }
.link-btn { @apply text-[12px] text-brand-600 hover:underline; }

.history-list { @apply space-y-1.5; }
.history-btn {
  @apply w-full text-left text-[12.5px] text-ink-muted rounded-lg border border-line-soft
    px-2.5 py-1.5 truncate hover:border-brand-200 hover:text-ink-strong transition-colors;
}

.result-title { @apply text-[15px] font-bold text-ink-strong mb-3; }
.answer-box { @apply mt-5 pt-4 border-t border-line-soft; }
</style>
