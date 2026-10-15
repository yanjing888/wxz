<template>
  <div class="student-shell min-h-screen bg-bg-base flex flex-col">
    <StudentShellHeader />

    <main class="flex-1 min-h-0 flex flex-col">
      <div class="tool-head shrink-0 px-6 py-4 bg-white border-b border-line-soft">
        <div class="tool-head-inner">
          <div>
            <h1 class="text-[18px] font-bold text-ink-strong">{{ tool?.name || 'AI 助手' }}</h1>
            <p class="text-[13px] text-ink-muted mt-1">{{ tool?.description || '' }}</p>
          </div>
          <button type="button" class="btn-ghost px-3 py-2 rounded-xl text-xs font-semibold" @click="startNewConversation">
            新对话
          </button>
        </div>
      </div>

      <div v-if="bootError" class="flex-1 flex items-center justify-center px-6">
        <p class="text-sm text-red-600">{{ bootError }}</p>
      </div>

      <div v-else class="flex-1 min-h-0 flex flex-col bg-white">
        <ChatBox
          :messages="messages"
          :loading="sending"
          :student-name="auth.displayName || auth.username"
          :welcome-subtitle="tool?.description || ''"
          :enable-feedback="false"
        />
        <Composer
          :suggestions="tool?.suggestions || []"
          :read-only="sending || !conversationId"
          :loading="sending"
          @send="sendMessage"
        />
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import StudentShellHeader from '../components/layout/StudentShellHeader.vue'
import ChatBox from '../components/chat/ChatBox.vue'
import Composer from '../components/chat/Composer.vue'
import { aiToolApi } from '../api'
import { postSse } from '../api/sse'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()

const toolCode = computed(() => route.params.code)
const tool = ref(null)
const conversationId = ref(null)
const messages = ref([])
const sending = ref(false)
const bootError = ref('')
const assistAbort = ref(null)

onMounted(() => bootstrap())
watch(toolCode, () => bootstrap())

async function bootstrap() {
  bootError.value = ''
  messages.value = []
  conversationId.value = null
  try {
    const { data: tools } = await aiToolApi.tools()
    tool.value = (tools || []).find((item) => item.code === toolCode.value)
    if (!tool.value) {
      bootError.value = '功能不存在'
      return
    }
    const { data: conversations } = await aiToolApi.conversations(toolCode.value)
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
  const { data } = await aiToolApi.createConversation(toolCode.value)
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

  if (assistAbort.value) {
    assistAbort.value.abort()
  }
  const abortCtrl = new AbortController()
  assistAbort.value = abortCtrl
  sending.value = true

  messages.value.push({ role: 'user', text: prompt, ts: Date.now() })
  const aiIndex = messages.value.length
  messages.value.push({ role: 'ai', text: '', streaming: true, ts: Date.now() })

  try {
    await postSse(
      `/api/ai/conversations/${conversationId.value}/chat/stream`,
      { userMessage: prompt },
      {
        onChunk: (chunk) => {
          messages.value[aiIndex].text += chunk
        },
        onAnswerEnd: () => {
          messages.value[aiIndex].streaming = false
        },
        onDone: (data) => {
          messages.value[aiIndex].streaming = false
          if (data?.feedback) {
            messages.value[aiIndex].text = data.feedback
          }
          if (data?.aiMessageId) {
            messages.value[aiIndex].id = data.aiMessageId
          }
        },
        onError: (msg) => {
          messages.value[aiIndex].streaming = false
          if (!messages.value[aiIndex].text) {
            messages.value[aiIndex].text = `**请求失败**：${msg}`
          }
        }
      },
      abortCtrl.signal
    )
    return true
  } catch (e) {
    if (!abortCtrl.signal.aborted) {
      messages.value[aiIndex].streaming = false
      if (!messages.value[aiIndex].text) {
        messages.value[aiIndex].text = `**请求失败**：${e.message || '网络异常'}`
      }
    }
    return false
  } finally {
    sending.value = false
    if (assistAbort.value === abortCtrl) {
      assistAbort.value = null
    }
    if (messages.value[aiIndex]) {
      messages.value[aiIndex].streaming = false
    }
  }
}
</script>

<style scoped>
.tool-head-inner {
  @apply w-full max-w-[960px] mx-auto flex items-center justify-between gap-4;
}
</style>
