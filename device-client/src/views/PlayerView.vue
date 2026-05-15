<template>
  <div
    class="player-page"
    :class="{ 'sidebar-hidden': effectiveSidebarHidden, fullscreen: fullscreenActive }"
    @mousemove="revealSidebarHandle"
    @mouseleave="hideSidebarHandle"
  >
    <audio ref="bgmRef" hidden @error="handleBgmError" @ended="handleBgmEnded" />

    <div class="sidebar-panel">
      <PlaylistSidebar
        :device="deviceSummary"
        :distributions="player.distributions"
        :current-distribution-id="currentDistribution?.id ?? null"
        :current-distribution="currentDistribution"
        :current-media="currentMedia"
        :pulled-at="player.pulledAt"
        :sync-status="player.syncStatus"
        :error-message="sidebarErrorMessage"
        :playback-muted="player.playbackMuted"
        :playback-rotation="player.playbackRotation"
        :brightness-schedule-enabled="player.brightnessScheduleEnabled"
        :brightness-start-hour="player.brightnessStartHour"
        :brightness-end-hour="player.brightnessEndHour"
        :brightness-dim-percent="player.brightnessDimPercent"
        :disabled-distribution-ids="player.disabledDistributionIds"
        @select-distribution="player.selectDistribution"
        @toggle-distribution="handleDistributionToggle"
        @toggle-mute="handleMuteToggle"
        @change-rotation="player.setPlaybackRotation"
        @toggle-brightness-schedule="player.setBrightnessScheduleEnabled"
        @change-brightness-hours="handleBrightnessHoursChange"
        @change-brightness-dim="player.setBrightnessDimPercent"
        @check-update="checkForUpdates"
        @show-system-info="showSystemInfo"
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
        {{ sidebarVisible ? '<' : '>' }}
      </button>

      <button
        type="button"
        class="fullscreen-toggle"
        :aria-label="fullscreenActive ? '退出全屏' : '全屏显示'"
        :title="fullscreenActive ? '退出全屏' : '全屏显示'"
        @click="toggleFullscreen"
      >
        {{ fullscreenActive ? '还原' : '全屏' }}
      </button>

      <div ref="stageRef" class="stage">
        <div class="rotated-stage" :style="rotatedStageStyle">
          <MediaPlayer
            :media="currentMedia"
            :media-list="player.currentMediaList"
            :album="currentDistribution?.album"
            :transition-style="player.currentTransitionStyle"
            :display-style="player.currentDisplayStyle"
            :display-variant="player.currentDisplayVariant"
            :show-time-and-date="player.showTimeAndDate"
            :muted="player.playbackMuted"
            :loading="player.syncStatus === 'loading' && !player.currentMedia"
            @ended="player.nextMedia"
            @loaded="handleMediaLoaded"
            @error="handleMediaError"
          />
        </div>
        <div v-if="brightnessDimActive" class="brightness-dim-overlay" :style="{ opacity: brightnessDimOpacity }" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import MediaPlayer from '@/components/MediaPlayer.vue'
import PlaylistSidebar from '@/components/PlaylistSidebar.vue'
import { useSecureObjectUrl, warmSecureObjectUrl } from '@/components/useSecureObjectUrl'
import { deviceApi } from '@/api/device'
import { useDeviceAuthStore } from '@/stores/deviceAuth'
import { usePlayerStore, resolveMediaIdentity } from '@/stores/player'

const IMAGE_ADVANCE_RETRY_DELAY_MS = 300
const IMAGE_ADVANCE_MAX_RETRY_COUNT = 10

const router = useRouter()
const deviceAuth = useDeviceAuthStore()
const player = usePlayerStore()
const bgmRef = ref()
const stageRef = ref()
const sidebarVisible = ref(true)
const showSidebarHandle = ref(false)
const fullscreenActive = ref(false)
const imageAdvanceRetryCount = ref(0)
const stageSize = ref({ width: 0, height: 0 })
const now = ref(new Date())

let imageTimer = null
let syncTimer = null
let mediaErrorAdvanceTimer = null
let bgmErrorAdvanceTimer = null
let sidebarHandleTimer = null
let clockTimer = null
let resizeObserver = null
let removeFullscreenListener = null
const preloadedImageIdentities = new Set()

