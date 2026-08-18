import http from './http'
import { postSse } from './sse'
import { apiUrl, mediaUrl } from './runtime'

function withMediaUrl(file) {
  if (!file || typeof file !== 'object') return file
  return { ...file, rawUrl: file.url, url: mediaUrl(file.url) }
}

export const authApi = {
  register: (data) => http.post('/api/auth/register', data),
  login: (data) => http.post('/api/auth/login', data),
  me: () => http.get('/api/auth/me'),
  resetPassword: (data) => http.patch('/api/auth/password', data)
}

export const experimentApi = {
  list: () => http.get('/api/experiments'),
  get: (code) => http.get(`/api/experiments/${code}`)
}

export const sessionApi = {
  start: (data) => http.post('/api/sessions', data),
  list: (params = {}) => http.get('/api/sessions', { params }),
  latest: (experimentCode) => http.get('/api/sessions/latest', { params: { experimentCode } }),
  get: (id) => http.get(`/api/sessions/${id}`),
  messages: (id) => http.get(`/api/sessions/${id}/messages`),
  attachLatestAiImage: (id, data) => http.patch(`/api/sessions/${id}/messages/latest-ai-image`, data),
  updateStep: (id, stepId) => http.patch(`/api/sessions/${id}/step?stepId=${stepId}`),
  assist: (id, data) => http.post(`/api/sessions/${id}/assist`, data),
  assistStream: (id, data, handlers, signal) =>
    postSse(`/api/sessions/${id}/assist/stream`, data, handlers, signal),
  envCheck: (id, data = {}) => http.post(`/api/sessions/${id}/env-check`, data),
  envLogs: (id) => http.get(`/api/sessions/${id}/env-logs`),
  tutorialView: (id) => http.post(`/api/sessions/${id}/tutorial-view`),
  getData: (id) => http.get(`/api/sessions/${id}/data`),
  submitData: (id, data) => http.post(`/api/sessions/${id}/data`, data),
  deviceConnect: (id, stepId) => http.post(`/api/sessions/${id}/device/connect?stepId=${stepId}`),
  deviceStatus: (id, stepId) => http.get(`/api/sessions/${id}/device/status?stepId=${stepId}`),
  deviceRead: (id, stepId) => http.post(`/api/sessions/${id}/device/read?stepId=${stepId}`),
  deviceAcquire: (id, stepId) => http.post(`/api/sessions/${id}/device/acquire?stepId=${stepId}`),
  deviceStop: (id, stepId) => http.post(`/api/sessions/${id}/device/stop?stepId=${stepId}`),
  deviceSnapshot: (id, stepId) => http.get(`/api/sessions/${id}/device/snapshot?stepId=${stepId}`),
  finish: (id) => http.post(`/api/sessions/${id}/finish`),
  report: (id) => http.get(`/api/sessions/${id}/report`),
  reportDocx: (id) => http.get(`/api/sessions/${id}/report/docx`, { responseType: 'blob' }),
  studentReportDocx: (id, data) =>
    http.post(`/api/sessions/${id}/report/student-docx`, data, { responseType: 'blob' })
}

export const uploadApi = {
  async image(file) {
    const fd = new FormData()
    fd.append('file', file, file.name || 'image.jpg')
    const token = localStorage.getItem('wxz_token')

    let res
    try {
      res = await fetch(apiUrl('/api/upload'), {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: fd
      })
    } catch {
      throw new Error('无法连接后端，请确认 backend 已启动（端口见 config/ports.env）')
    }

    const text = await res.text()
    let payload
    try {
      payload = JSON.parse(text)
    } catch {
      throw new Error(text || `上传失败 (${res.status})`)
    }

    if (!res.ok) {
      throw new Error(payload.message || `上传失败 (${res.status})`)
    }

    return { data: payload }
  }
}

export const systemApi = {
  difyStatus: () => http.get('/api/system/dify-status'),
  benchCamera: () => http.get('/api/system/bench-camera')
}

export const feedbackApi = {
  submit: (sessionId, messageId, rating) =>
    http.post(`/api/sessions/${sessionId}/messages/${messageId}/feedback`, { rating })
}

export const studentExperimentApi = {
  profileSummary: () => http.get('/api/student/experiments/profile-summary'),
  listProgress: () => http.get('/api/student/experiments/progress'),
  getProgress: (code) => http.get(`/api/student/experiments/${code}/progress`),
  completePreLab: (code) => http.post(`/api/student/experiments/${code}/progress/pre-lab`),
  completeReport: (code) => http.post(`/api/student/experiments/${code}/progress/report`),
  completeRecap: (code) => http.post(`/api/student/experiments/${code}/progress/recap`)
}

export const studentFileApi = {
  async list(experimentCode) {
    const res = await http.get('/api/student/files', { params: { experimentCode } })
    res.data = Array.isArray(res.data) ? res.data.map(withMediaUrl) : res.data
    return res
  },
  save: (data) => http.post('/api/student/files', data),
  rename: (id, data) => http.patch(`/api/student/files/${id}`, data),
  remove: (id) => http.delete(`/api/student/files/${id}`),
  async upload(file, meta = {}) {
    const fd = new FormData()
    fd.append('file', file, file.name || 'file')
    Object.entries(meta).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') fd.append(key, value)
    })
    const token = localStorage.getItem('wxz_token')

    let res
    try {
      res = await fetch(apiUrl('/api/student/files/upload'), {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: fd
      })
    } catch {
      throw new Error('无法连接后端，请确认 backend 已启动（端口见 config/ports.env）')
    }

    const text = await res.text()
    let payload
    try {
      payload = JSON.parse(text)
    } catch {
      throw new Error(text || `上传失败 (${res.status})`)
    }
    if (!res.ok) throw new Error(payload.message || `上传失败 (${res.status})`)
    return { data: payload }
  }
}

export const aiToolApi = {
  tools: () => http.get('/api/ai/tools'),
  invoke: (toolCode, data) => http.post(`/api/ai/tools/${toolCode}/invoke`, data),
  recapSessions: () => http.get('/api/ai/recap/sessions'),
  conversations: (toolCode) => http.get(`/api/ai/tools/${toolCode}/conversations`),
  createConversation: (toolCode) => http.post(`/api/ai/tools/${toolCode}/conversations`),
  messages: (conversationId) => http.get(`/api/ai/conversations/${conversationId}/messages`)
}

export const teacherApi = {
  overview: () => http.get('/api/teacher/overview'),
  reports: (params = {}) => http.get('/api/teacher/reports', { params }),
  report: (sessionId) => http.get(`/api/teacher/reports/${sessionId}`),
  reportDocx: (sessionId) => http.get(`/api/teacher/reports/${sessionId}/docx`, { responseType: 'blob' }),
  reviewReport: (sessionId) => http.post(`/api/teacher/reports/${sessionId}/ai-review`),
  classroom: (params = {}) => http.get('/api/teacher/classroom', { params }),
  feedback: (params = {}) => http.get('/api/teacher/feedback', { params }),
  markFeedbackProcessed: (feedbackId) => http.patch(`/api/teacher/feedback/${feedbackId}/processed`),
  students: () => http.get('/api/teacher/students'),
  importStudents: (data) => http.post('/api/teacher/students/import', data),
  assignExperiments: (userId, data) => http.put(`/api/teacher/students/${userId}/experiments`, data),
  bulkAssignExperiments: (data) => http.post('/api/teacher/students/assignments/bulk', data)
}
