<template>
  <div class="flex flex-col flex-1 min-h-0" :class="fill ? 'overflow-hidden' : ''">
    <div
      class="flex-1 min-h-0"
      :class="fill ? 'overflow-hidden flex flex-col' : 'overflow-y-auto custom-scroll'"
    >
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  tool: { type: Object, default: null },
  fill: { type: Boolean, default: false }
})
</script>