const currentDistribution = computed(() => player.currentDistribution)
const currentMedia = computed(() => player.currentMedia)
const isBentoPlayback = computed(() => player.currentDisplayStyle === 'BENTO' && currentMedia.value?.mediaType === 'IMAGE')
const effectiveSidebarHidden = computed(() => !sidebarVisible.value || fullscreenActive.value)
const sidebarErrorMessage = computed(() => player.errorMessage || player.mediaErrorMessage || player.bgmErrorMessage)
const resolvedBgmInputUrl = computed(() => player.currentBgmUrl)
const { resolvedSrc: resolvedBgmUrl, error: bgmResolveError } = useSecureObjectUrl(resolvedBgmInputUrl)
const effectiveRotation = computed(() => {
  if (player.playbackRotation === 'auto') {
    return 0
  }
  return Number(player.playbackRotation) || 0
})
const rotatedStageStyle = computed(() => {
  const rotation = effectiveRotation.value
  if (rotation === 90 || rotation === 270) {
    return {
      width: `${Math.max(stageSize.value.height, 1)}px`,
      height: `${Math.max(stageSize.value.width, 1)}px`,
      transform: `translate(-50%, -50%) rotate(${rotation}deg)`
    }
  }
  return {
    width: '100%',
    height: '100%',
    transform: `translate(-50%, -50%) rotate(${rotation}deg)`
  }
})
const brightnessDimActive = computed(() => {
  if (!player.brightnessScheduleEnabled) {
    return false
  }
  return !isHourInRange(now.value.getHours(), player.brightnessStartHour, player.brightnessEndHour)
})
const brightnessDimOpacity = computed(() => {
  return Math.min(Math.max(player.brightnessDimPercent / 100, 0.1), 0.9)
})

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

function clearBgmErrorAdvanceTimer() {
  if (bgmErrorAdvanceTimer) {
    clearTimeout(bgmErrorAdvanceTimer)
    bgmErrorAdvanceTimer = null
  }
}

function preloadNextImage() {
  const nextMedia = player.peekNextMedia()
  if (!nextMedia || nextMedia.mediaType !== 'IMAGE') {
    return
  }

  const nextMediaIdentity = resolveMediaIdentity(nextMedia)
  if (preloadedImageIdentities.has(nextMediaIdentity)) {
    return
  }

  warmSecureObjectUrl(nextMedia.url)
    .then(() => {
      preloadedImageIdentities.add(nextMediaIdentity)
    })
    .catch(() => {
      preloadedImageIdentities.delete(nextMediaIdentity)
    })
}

function advanceImageWhenReady() {
  const nextMedia = player.peekNextMedia()
  if (nextMedia?.mediaType === 'IMAGE') {
    const nextMediaIdentity = resolveMediaIdentity(nextMedia)
    if (!preloadedImageIdentities.has(nextMediaIdentity) && imageAdvanceRetryCount.value < IMAGE_ADVANCE_MAX_RETRY_COUNT) {
      imageAdvanceRetryCount.value += 1
      preloadNextImage()
      imageTimer = window.setTimeout(advanceImageWhenReady, IMAGE_ADVANCE_RETRY_DELAY_MS)
      return
    }
  }

  imageAdvanceRetryCount.value = 0
  player.nextMedia()
}

function scheduleImageAdvance() {
  clearImageTimer()

  const media = currentMedia.value
  const distribution = currentDistribution.value
  if (!media || media.mediaType === 'VIDEO' || media.mediaType === 'AUDIO' || isBentoPlayback.value) {
    return
  }

  const durationSeconds = media.itemDuration || distribution?.itemDuration || 10
  imageTimer = window.setTimeout(advanceImageWhenReady, durationSeconds * 1000)
}

function scheduleMediaErrorAdvance() {
  clearMediaErrorAdvanceTimer()

  if (!currentMedia.value) {
    return
  }

  mediaErrorAdvanceTimer = window.setTimeout(() => {
    player.nextMedia()
  }, 1500)
}

function scheduleBgmErrorAdvance() {
  clearBgmErrorAdvanceTimer()

  if (!player.currentBgmUrl) {
    return
  }

  bgmErrorAdvanceTimer = window.setTimeout(() => {
    player.nextBgm()
  }, 1500)
}

function handleMediaLoaded() {
  player.clearMediaError()
  scheduleImageAdvance()
}

function handleMediaError(message) {
  player.setMediaError(message)
  scheduleMediaErrorAdvance()
}

function handleBgmEnded() {
  player.nextBgm()
}

function handleBgmError() {
  player.setBgmError('背景音乐播放失败')
  scheduleBgmErrorAdvance()
}

function handleDistributionToggle(payload) {
  player.setDistributionEnabled(payload.distributionId, payload.enabled)
}

function handleMuteToggle(value) {
  player.setPlaybackMuted(value)
}

function handleBrightnessHoursChange(payload) {
  player.setBrightnessHours(payload.startHour, payload.endHour)
}

function isHourInRange(hour, startHour, endHour) {
  if (startHour === endHour) {
    return true
  }
  if (startHour < endHour) {
    return hour >= startHour && hour < endHour
  }
  return hour >= startHour || hour < endHour
}

