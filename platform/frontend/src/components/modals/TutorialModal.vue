<template>
  <div v-if="visible" class="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-6 bg-slate-900/30 backdrop-blur-md" @click.self="$emit('close')">
    <div class="relative w-full max-w-4xl max-h-[92vh] rounded-3xl overflow-hidden flex flex-col bg-white border border-line-soft shadow-lift fade-in-up">
      <div class="h-1.5 w-full brand-gradient shrink-0" />
      <div class="p-5 md:p-6 border-b border-line-soft flex items-center justify-between shrink-0">
        <div class="min-w-0 pr-4">
          <div class="flex items-center gap-2 mb-1.5">
            <span class="chip text-brand-700 bg-brand-50 border-brand-100">
              <span class="font-mono">STEP {{ String(stepNo).padStart(2, '0') }}</span>
            </span>
            <span class="text-[11px] text-ink-faint truncate">{{ experimentName }}</span>
          </div>
          <h3 class="text-lg md:text-xl font-bold text-ink-strong">{{ step?.title || '多模态实验指导' }}</h3>
        </div>
        <button
          type="button"
          class="w-9 h-9 rounded-xl text-ink-faint hover:text-ink-strong hover:bg-surface-muted text-2xl leading-none flex items-center justify-center transition-colors shrink-0"
          @click="$emit('close')"
        >
          ×
        </button>
      </div>

      <div class="p-5 md:p-6 overflow-y-auto custom-scroll flex-1 space-y-5">
        <section v-if="tutorialImages.length">
          <h4 class="section-label mb-2">示意图</h4>
          <TutorialImageGallery :images="tutorialImages" size="modal" title-prefix="教程示意图" />
        </section>

        <section>
          <h4 class="section-label mb-2">演示视频</h4>
          <div
            v-if="videoUrl"
            class="max-w-sm mx-auto h-36 rounded-xl overflow-hidden border border-line-soft bg-black shadow-card"
          >
            <video
              class="w-full h-full object-contain bg-black"
              controls
              playsinline
              preload="metadata"
              :src="videoUrl"
            >
              您的浏览器不支持视频播放
            </video>
          </div>
          <div
            v-else
            class="max-w-sm mx-auto h-36 rounded-xl bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 overflow-hidden relative border border-line-soft flex flex-col items-center justify-center shadow-card gap-1.5"
          >
            <div class="absolute inset-0 opacity-30 bg-[repeating-linear-gradient(0deg,transparent,transparent_2px,rgba(255,255,255,.06)_2px,rgba(255,255,255,.06)_4px)]" />
            <svg class="w-8 h-8 text-slate-500 relative z-10" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
              <path stroke-linecap="round" stroke-linejoin="round" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <p class="text-slate-400 text-[12px] relative z-10">步骤 {{ stepNo }} · 演示视频占位</p>
          </div>
        </section>

        <section v-if="step?.tut?.steps?.length">
          <h4 class="section-label mb-3">操作步骤</h4>
          <ol class="space-y-2">
            <li v-for="(s, i) in step.tut.steps" :key="i" class="text-sm text-ink-base flex gap-3 items-start">
              <span class="w-6 h-6 brand-gradient text-white rounded-lg flex items-center justify-center text-[11px] font-bold shrink-0 shadow-card">{{ i + 1 }}</span>
              <span class="leading-relaxed pt-0.5">{{ s }}</span>
            </li>
          </ol>
        </section>

        <section v-if="step?.tut?.warnings?.length">
          <h4 class="text-sm font-bold text-red-600 mb-3 flex items-center gap-2">
            <span class="w-1 h-4 bg-red-500 rounded-full" />注意事项
          </h4>
          <ul class="space-y-2 bg-red-50 p-4 rounded-2xl border border-red-100">
            <li v-for="(w, i) in step.tut.warnings" :key="i" class="text-xs text-red-700 leading-relaxed flex gap-2">
              <span class="text-red-400 shrink-0">●</span>
              <span>{{ w }}</span>
            </li>
          </ul>
        </section>
      </div>

      <div class="p-4 border-t border-line-soft bg-surface-soft flex justify-end shrink-0">
        <button type="button" class="btn-brand px-8 py-2.5 rounded-xl font-bold text-sm" @click="$emit('close')">已了解，返回实验</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import TutorialImageGallery from '../tutorial/TutorialImageGallery.vue'

const props = defineProps({
  visible: Boolean,
  experimentName: String,
  step: Object,
  stepNo: { type: Number, default: 1 }
})

defineEmits(['close'])

const tutorialImages = computed(() => props.step?.tut?.images || [])

const videoUrl = computed(() => {
  const raw = props.step?.tut?.videoUrl || ''
  return String(raw).trim()
})
</script>
