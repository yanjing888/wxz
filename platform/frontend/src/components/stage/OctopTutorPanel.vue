<template>
  <StagePanel layout="chat" :title="embedded ? '' : '专家顾问'" :desc="embedded ? '' : '围绕物理实验，讨论原理、操作与数据分析。'">
    <template v-if="!embedded" #actions>
      <select v-if="conversations.length" aria-label="专家顾问历史对话" :value="conversationId" :disabled="sending || restoring" class="expert-history" @change="restoreConversation($event.target.value)">
        <option v-if="!conversations.some(item => item.id === conversationId)" :value="conversationId">新对话</option>
        <option v-for="item in conversations" :key="item.id" :value="item.id">{{ item.title }}</option>
      </select>
      <button type="button" class="btn-ghost px-3 py-1.5 text-sm" :disabled="sending || restoring" @click="startConversation">
        新对话
      </button>
    </template>
    <div v-if="historyError" role="alert" class="px-4 py-2 text-sm text-rose-600">
      {{ historyError }} <button type="button" class="underline" @click="loadHistory">重试</button>
    </div>
    <p v-if="restoring" role="status" class="px-4 py-2 text-sm text-ink-muted">正在恢复历史对话…</p>
    <ChatBox
      :messages="messages"
      :student-name="studentName"
      welcome-subtitle="可以围绕实验原理、仪器操作、数据处理和误差分析展开交流。"
      :enable-feedback="false"
      :assistant-avatar="tutorAvatar"
      rich-reply
    >
      <template #activity="{ message }">
        <details v-if="message.activity?.length" class="tutor-activity" :open="message.activityOpen ?? message.streaming"
          @toggle="onActivityToggle(message, $event)">
          <summary>
            {{ message.streaming ? '正在处理' : '处理记录' }}
            <span v-if="message.thinkingCount"> · {{ message.thinkingCount }} 次深度思考</span>
            <span v-if="message.toolCount"> · {{ message.toolCount }} 次工具调用</span>
          </summary>
          <div class="tutor-trace">
            <template v-for="entry in message.activity" :key="entry.id">
              <div v-if="entry.kind === 'thinking'" class="tutor-reasoning">{{ entry.text }}</div>
              <div v-else class="tutor-tool">
                <span>{{ entry.label }}</span>
                <span>{{ entry.status }}<template v-if="entry.detail"> · {{ entry.detail }}</template></span>
              </div>
            </template>
          </div>
        </details>
      </template>
    </ChatBox>
    <Composer
      text-only
      :read-only="readOnly || sending || restoring || !!historyError"
      :loading-assist="sending"
      :suggestions="suggestions"
      @send="send"
      @stop="request?.abort()"
    >
      <template #actions>
        <div class="tutor-mode" @keydown.esc.stop="closeMode(true)" @focusout="onModeFocusOut">
          <button
            ref="modeButton"
            type="button"
            class="tutor-mode-trigger"
            :disabled="readOnly || sending"
            :aria-expanded="modeOpen"
            :aria-label="`对话模式：${activeMode.label}`"
            @click="modeOpen = !modeOpen"
          >
            <svg aria-hidden="true" viewBox="0 0 24 24"><path :d="activeMode.icon" /></svg>
            {{ activeMode.label }}
            <svg aria-hidden="true" viewBox="0 0 24 24"><path d="m6 15 6-6 6 6" /></svg>
          </button>
          <fieldset v-if="modeOpen" class="tutor-mode-options" aria-label="对话模式">
            <label v-for="mode in modes" :key="mode.value" class="tutor-mode-option">
              <input v-model="conversationMode" type="radio" :value="mode.value" @change="closeMode(true)">
              <svg aria-hidden="true" viewBox="0 0 24 24"><path :d="mode.icon" /></svg>
              <span><strong>{{ mode.label }}</strong><small>{{ mode.hint }}</small></span>
            </label>
          </fieldset>
        </div>
      </template>
    </Composer>
  </StagePanel>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import http from '../../api/http'
import { postSse } from '../../api/sse'
import { apiUrl } from '../../api/runtime'
import ChatBox from '../chat/ChatBox.vue'
import Composer from '../chat/Composer.vue'
import StagePanel from './StagePanel.vue'
import tutorAvatar from './octop-coding-coach.svg'

const props = defineProps({
  embedded: Boolean,
  readOnly: Boolean,
  studentName: String
})

const emit = defineEmits(['busy', 'history-state'])

