<template>
  <div v-if="imageList.length" class="tutorial-gallery" :class="resolvedSize === 'compact' ? 'tutorial-gallery--compact' : ''">
    <div
      class="tutorial-gallery-stage relative overflow-hidden bg-bg-soft border border-line-soft cursor-zoom-in group"
      :class="stageClass"
      @click="openLightbox(activeIndex)"
    >
      <img
        :src="imageList[activeIndex]"
        :alt="altText(activeIndex)"
        class="absolute inset-0 w-full h-full object-contain bg-white"
        draggable="false"
      />
      <div class="absolute inset-0 bg-black/0 group-hover:bg-black/5 transition-colors pointer-events-none" />
      <div class="absolute bottom-2 right-2 px-2 py-0.5 rounded-md bg-black/45 text-white text-[10px] font-medium opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none">
        点击放大
      </div>
      <button
        v-if="imageList.length > 1"
        type="button"
        class="absolute left-2 top-1/2 -translate-y-1/2 w-7 h-7 rounded-full bg-white/90 border border-line-soft shadow-card flex items-center justify-center text-ink-muted hover:text-brand-600 btn-active-scale"
        aria-label="上一张"
        @click.stop="prev"
      >
        ‹
      </button>
      <button
        v-if="imageList.length > 1"
        type="button"
        class="absolute right-2 top-1/2 -translate-y-1/2 w-7 h-7 rounded-full bg-white/90 border border-line-soft shadow-card flex items-center justify-center text-ink-muted hover:text-brand-600 btn-active-scale"
        aria-label="下一张"
        @click.stop="next"
      >
        ›
      </button>
    </div>
    <div v-if="imageList.length > 1" class="flex items-center justify-center gap-1.5 mt-2">
      <button
        v-for="(_, i) in imageList"
        :key="i"
        type="button"
        class="w-1.5 h-1.5 rounded-full transition-all"
        :class="i === activeIndex ? 'bg-brand-600 w-4' : 'bg-line-strong hover:bg-brand-300'"
        :aria-label="`第 ${i + 1} 张`"
        @click="activeIndex = i"
      />
      <span class="text-[10px] text-ink-faint ml-1 tabular-nums">{{ activeIndex + 1 }} / {{ imageList.length }}</span>
    </div>

    <Teleport to="body">
      <div
        v-if="lightboxOpen"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 md:p-8"
        @click.self="closeLightbox"
      >
        <button
          type="button"
          class="absolute top-4 right-4 w-10 h-10 rounded-full bg-white/15 text-white text-xl hover:bg-white/25 flex items-center justify-center z-10"
          aria-label="关闭预览"
          @click="closeLightbox"
        >
          ×
        </button>
        <button
          v-if="imageList.length > 1"
          type="button"
          class="absolute left-3 md:left-6 top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-white/15 text-white text-2xl hover:bg-white/25 flex items-center justify-center z-10"
          aria-label="上一张"
          @click.stop="lightboxPrev"
        >
          ‹
        </button>
        <img
          :src="imageList[lightboxIndex]"
          :alt="altText(lightboxIndex)"
          class="max-w-full max-h-[90vh] object-contain rounded-lg shadow-2xl select-none"
          @click.stop
        />
        <button
          v-if="imageList.length > 1"
          type="button"
          class="absolute right-3 md:right-6 top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-white/15 text-white text-2xl hover:bg-white/25 flex items-center justify-center z-10"
          aria-label="下一张"
          @click.stop="lightboxNext"
        >
          ›
        </button>
        <p v-if="imageList.length > 1" class="absolute bottom-4 left-1/2 -translate-x-1/2 text-white/80 text-sm tabular-nums">
          {{ lightboxIndex + 1 }} / {{ imageList.length }}
        </p>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  images: { type: Array, default: () => [] },
  compact: { type: Boolean, default: false },
  /** compact=左侧工作区；modal=教程弹窗（更小） */
  size: { type: String, default: '' },
  titlePrefix: { type: String, default: '示意图' }
})

const activeIndex = ref(0)
const lightboxOpen = ref(false)
const lightboxIndex = ref(0)

const resolvedSize = computed(() => {
  if (props.size === 'modal' || props.size === 'compact') return props.size
  return props.compact ? 'compact' : 'default'
})

const stageClass = computed(() => {
  if (resolvedSize.value === 'modal') {
    return 'rounded-xl h-36 max-w-sm mx-auto'
  }
  if (resolvedSize.value === 'compact') {
    return 'rounded-xl h-40'
  }
  return 'rounded-2xl aspect-video max-h-52'
})

const imageList = computed(() =>
  (props.images || []).map((item) => String(item || '').trim()).filter(Boolean)
)

watch(
  () => props.images,
  () => {
    activeIndex.value = 0
    lightboxIndex.value = 0
  }
)

function altText(index) {
  return `${props.titlePrefix} ${index + 1}`
}

function prev() {
  activeIndex.value = (activeIndex.value - 1 + imageList.value.length) % imageList.value.length
}

function next() {
  activeIndex.value = (activeIndex.value + 1) % imageList.value.length
}

function openLightbox(index) {
  lightboxIndex.value = index
  lightboxOpen.value = true
}

function closeLightbox() {
  lightboxOpen.value = false
}

function lightboxPrev() {
  lightboxIndex.value = (lightboxIndex.value - 1 + imageList.value.length) % imageList.value.length
}

function lightboxNext() {
  lightboxIndex.value = (lightboxIndex.value + 1) % imageList.value.length
}
</script>
