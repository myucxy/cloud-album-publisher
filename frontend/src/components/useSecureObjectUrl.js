import request from '@/api/request'
import { onBeforeUnmount, ref, unref, watch } from 'vue'

const API_PREFIX = '/api/v1'

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
  let objectUrl = ''
  let requestToken = 0

  function clearObjectUrl() {
    if (objectUrl) {
      URL.revokeObjectURL(objectUrl)
      objectUrl = ''
    }
  }

  async function resolveSource(src) {
    requestToken += 1
    const currentToken = requestToken
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

    if (!localStorage.getItem('access_token')) {
      return
    }

    try {
      const requestUrl = normalizeProtectedRequestUrl(src)
      const blob = await request.get(requestUrl, {
        responseType: 'blob'
      })

      if (currentToken !== requestToken) {
        return
      }

      objectUrl = URL.createObjectURL(blob)
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
    clearObjectUrl()
  })

  return {
    resolvedSrc,
    error,
    refresh: () => resolveSource(resolveSourceValue(source))
  }
}
