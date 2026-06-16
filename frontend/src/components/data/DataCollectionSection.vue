<template>
  <div class="flex flex-col min-h-0" :class="open ? 'flex-1' : 'shrink-0'">
    <div class="flex items-center justify-between px-5 pt-3" :class="open ? 'pb-1' : 'pb-3'">
      <div class="flex items-center gap-2 min-w-0">
        <span class="workzone-eyebrow">数据采集</span>
        <span class="text-[10px] text-ink-faint">· {{ mode === 'device' ? '仪器自动' : '手动填写' }}</span>
      </div>
      <button
        type="button"
        class="flex items-center gap-1 text-[10px] font-semibold text-ink-muted hover:text-brand-600 px-1.5 py-0.5 rounded-md hover:bg-brand-50 transition-colors btn-active-scale"
        :title="open ? '收起数据采集（本步骤无需采集时）' : '展开数据采集'"
        @click="$emit('toggle')"
      >
        <span>{{ open ? '收起' : '展开' }}</span>
        <svg class="w-2.5 h-2.5 transition-transform" :class="open ? 'rotate-180' : ''" fill="none" stroke="currentColor" stroke-width="2.2" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>
    </div>
    <div v-if="open" class="flex-1 flex flex-col min-h-0">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  open: { type: Boolean, default: true },
  mode: { type: String, default: 'manual' }
})
defineEmits(['toggle'])
</script>