const modes = [
  { value: 'craft', label: 'Craft 模式', hint: '使用专家配置的工具、技能和连接器。', icon: 'm14 12-8.5 8.5-2-2L12 10m-3-5 3-3 4 4 2-1 4 4-3 3-4-4-2 1z' },
  { value: 'plan', label: 'Plan 模式', hint: '先只读探索，再把计划写入 plans/。', icon: 'M9 5h11M9 12h11M9 19h11M3 5l1 1 2-2M3 12l1 1 2-2M3 19l1 1 2-2' },
  { value: 'ask', label: 'Ask 模式', hint: '只读问答，可使用获准的查询工具。', icon: 'M20 11a8 8 0 0 1-8 8 9 9 0 0 1-3-.5L4 20l1.5-5A8 8 0 1 1 20 11Zm-10-2a2 2 0 1 1 3 1.7c-.7.4-1 1-1 1.8m0 2.5h.01' }
]
const conversationMode = ref('craft')
const activeMode = computed(() => modes.find((mode) => mode.value === conversationMode.value))
const modeOpen = ref(false)
const modeButton = ref(null)

function closeMode(restoreFocus) {
  modeOpen.value = false
  if (restoreFocus) modeButton.value?.focus()
}

function onModeFocusOut(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) closeMode(false)
}

const suggestions = ['如何理解当前实验的物理原理？', '实验数据应该怎样处理和分析误差？']
const messages = ref([])
const sending = ref(false)
watch(sending, (busy) => emit('busy', busy))
let request
const conversationId = ref(newConversationId())
const activeConversationKey = `wxz_expert_conversation:${localStorage.getItem('wxz_userId') || 'guest'}`
const conversations = ref([])
const restoring = ref(true)
const historyError = ref('')
watch([conversations, conversationId, restoring, sending], () => {
  emit('history-state', { items: conversations.value, currentId: conversationId.value,
    loading: restoring.value, busy: sending.value || restoring.value })
}, { immediate: true })
defineExpose({ startConversation, restoreConversation })

onMounted(loadHistory)

async function loadHistory() {
  restoring.value = true
  historyError.value = ''
  try {
    const { data } = await http.get('/api/ai/octop-preview/conversations')
    conversations.value = data
    let activeId
    try { activeId = localStorage.getItem(activeConversationKey) } catch { /* Optional selection cache. */ }
    if (activeId && /^[0-9a-f-]{36}$/i.test(activeId)) {
      if (data.some(item => item.id === activeId)) await restoreConversation(activeId)
      else { conversationId.value = activeId; messages.value = [] }
    } else if (data.length) await restoreConversation(data[0].id)
  } catch {
    historyError.value = '历史对话加载失败，请重试。'
  } finally {
    restoring.value = false
  }
}

async function restoreConversation(id) {
  if (sending.value) return
  restoring.value = true
  historyError.value = ''
  try {
    const { data } = await http.get(`/api/ai/octop-preview/conversations/${id}`)
    const restored = data.messages.map(message => {
      const answer = { ...message, streaming: false, activity: [], thinkingCount: 0, toolCount: 0 }
      for (const status of message.statuses || []) updateActivity(answer, status)
      for (const entry of answer.activity) {
        if (entry.status === '运行中') entry.status = '未收到执行结果'
      }
      if (message.streaming) answer.text += '\n\n回复在页面离开前尚未完成，以上为已保存的内容。'
      return answer
    })
    conversationId.value = data.id
    rememberConversation()
    messages.value = restored
    conversationMode.value = modes.some(mode => mode.value === data.mode) ? data.mode : 'craft'
  } catch {
    historyError.value = '恢复对话失败，请重试。'
  } finally {
    restoring.value = false
  }
}

onBeforeUnmount(() => request?.abort())

function newConversationId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 15) | 64
  bytes[8] = (bytes[8] & 63) | 128
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-')
}

function startConversation() {
  if (sending.value || restoring.value) return
  conversationId.value = newConversationId()
  rememberConversation()
  messages.value = []
}

function rememberConversation() {
  try { localStorage.setItem(activeConversationKey, conversationId.value) } catch { /* History remains on the server. */ }
}

