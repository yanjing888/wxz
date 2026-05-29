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
  envCheck: (id) => http.post(`/api/sessions/${id}/env-check`),
  tutorialView: (id) => http.post(`/api/sessions/${id}/tutorial-view`),
  finish: (id) => http.post(`/api/sessions/${id}/finish`),
  report: (id) => http.get(`/api/sessions/${id}/report`),
  reportDocx: (id) => http.get(`/api/sessions/${id}/report/docx`, { responseType: 'blob' })
}

export const uploadApi = {
  image: (file) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.post('/api/upload', fd)
  }
}

export const systemApi = {
  difyStatus: () => http.get('/api/system/dify-status')
}
