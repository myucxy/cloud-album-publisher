import axios from 'axios'
import { message } from 'ant-design-vue'

export function sanitizeBaseUrl(value) {
  return (value || 'http://localhost:8080').trim().replace(/\/+$/, '')
}

function clearDeviceSession() {
  localStorage.removeItem('device_access_token')
  localStorage.removeItem('device_id')
}

const request = axios.create({
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const authType = config.authType || 'device'
  const baseUrl = sanitizeBaseUrl(localStorage.getItem('device_server_base_url') || 'http://localhost:8080')

  config.baseURL = `${baseUrl}/api/v1`
  config.headers = config.headers || {}

  if (authType === 'owner') {
    const token = sessionStorage.getItem('owner_access_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
  } else if (authType === 'device') {
    const token = localStorage.getItem('device_access_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
  }

  return config
})

request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const status = error.response?.status
    const authType = error.config?.authType || 'device'
    const messageText = error.response?.data?.message || error.message || '请求失败'

    if (status === 401 && authType === 'device') {
      clearDeviceSession()
      message.error('设备访问已失效，请重新激活')
      if (window.location.hash !== '#/activate') {
        window.location.hash = '#/activate'
      }
    } else if (status === 403) {
      message.error(error.response?.data?.message || '无权限执行该操作')
    } else if (status !== 401) {
      message.error(messageText)
    }

    return Promise.reject(error)
  }
)

export default request
