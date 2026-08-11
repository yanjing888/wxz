<template>
  <StagePanel layout="chat" :title="title" :desc="desc">
    <template #actions>
      <button type="button" class="btn-ghost px-3 py-1.5 rounded-lg text-[13px] font-semibold" @click="startNewConversation">
        新对话
      </button>
    </template>

    <div v-if="bootError" class="flex-1 flex items-center justify-center px-6">
      <p class="text-sm text-rose-600">{{ bootError }}</p>
    </div>
    <div v-else class="flex-1 min-h-0 flex flex-col bg-white max-w-[960px] mx-auto w-full">
      <ChatBox
        :messages="messages"
        :loading="sending"
        :student-name="auth.displayName || auth.username"
        :welcome-subtitle="welcomeSubtitle"
        :enable-feedback="false"
      />
      <Composer
        :suggestions="suggestions"
        :read-only="sending || !conversationId"
        :loading="sending"
        @send="sendMessage"
      />
    </div>
  </StagePanel>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import StagePanel from './StagePanel.vue'
import ChatBox from '../chat/ChatBox.vue'
import Composer from '../chat/Composer.vue'
import { aiToolApi } from '../../api'
import { postSse } from '../../api/sse'
import { useAuthStore } from '../../stores/auth'

const props = defineProps({
  toolCode: { type: String, default: 'explore' },
  title: { type: String, default: '原理答疑' },
  desc: { type: String, default: '围绕实验原理、公式推导与仪器结构提问，回答会结合本实验讲义。' },
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' },
  suggestions: { type: Array, default: () => [] }
})

const auth = useAuthStore()

const conversationId = ref(null)
const messages = ref([])
const sending = ref(false)
const bootError = ref('')
const assistAbort = ref(null)

const welcomeSubtitle = computed(() =>
  props.experimentName ? `围绕「${props.experimentName}」提问，回答会结合本实验讲义。` : props.desc
)

onMounted(bootstrap)
watch(() => props.toolCode, bootstrap)

async function bootstrap() {
  bootError.value = ''
  messages.value = []
  conversationId.value = null
  try {
    const { data: conversations } = await aiToolApi.conversations(props.toolCode)
    if (conversations?.length) {
      conversationId.value = conversations[0].id
      await loadMessages()
    } else {
      await startNewConversation()
    }
  } catch (e) {
    bootError.value = e.response?.data?.message || e.message || '加载失败'
  }
}

async function startNewConversation() {
  const { data } = await aiToolApi.createConversation(props.toolCode)
  conversationId.value = data.id
  messages.value = []
}

async function loadMessages() {
  if (!conversationId.value) return
  const { data } = await aiToolApi.messages(conversationId.value)
  messages.value = (data || []).map((msg) => ({
    id: msg.id,
    role: msg.role === 'user' ? 'user' : 'ai',
    text: msg.text || '',
    ts: msg.createdAt ? Date.parse(msg.createdAt) || Date.now() : Date.now()
  }))
}

async function sendMessage(text) {
  const prompt = (text || '').trim()
  if (!prompt || !conversationId.value || sending.value) return false

  if (assistAbort.value) assistAbort.value.abort()
  const abortCtrl = new AbortController()
  assistAbort.value = abortCtrl
  sending.value = true

  messages.value.push({ role: 'user', text: prompt, ts: Date.now() })
  const aiIndex = messages.value.length
  messages.value.push({ role: 'ai', text: '', streaming: true, ts: Date.now() })

  try {
    await postSse(
      `/api/ai/conversations/${conversationId.value}/chat/stream`,
      { userMessage: prompt, experimentCode: props.experimentCode || null },
      {
        onChunk: (chunk) => { messages.value[aiIndex].text += chunk },
        onAnswerEnd: () => { messages.value[aiIndex].streaming = false },
        onDone: (data) => {
          messages.value[aiIndex].streaming = false
          if (data?.feedback) messages.value[aiIndex].text = data.feedback
          if (data?.aiMessageId) messages.value[aiIndex].id = data.aiMessageId
        },
        onError: (msg) => {
          messages.value[aiIndex].streaming = false
          if (!messages.value[aiIndex].text) messages.value[aiIndex].text = `**请求失败**：${msg}`
        }
      },
      abortCtrl.signal
    )
    return true
  } catch (e) {
    if (!abortCtrl.signal.aborted && messages.value[aiIndex] && !messages.value[aiIndex].text) {
      messages.value[aiIndex].text = `**请求失败**：${e.message || '网络异常'}`
    }
    return false
  } finally {
    sending.value = false
    if (assistAbort.value === abortCtrl) assistAbort.value = null
    if (messages.value[aiIndex]) messages.value[aiIndex].streaming = false
  }
}
</script>
