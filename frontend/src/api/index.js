import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 60000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('campus_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  res => {
    if (res.data && res.data.code && res.data.code >= 400) {
      return Promise.reject(new Error(res.data.message || '请求失败'))
    }
    return res.data
  },
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('campus_token')
      localStorage.removeItem('campus_user')
      window.location.hash = '#/login'
    }
    const msg = err.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

export default api
