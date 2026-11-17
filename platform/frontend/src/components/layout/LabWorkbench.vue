<template>
  <section class="workbench flex-1 min-h-0 flex flex-col overflow-hidden">
    <div class="wb-toolbar shrink-0">
    <nav class="wb-tabs" aria-label="实验台侧栏">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="wb-tab"
        :class="{ 'wb-tab--active': modelValue === tab.key }"
        @click="$emit('update:modelValue', tab.key)"
      >
        {{ tab.label }}
      </button>
    </nav>
    </div>

    <div class="wb-body flex-1 min-h-0 flex flex-col overflow-hidden">
      <div v-show="modelValue === 'guide'" class="flex-1 min-h-0 flex flex-col overflow-hidden">
        <div v-if="octopAvailable" class="wb-assistant shrink-0">
          <AssistantPicker v-model="provider" :disabled="loadingAssist || submittingData || uploadingImage || octopSending" />
        </div>
        <RightPanel
          class="flex-1 min-h-0"
          :show-header="false"
          :messages="messages"
          :loading-assist="loadingAssist"
          :submitting-data="submittingData"
          :uploading-image="uploadingImage"
          :image-preview="imagePreview"
          :image-ready="imageReady"
          :data-attachment="dataAttachment"
          :experiment-name="experimentName"
          :step-title="stepTitle"
          :student-name="studentName"
          :suggestions="suggestions"
          :read-only="readOnly"
          :qa-locked="qaLocked"
          :session-history="usingOctop ? expertHistory.items : sessionHistory"
          :current-session-id="usingOctop ? expertHistory.currentId : currentSessionId"
          :session-history-loading="usingOctop ? expertHistory.loading : sessionHistoryLoading"
          :alternative-active="usingOctop"
          :history-archive-enabled="!usingOctop"
          :history-busy="usingOctop ? expertHistory.busy : loadingAssist"
          @new-session="usingOctop ? expertPanel?.startConversation() : $emit('new-session')"
          @select-session="usingOctop ? expertPanel?.restoreConversation($event.id) : $emit('select-session', $event)"
          @archive-session="$emit('archive-session', $event)"
          @send="$emit('send', $event)"
          @stop="$emit('stop')"
          @upload-image="$emit('upload-image', $event)"
          @capture-image="$emit('capture-image')"
          @clear-image="$emit('clear-image')"
          @clear-data="$emit('clear-data')"
        >
          <template #alternative>
            <OctopTutorPanel
              v-if="octopAvailable"
              ref="expertPanel"
              embedded
              :read-only="readOnly || qaLocked"
              :student-name="studentName"
              @busy="octopSending = $event"
              @history-state="expertHistory = $event"
            />
          </template>
        </RightPanel>
      </div>

      <LabSessionDataPanel
        v-if="modelValue === 'data'"
        :session-id="sessionId"
        :session-data-revision="sessionDataRevision"
        :read-only="readOnly"
        @deleted="$emit('data-deleted')"
      />

      <ChatRecordPanel
        v-else-if="modelValue === 'record'"
        :key="currentSessionId"
        :session-id="currentSessionId"
      />
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import http from '../../api/http'
import { useAssistantChoice } from '../../composables/useAssistantChoice'
import OctopTutorPanel from '../stage/OctopTutorPanel.vue'
import AssistantPicker from '../chat/AssistantPicker.vue'
import RightPanel from './RightPanel.vue'
import ChatRecordPanel from '../lab/ChatRecordPanel.vue'
import LabSessionDataPanel from '../lab/LabSessionDataPanel.vue'

defineProps({
  modelValue: { type: String, default: 'guide' },
  dataCount: { type: Number, default: 0 },
  sessionId: { type: Number, default: 0 },
  sessionDataRevision: { type: Number, default: 0 },
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' },
  stepTitle: { type: String, default: '' },
  studentName: { type: String, default: '' },
  messages: { type: Array, default: () => [] },
  loadingAssist: { type: Boolean, default: false },
  submittingData: { type: Boolean, default: false },
  uploadingImage: { type: Boolean, default: false },
  imagePreview: { type: String, default: '' },
  imageReady: { type: Boolean, default: false },
  dataAttachment: { type: Object, default: null },
  suggestions: { type: Array, default: () => [] },
  readOnly: { type: Boolean, default: false },
  qaLocked: { type: Boolean, default: false },
  sessionHistory: { type: Array, default: () => [] },
  currentSessionId: { type: Number, default: 0 },
  sessionHistoryLoading: { type: Boolean, default: false }
})

defineEmits([
  'update:modelValue',
  'new-session',
  'select-session',
  'archive-session',
  'send',
  'stop',
  'upload-image',
  'capture-image',
  'clear-image',
  'clear-data',
  'data-deleted'
])

const octopAvailable = ref(false)
const provider = useAssistantChoice()
const octopSending = ref(false)
const expertPanel = ref(null)
const expertHistory = ref({ items: [], currentId: '', loading: true, busy: true })
const usingOctop = computed(() => octopAvailable.value && provider.value === 'octop')
onMounted(async () => {
  try {
    const { data } = await http.get('/api/ai/octop-preview')
    octopAvailable.value = data?.enabled === true
  } catch {
    // The Octop preview endpoint is optional.
  }
})

const tabs = [
  { key: 'guide', label: '物小智' },
  { key: 'data', label: '我的数据' },
  { key: 'record', label: '问答记录' }
]
</script>

<style scoped>
.workbench { @apply bg-white; }
.wb-toolbar { @apply flex items-center gap-2 px-3 border-b border-line-soft bg-white min-w-0; }
.wb-tabs {
  @apply flex flex-1 min-w-0 items-center gap-1 pt-1 overflow-x-auto;
}
.wb-assistant { @apply px-4 py-2 border-b border-line-soft bg-white; }
.wb-tab {
  @apply relative shrink-0 whitespace-nowrap px-3.5 py-2.5 text-[13px] font-semibold text-ink-muted
    hover:text-ink-strong transition-colors;
}
.wb-tab--active { @apply text-brand-700; }
.wb-tab--active::after {
  content: '';
  @apply absolute left-3 right-3 bottom-0 h-0.5 bg-brand-600 rounded-full;
}
.wb-body { @apply flex flex-col; }
@media (max-width: 480px) {
  .wb-toolbar { @apply px-2 gap-1; }
  .wb-tab { @apply px-2; }
}
</style>
