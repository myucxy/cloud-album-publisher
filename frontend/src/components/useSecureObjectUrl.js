import request from '@/api/request'
import { onBeforeUnmount, ref, unref, watch } from 'vue'

const API_PREFIX = '/api/v1'

// Module-level blob URL cache: normalizedUrl → { objectUrl, refCount }
const blobCache = new Map()

function acquireBlobUrl(normalizedUrl) {
  const entry = blobCache.get(normalizedUrl)
  if (entry) {
    entry.refCount++
    return entry.objectUrl
  }
  return null
}

function releaseBlobUrl(normalizedUrl) {
  const entry = blobCache.get(normalizedUrl)
  if (!entry) return
  entry.refCount--
  if (entry.refCount <= 0) {
    URL.revokeObjectURL(entry.objectUrl)
    blobCache.delete(normalizedUrl)
  }
}

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

export function useSecureObjectUrl(source) {
  const resolvedSrc = ref('')
  const error = ref(null)
  let currentNormalizedUrl = ''
  let requestToken = 0

  function clearCurrentUrl() {
    if (currentNormalizedUrl) {
      releaseBlobUrl(currentNormalizedUrl)
      currentNormalizedUrl = ''
    }
  }

  async function resolveSource(src) {
    requestToken += 1
    const currentToken = requestToken
    clearCurrentUrl()
    resolvedSrc.value = ''
    error.value = null

    if (!src) {
      return
    }

    if (!isProtectedApiUrl(src)) {
      resolvedSrc.value = src
      return
    }

    if (!localStorage.getItem('access_token')) {
      return
    }

    const requestUrl = normalizeProtectedRequestUrl(src)

    // Check cache first
    const cached = acquireBlobUrl(requestUrl)
    if (cached) {
      if (currentToken !== requestToken) return
      currentNormalizedUrl = requestUrl
      resolvedSrc.value = cached
      return
    }

    try {
      const blob = await request.get(requestUrl, {
        responseType: 'blob'
      })

      if (currentToken !== requestToken) {
        return
      }

      const objectUrl = URL.createObjectURL(blob)
      blobCache.set(requestUrl, { objectUrl, refCount: 1 })
      currentNormalizedUrl = requestUrl
      resolvedSrc.value = objectUrl
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
    clearCurrentUrl()
  })

  return {
    resolvedSrc,
    error,
    refresh: () => resolveSource(resolveSourceValue(source))
  }
}
