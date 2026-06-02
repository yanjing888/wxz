<template>
  <section class="flex-1 flex flex-col overflow-hidden relative min-h-0 surface-card rounded-2xl">
    <!-- 顶部状态条（紧凑版，适配窄栏） -->
    <div class="shrink-0 flex items-center justify-between px-3 py-2 border-b border-line-soft gap-2">
      <div class="flex items-center gap-2 min-w-0">
        <div class="w-7 h-7 rounded-lg brand-gradient-soft border border-brand-100 flex items-center justify-center text-brand-600 shrink-0">
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <rect x="3" y="6" width="18" height="14" rx="2.5" />
            <circle cx="12" cy="13" r="3.5" />
          </svg>
        </div>
        <div class="flex flex-col leading-tight min-w-0">
          <h2 class="text-xs font-bold text-ink-strong truncate">图像纠错</h2>
          <span class="text-[9px] text-ink-faint truncate">AI 自动标注错误点</span>
        </div>
      </div>
      <div class="flex items-center gap-1.5 shrink-0">
        <span v-if="marks.length && imagePreview" class="chip text-red-600 bg-red-50 border-red-200 !text-[9px] !px-1.5">
          <span class="w-1 h-1 rounded-full bg-red-500" />
          {{ marks.length }}处
        </span>
        <span v-else-if="imagePreview" class="chip text-emerald-600 bg-emerald-50 border-emerald-200 !text-[9px] !px-1.5">
          <span class="w-1 h-1 rounded-full bg-emerald-500" />
          已上传
        </span>
        <span v-else class="chip !text-[9px] !px-1.5">
          <span class="w-1 h-1 rounded-full bg-ink-faint" />
          等待
        </span>
        <button
          v-if="imagePreview"
          type="button"
          class="btn-ghost px-2 py-1 rounded-md text-[10px] font-semibold btn-active-scale"
          :disabled="uploadingImage"
          @click.stop="triggerUpload"
        >
          更换
        </button>
        <button
          v-if="imagePreview"
          type="button"
          class="w-6 h-6 flex items-center justify-center bg-white hover:bg-red-500 text-red-500 hover:text-white rounded-md border border-red-100 hover:border-red-500 transition-all btn-active-scale"
          title="清除图片"
          @click.stop="$emit('clear')"
        >
          <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
        </button>
      </div>
    </div>

    <!-- 主舞台 -->
    <div
      class="relative flex-1 min-h-0 m-2.5 rounded-xl overflow-hidden flex items-center justify-center transition-all"
      :class="imagePreview ? 'bg-white border border-line-soft' : 'upload-zone-empty border-2 border-dashed border-line-strong cursor-pointer'"
      @click="onZoneClick"
    >
      <img
        v-if="imagePreview"
        :key="imagePreview"
        :src="imagePreview"
        class="max-w-full max-h-full w-auto h-auto object-contain"
        alt="实验台拍摄"
      />

      <div v-if="uploadingImage" class="absolute inset-0 flex items-center justify-center bg-white/85 backdrop-blur-sm text-sm text-ink-base z-20 gap-2.5">
        <span class="w-5 h-5 rounded-full border-2 border-brand-500 border-t-transparent animate-spin" />
        图片上传中…
      </div>

      <div v-if="uploadError && imagePreview" class="absolute bottom-4 left-4 right-4 z-20 px-4 py-2.5 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-800 shadow-soft">
        上传失败：{{ uploadError }}（预览仍保留，请点「更换图片」重试）
      </div>

      <svg v-if="marks.length && imagePreview" class="absolute inset-0 w-full h-full pointer-events-none" viewBox="0 0 1000 1000" preserveAspectRatio="xMidYMid meet">
        <g v-for="m in marks" :key="m.n">
          <rect class="error-rect" :x="m.x" :y="m.y" :width="m.w" :height="m.h" rx="4" />
          <circle :cx="m.x + 14" :cy="m.y + 14" r="14" fill="#ef4444" />
          <text :x="m.x + 14" :y="m.y + 19" text-anchor="middle" fill="white" font-size="13" font-weight="bold">{{ m.n }}</text>
        </g>
      </svg>

      <!-- 空态（紧凑） -->
      <div v-if="!imagePreview" class="flex flex-col items-center justify-center text-ink-muted pointer-events-none px-4 text-center">
        <div class="w-14 h-14 rounded-2xl flex items-center justify-center mb-3 brand-gradient-soft border border-brand-100 shadow-card">
          <svg class="w-7 h-7 text-brand-600" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
            <rect x="3" y="6" width="18" height="14" rx="2.5" />
            <circle cx="12" cy="13" r="3.5" />
            <path stroke-linecap="round" d="M8 6l1.5-2h5L16 6" />
          </svg>
        </div>
        <span class="text-sm font-bold text-ink-strong mb-1.5">点击上传实验台画面</span>
        <span class="text-[11px] text-ink-muted leading-relaxed mb-3">支持 JPG / PNG / WEBP<br/>AI 会自动识别错误点</span>
        <div class="flex items-center gap-2 text-[9px] text-ink-faint">
          <span class="flex items-center gap-1"><span class="w-1 h-1 rounded-full bg-brand-500" />自动标注</span>
          <span class="text-line-strong">·</span>
          <span class="flex items-center gap-1"><span class="w-1 h-1 rounded-full bg-emerald-500" />知识库</span>
          <span class="text-line-strong">·</span>
          <span class="flex items-center gap-1"><span class="w-1 h-1 rounded-full bg-accent-cyan" />实时反馈</span>
        </div>
      </div>
    </div>

    <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/gif,image/webp,image/bmp,.jpg,.jpeg,.png,.gif,.webp,.bmp" class="hidden" @change="onFileChange" />
  </section>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  imagePreview: { type: String, default: '' },
  uploadingImage: { type: Boolean, default: false },
  uploadError: { type: String, default: '' },
  marks: { type: Array, default: () => [] }
})

const emit = defineEmits(['upload', 'clear'])
const fileInput = ref(null)

function onZoneClick() {
  if (props.uploadingImage || props.imagePreview) return
  triggerUpload()
}

function triggerUpload() {
  if (props.uploadingImage) return
  fileInput.value?.click()
}

defineExpose({ triggerUpload })

function onFileChange(e) {
  const file = e.target.files?.[0]
  if (file) emit('upload', file)
  e.target.value = ''
}
</script>
