import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { deviceApi } from '@/api/device'

const DISABLED_DISTRIBUTION_IDS_STORAGE_KEY = 'device_disabled_distribution_ids'
const PLAYBACK_MUTED_STORAGE_KEY = 'device_playback_muted'
const SUPPORTED_TRANSITION_STYLES = ['NONE', 'FADE', 'SLIDE', 'CUBE', 'REVEAL', 'FLIP', 'RANDOM']
const SUPPORTED_DISPLAY_STYLES = ['SINGLE', 'BENTO', 'FRAME_WALL', 'FRAMEWALL', 'CAROUSEL', 'CALENDAR']

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

function loadDisabledDistributionIds() {
  try {
    const raw = localStorage.getItem(DISABLED_DISTRIBUTION_IDS_STORAGE_KEY)
    const parsed = JSON.parse(raw || '[]')
    return Array.isArray(parsed) ? parsed.map(value => String(value)) : []
  } catch {
    return []
  }
}

function saveDisabledDistributionIds(values) {
  localStorage.setItem(DISABLED_DISTRIBUTION_IDS_STORAGE_KEY, JSON.stringify(values))
}

function loadPlaybackMuted() {
  return localStorage.getItem(PLAYBACK_MUTED_STORAGE_KEY) === 'true'
}

function savePlaybackMuted(value) {
  localStorage.setItem(PLAYBACK_MUTED_STORAGE_KEY, value ? 'true' : 'false')
}

function getPlayableMediaList(distribution) {
  if (!distribution || !Array.isArray(distribution.mediaList)) {
    return []
  }

  const sorted = [...distribution.mediaList].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
  return distribution.shuffle ? stableShuffle(sorted, distribution.id) : sorted
}

function getSortedBgmList(distribution) {
  const bgmList = distribution?.album?.bgmList
  if (!Array.isArray(bgmList)) {
    return []
  }
  return [...bgmList].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
}

function normalizeTransitionStyle(value) {
  if (!value || !String(value).trim()) {
    return 'NONE'
  }
  const normalized = String(value).trim().toUpperCase()
  return SUPPORTED_TRANSITION_STYLES.includes(normalized) ? normalized : 'NONE'
}

