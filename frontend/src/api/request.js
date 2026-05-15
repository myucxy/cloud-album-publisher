import axios from 'axios'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const SUCCESS_CODE = 200
const UNAUTHORIZED_CODE = 401

function redirectToLogin() {
  const authStore = useAuthStore()
  authStore.clearAuth()
  if (router.currentRoute.value.path !== '/login') {
    router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
}

function toApiError(response, fallbackMessage = '请求失败') {
  const body = response?.data || {}
  const error = new Error(body.message || fallbackMessage)
  error.response = {
    status: body.code || response?.status,
    data: body,
    headers: response?.headers,
    config: response?.config
  }
  error.config = response?.config
  return error
}

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
  res => {
    const body = res.data
    if (body && typeof body.code === 'number' && body.code !== SUCCESS_CODE) {
      if (body.code === UNAUTHORIZED_CODE) {
        redirectToLogin()
      }
      const errMsg = body.message || '请求失败'
      message.error(errMsg)
      return Promise.reject(toApiError(res))
    }
    return body
  },
  async err => {
    const status = err.response?.status
    const url = err.config?.url || ''
    if (status === 401 && !url.startsWith('/auth/login') && !url.startsWith('/auth/register') && !url.startsWith('/auth/refresh')) {
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
          redirectToLogin()
        }
      } else {
        redirectToLogin()
      }
    } else if (status === 403) {
      message.error(err.response?.data?.message || '无权限执行该操作')
    } else {
      const msg = err.response?.data?.message || err.message || '请求失败'
      message.error(msg)
    }
    return Promise.reject(err)
  }
)

export default request
