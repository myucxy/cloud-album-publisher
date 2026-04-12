<template>
  <div
    class="player-page"
    :class="{ 'sidebar-hidden': !sidebarVisible }"
    @mousemove="revealSidebarHandle"
    @mouseleave="hideSidebarHandle"
  >
    <audio ref="bgmRef" hidden loop @error="handleBgmError" />

    <div class="sidebar-panel">
      <PlaylistSidebar
        :device="deviceSummary"
        :distributions="player.distributions"
        :current-distribution-index="player.currentDistributionIndex"
        :current-distribution="currentDistribution"
        :current-media="currentMedia"
        :pulled-at="player.pulledAt"
        :sync-status="player.syncStatus"
        :error-message="sidebarErrorMessage"
        @select-distribution="player.selectDistribution"
        @refresh="refresh"
        @open-setup="router.push('/setup')"
      />
    </div>

    <div class="content-panel">
      <button
        v-show="showSidebarHandle"
        type="button"
        class="sidebar-handle"
        :aria-label="sidebarVisible ? '隐藏侧边栏' : '显示侧边栏'"
        @click="sidebarVisible = !sidebarVisible"
      >
        {{ sidebarVisible ? '‹' : '›' }}
      </button>

      <div class="stage">
        <MediaPlayer
          :media="currentMedia"
          :album="currentDistribution?.album"
          :loading="player.syncStatus === 'loading' && !player.currentMedia"
          @ended="player.nextMedia"
          @loaded="handleMediaLoaded"
          @error="handleMediaError"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import MediaPlayer from '@/components/MediaPlayer.vue'
import PlaylistSidebar from '@/components/PlaylistSidebar.vue'
import { useSecureObjectUrl } from '@/components/useSecureObjectUrl'
import { useDeviceAuthStore } from '@/stores/deviceAuth'
import { usePlayerStore } from '@/stores/player'

const router = useRouter()
const deviceAuth = useDeviceAuthStore()
const player = usePlayerStore()
const bgmRef = ref()
const sidebarVisible = ref(true)
const showSidebarHandle = ref(false)

let imageTimer = null
let syncTimer = null
let mediaErrorAdvanceTimer = null
let sidebarHandleTimer = null

const currentDistribution = computed(() => player.currentDistribution)
const currentMedia = computed(() => player.currentMedia)
const sidebarErrorMessage = computed(() => player.errorMessage || player.mediaErrorMessage || player.bgmErrorMessage)
const bgmUrl = computed(() => currentDistribution.value?.album?.bgmUrl || '')
const { resolvedSrc: resolvedBgmUrl, error: bgmResolveError } = useSecureObjectUrl(bgmUrl)

const deviceSummary = computed(() => ({
  deviceUid: deviceAuth.deviceUid,
  deviceName: deviceAuth.deviceName,
  hostname: deviceAuth.hostname,
  serverBaseUrl: deviceAuth.serverBaseUrl,
  deviceType: deviceAuth.deviceType
}))

function clearSidebarHandleTimer() {
  if (sidebarHandleTimer) {
    clearTimeout(sidebarHandleTimer)
    sidebarHandleTimer = null
  }
}

function revealSidebarHandle() {
  showSidebarHandle.value = true
  clearSidebarHandleTimer()
  sidebarHandleTimer = window.setTimeout(() => {
    showSidebarHandle.value = false
    sidebarHandleTimer = null
  }, 1600)
}

function hideSidebarHandle() {
  clearSidebarHandleTimer()
  showSidebarHandle.value = false
}

function clearImageTimer() {
  if (imageTimer) {
    clearTimeout(imageTimer)
    imageTimer = null
  }
}

function clearMediaErrorAdvanceTimer() {
  if (mediaErrorAdvanceTimer) {
    clearTimeout(mediaErrorAdvanceTimer)
    mediaErrorAdvanceTimer = null
  }
}

function scheduleImageAdvance() {
  clearImageTimer()

  const media = currentMedia.value
  const distribution = currentDistribution.value
  if (!media || media.mediaType === 'VIDEO') {
    return
  }

  const durationSeconds = media.itemDuration || distribution?.itemDuration || 10
  imageTimer = window.setTimeout(() => {
    player.nextMedia()
  }, durationSeconds * 1000)
}

function scheduleMediaErrorAdvance() {
  clearMediaErrorAdvanceTimer()

  if (!currentMedia.value || !currentMedia.value.id) {
    return
  }

  mediaErrorAdvanceTimer = window.setTimeout(() => {
    player.nextMedia()
  }, 1500)
}

