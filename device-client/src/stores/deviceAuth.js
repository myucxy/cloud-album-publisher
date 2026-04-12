import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import { deviceApi } from '@/api/device'
import { sanitizeBaseUrl } from '@/api/request'

function getFallbackIdentity() {
  const existingUid = localStorage.getItem('device_fallback_uid')
  if (existingUid) {
    return {
      deviceUid: existingUid,
      platform: navigator.platform || 'web',
      hostname: window.location.hostname || 'browser-device'
    }
  }

  const entropy = [
    navigator.userAgent,
    navigator.language,
    new Intl.DateTimeFormat().resolvedOptions().timeZone,
    screen.width,
    screen.height,
    screen.colorDepth
  ].join('|')
  const generatedUid = `desktop-web-${btoa(entropy).replace(/[^a-zA-Z0-9]/g, '').slice(0, 16).toLowerCase()}`
  localStorage.setItem('device_fallback_uid', generatedUid)
  return {
    deviceUid: generatedUid,
    platform: navigator.platform || 'web',
    hostname: window.location.hostname || 'browser-device'
  }
}

function resolveDeviceType(platform) {
  const normalized = String(platform || '').toLowerCase()
  if (normalized.includes('win')) return 'WINDOWS'
  if (normalized.includes('mac') || normalized.includes('darwin')) return 'MACOS'
  if (normalized.includes('linux')) return 'LINUX'
  return 'DESKTOP'
}

export const useDeviceAuthStore = defineStore('deviceAuth', () => {
  const initialized = ref(false)
  const serverBaseUrl = ref(localStorage.getItem('device_server_base_url') || 'http://localhost:8080')
  const deviceUid = ref(localStorage.getItem('device_uid') || '')
  const deviceId = ref(localStorage.getItem('device_id') || '')
  const deviceName = ref(localStorage.getItem('device_name') || '')
  const deviceAccessToken = ref(localStorage.getItem('device_access_token') || '')
  const platform = ref(localStorage.getItem('device_platform') || '')
  const hostname = ref(localStorage.getItem('device_hostname') || '')
  const ownerUsername = ref(sessionStorage.getItem('owner_username') || '')

  const isActivated = computed(() => Boolean(deviceAccessToken.value))
  const deviceType = computed(() => resolveDeviceType(platform.value))

  async function initializeIdentity() {
    if (initialized.value) {
      return
    }

    const identity = window.deviceBridge?.getDeviceIdentity
      ? await window.deviceBridge.getDeviceIdentity()
      : getFallbackIdentity()

    deviceUid.value = identity.deviceUid
    platform.value = identity.platform
    hostname.value = identity.hostname

    if (!deviceName.value) {
      deviceName.value = identity.hostname || identity.deviceUid
    }

    localStorage.setItem('device_uid', deviceUid.value)
    localStorage.setItem('device_platform', platform.value)
    localStorage.setItem('device_hostname', hostname.value)
    localStorage.setItem('device_name', deviceName.value)
    initialized.value = true
  }

  function saveSettings(payload = {}) {
    serverBaseUrl.value = sanitizeBaseUrl(payload.serverBaseUrl ?? serverBaseUrl.value)
    deviceName.value = String(payload.deviceName ?? deviceName.value).trim() || hostname.value || deviceUid.value

    localStorage.setItem('device_server_base_url', serverBaseUrl.value)
    localStorage.setItem('device_name', deviceName.value)
  }

  async function loginOwner(credentials) {
    const res = await authApi.login(credentials)
    const data = res.data

    sessionStorage.setItem('owner_access_token', data.accessToken)
    sessionStorage.setItem('owner_refresh_token', data.refreshToken)
    sessionStorage.setItem('owner_username', data.username)
    ownerUsername.value = data.username

    return data
  }

  function clearOwnerSession() {
    ownerUsername.value = ''
    sessionStorage.removeItem('owner_access_token')
    sessionStorage.removeItem('owner_refresh_token')
    sessionStorage.removeItem('owner_username')
  }

  function clearDeviceSession() {
    deviceAccessToken.value = ''
    deviceId.value = ''
    localStorage.removeItem('device_access_token')
    localStorage.removeItem('device_id')
  }

  async function bindCurrentDevice() {
    const res = await deviceApi.bind({
      deviceUid: deviceUid.value,
      type: deviceType.value,
      name: deviceName.value || hostname.value || deviceUid.value
    })

    return res.data
  }

  async function issueDeviceToken() {
    const res = await deviceApi.createAccessToken(deviceUid.value)
    const data = res.data

    deviceAccessToken.value = data.accessToken
    deviceId.value = String(data.deviceId)
    localStorage.setItem('device_access_token', data.accessToken)
    localStorage.setItem('device_id', data.deviceId)
    return data
  }

  async function activateDevice(credentials) {
    await initializeIdentity()
    saveSettings()
    await loginOwner(credentials)

    try {
      await bindCurrentDevice()
    } catch (error) {
      if (error.response?.status !== 409) {
        clearOwnerSession()
        throw error
      }
    }

    try {
      const tokenData = await issueDeviceToken()
      clearOwnerSession()
      return tokenData
    } catch (error) {
      clearOwnerSession()
      throw error
    }
  }

  return {
    initialized,
    serverBaseUrl,
    deviceUid,
    deviceId,
    deviceName,
    deviceAccessToken,
    platform,
    hostname,
    ownerUsername,
    isActivated,
    deviceType,
    initializeIdentity,
    saveSettings,
    loginOwner,
    clearOwnerSession,
    clearDeviceSession,
    bindCurrentDevice,
    issueDeviceToken,
    activateDevice
  }
})
