import { computed, ref, toValue, watch } from 'vue'

export function usePagination(source, resetSources = [], defaultSize = 10) {
  const page = ref(1), pageSize = ref(defaultSize)
  const total = computed(() => (toValue(source) || []).length)
  const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
  const offset = computed(() => (Math.min(page.value, pageCount.value) - 1) * pageSize.value)
  const items = computed(() => (toValue(source) || []).slice(offset.value, offset.value + pageSize.value))
  watch(pageCount, count => { page.value = Math.max(1, Math.min(page.value, count)) }, { flush: 'sync' })
  watch(pageSize, () => { page.value = 1 }, { flush: 'sync' })
  if (resetSources.length) watch(resetSources, () => { page.value = 1 }, { flush: 'sync' })
  return { page, pageSize, total, offset, items }
}
