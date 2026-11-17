import { ref, watch } from 'vue'

export function useAssistantChoice() {
  const key = `wxz_assistant:${localStorage.getItem('wxz_userId') || 'guest'}`
  let saved
  try { saved = localStorage.getItem(key) } catch { /* Storage may be unavailable. */ }
  const provider = ref(saved === 'octop' ? 'octop' : 'dify')
  watch(provider, value => {
    try { localStorage.setItem(key, value) } catch { /* Selection still works without storage. */ }
  })
  return provider
}
