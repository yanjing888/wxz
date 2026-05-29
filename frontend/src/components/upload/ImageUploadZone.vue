<template>
  <section class="flex-1 p-5 flex flex-col overflow-hidden relative min-h-0 bg-white">
    <div v-if="showAnalysisLabel" class="absolute top-6 left-6 z-20 px-3 py-1.5 bg-red-600 text-white text-[10px] font-bold rounded-lg shadow-xl flex items-center gap-2 animate-bounce">
      <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20"><path d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" /></svg>
      视觉纠错：已在图中对应标注错误点
    </div>

    <div class="flex items-center gap-2 shrink-0 mb-3">
      <span class="w-2 h-2 rounded-full bg-blue-500 shrink-0" />
      <h2 class="text-[10px] font-bold text-slate-500 uppercase tracking-widest">实景拍摄与原理图对照区</h2>
    </div>

    <div
      class="relative flex-1 min-h-0 rounded-2xl flex flex-col overflow-hidden border border-[#d7e0ec] bg-white cursor-pointer"
      :class="{ 'has-upload': !!imagePreview }"
      @click="triggerUpload"
    >
      <div class="upload-drop-inner relative flex-1 flex items-center justify-center min-h-[168px] m-2 rounded-[10px] border-2 border-dashed border-[#c5d2e3] bg-[#f4f7fb]" :class="imagePreview ? '!border-solid !border-[#e8edf4] !bg-white' : ''">
        <img v-if="imagePreview" :src="imagePreview" class="max-w-full max-h-full object-contain rounded-lg" alt="实验台拍摄" />
        <svg v-if="marks.length" class="absolute inset-0 w-full h-full pointer-events-none" viewBox="0 0 1000 600" preserveAspectRatio="xMidYMid meet">
          <g v-for="m in marks" :key="m.n">
            <rect class="error-rect" :x="m.x" :y="m.y" :width="m.w" :height="m.h" rx="4" />
            <circle :cx="m.x + 14" :cy="m.y + 14" r="12" fill="#ef4444" />
            <text :x="m.x + 14" :y="m.y + 18" text-anchor="middle" fill="white" font-size="12" font-weight="bold">{{ m.n }}</text>
          </g>
        </svg>
        <div v-if="!imagePreview" class="absolute inset-0 flex flex-col items-center justify-center text-slate-500 pointer-events-none px-6">
          <div class="w-14 h-14 rounded-xl flex items-center justify-center mb-3 border border-slate-200 bg-white shadow-sm">
            <svg class="w-7 h-7" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><rect x="5" y="5" width="14" height="14" rx="2"/><path stroke-linecap="round" d="M9 5V3h6v2"/></svg>
          </div>
          <span class="text-sm font-bold text-[#1e3a5f] mb-1.5">实验台画面 / 上传实拍</span>
          <span class="text-xs text-slate-500 max-w-[280px] text-center leading-relaxed">上传原理图或台面实拍，可在此框内点击圈出异常位置</span>
        </div>
        <button
          v-if="imagePreview"
          type="button"
          class="absolute top-3 right-3 p-2.5 bg-red-50 hover:bg-red-600 text-red-500 hover:text-white rounded-xl border border-red-200 transition-all btn-active-scale z-30"
          @click.stop="$emit('clear')"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
        </button>
      </div>
    </div>
    <input ref="fileInput" type="file" accept="image/*" class="hidden" @change="onFileChange" />
  </section>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  imagePreview: { type: String, default: '' },
  marks: { type: Array, default: () => [] },
  showAnalysisLabel: { type: Boolean, default: false }
})

const emit = defineEmits(['upload', 'clear'])
const fileInput = ref(null)

function triggerUpload() {
  fileInput.value?.click()
}

defineExpose({ triggerUpload })

function onFileChange(e) {
  const file = e.target.files?.[0]
  if (file) emit('upload', file)
  e.target.value = ''
}
</script>
