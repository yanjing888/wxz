import axios from 'axios'

const http = axios.create({ baseURL: '' })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('wxz_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      ;['wxz_token', 'wxz_userId', 'wxz_username', 'wxz_displayName', 'wxz_class'].forEach((key) =>
        localStorage.removeItem(key)
      )
      if (window.location.pathname !== '/login') {
        window.location.assign('/login')
      }
    }
    return Promise.reject(error)
  }
)

export default http
