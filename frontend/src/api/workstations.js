import http from './http'

const terminalHeaders = () => ({ 'X-Terminal-Token': localStorage.getItem('wxz_terminal_token') || '' })
export const workstationApi = {
  overview: () => http.get('/api/teacher/workstations/overview'),
  bindingStudents: roomId => http.get('/api/teacher/workstations/binding-students', { params: { roomId } }),
  bindStudents: (id, className, studentIds) => http.put(`/api/teacher/workstations/${id}/bindings`, { className, studentIds }),
  clearBindings: roomId => http.delete('/api/teacher/workstations/bindings', { params: { roomId } }),
  saveSimple: (roomId, data, id) => http.post('/api/admin/workstations/simple', data, { params: { roomId, id } }),
  rooms: () => http.get('/api/workstations/rooms'),
  createRoom: name => http.post('/api/admin/workstations/rooms', { name }),
  importCamera: (roomId, name) => http.post('/api/admin/workstations/deployment-camera', { name }, { params: { roomId } }),
  mySeat: () => http.get('/api/workstations/my-seat'),
  leaveSeat: () => http.delete('/api/workstations/my-seat'),
  groupCode: () => http.post('/api/workstations/group-code', {}, { headers: terminalHeaders() }),
  joinGroup: (code, sessionId) => http.post('/api/workstations/join-group', { code, sessionId }),
  dashboard: (experimentCode, roomId) => http.get('/api/teacher/workstations', { params: { experimentCode, roomId } }),
  adminDashboard: (experimentCode, roomId) => http.get('/api/admin/workstations', { params: { experimentCode, roomId } }),
  renameRoom: (experimentCode, name, roomId) => http.put('/api/admin/workstations/room', { name }, { params: { experimentCode, roomId } }),
  save: (experimentCode, data, id, roomId) => http[id ? 'put' : 'post'](`/api/admin/workstations${id ? '/' + id : ''}`, data, { params: { experimentCode, roomId } }),
  config: (id) => http.get(`/api/admin/workstations/${id}/config`),
  startLesson: (experimentCode, className, roomId) => http.post('/api/teacher/workstations/lesson', { className }, { params: { experimentCode, roomId } }),
  closeLesson: (experimentCode, roomId) => http.delete('/api/teacher/workstations/lesson', { params: { experimentCode, roomId } }),
  pairCode: (id) => http.post(`/api/admin/workstations/${id}/pair-code`),
  publisherKey: (id) => http.post(`/api/admin/workstations/${id}/publisher-key`),
  addMember: (id, sessionId) => http.post(`/api/teacher/workstations/${id}/members`, { sessionId }),
  releaseStation: (id) => http.delete(`/api/teacher/workstations/${id}/occupancy`),
  history: (id) => http.get(`/api/teacher/workstations/${id}/history`),
  logs: (id) => http.get(`/api/teacher/workstations/${id}/logs`),
  inspection: (id, enabled) => http.patch(`/api/teacher/workstations/${id}/inspection`, { enabled }),
  inspect: (id) => http.post(`/api/workstations/${id}/inspect`, {}, { headers: terminalHeaders(), timeout: 180000 }),
  frame: (id, signal) => http.get(`/api/workstations/${id}/frame`, { headers: terminalHeaders(), responseType: 'blob', signal }),
  pair: (code) => http.post('/api/admin/workstations/pair', { code }),
  terminal: () => http.get('/api/workstations/terminal', { headers: terminalHeaders() }),
  claim: (sessionId) => http.post('/api/workstations/claim', { sessionId }, { headers: terminalHeaders() }),
  leave: () => http.delete('/api/workstations/claim', { headers: terminalHeaders() })
}
export const workstationError = (e) => e.response?.data?.message || e.message || '操作失败，请重试'
