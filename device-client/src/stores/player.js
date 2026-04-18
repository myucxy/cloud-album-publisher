import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { deviceApi } from '@/api/device'

function toNumber(value, fallback = 0) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

function stableShuffle(list, seedValue) {
  const output = [...list]
  let seed = Math.abs(toNumber(seedValue, 1)) || 1

  for (let i = output.length - 1; i > 0; i -= 1) {
    seed = (seed * 1664525 + 1013904223) % 4294967296
    const j = seed % (i + 1)
    ;[output[i], output[j]] = [output[j], output[i]]
  }

  return output
}

export function resolveMediaIdentity(media) {
  if (!media) {
    return ''
  }

  if (media.id !== undefined && media.id !== null) {
    return `id:${media.id}`
  }

  if (media.externalMediaKey) {
    return `external:${media.externalMediaKey}`
  }

  if (media.url) {
    return `url:${media.url}`
  }

  return `${media.mediaType || 'media'}:${media.fileName || ''}:${media.sortOrder ?? ''}`
}

export const usePlayerStore = defineStore('player', () => {
  const distributions = ref([])
  const pulledAt = ref('')
  const syncStatus = ref('idle')
  const errorMessage = ref('')
  const mediaErrorMessage = ref('')
  const bgmErrorMessage = ref('')
  const currentDistributionIndex = ref(0)
  const currentMediaIndex = ref(0)

  const currentDistribution = computed(() => distributions.value[currentDistributionIndex.value] || null)

  const currentMediaList = computed(() => {
    const distribution = currentDistribution.value
    if (!distribution) {
      return []
    }

    const sorted = [...(distribution.mediaList || [])].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    return distribution.shuffle ? stableShuffle(sorted, distribution.id) : sorted
  })

  const currentMedia = computed(() => currentMediaList.value[currentMediaIndex.value] || null)
  const hasPlayableContent = computed(() => distributions.value.some(item => (item.mediaList || []).length > 0))

  function resetIndices() {
    const firstPlayableIndex = distributions.value.findIndex(item => (item.mediaList || []).length > 0)

    if (firstPlayableIndex === -1) {
      currentDistributionIndex.value = 0
      currentMediaIndex.value = 0
      return
    }

    if (!distributions.value[currentDistributionIndex.value] || !(distributions.value[currentDistributionIndex.value].mediaList || []).length) {
      currentDistributionIndex.value = firstPlayableIndex
      currentMediaIndex.value = 0
      return
    }

    if (currentMediaIndex.value >= currentMediaList.value.length) {
      currentMediaIndex.value = 0
    }
  }

  function restoreSelection(previousDistributionId, previousMediaIdentity) {
    if (!previousDistributionId) {
      resetIndices()
      return
    }

    const nextDistributionIndex = distributions.value.findIndex(item => item.id === previousDistributionId && (item.mediaList || []).length > 0)
    if (nextDistributionIndex === -1) {
      resetIndices()
      return
    }

    currentDistributionIndex.value = nextDistributionIndex

    const list = currentMediaList.value
    if (!list.length) {
      currentMediaIndex.value = 0
      return
    }

    const nextMediaIndex = list.findIndex(item => resolveMediaIdentity(item) === previousMediaIdentity)
    currentMediaIndex.value = nextMediaIndex >= 0 ? nextMediaIndex : 0
  }

  async function pullContent() {
    syncStatus.value = 'loading'
    const previousDistributionId = currentDistribution.value?.id
    const previousMediaIdentity = resolveMediaIdentity(currentMedia.value)

    try {
      const res = await deviceApi.pullCurrent()
      distributions.value = res.data?.distributions || []
      pulledAt.value = res.data?.pulledAt || ''
      errorMessage.value = ''
      restoreSelection(previousDistributionId, previousMediaIdentity)
      syncStatus.value = 'ready'
      return res.data
    } catch (error) {
      syncStatus.value = 'error'
      errorMessage.value = error.response?.data?.message || error.message || '同步失败'
      throw error
    }
  }

  function selectDistribution(index) {
    currentDistributionIndex.value = index
    currentMediaIndex.value = 0
  }

  function nextMedia() {
    const distribution = currentDistribution.value
    const list = currentMediaList.value

    if (!distribution || !list.length) {
      return
    }

    if (currentMediaIndex.value < list.length - 1) {
      currentMediaIndex.value += 1
      return
    }

    if (distribution.loopPlay !== false) {
      currentMediaIndex.value = 0
    }
  }

  function previousMedia() {
    if (!currentMediaList.value.length) {
      return
    }

    if (currentMediaIndex.value > 0) {
      currentMediaIndex.value -= 1
      return
    }

    if (currentDistribution.value?.loopPlay !== false) {
      currentMediaIndex.value = Math.max(currentMediaList.value.length - 1, 0)
    }
  }

  function setMediaError(message) {
    mediaErrorMessage.value = message || ''
  }

  function clearMediaError() {
    mediaErrorMessage.value = ''
  }

  function setBgmError(message) {
    bgmErrorMessage.value = message || ''
  }

  function clearBgmError() {
    bgmErrorMessage.value = ''
  }

  return {
    distributions,
    pulledAt,
    syncStatus,
    errorMessage,
    mediaErrorMessage,
    bgmErrorMessage,
    currentDistributionIndex,
    currentMediaIndex,
    currentDistribution,
    currentMediaList,
    currentMedia,
    hasPlayableContent,
    pullContent,
    selectDistribution,
    nextMedia,
    previousMedia,
    resetIndices,
    setMediaError,
    clearMediaError,
    setBgmError,
    clearBgmError
  }
})

