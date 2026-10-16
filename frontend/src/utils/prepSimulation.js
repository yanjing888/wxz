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
  '第1步 逐步安装：依次安装 6 个教学组件，认识 8 个学生操作单元',
  '第2步 引导调节：点击模型或滚轮调节，观察光路和实时成像',
  '第3步 预习测验：完成单选、多选、判断共 8 题，知识测验 40 分',
  '第4步 成像挑战：独立调出明亮、均匀、清晰、居中的图像，挑战 60 分',
  '第5步 预习报告：总分达到 80 分且成像挑战通过，下载结果与操作记录'
]
