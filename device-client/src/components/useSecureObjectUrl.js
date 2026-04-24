import request from '@/api/request'
import { onBeforeUnmount, ref, unref, watch } from 'vue'

const API_PREFIX = '/api/v1'
const OBJECT_URL_CACHE_TTL_MS = 60000
const protectedObjectUrlCache = new Map()

export function isProtectedApiUrl(src) {
  if (!src || src.startsWith('blob:') || src.startsWith('data:')) {
    return false
  }

  if (src.startsWith('/api/')) {
    return true
  }

  try {
    const url = new URL(src, window.location.origin)
    return url.pathname.startsWith('/api/')
  } catch {
    return false
  }
}

function normalizeProtectedRequestUrl(src) {
  try {
    const url = new URL(src, window.location.origin)
    const pathWithQuery = `${url.pathname}${url.search || ''}`

    if (!url.pathname.startsWith('/api/')) {
      return src
    }

    if (pathWithQuery.startsWith(`${API_PREFIX}/`) || pathWithQuery === API_PREFIX) {
      const normalizedPath = pathWithQuery.slice(API_PREFIX.length)
      return normalizedPath || '/'
    }

    return pathWithQuery
  } catch {
    return src
  }
}

function resolveSourceValue(source) {
  return typeof source === 'function' ? source() : unref(source)
}

function resolveAuthStorage(authType) {
  return authType === 'owner' ? sessionStorage : localStorage
}

function getAuthToken(authType) {
  const storage = resolveAuthStorage(authType)
  const key = authType === 'owner' ? 'owner_access_token' : 'device_access_token'
  return storage.getItem(key) || ''
}

function hasToken(authType) {
  return Boolean(getAuthToken(authType))
}

function buildProtectedCacheKey(src, authType) {
  return `${authType}:${normalizeProtectedRequestUrl(src)}`
}

function clearCacheTimer(entry) {
  if (entry?.cleanupTimer) {
    clearTimeout(entry.cleanupTimer)
    entry.cleanupTimer = null
  }
}

function scheduleCacheCleanup(cacheKey) {
  const entry = protectedObjectUrlCache.get(cacheKey)
  if (!entry || entry.refs > 0 || !entry.objectUrl || entry.cleanupTimer) {
    return
  }

  entry.cleanupTimer = setTimeout(() => {
    const latestEntry = protectedObjectUrlCache.get(cacheKey)
    if (!latestEntry || latestEntry.refs > 0 || !latestEntry.objectUrl) {
      return
    }

    URL.revokeObjectURL(latestEntry.objectUrl)
    protectedObjectUrlCache.delete(cacheKey)
  }, OBJECT_URL_CACHE_TTL_MS)
}

async function ensureProtectedObjectUrl(src, authType = 'device') {
  const cacheKey = buildProtectedCacheKey(src, authType)
  let entry = protectedObjectUrlCache.get(cacheKey)

  if (!entry) {
    entry = {
      refs: 0,
      objectUrl: '',
      promise: null,
      cleanupTimer: null
    }
    protectedObjectUrlCache.set(cacheKey, entry)
  }

  clearCacheTimer(entry)

  if (entry.objectUrl) {
    return {
      cacheKey,
      objectUrl: entry.objectUrl
    }
  }

  if (!entry.promise) {
    const requestUrl = normalizeProtectedRequestUrl(src)
    entry.promise = request
      .get(requestUrl, {
        responseType: 'blob',
        authType
      })
      .then(blob => {
        if (entry.objectUrl) {
          URL.revokeObjectURL(entry.objectUrl)
        }

        entry.objectUrl = URL.createObjectURL(blob)
        entry.promise = null
        scheduleCacheCleanup(cacheKey)
        return entry.objectUrl
      })
      .catch(error => {
        entry.promise = null
        if (!entry.objectUrl && entry.refs === 0) {
          protectedObjectUrlCache.delete(cacheKey)
        }
        throw error
      })
  }

  return {
    cacheKey,
    objectUrl: await entry.promise
  }
}

function retainProtectedObjectUrl(cacheKey) {
  const entry = protectedObjectUrlCache.get(cacheKey)
  if (!entry) {
    return
  }

  clearCacheTimer(entry)
  entry.refs += 1
}

function releaseProtectedObjectUrl(cacheKey) {
  const entry = protectedObjectUrlCache.get(cacheKey)
  if (!entry) {
    return
  }

  entry.refs = Math.max(0, entry.refs - 1)
  scheduleCacheCleanup(cacheKey)
}

export function warmSecureObjectUrl(src, options = {}) {
  const authType = options.authType || 'device'
  if (!src || !isProtectedApiUrl(src) || !hasToken(authType)) {
    return Promise.resolve('')
  }

  return ensureProtectedObjectUrl(src, authType).then(result => result.objectUrl)
}

export function useSecureObjectUrl(source, options = {}) {
  const resolvedSrc = ref('')
  const error = ref(null)
  const authType = options.authType || 'device'
  let objectUrl = ''
  let requestToken = 0
  let retainedCacheKey = ''

  function clearObjectUrl() {
    if (objectUrl) {
      objectUrl = ''
    }
  }

  function clearRetainedCacheKey() {
    if (retainedCacheKey) {
      releaseProtectedObjectUrl(retainedCacheKey)
      retainedCacheKey = ''
    }
  }

  async function resolveSource(src) {
    requestToken += 1
    const currentToken = requestToken
    clearRetainedCacheKey()
    clearObjectUrl()
    resolvedSrc.value = ''
    error.value = null

    if (!src) {
      return
    }

    if (!isProtectedApiUrl(src)) {
      resolvedSrc.value = src
      return
    }

    if (!hasToken(authType)) {
      return
    }

    try {
      const { cacheKey, objectUrl: nextObjectUrl } = await ensureProtectedObjectUrl(src, authType)
      if (currentToken !== requestToken) {
        return
      }

      retainProtectedObjectUrl(cacheKey)
      retainedCacheKey = cacheKey
      objectUrl = nextObjectUrl
      resolvedSrc.value = nextObjectUrl
    } catch (err) {
      if (currentToken !== requestToken) {
        return
      }
      error.value = err
      resolvedSrc.value = ''
    }
  }

  watch(() => resolveSourceValue(source), resolveSource, { immediate: true })

  onBeforeUnmount(() => {
    requestToken += 1
    clearRetainedCacheKey()
    clearObjectUrl()
  })

  return {
    resolvedSrc,
    error,
    refresh: () => resolveSource(resolveSourceValue(source))
  }
}
