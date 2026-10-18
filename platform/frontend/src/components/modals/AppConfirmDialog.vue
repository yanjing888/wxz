<template>
  <div v-if="visible" class="fixed inset-0 z-[70] flex items-center justify-center px-4 py-6">
    <div class="absolute inset-0 bg-slate-900/35 backdrop-blur-[2px]" @click="onCancel" />
    <section class="relative w-full max-w-[460px] rounded-2xl bg-white border border-line-soft shadow-2xl overflow-hidden">
      <header class="px-5 pt-5 pb-3 flex items-start gap-3">
        <div class="w-10 h-10 rounded-xl brand-gradient flex items-center justify-center text-white font-bold shadow-brand shrink-0">
          智
        </div>
        <div class="min-w-0">
          <h2 class="text-lg font-black text-ink-strong leading-snug">{{ title }}</h2>
          <p v-if="message" class="text-sm text-ink-base leading-relaxed mt-2 whitespace-pre-line">{{ message }}</p>
        </div>
      </header>

      <div v-if="detail" class="mx-5 mb-4 rounded-xl border border-line-soft bg-surface-soft px-3 py-2.5">
        <p class="text-xs text-ink-muted leading-relaxed whitespace-pre-line">{{ detail }}</p>
      </div>

      <footer class="px-5 pb-5 flex justify-end gap-2">
        <button
          v-if="mode === 'confirm'"
          type="button"
          class="btn-ghost px-4 py-2 rounded-xl text-xs font-semibold"
          @click="onCancel"
        >
          {{ cancelText }}
        </button>
        <button
          type="button"
          class="btn-brand px-4 py-2 rounded-xl text-xs font-bold"
          @click="$emit('confirm')"
        >
          {{ confirmText }}
        </button>
      </footer>
    </section>
  </div>
</template>

<script setup>
const props = defineProps({
  visible: { type: Boolean, default: false },
  mode: { type: String, default: 'confirm' },
  title: { type: String, default: '确认操作' },
  message: { type: String, default: '' },
  detail: { type: String, default: '' },
  confirmText: { type: String, default: '确定' },
  cancelText: { type: String, default: '取消' }
})

const emit = defineEmits(['confirm', 'cancel'])

function onCancel() {
  if (props.mode === 'confirm') {
    emit('cancel')
    return
  }
  emit('confirm')
}
</script>
