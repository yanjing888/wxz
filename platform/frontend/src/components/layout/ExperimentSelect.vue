<template>
  <div ref="rootRef" class="exp-select relative shrink-0">
    <button
      type="button"
      class="exp-select-trigger"
      :disabled="switching || !experiments.length"
      :aria-expanded="open"
      aria-haspopup="listbox"
      @click="toggle"
    >
      <span class="exp-select-name truncate">{{ currentName }}</span>
      <span v-if="switching" class="exp-select-chevron shrink-0">
        <span class="inline-block w-4 h-4 border-2 border-brand-400 border-t-transparent rounded-full animate-spin" />
      </span>
      <svg
        v-else
        class="exp-select-chevron w-4 h-4 shrink-0 text-ink-faint transition-transform duration-200"
        :class="{ 'rotate-180': open }"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        viewBox="0 0 24 24"
      >
        <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
      </svg>
    </button>

    <Transition name="exp-menu">
      <ul
        v-if="open && experiments.length"
        class="exp-select-menu"
        role="listbox"
        :aria-activedescendant="experimentCode ? `exp-opt-${experimentCode}` : undefined"
      >
        <li
          v-for="exp in experiments"
          :id="`exp-opt-${exp.code}`"
          :key="exp.code"
          role="option"
          :aria-selected="exp.code === experimentCode"
        >
          <button
            type="button"
            class="exp-select-option"
            :class="{ 'is-active': exp.code === experimentCode }"
            @click="pick(exp.code)"
          >
            <span class="min-w-0 flex-1 text-left text-xs font-semibold text-ink-strong truncate">{{ exp.name }}</span>
            <svg
              v-if="exp.code === experimentCode"
              class="w-4 h-4 shrink-0 text-brand-600"
              fill="none"
              stroke="currentColor"
              stroke-width="2.5"
              viewBox="0 0 24 24"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
            </svg>
          </button>
        </li>
      </ul>
    </Transition>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'

const props = defineProps({
  experiments: { type: Array, default: () => [] },
  experimentCode: { type: String, default: '' },
  switching: { type: Boolean, default: false }
})

const emit = defineEmits(['experiment-change'])

const open = ref(false)
const rootRef = ref(null)

const currentName = computed(() => {
  const hit = props.experiments.find((e) => e.code === props.experimentCode)
  return hit?.name || '选择实验'
})

function toggle() {
  if (props.switching) return
  open.value = !open.value
}

function pick(code) {
  open.value = false
  if (code && code !== props.experimentCode) {
    emit('experiment-change', code)
  }
}

function onDocClick(e) {
  if (!open.value || !rootRef.value) return
  if (!rootRef.value.contains(e.target)) {
    open.value = false
  }
}

function onKeydown(e) {
  if (e.key === 'Escape') open.value = false
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
})
</script>
