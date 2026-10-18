import http from './http'
export const adminApi = {
  users: (params = {}) => http.get('/api/admin/users', { params }),
  saveUser: (data, id) => http[id ? 'put' : 'post'](`/api/admin/users${id ? '/' + id : ''}`, data),
  resetPassword: (id, password) => http.patch(`/api/admin/users/${id}/password`, { password }),
  audit: () => http.get('/api/admin/audit')
}
