import { Capacitor } from '@capacitor/core'
import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const studentMeta = { requiresAuth: true, role: 'STUDENT' }

const routes = [
  {
    path: '/',
    component: () => import('../layouts/StudentLayout.vue'),
    meta: studentMeta,
    children: [
      { path: '', redirect: { name: 'lab' } },
      {
        path: 'experiments',
        name: 'experiments',
        redirect: { name: 'lab' }
      },
      {
        path: 'after/:code',
        name: 'after-center',
        component: () => import('../views/after/AfterCenterView.vue'),
        meta: studentMeta
      },
      {
        path: 'agents',
        name: 'agents',
        component: () => import('../views/AgentHubView.vue'),
        meta: studentMeta
      },
      {
        path: 'agents/:code/:agent',
        name: 'agent-workspace',
        component: () => import('../views/AgentWorkspaceView.vue'),
        meta: studentMeta
      },
      {
        path: 'prep/:code',
        name: 'prep-ready',
        component: () => import('../views/experiment/PrepareReadyView.vue'),
        meta: studentMeta
      },
      {
        path: 'lab',
        name: 'lab',
        component: () => import('../views/LabView.vue'),
        meta: studentMeta
      },
      {
        path: 'monitor',
        name: 'lab-monitor',
        component: () => import('../views/LabMonitorView.vue'),
        meta: studentMeta
      },
      {
        path: 'after/:code/report',
        name: 'after-report',
        component: () => import('../views/after/AfterReportView.vue'),
        meta: studentMeta
      },
      {
        path: 'after/:code/review',
        name: 'after-review',
        component: () => import('../views/after/AfterReviewView.vue'),
        meta: studentMeta
      },
      {
        path: 'files',
        name: 'files',
        component: () => import('../views/FilesView.vue'),
        meta: studentMeta
      },
      { path: 'profile', redirect: { name: 'lab' } },
      {
        path: 'experiments/:code',
        redirect: (to) => ({ name: 'lab', query: { exp: to.params.code } })
      },
      {
        path: 'experiments/:code/:rest(.*)',
        redirect: (to) => ({ name: 'lab', query: { exp: to.params.code } })
      }
    ]
  },
  { path: '/home', redirect: { name: 'lab' } },
  { path: '/ai', redirect: { name: 'agents' } },
  { path: '/ai/:code', redirect: { name: 'agents' } },
  { path: '/experiment/:code', redirect: (to) => ({ name: 'lab', query: { exp: to.params.code } }) },
  {
    path: '/teacher',
    name: 'teacher',
    component: () => import('../views/TeacherView.vue'),
    meta: { requiresAuth: true, role: 'TEACHER' }
  },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/:pathMatch(.*)*', redirect: { name: 'lab' } }
]

const router = createRouter({
  history: Capacitor.isNativePlatform() ? createWebHashHistory() : createWebHistory(),
  routes
})

function routeMeta(to, key) {
  for (let i = to.matched.length - 1; i >= 0; i -= 1) {
    if (to.matched[i].meta[key] !== undefined) {
      return to.matched[i].meta[key]
    }
  }
  return undefined
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (auth.token && !auth.authChecked) {
    await auth.validateSession()
  }

  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)
  const role = routeMeta(to, 'role')

  if (requiresAuth && !auth.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.token) {
    return auth.homeRoute()
  }
  if (role === 'TEACHER' && !auth.isTeacher) {
    return auth.homeRoute()
  }
  if (role === 'STUDENT' && auth.isTeacher) {
    return '/teacher'
  }
  return true
})

export default router
