<template>
  <section class="flex-1 min-h-0 flex flex-col overflow-hidden">
    <div class="flex-1 min-h-0 overflow-y-auto custom-scroll px-5 pt-3 pb-4 space-y-3">
      <TutorialImageGallery
        v-if="tutorialImages.length"
        :images="tutorialImages"
        size="compact"
        title-prefix="步骤示意图"
      />

      <p v-if="description" class="text-[12px] text-ink-muted leading-relaxed">{{ description }}</p>
      <p v-else-if="!tutorialImages.length" class="text-[12px] text-ink-faint leading-relaxed italic">暂无步骤说明，可点击上方「本步骤教程」查看操作要点。</p>

      <div v-if="checklist.length">
        <p class="text-[10px] font-bold text-ink-faint uppercase tracking-wider mb-2">操作要点</p>
        <ul class="space-y-1.5">
          <li
            v-for="(item, index) in checklist"
            :key="index"
            class="flex items-start gap-2 text-[12px] text-ink-base leading-relaxed"
          >
            <span class="mt-0.5 w-4 h-4 rounded-md bg-surface-soft border border-line-soft text-[10px] font-bold text-ink-muted flex items-center justify-center shrink-0">
              {{ index + 1 }}
            </span>
            <span>{{ item }}</span>
          </li>
        </ul>
      </div>

      <div v-if="warnings.length" class="rounded-xl border border-amber-100 bg-amber-50/70 px-3 py-2.5">
        <p class="text-[10px] font-bold text-amber-700 uppercase tracking-wider mb-1.5">注意事项</p>
        <ul class="space-y-1">
          <li
            v-for="(item, index) in warnings"
            :key="index"
            class="text-[11px] text-amber-900/85 leading-relaxed pl-3 relative before:content-['•'] before:absolute before:left-0"
          >
            {{ item }}
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import TutorialImageGallery from '../tutorial/TutorialImageGallery.vue'

const props = defineProps({
  step: { type: Object, default: null }
})

const description = computed(() => (props.step?.desc || '').trim())

const tutorialImages = computed(() => props.step?.tut?.images || [])

const checklist = computed(() => {
  const steps = props.step?.tut?.steps
  return Array.isArray(steps) ? steps.filter(Boolean) : []
})

const warnings = computed(() => {
  const items = props.step?.tut?.warnings
  return Array.isArray(items) ? items.filter(Boolean) : []
})
</script>
