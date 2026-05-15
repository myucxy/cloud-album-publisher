import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { deviceApi } from '@/api/device'
import { sanitizeServerAddress } from '@/api/request'

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
  const serverBaseUrl = ref(sanitizeServerAddress(localStorage.getItem('device_server_base_url') || 'localhost:8080'))
  const deviceUid = ref(localStorage.getItem('device_uid') || '')
  const deviceId = ref(localStorage.getItem('device_id') || '')
  const deviceName = ref(localStorage.getItem('device_name') || '')
  const deviceAccessToken = ref(localStorage.getItem('device_access_token') || '')
  const platform = ref(localStorage.getItem('device_platform') || '')
  const hostname = ref(localStorage.getItem('device_hostname') || '')

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
    serverBaseUrl.value = sanitizeServerAddress(payload.serverBaseUrl ?? serverBaseUrl.value)
    deviceName.value = String(payload.deviceName ?? deviceName.value).trim() || hostname.value || deviceUid.value

    localStorage.setItem('device_server_base_url', serverBaseUrl.value)
    localStorage.setItem('device_name', deviceName.value)
  }

  function clearDeviceSession() {
    deviceAccessToken.value = ''
    deviceId.value = ''
    localStorage.removeItem('device_access_token')
    localStorage.removeItem('device_id')
  }

  async function registerCurrentDevice() {
    const res = await deviceApi.selfRegister({
      deviceUid: deviceUid.value,
      type: deviceType.value,
      name: deviceName.value || hostname.value || deviceUid.value
    })
    if (res.data?.id !== undefined && res.data?.id !== null) {
      deviceId.value = String(res.data.id)
      localStorage.setItem('device_id', deviceId.value)
    }
    return res.data
  }

  async function issueDeviceToken() {
    const res = await deviceApi.createSelfAccessToken(deviceUid.value)
    const data = res.data
    if (!data?.accessToken) {
      throw new Error(res.message || '设备令牌为空，请先确认设备已在后台完成绑定')
    }

    deviceAccessToken.value = data.accessToken
    deviceId.value = String(data.deviceId)
    localStorage.setItem('device_access_token', data.accessToken)
    localStorage.setItem('device_id', data.deviceId)
    return data
  }

  async function registerAndTryActivate() {
    await initializeIdentity()
    saveSettings()
    await registerCurrentDevice()
    try {
      return await issueDeviceToken()
    } catch (error) {
      if (error.response?.status === 409) {
        return null
      }
      throw error
    }
  }

  async function discoverServers() {
    let localIp = ''
    try {
      const pc = new RTCPeerConnection({ iceServers: [] })
      pc.createDataChannel('')
      pc.createOffer().then(offer => pc.setLocalDescription(offer))
      await new Promise(resolve => {
        pc.onicecandidate = event => {
          if (event.candidate) {
            const match = event.candidate.candidate.match(/(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})/)
            if (match && match[1] !== '0.0.0.0') {
              localIp = match[1]
              pc.close()
              resolve()
            }
          }
        }
        setTimeout(() => { pc.close(); resolve() }, 2000)
      })
    } catch {
      return []
    }

    if (!localIp) return []

    const subnet = localIp.split('.').slice(0, 3).join('.')
    const ports = [8080]
    try {
      const parts = serverBaseUrl.value.split(':')
      const savedPort = parseInt(parts[parts.length - 1], 10)
      if (savedPort && savedPort !== 8080) ports.push(savedPort)
    } catch {}
    const promises = []
    for (let i = 1; i <= 254; i++) {
      const ip = `${subnet}.${i}`
      for (const port of ports) {
        promises.push(
          fetch(`http://${ip}:${port}/api/v1/discover`, { signal: AbortSignal.timeout(800) })
            .then(res => res.json())
            .then(body => {
              if (body?.data?.name === 'CloudAlbum') {
                return { address: `${ip}:${body.data.port || port}`, name: body.data.name }
              }
              return null
            })
            .catch(() => null)
        )
      }
    }

    const results = await Promise.allSettled(promises)
    const seen = new Set()
    return results
      .filter(r => r.status === 'fulfilled' && r.value)
      .map(r => r.value)
      .filter(server => {
        if (seen.has(server.address)) return false
        seen.add(server.address)
        return true
      })
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
    isActivated,
    deviceType,
    initializeIdentity,
    saveSettings,
    clearDeviceSession,
    registerCurrentDevice,
    issueDeviceToken,
    registerAndTryActivate,
    discoverServers
  }
})