async function toggleFullscreen() {
  if (window.deviceBridge?.toggleFullscreen) {
    fullscreenActive.value = await window.deviceBridge.toggleFullscreen()
    return
  }
  if (!document.fullscreenElement) {
    await document.documentElement.requestFullscreen?.()
  } else {
    await document.exitFullscreen?.()
  }
  fullscreenActive.value = Boolean(document.fullscreenElement)
}

function handleDocumentFullscreenChange() {
  if (!window.deviceBridge?.isFullscreen) {
    fullscreenActive.value = Boolean(document.fullscreenElement)
  }
}

async function refresh() {
  await player.pullContent()
}

async function getCurrentVersion() {
  if (window.deviceBridge?.getAppVersion) {
    return await window.deviceBridge.getAppVersion()
  }
  return import.meta.env.VITE_APP_VERSION || '0.0.0'
}

async function openDownloadUrl(url) {
  if (!url) {
    return
  }
  if (window.deviceBridge?.openExternal) {
    await window.deviceBridge.openExternal(url)
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function downloadAndInstallUpdate(update) {
  if (!window.deviceBridge?.downloadUpdate) {
    await openDownloadUrl(update.downloadUrl)
    return
  }
  const hide = message.loading('正在下载更新安装包...', 0)
  try {
    await window.deviceBridge.downloadUpdate(update)
    message.success('安装包已下载，正在打开安装程序')
  } catch (error) {
    message.error(error?.message || '更新下载失败，已打开浏览器下载')
    await openDownloadUrl(update.downloadUrl)
  } finally {
    hide()
  }
}

function formatUpdateContent(update) {
  const lines = []
  if (update.latestVersion) {
    lines.push(`最新版本：${update.latestVersion}`)
  }
  if (update.latestVersionCode) {
    lines.push(`版本号：${update.latestVersionCode}`)
  }
  if (update.releaseNotes) {
    lines.push('')
    lines.push(update.releaseNotes)
  }
  return lines.join('\n') || '检测到新版本，是否下载并安装？'
}

async function checkForUpdates() {
  try {
    const currentVersion = await getCurrentVersion()
    const res = await deviceApi.checkUpdate({
      platform: 'pc',
      currentVersion,
      channel: 'stable'
    })
    const update = res.data
    if (!update?.hasUpdate) {
      message.success('已是最新版本')
      return
    }
    Modal.confirm({
      title: update.forceUpdate ? '发现必须更新的客户端版本' : '发现客户端新版本',
      content: formatUpdateContent(update),
      okText: '下载并安装',
      cancelText: update.forceUpdate ? undefined : '稍后',
      cancelButtonProps: update.forceUpdate ? { style: { display: 'none' } } : undefined,
      maskClosable: !update.forceUpdate,
      keyboard: !update.forceUpdate,
      async onOk() {
        await downloadAndInstallUpdate(update)
      }
    })
  } catch (error) {
    message.error(error?.message || '版本检查失败')
  }
}

function formatBytes(bytes) {
  if (!bytes) {
    return '-'
  }
  const gb = bytes / (1024 ** 3)
  if (gb >= 1) {
    return `${gb.toFixed(1)} GB`
  }
  return `${(bytes / (1024 ** 2)).toFixed(0)} MB`
}

function getMediaCapabilityLines() {
  const checks = [
    ['H.264 / AVC', 'video/mp4; codecs="avc1.42E01E"'],
    ['H.265 / HEVC', 'video/mp4; codecs="hvc1.1.6.L93.B0"'],
    ['VP9', 'video/webm; codecs="vp9"'],
    ['AV1', 'video/mp4; codecs="av01.0.05M.08"'],
    ['MP3', 'audio/mpeg'],
    ['AAC', 'audio/mp4; codecs="mp4a.40.2"'],
    ['Opus', 'audio/webm; codecs="opus"'],
    ['Vorbis', 'audio/webm; codecs="vorbis"']
  ]
  return checks.map(([label, mime]) => {
    const supported = window.MediaSource?.isTypeSupported?.(mime) || document.createElement('video').canPlayType(mime)
    return `${label}: ${supported ? '支持' : '不支持'}`
  })
}

async function showSystemInfo() {
  try {
    const info = await window.deviceBridge?.getSystemInfo?.()
    const lines = [
      '系统硬件信息',
      `主机名: ${info?.hostname || navigator.userAgent}`,
      `平台: ${info?.platform || navigator.platform} ${info?.release || ''} ${info?.arch || ''}`.trim(),
      `客户端版本: ${info?.appVersion || await getCurrentVersion()}`,
      `CPU: ${info?.cpuModel || '-'}${info?.cpuCount ? ` (${info.cpuCount} 核心)` : ''}`,
      `内存: ${formatBytes(info?.freeMemory)} 可用 / ${formatBytes(info?.totalMemory)} 总计`,
      `屏幕: ${info?.primaryDisplay?.width || window.screen.width} x ${info?.primaryDisplay?.height || window.screen.height}${info?.displayCount ? ` (${info.displayCount} 个显示器)` : ''}`,
      '',
      '浏览器解码能力',
      ...getMediaCapabilityLines()
    ]

    Modal.info({
      title: '系统能力',
      width: 680,
      content: lines.join('\n'),
      okText: '关闭'
    })
  } catch (error) {
    message.error(error?.message || '读取系统能力失败')
  }
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
  () => [resolveMediaIdentity(currentMedia.value), currentDistribution.value?.id],
  () => {
    clearMediaErrorAdvanceTimer()
    player.clearMediaError()
    imageAdvanceRetryCount.value = 0

    if (!currentMedia.value) {
      clearImageTimer()
      return
    }

    const currentMediaIdentity = resolveMediaIdentity(currentMedia.value)
    if (currentMedia.value.mediaType === 'IMAGE' && currentMediaIdentity) {
      preloadedImageIdentities.add(currentMediaIdentity)
      preloadNextImage()
    }

  },
  { immediate: true }
)

watch(
  () => [player.currentBgm?.url, currentDistribution.value?.id],
  () => {
    clearBgmErrorAdvanceTimer()
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
  () => [resolvedBgmUrl.value, player.currentBgmVolume, currentMedia.value?.mediaType, player.currentBgmIndex, player.playbackMuted],
  async () => {
    const audio = bgmRef.value
    if (!audio) {
      return
    }

    const hasBgm = Boolean(resolvedBgmUrl.value)
    audio.volume = Math.min(Math.max(player.currentBgmVolume / 100, 0), 1)
    audio.muted = player.playbackMuted || currentMedia.value?.mediaType === 'VIDEO'

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
      // Ignore autoplay failures in the renderer process.
    }
  },
  { immediate: true }
)

onMounted(() => {
  revealSidebarHandle()
  clockTimer = window.setInterval(() => {
    now.value = new Date()
  }, 60000)
  if (stageRef.value) {
    const updateStageSize = () => {
      stageSize.value = {
        width: stageRef.value?.clientWidth || 0,
        height: stageRef.value?.clientHeight || 0
      }
    }
    updateStageSize()
    resizeObserver = new ResizeObserver(updateStageSize)
    resizeObserver.observe(stageRef.value)
  }
  if (window.deviceBridge?.isFullscreen) {
    window.deviceBridge.isFullscreen().then(value => { fullscreenActive.value = Boolean(value) }).catch(() => {})
  }
  removeFullscreenListener = window.deviceBridge?.onFullscreenChanged?.(value => {
    fullscreenActive.value = value
  }) || null
  document.addEventListener('fullscreenchange', handleDocumentFullscreenChange)
  syncAndStart().catch(() => {})
})

onBeforeUnmount(() => {
  clearSidebarHandleTimer()
  clearImageTimer()
  clearMediaErrorAdvanceTimer()
  clearBgmErrorAdvanceTimer()
  if (syncTimer) {
    clearInterval(syncTimer)
    syncTimer = null
  }
  if (clockTimer) {
    clearInterval(clockTimer)
    clockTimer = null
  }
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (removeFullscreenListener) {
    removeFullscreenListener()
    removeFullscreenListener = null
  }
  document.removeEventListener('fullscreenchange', handleDocumentFullscreenChange)
  bgmRef.value?.pause()
})
</script>

<style scoped>
.player-page {
  position: relative;
  height: 100vh;
  width: 100vw;
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  overflow: hidden;
  background: linear-gradient(135deg, #020617, #0f172a 45%, #111827);
}

.player-page.fullscreen {
  grid-template-columns: 0 minmax(0, 1fr);
  background: #000;
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

.player-page.fullscreen .content-panel {
  padding: 0;
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

.fullscreen-toggle {
  position: absolute;
  top: 18px;
  right: 18px;
  z-index: 3;
  min-width: 64px;
  height: 36px;
  border: 1px solid rgba(148, 163, 184, 0.32);
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.68);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  backdrop-filter: blur(12px);
  transition: opacity 0.2s ease, background 0.2s ease;
}

.fullscreen-toggle:hover {
  background: rgba(30, 41, 59, 0.92);
}

.player-page.fullscreen .fullscreen-toggle {
  opacity: 0.18;
}

.player-page.fullscreen .fullscreen-toggle:hover {
  opacity: 1;
}

.stage {
  position: relative;
  flex: 1;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: #000;
}

.rotated-stage {
  position: absolute;
  top: 50%;
  left: 50%;
  transform-origin: center;
}

.brightness-dim-overlay {
  position: absolute;
  inset: 0;
  z-index: 4;
  pointer-events: none;
  background: #000;
  transition: opacity 0.4s ease;
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
