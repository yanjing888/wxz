import { isRef, ref, unref } from 'vue'
import { aiToolApi } from '../api'

/**
 * 阶段面板公用逻辑：实验上下文、调用状态与错误处理。
 * experimentSource 传入所在阶段的实验编号（ref、getter 或字符串）。
 */
export function useAgentTool(toolCode, experimentSource = '') {
  const fallback = ref('')
  const loading = ref(false)
  const error = ref('')
  const result = ref(null)

  function currentExperiment() {
    if (typeof experimentSource === 'function') return experimentSource() || ''
    if (isRef(experimentSource)) return unref(experimentSource) || ''
    return experimentSource || fallback.value
  }

  async function invoke(action, inputs = {}, options = {}) {
    loading.value = true
    error.value = ''
    try {
      const { data } = await aiToolApi.invoke(toolCode, {
        action,
        imageUrl: options.imageUrl || null,
        inputs: {
          experimentCode: currentExperiment(),
          ...inputs
        }
      })
      result.value = data
      return data
    } catch (e) {
      error.value = e.response?.data?.message || e.message || 'AI 服务调用失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  return { currentExperiment, loading, error, result, invoke }
}

/** 读取某个工具在后端是否可用（未配置 Dify Key 时为 false） */
export async function fetchToolAvailability(codes = []) {
  try {
    const { data } = await aiToolApi.tools()
    const map = {}
    ;(data || []).forEach((item) => {
      map[item.code] = item
    })
    return codes.length ? Object.fromEntries(codes.map((c) => [c, map[c] || null])) : map
  } catch {
    return {}
  }
}