function handleMediaLoaded() {
  player.clearMediaError()
}

function handleMediaError(message) {
  player.setMediaError(message)
  scheduleMediaErrorAdvance()
}

function handleBgmError() {
  const source = bgmUrl.value
  player.setBgmError(source ? `BGM 加载失败：${source}` : 'BGM 加载失败')
}

async function refresh() {
  await player.pullContent()
}

async function syncAndStart() {
  await deviceAuth.initializeIdentity()

  const runRefresh = () => refresh().catch(() => {})
  await runRefresh()

  syncTimer = window.setInterval(() => {
    runRefresh()
  }, 30000)
}

watch(
  () => [currentMedia.value?.id, currentDistribution.value?.id],
  () => {
    clearMediaErrorAdvanceTimer()
    player.clearMediaError()
    scheduleImageAdvance()
  },
  { immediate: true }
)

watch(
  () => currentDistribution.value?.album?.bgmUrl,
  () => {
    player.clearBgmError()
  },
  { immediate: true }
)

watch(bgmResolveError, value => {
  if (value) {
    handleBgmError()
  }
})

watch(
  () => [resolvedBgmUrl.value, currentDistribution.value?.album?.bgmVolume, currentMedia.value?.mediaType],
  async () => {
    const audio = bgmRef.value
    if (!audio) {
      return
    }

    const hasBgm = Boolean(resolvedBgmUrl.value)

    audio.volume = Math.min(Math.max((currentDistribution.value?.album?.bgmVolume ?? 40) / 100, 0), 1)
    audio.muted = currentMedia.value?.mediaType === 'VIDEO'

    if (!hasBgm) {
      player.clearBgmError()
      audio.pause()
      audio.removeAttribute('src')
      audio.load()
      return
    }

    if (audio.src !== resolvedBgmUrl.value) {
      player.clearBgmError()
      audio.src = resolvedBgmUrl.value
      audio.load()
    }

    try {
      await audio.play()
    } catch {
      // ignore autoplay failures in renderer
    }
  },
  { immediate: true }
)

onMounted(() => {
  revealSidebarHandle()
  syncAndStart().catch(() => {})
})

onBeforeUnmount(() => {
  clearSidebarHandleTimer()
  clearImageTimer()
  clearMediaErrorAdvanceTimer()
  if (syncTimer) {
    clearInterval(syncTimer)
    syncTimer = null
  }
  bgmRef.value?.pause()
})
</script>

<style scoped>
.player-page {
  position: relative;
  height: 100vh;
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  overflow: hidden;
  background: linear-gradient(135deg, #020617, #0f172a 45%, #111827);
}

.player-page.sidebar-hidden {
  grid-template-columns: 0 minmax(0, 1fr);
}

.sidebar-panel {
  min-height: 0;
  box-sizing: border-box;
  padding: 20px;
  overflow: hidden;
  border-right: 1px solid rgba(148, 163, 184, 0.12);
  background: rgba(2, 6, 23, 0.88);
  transition: padding 0.2s ease, opacity 0.2s ease, border-color 0.2s ease;
}

.player-page.sidebar-hidden .sidebar-panel {
  min-width: 0;
  min-height: 0;
  height: 0;
  padding: 0;
  border-right-color: transparent;
  opacity: 0;
  pointer-events: none;
}

.content-panel {
  position: relative;
  min-width: 0;
  min-height: 0;
  box-sizing: border-box;
  padding: 20px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-handle {
  position: absolute;
  top: 50%;
  left: 0;
  z-index: 2;
  width: 28px;
  height: 72px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 0 999px 999px 0;
  background: rgba(15, 23, 42, 0.82);
  color: #fff;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  transform: translateY(-50%);
  transition: background 0.2s ease, opacity 0.2s ease;
}

.sidebar-handle:hover {
  background: rgba(30, 41, 59, 0.94);
}

.stage {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}

@media (max-width: 1200px) {
  .player-page {
    grid-template-columns: 1fr;
  }

  .player-page.sidebar-hidden {
    grid-template-columns: 1fr;
  }

  .sidebar-panel {
    border-right: 0;
    border-bottom: 1px solid rgba(148, 163, 184, 0.12);
  }

  .player-page.sidebar-hidden .sidebar-panel {
    border-bottom-color: transparent;
  }

  .stage {
    min-height: 60vh;
  }
}
</style>
