import axios from 'axios'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 15000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  res => res.data,
  async err => {
    const status = err.response?.status
    if (status === 401) {
      // 尝试刷新 Token
      const refreshToken = localStorage.getItem('refresh_token')
      if (refreshToken && !err.config._retry) {
        err.config._retry = true
        try {
          const res = await axios.post('/api/v1/auth/refresh', { refreshToken })
          const { accessToken } = res.data.data
          localStorage.setItem('access_token', accessToken)
          err.config.headers.Authorization = `Bearer ${accessToken}`
          return request(err.config)
        } catch {
          const authStore = useAuthStore()
          authStore.logout()
          router.push('/login')
        }
      } else {
        const authStore = useAuthStore()
        authStore.logout()
        router.push('/login')
      }
    } else if (status === 403) {
      message.error('无权限执行该操作')
    } else {
      const msg = err.response?.data?.message || '请求失败'
      message.error(msg)
    }
    return Promise.reject(err)
  }
)

export default request