async function send(text) {
  const message = text.trim()
  if (!message || props.readOnly || sending.value || restoring.value || historyError.value) return false

  const controller = new AbortController()
  request = controller
  closeMode(false)
  sending.value = true
  messages.value.push({ role: 'user', text: message, ts: Date.now() })
  const index = messages.value.push({
    role: 'ai', text: '', streaming: true, ts: Date.now(),
    activity: [], thinkingCount: 0, toolCount: 0
  }) - 1
  const answer = messages.value[index]
  let done = false
  let failure = ''

  try {
    await postSse(apiUrl('/api/ai/octop-preview/chat/stream'), { message, conversationId: conversationId.value, conversationMode: conversationMode.value }, {
      onChunk: (chunk) => { answer.text += chunk },
      onStatus: (status) => updateActivity(answer, status),
      onDone: () => { done = true },
      onError: (detail) => { failure = detail }
    }, controller.signal)
    if (controller.signal.aborted) {
      answer.text += answer.text ? '\n\n已停止等待回复。' : '已停止等待回复。'
      return false
    }
    if (failure) throw new Error(failure)
    if (!done) throw new Error('回复连接提前断开，请重试')
    if (!answer.text.trim()) throw new Error('专家顾问本轮未返回回复，请重试')
    return true
  } catch (error) {
    const detail = controller.signal.aborted ? '已停止等待回复。' : `**请求失败**：${error.message || '网络异常'}`
    answer.text += answer.text ? `\n\n${detail}` : detail
    return false
  } finally {
    for (const entry of answer.activity) {
      if (entry.status === '运行中') entry.status = controller.signal.aborted ? '已停止等待' : '未收到执行结果'
    }
    answer.streaming = false
    request = null
    sending.value = false
    try {
      const { data } = await http.get('/api/ai/octop-preview/conversations')
      conversations.value = data
    } catch { /* Existing messages remain available if refreshing the list fails. */ }
  }
}
function onActivityToggle(answer, event) {
  const open = event.currentTarget.open
  if (open !== (answer.activityOpen ?? answer.streaming)) answer.activityOpen = open
}

function updateActivity(answer, { kind, number, text, label, detail }) {
  if (kind === 'thinking') {
    const id = `thinking-${number}`
    const block = answer.activity.find((entry) => entry.id === id)
    if (block) {
      block.text += text
    } else {
      answer.thinkingCount++
      answer.activity.push({ id, kind, text })
    }
    return
  }
  const id = `tool-${number}`
  if (kind === 'tool_started') {
    answer.toolCount++
    answer.activity.push({ id, kind, label: label || `工具 ${number}`, status: '运行中' })
    return
  }
  const entry = answer.activity.find((item) => item.id === id)
  if (!entry) return
  if (kind === 'tool_updated') {
    if (label) entry.label = label
    return
  }
  entry.status = kind === 'tool_completed' ? '已完成' : '失败'
  entry.detail = detail
}
</script>

<style scoped>
.expert-history { max-width: 180px; min-height: 34px; padding: 4px 8px; border: 1px solid #e1e5eb; border-radius: 7px; font-size: 12px; }
.tutor-activity {
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid #e6e8ed;
  border-radius: 9px;
  background: #f6f7f8;
  color: #4b5563;
  font-size: 13px;
}
.tutor-activity summary { cursor: pointer; font-weight: 600; }
.tutor-trace {
  display: grid;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #e8eaee;
}
.tutor-reasoning {
  padding: 10px 12px;
  border-radius: 8px;
  background: #eef0f3;
  color: #222a38;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.65;
}
.tutor-tool {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 10px;
  border: 1px solid #e6e8ed;
  border-radius: 8px;
}
.tutor-tool span:last-child { white-space: nowrap; color: #687385; }

.tutor-mode { position: relative; }
.tutor-mode-trigger, .tutor-mode-option { display: flex; align-items: center; gap: 8px; }
.tutor-mode-trigger {
  padding: 6px 9px;
  border: 1px solid #e1e5eb;
  border-radius: 7px;
  color: #475569;
  font-size: 12px;
}
.tutor-mode-trigger:disabled { opacity: .5; }
.tutor-mode svg {
  width: 16px;
  height: 16px;
  flex: none;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.tutor-mode-options {
  position: absolute;
  z-index: 5;
  bottom: calc(100% + 6px);
  left: 0;
  width: min(270px, calc(100vw - 44px));
  min-width: 0;
  padding: 5px;
  border: 1px solid #e1e5eb;
  border-radius: 9px;
  background: white;
  box-shadow: 0 4px 14px #24314b18;
}
.tutor-mode-option { padding: 7px 8px; border-radius: 5px; cursor: pointer; }
.tutor-mode-option:hover, .tutor-mode-option:focus-within { background: #f2f4f7; }
.tutor-mode-option strong, .tutor-mode-option small { display: block; }
.tutor-mode-option strong { color: #263242; font-size: 13px; }
.tutor-mode-option small { color: #526173; font-size: 12px; }
</style>
