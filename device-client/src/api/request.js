import axios from 'axios'
import { message } from 'ant-design-vue'

const DEFAULT_SERVER_ADDRESS = 'localhost:8080'
const SUCCESS_CODE = 200

export function sanitizeServerAddress(value) {
  let normalized = String(value || '').trim()
  if (!normalized) {
    normalized = DEFAULT_SERVER_ADDRESS
  }

  const candidate = /^https?:\/\//i.test(normalized) ? normalized : `http://${normalized}`

  try {
    const url = new URL(candidate)
    if (!url.port && url.protocol === 'http:') {
      url.port = '8080'
    }
    return url.host || DEFAULT_SERVER_ADDRESS
  } catch {
    return DEFAULT_SERVER_ADDRESS
  }
}

export function buildApiBaseUrl(value) {
  const normalized = sanitizeServerAddress(value)
  return `http://${normalized}`
}

function clearDeviceSession() {
  localStorage.removeItem('device_access_token')
  localStorage.removeItem('device_id')
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
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const authType = config.authType || 'device'
  const serverAddress = sanitizeServerAddress(localStorage.getItem('device_server_base_url') || DEFAULT_SERVER_ADDRESS)

  config.baseURL = `${buildApiBaseUrl(serverAddress)}/api/v1`
  config.headers = config.headers || {}

  if (authType === 'device') {
    const token = localStorage.getItem('device_access_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
  }

  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body.code === 'number' && body.code !== SUCCESS_CODE) {
      return Promise.reject(toApiError(response))
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    const authType = error.config?.authType || 'device'
    const messageText = error.response?.data?.message || error.message || '请求失败'

    if (status === 401 && authType === 'device') {
      clearDeviceSession()
      message.error('设备访问已失效，请重新等待绑定')
      if (window.location.hash !== '#/activate') {
        window.location.hash = '#/activate'
      }
    } else if (status === 403) {
      message.error(error.response?.data?.message || '无权限执行该操作')
    } else if (status !== 401 && !error.config?.silent) {
      message.error(messageText)
    }

    return Promise.reject(error)
  }
)

export default request
