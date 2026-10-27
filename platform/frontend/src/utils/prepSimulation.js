/** 判断是否必须先完成虚拟仿真才能进入实验台 */
export function requiresSimulation(progress) {
  return !!progress?.simulationRequired && !progress?.simulationCompleted
}

/** 仿真预习页路由 */
export function simulationPrepRoute(code) {
  return { name: 'prep-simulation', params: { code } }
}

/** 进入实验台前的仿真拦截：未完成则返回跳转目标 */
export function labEntryBlockedRoute(progress, code) {
  if (!code || !progress) return null
  if (progress.experimentCode && progress.experimentCode !== code) return null
  return !progress.labCompleted && requiresSimulation(progress) ? simulationPrepRoute(code) : null
}

/** 仿真未完成时需拦截的学生端路由 */
export const SIMULATION_GATED_ROUTE_NAMES = [
  'lab',
  'lab-monitor'
]

export function experimentCodeFromRoute(to, fallback = '') {
  return String(to.params?.code || to.query?.exp || fallback || '').trim()
}

/** 牛顿环仿真完成条件（与 public/newton-rings-simulation 内判定一致） */
export const NEWTON_RINGS_SIM_STEPS = [
  '第1步 仿真练习：按提示安装组件，调出清晰环纹',
  '第2步 测验：完成 8 道预习题，共 40 分',
  '第3步 挑战：独立完成成像调节，共 60 分',
  '第4步 报告：总分达到 80 分且挑战通过，完成预习'
]
