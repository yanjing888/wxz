import { defineStore } from 'pinia'
import { authApi } from '../api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('wxz_token') || '',
    userId: localStorage.getItem('wxz_userId') || null,
    username: localStorage.getItem('wxz_username') || '',
    displayName: localStorage.getItem('wxz_displayName') || '',
    role: localStorage.getItem('wxz_role') || 'STUDENT',
    studentClass: localStorage.getItem('wxz_class') || '',
    authChecked: false
  }),
  getters: {
    isTeacher: (state) => state.role === 'TEACHER',
    isStudent: (state) => state.role !== 'TEACHER'
  },
  actions: {
    persist(data) {
      this.token = data.token
      this.userId = data.userId
      this.username = data.username
      this.displayName = data.displayName
      this.role = data.role || 'STUDENT'
      this.studentClass = data.studentClass || ''
      localStorage.setItem('wxz_token', data.token)
      localStorage.setItem('wxz_userId', String(data.userId))
      localStorage.setItem('wxz_username', data.username)
      localStorage.setItem('wxz_displayName', data.displayName)
      localStorage.setItem('wxz_role', this.role)
      localStorage.setItem('wxz_class', this.studentClass)
      this.authChecked = true
    },
    hydrateUser(data) {
      this.userId = data.userId
      this.username = data.username
      this.displayName = data.displayName
      this.role = data.role || 'STUDENT'
      this.studentClass = data.studentClass || ''
      localStorage.setItem('wxz_userId', String(data.userId))
      localStorage.setItem('wxz_username', data.username)
      localStorage.setItem('wxz_displayName', data.displayName)
      localStorage.setItem('wxz_role', this.role)
      localStorage.setItem('wxz_class', this.studentClass)
    },
    async validateSession() {
      if (!this.token) {
        this.authChecked = true
        return false
      }
      try {
        const { data } = await authApi.me()
        this.hydrateUser(data)
        this.authChecked = true
        return true
      } catch {
        this.logout()
        this.authChecked = true
        return false
      }
    },
    async register(form) {
      const { data } = await authApi.register(form)
      this.persist(data)
    },
    async login(form) {
      const { data } = await authApi.login(form)
      this.persist(data)
    },
    async resetPassword(form) {
      const { data } = await authApi.resetPassword(form)
      this.persist(data)
    },
    logout() {
      this.token = ''
      this.userId = null
      this.username = ''
      this.displayName = ''
      this.role = 'STUDENT'
      this.studentClass = ''
      this.authChecked = false
      ;['wxz_token', 'wxz_userId', 'wxz_username', 'wxz_displayName', 'wxz_role', 'wxz_class'].forEach((k) => localStorage.removeItem(k))
    },
    homeRoute() {
      return this.isTeacher ? '/teacher' : '/experiments'
    }
  }
})
