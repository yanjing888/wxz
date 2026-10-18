import http from './http'
export const learningApi = {
  student: className => http.get('/api/student/learning', { params: { className } }),
  teacher: (className, experimentCode) => http.get('/api/teacher/learning', { params: { className, experimentCode } }),
  detail: (id, className, experimentCode) => http.get(`/api/teacher/learning/students/${id}`, { params: { className, experimentCode } }),
  reflect: (code, content, feedbackRevision) => http.put(`/api/student/learning/${encodeURIComponent(code)}/reflection`, { content, feedbackRevision })
}
export const learningError = e => e.response?.data?.message || '加载失败，请重试'
