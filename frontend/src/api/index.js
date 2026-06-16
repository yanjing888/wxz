import http from './http'
import { postSse } from './sse'

export const authApi = {
  register: (data) => http.post('/api/auth/register', data),
  login: (data) => http.post('/api/auth/login', data)
}

export const experimentApi = {
  list: () => http.get('/api/experiments'),
  get: (code) => http.get(`/api/experiments/${code}`)
}

export const sessionApi = {
  start: (data) => http.post('/api/sessions', data),
  get: (id) => http.get(`/api/sessions/${id}`),
  updateStep: (id, stepId) => http.patch(`/api/sessions/${id}/step?stepId=${stepId}`),
  assist: (id, data) => http.post(`/api/sessions/${id}/assist`, data),
  assistStream: (id, data, handlers, signal) =>
    postSse(`/api/sessions/${id}/assist/stream`, data, handlers, signal),
  envCheck: (id, data = {}) => http.post(`/api/sessions/${id}/env-check`, data),
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
  reportDocx: (id) => http.get(`/api/sessions/${id}/report/docx`, { responseType: 'blob' })
}

export const uploadApi = {
  async image(file) {
    const fd = new FormData()
    fd.append('file', file, file.name || 'image.jpg')

    let res
    try {
      res = await fetch('/api/upload', { method: 'POST', body: fd })
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
  difyStatus: () => http.get('/api/system/dify-status')
}
