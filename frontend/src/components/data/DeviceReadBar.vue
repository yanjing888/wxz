<template>
  <div class="shrink-0 px-5 py-2.5">
    <div class="flex gap-2">
    <button
      type="button"
      class="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl border text-[12px] font-bold transition-all btn-active-scale disabled:opacity-50 disabled:cursor-not-allowed"
      :class="busy
        ? 'border-brand-200 bg-brand-50 text-brand-700'
        : 'border-line-soft bg-white text-ink-strong hover:border-brand-200 hover:bg-brand-50/50 hover:text-brand-700 shadow-card'"
      :disabled="disabled || busy"
      title="获取当前 UVC 相机视场图像，并添加到右侧对话框"
      @click="$emit('ccd-capture')"
    >
      <svg v-if="busy" class="w-4 h-4 animate-spin shrink-0" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" />
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4l3-3-3-3v4a8 8 0 100 16z" />
      </svg>
      <svg v-else class="w-4 h-4 shrink-0 text-brand-600" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" d="M4 7h4l2-3h4l2 3h4v12H4V7z" />
        <circle cx="12" cy="13" r="2.5" />
      </svg>
      <span>{{ busy ? '获取中…' : '获取成像' }}</span>
    </button>
    <button
      type="button"
      class="shrink-0 flex items-center justify-center gap-1.5 px-3 py-2.5 rounded-xl border border-line-soft
        bg-white text-[12px] font-bold text-ink-strong shadow-card transition-all btn-active-scale
        hover:border-violet-200 hover:bg-violet-50/60 hover:text-violet-700"
      title="打开读数助手；也可以在下方具体字段旁点击拍照识别"
      @click="$emit('photo-read')"
    >
      <svg class="w-4 h-4 shrink-0 text-violet-500" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" d="M3 9h3l1.5-2h9L18 9h3v10H3V9z" />
        <circle cx="12" cy="14" r="3" />
      </svg>
      <span>读数助手</span>
    </button>
    </div>
    <p v-if="dataReady" class="mt-1.5 text-[10px] text-emerald-700 text-center leading-snug">
      读数已带入右侧对话框，确认后发送
    </p>
  </div>
</template>

<script setup>
defineProps({
  busy: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  dataReady: { type: Boolean, default: false },
  canReadDevice: { type: Boolean, default: false },
  canCaptureCcd: { type: Boolean, default: false }
})

defineEmits(['read', 'photo-read', 'ccd-capture'])
</script>
