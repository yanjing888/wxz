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
  return requiresSimulation(progress) ? simulationPrepRoute(code) : null
}

/** 仿真未完成时需拦截的学生端路由 */
export const SIMULATION_GATED_ROUTE_NAMES = [
  'lab',
  'lab-monitor',
  'after-center',
  'after-report',
  'after-review'
]

export function experimentCodeFromRoute(to, fallback = '') {
  return String(to.params?.code || to.query?.exp || fallback || '').trim()
}

/** 牛顿环仿真完成条件（与 public/newton-rings-simulation 内判定一致） */
export const NEWTON_RINGS_SIM_STEPS = [
  '第1步 光路装调：打开钠光灯，调节灯位/半反镜，收起底座反光镜',
  '第2步 目镜与粗调焦：叉丝清晰，镜筒上升找到并调清干涉环',
  '第3步 物镜微调：视场均匀，暗斑居中',
  '第4步 暗环读数：左右两侧各记录至少 3 个暗环读数，鼓轮不可反转',
  '第5步 数据处理：在「数据」页点击「计算 R」，得到曲率半径'
]