function normalizeDisplayStyle(value) {
  if (!value || !String(value).trim()) {
    return 'SINGLE'
  }
  const normalized = String(value).trim().toUpperCase()
  if (normalized === 'FRAMEWALL') {
    return 'FRAME_WALL'
  }
  return SUPPORTED_DISPLAY_STYLES.includes(normalized) ? normalized : 'SINGLE'
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

export function resolveBgmIdentity(bgm) {
  if (!bgm) {
    return ''
  }

  if (bgm.mediaId !== undefined && bgm.mediaId !== null) {
    return `id:${bgm.mediaId}`
  }

  if (bgm.externalMediaKey) {
    return `external:${bgm.externalMediaKey}`
  }

  if (bgm.url) {
    return `url:${bgm.url}`
  }

  return bgm.fileName || ''
}

export const usePlayerStore = defineStore('player', () => {
  const distributions = ref([])
  const disabledDistributionIds = ref(loadDisabledDistributionIds())
  const playbackMuted = ref(loadPlaybackMuted())
  const pulledAt = ref('')
  const syncStatus = ref('idle')
  const errorMessage = ref('')
  const mediaErrorMessage = ref('')
  const bgmErrorMessage = ref('')
  const currentDistributionIndex = ref(0)
  const currentMediaIndex = ref(0)
  const currentBgmIndex = ref(0)

  const enabledDistributions = computed(() => {
    return distributions.value.filter(distribution => {
      return !disabledDistributionIds.value.includes(String(distribution?.id)) && getPlayableMediaList(distribution).length > 0
    })
  })

  const currentDistribution = computed(() => enabledDistributions.value[currentDistributionIndex.value] || null)
  const currentMediaList = computed(() => getPlayableMediaList(currentDistribution.value))
  const currentMedia = computed(() => currentMediaList.value[currentMediaIndex.value] || null)
  const currentBgmList = computed(() => getSortedBgmList(currentDistribution.value))
  const currentBgm = computed(() => currentBgmList.value[currentBgmIndex.value] || null)
  const currentBgmUrl = computed(() => currentBgm.value?.url || currentDistribution.value?.album?.bgmUrl || '')
  const currentBgmVolume = computed(() => currentDistribution.value?.album?.bgmVolume ?? 40)
  const currentTransitionStyle = computed(() => normalizeTransitionStyle(currentDistribution.value?.album?.transitionStyle))
  const currentDisplayStyle = computed(() => normalizeDisplayStyle(currentDistribution.value?.album?.displayStyle))
  const currentDisplayVariant = computed(() => currentDistribution.value?.album?.displayVariant || 'DEFAULT')
  const showTimeAndDate = computed(() => Boolean(currentDistribution.value?.album?.showTimeAndDate))
  const selectableDistributionCount = computed(() => {
    return distributions.value.filter(distribution => getPlayableMediaList(distribution).length > 0).length
  })
  const enabledDistributionCount = computed(() => enabledDistributions.value.length)
  const hasPlayableContent = computed(() => enabledDistributions.value.length > 0)

  function persistDisabledDistributionIds() {
    disabledDistributionIds.value = Array.from(new Set(disabledDistributionIds.value.map(value => String(value))))
    saveDisabledDistributionIds(disabledDistributionIds.value)
  }

  function setPlaybackMuted(value) {
    playbackMuted.value = Boolean(value)
    savePlaybackMuted(playbackMuted.value)
  }

  function resetIndices() {
    currentDistributionIndex.value = 0
    currentMediaIndex.value = 0
    currentBgmIndex.value = 0
  }

  function syncDisabledDistributionSelection() {
    const availableDistributionIds = new Set(
      distributions.value
        .filter(distribution => getPlayableMediaList(distribution).length > 0)
        .map(distribution => String(distribution.id))
    )

    disabledDistributionIds.value = disabledDistributionIds.value.filter(id => availableDistributionIds.has(id))
    persistDisabledDistributionIds()
  }

  function restoreSelection(previousDistributionId, previousMediaIdentity) {
    const enabled = enabledDistributions.value
    if (!enabled.length || !previousDistributionId) {
      resetIndices()
      return
    }

    const nextDistributionIndex = enabled.findIndex(item => item.id === previousDistributionId)
    if (nextDistributionIndex === -1) {
      resetIndices()
      return
    }

    currentDistributionIndex.value = nextDistributionIndex
    currentMediaIndex.value = 0

    const list = currentMediaList.value
    const nextMediaIndex = list.findIndex(item => resolveMediaIdentity(item) === previousMediaIdentity)
    if (nextMediaIndex >= 0) {
      currentMediaIndex.value = nextMediaIndex
    }
  }

  function restoreBgmSelection(previousBgmIdentity) {
    const list = currentBgmList.value
    if (!list.length) {
      currentBgmIndex.value = 0
      return
    }

    currentBgmIndex.value = 0
    if (!previousBgmIdentity) {
      return
    }

    const nextBgmIndex = list.findIndex(item => resolveBgmIdentity(item) === previousBgmIdentity)
    if (nextBgmIndex >= 0) {
      currentBgmIndex.value = nextBgmIndex
    }
  }

  function setDistributionEnabled(distributionId, enabled) {
    const normalizedId = String(distributionId)
    const previousDistributionId = currentDistribution.value?.id
    const previousMediaIdentity = resolveMediaIdentity(currentMedia.value)
    const previousBgmIdentity = resolveBgmIdentity(currentBgm.value)

    if (enabled) {
      disabledDistributionIds.value = disabledDistributionIds.value.filter(id => id !== normalizedId)
    } else if (!disabledDistributionIds.value.includes(normalizedId)) {
      disabledDistributionIds.value = [...disabledDistributionIds.value, normalizedId]
    }

    syncDisabledDistributionSelection()
    restoreSelection(previousDistributionId, previousMediaIdentity)
    restoreBgmSelection(previousBgmIdentity)
  }

  function selectDistribution(distributionId) {
    const nextDistributionIndex = enabledDistributions.value.findIndex(item => item.id === distributionId)
    if (nextDistributionIndex === -1) {
      return
    }

    currentDistributionIndex.value = nextDistributionIndex
    currentMediaIndex.value = 0
    currentBgmIndex.value = 0
  }

  function getNextDistributionIndex() {
    const enabled = enabledDistributions.value
    if (!enabled.length) {
      return -1
    }

    if (enabled.length === 1) {
      return 0
    }

    const nextDistributionIndex = currentDistributionIndex.value + 1
    return nextDistributionIndex >= enabled.length ? 0 : nextDistributionIndex
  }

  function peekNextMedia() {
    const distribution = currentDistribution.value
    const list = currentMediaList.value

    if (!distribution || !list.length) {
      return null
    }

    if (currentMediaIndex.value < list.length - 1) {
      return list[currentMediaIndex.value + 1]
    }

    const nextDistributionIndex = getNextDistributionIndex()
    if (nextDistributionIndex === -1) {
      return null
    }

    const nextDistribution = enabledDistributions.value[nextDistributionIndex]
    const nextList = getPlayableMediaList(nextDistribution)
    return nextList[0] || null
  }

  async function pullContent() {
    syncStatus.value = 'loading'
    const previousDistributionId = currentDistribution.value?.id
    const previousMediaIdentity = resolveMediaIdentity(currentMedia.value)
    const previousBgmIdentity = resolveBgmIdentity(currentBgm.value)

    try {
      const res = await deviceApi.pullCurrent()
      distributions.value = res.data?.distributions || []
      pulledAt.value = res.data?.pulledAt || ''
      errorMessage.value = ''
      syncDisabledDistributionSelection()
      restoreSelection(previousDistributionId, previousMediaIdentity)
      restoreBgmSelection(previousBgmIdentity)
      syncStatus.value = 'ready'
      return res.data
    } catch (error) {
      syncStatus.value = 'error'
      errorMessage.value = error.response?.data?.message || error.message || '同步失败'
      throw error
    }
  }

  function nextMedia() {
    const distribution = currentDistribution.value
    const list = currentMediaList.value

    if (!distribution || !list.length) {
      resetIndices()
      return
    }

    if (currentMediaIndex.value < list.length - 1) {
      currentMediaIndex.value += 1
      return
    }

    const nextDistributionIndex = getNextDistributionIndex()
    if (nextDistributionIndex >= 0 && nextDistributionIndex !== currentDistributionIndex.value) {
      currentDistributionIndex.value = nextDistributionIndex
      currentMediaIndex.value = 0
      currentBgmIndex.value = 0
      return
    }

    currentMediaIndex.value = 0
  }

  function previousMedia() {
    const list = currentMediaList.value
    if (!list.length) {
      return
    }

    if (currentMediaIndex.value > 0) {
      currentMediaIndex.value -= 1
      return
    }

    currentMediaIndex.value = Math.max(list.length - 1, 0)
  }

  function nextBgm() {
    const list = currentBgmList.value
    if (!list.length) {
      currentBgmIndex.value = 0
      return
    }

    currentBgmIndex.value = (currentBgmIndex.value + 1) % list.length
  }

  function resetBgmSelection() {
    currentBgmIndex.value = 0
  }

  function isDistributionEnabled(distributionId) {
    return !disabledDistributionIds.value.includes(String(distributionId))
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
    disabledDistributionIds,
    playbackMuted,
    pulledAt,
    syncStatus,
    errorMessage,
    mediaErrorMessage,
    bgmErrorMessage,
    currentDistributionIndex,
    currentMediaIndex,
    currentBgmIndex,
    enabledDistributions,
    currentDistribution,
    currentMediaList,
    currentMedia,
    currentBgmList,
    currentBgm,
    currentBgmUrl,
    currentBgmVolume,
    currentTransitionStyle,
    currentDisplayStyle,
    currentDisplayVariant,
    showTimeAndDate,
    selectableDistributionCount,
    enabledDistributionCount,
    hasPlayableContent,
    pullContent,
    setPlaybackMuted,
    peekNextMedia,
    selectDistribution,
    setDistributionEnabled,
    nextMedia,
    previousMedia,
    nextBgm,
    resetIndices,
    resetBgmSelection,
    isDistributionEnabled,
    setMediaError,
    clearMediaError,
    setBgmError,
    clearBgmError
  }
})
