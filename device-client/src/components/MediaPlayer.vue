<template>
  <div class="media-player">
    <div v-if="loading" class="placeholder">
      <a-spin size="large" />
      <div class="placeholder-text">正在同步设备内容...</div>
    </div>
    <div v-else-if="!media" class="placeholder">
      <a-empty description="当前没有可播放的媒体" />
    </div>
    <div v-else-if="loadErrorMessage" class="placeholder">
      <a-empty description="媒体加载失败" />
      <div class="placeholder-text">{{ loadErrorMessage }}</div>
    </div>
    <template v-else>
      <div v-if="isCalendarLayout" class="calendar-layout">
        <div class="calendar-media-pane">
          <img
            v-if="media.mediaType === 'IMAGE' && mediaResolvedUrl"
            :key="`calendar-image-${mediaIdentity}`"
            class="calendar-image"
            :src="mediaResolvedUrl"
            :alt="media.fileName"
            @load="handleMediaLoaded"
            @error="handleMediaError"
          />
          <video
            v-else-if="media.mediaType === 'VIDEO' && mediaResolvedUrl"
            :key="`calendar-video-${mediaIdentity}`"
            ref="videoRef"
            class="calendar-image"
            :src="mediaResolvedUrl"
            :muted="muted"
            autoplay
            playsinline
            controls
            @loadeddata="handleMediaLoaded"
            @ended="$emit('ended')"
            @error="handleMediaError"
          />
        </div>
        <div class="calendar-card">
          <div class="calendar-weekday">{{ calendarParts.weekday }}</div>
          <div class="calendar-day">{{ calendarParts.day }}</div>
          <div class="calendar-month">{{ calendarParts.monthNumber }} / {{ calendarParts.year }}</div>
        </div>
      </div>
      <div
        v-if="isAdvancedImageLayout && advancedLayoutReady"
        :key="`advanced-${mediaIdentity}-${normalizedDisplayStyle}-${normalizedDisplayVariant}-${viewportVersion}`"
        :class="['advanced-layout', `advanced-${normalizedDisplayStyle.toLowerCase().replace('_', '-')}`]"
        :style="advancedLayoutStyle"
      >
        <img
          v-for="(item, index) in displayedAdvancedItems"
          :key="`${item.identity}-${index}-${item.version || 0}`"
          class="advanced-image"
          :class="{ primary: index === primaryAdvancedIndex, 'flip-x': item.flipAxis === 'x', 'flip-y': item.flipAxis !== 'x' }"
          :style="getAdvancedItemStyle(index)"
          :src="item.resolvedUrl"
          :alt="item.media.fileName"
          @load="handleAdvancedImageLoaded(index)"
          @error="handleAdvancedImageError(index)"
        />
      </div>
      <Transition :name="imageTransitionName" :css="imageTransitionEnabled">
        <img
          v-if="!isAdvancedImageLayout && media.mediaType === 'IMAGE' && mediaResolvedUrl"
          :key="`image-${mediaIdentity}`"
          class="image-content transition-image"
          :src="mediaResolvedUrl"
          :alt="media.fileName"
          @load="handleMediaLoaded"
          @error="handleMediaError"
        />
      </Transition>
      <video
        v-if="media.mediaType === 'VIDEO' && mediaResolvedUrl"
        :key="`video-${mediaIdentity}`"
        ref="videoRef"
        class="video-content"
        :src="mediaResolvedUrl"
        :muted="muted"
        autoplay
        playsinline
        controls
        @loadeddata="handleMediaLoaded"
        @ended="$emit('ended')"
        @error="handleMediaError"
      />
      <div
        v-if="media.mediaType === 'AUDIO' && mediaResolvedUrl"
        :key="`audio-${mediaIdentity}`"
        class="audio-content"
      >
        <div class="audio-title">{{ media.fileName }}</div>
        <audio
          ref="audioRef"
          class="audio-element"
          :src="mediaResolvedUrl"
          :muted="muted"
          autoplay
          controls
          @loadeddata="handleMediaLoaded"
          @ended="$emit('ended')"
          @error="handleMediaError"
        />
      </div>
      <div v-if="isUnsupportedMedia" class="placeholder">
        <a-empty description="暂不支持的媒体类型" />
        <div class="placeholder-text">{{ media.mediaType }} / {{ media.fileName }}</div>
      </div>
      <div v-if="showClockOverlay" class="time-overlay">
        <div class="time-overlay-time">{{ calendarParts.time }}</div>
        <div class="time-overlay-date">{{ calendarParts.dateText }}</div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useSecureObjectUrl, warmSecureObjectUrl } from '@/components/useSecureObjectUrl'
import { resolveMediaIdentity } from '@/stores/player'

const TRANSITION_STYLES = ['NONE', 'FADE', 'SLIDE', 'CUBE', 'REVEAL', 'FLIP', 'RANDOM']
const RANDOM_IMAGE_TRANSITIONS = ['FADE', 'SLIDE', 'CUBE', 'REVEAL', 'FLIP']

const props = defineProps({
  media: {
    type: Object,
    default: null
  },
  mediaList: {
    type: Array,
    default: () => []
  },
  album: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  },
  muted: {
    type: Boolean,
    default: false
  },
  transitionStyle: {
    type: String,
    default: 'NONE'
  },
  displayStyle: {
    type: String,
    default: 'SINGLE'
  },
  displayVariant: {
    type: String,
    default: 'DEFAULT'
  },
  showTimeAndDate: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['ended', 'loaded', 'error'])

const videoRef = ref(null)
const audioRef = ref(null)
const previousMediaType = ref('')
const enableImageTransition = ref(false)
const concreteImageTransition = ref('NONE')
const loadErrorMessage = ref('')
const advancedLoadedIndexes = ref(new Set())
const advancedFailedIndexes = ref(new Set())
const advancedResolvedUrls = ref({})
const advancedReadyEmitted = ref(false)
const bentoDisplayItems = ref([])
const bentoNextSourceIndex = ref(0)
const bentoPreviousSlotIndex = ref(-1)
const frameWallDisplayItems = ref([])
const frameWallNextSourceIndex = ref(0)
const frameWallPreviousSlotIndex = ref(-1)
const carouselDisplayItems = ref([])
const carouselActiveSourceIndex = ref(0)
const viewportVersion = ref(0)
const now = ref(new Date())
let clockTimer = null
let bentoTimer = null
let frameWallTimer = null
let carouselTimer = null
const mediaUrl = computed(() => props.media?.url || '')
const mediaIdentity = computed(() => resolveMediaIdentity(props.media))
const normalizedTransitionStyle = computed(() => normalizeTransitionStyle(props.transitionStyle || props.album?.transitionStyle))
const normalizedDisplayStyle = computed(() => normalizeDisplayStyle(props.displayStyle || props.album?.displayStyle))
const normalizedDisplayVariant = computed(() => normalizeDisplayVariant(props.displayVariant || props.album?.displayVariant))
const isCalendarLayout = computed(() => normalizedDisplayStyle.value === 'CALENDAR')
const isAdvancedImageLayout = computed(() => props.media?.mediaType === 'IMAGE' && !isCalendarLayout.value && normalizedDisplayStyle.value !== 'SINGLE')
const showClockOverlay = computed(() => props.showTimeAndDate && !isCalendarLayout.value)
const advancedImageItems = computed(() => buildAdvancedImageItems())
const advancedRenderableItems = computed(() => advancedImageItems.value
  .map(item => ({
    media: item,
    identity: resolveMediaIdentity(item),
    resolvedUrl: getAdvancedImageSrc(item)
  }))
  .filter(item => item.resolvedUrl))
const isBentoLayout = computed(() => normalizedDisplayStyle.value === 'BENTO')
const isFrameWallLayout = computed(() => normalizedDisplayStyle.value === 'FRAME_WALL')
const isCarouselLayout = computed(() => normalizedDisplayStyle.value === 'CAROUSEL')
const displayedAdvancedItems = computed(() => {
  if (isBentoLayout.value) return bentoDisplayItems.value
  if (isFrameWallLayout.value) return frameWallDisplayItems.value
  if (isCarouselLayout.value) return carouselDisplayItems.value
  return advancedRenderableItems.value
})
const advancedLayoutReady = computed(() => {
  if (isBentoLayout.value) {
    return bentoDisplayItems.value.length > 0 && bentoDisplayItems.value.every(item => item.resolvedUrl)
  }
  if (isFrameWallLayout.value) {
    return frameWallDisplayItems.value.length > 0 && frameWallDisplayItems.value.every(item => item.resolvedUrl)
  }
  if (isCarouselLayout.value) {
    return carouselDisplayItems.value.length > 0 && carouselDisplayItems.value.every(item => item.resolvedUrl)
  }
  return advancedImageItems.value.length > 0 && advancedRenderableItems.value.length === advancedImageItems.value.length
})
const bentoGridSize = computed(() => getBentoGridSize())
const advancedLayoutStyle = computed(() => {
  if (normalizedDisplayStyle.value !== 'BENTO') return null
  return {
    gridTemplateColumns: `repeat(${bentoGridSize.value.columns}, minmax(0, 1fr))`,
    gridTemplateRows: `repeat(${bentoGridSize.value.rows}, minmax(0, 1fr))`
  }
})
const primaryAdvancedIndex = computed(() => normalizedDisplayStyle.value === 'CAROUSEL' && displayedAdvancedItems.value.length >= 3 ? Math.floor(displayedAdvancedItems.value.length / 2) : 0)
const imageTransitionEnabled = computed(() => enableImageTransition.value && concreteImageTransition.value !== 'NONE')
const imageTransitionName = computed(() => `image-${concreteImageTransition.value.toLowerCase()}`)
const calendarParts = computed(() => formatCalendarParts(now.value))
const { resolvedSrc: mediaResolvedUrl, error: mediaResolveError } = useSecureObjectUrl(mediaUrl)
const isUnsupportedMedia = computed(() => {
  if (!props.media) {
    return false
  }
  if (props.media.mediaType === 'IMAGE' || props.media.mediaType === 'VIDEO' || props.media.mediaType === 'AUDIO') {
    return !mediaResolvedUrl.value
  }
  return true
})
const mediaErrorMap = {
  1: '媒体加载被中止',
  2: '网络异常，无法加载媒体',
  3: '媒体解码失败，无法播放',
  4: '媒体地址不可用或格式不受支持'
}

function normalizeTransitionStyle(value) {
  if (!value || !String(value).trim()) {
    return 'NONE'
  }
  const normalized = String(value).trim().toUpperCase()
  return TRANSITION_STYLES.includes(normalized) ? normalized : 'NONE'
}

function normalizeDisplayStyle(value) {
  if (!value || !String(value).trim()) {
    return 'SINGLE'
  }
  const normalized = String(value).trim().toUpperCase()
  if (normalized === 'FRAMEWALL') return 'FRAME_WALL'
  return ['SINGLE', 'BENTO', 'FRAME_WALL', 'CAROUSEL', 'CALENDAR'].includes(normalized) ? normalized : 'SINGLE'
}

function normalizeDisplayVariant(value) {
  if (!value || !String(value).trim()) return 'DEFAULT'
  return String(value).trim().toUpperCase().replace('-', '_')
}

function getBentoSlots(count) {
  const variant = normalizedDisplayVariant.value === 'DEFAULT' ? 'BENTO_5' : normalizedDisplayVariant.value
  const variants = {
    BENTO_1: [
      ['1 / span 1', '1 / span 1'], ['2 / span 1', '1 / span 1'], ['3 / span 2', '1 / span 1'],
      ['5 / span 2', '3 / span 1'], ['1 / span 2', '2 / span 2'], ['3 / span 2', '2 / span 1'],
      ['5 / span 2', '1 / span 2'], ['3 / span 2', '3 / span 1'], ['5 / span 2', '4 / span 1']
    ],
    BENTO_2: [
      ['1 / span 1', '1 / span 2'], ['1 / span 2', '3 / span 1'], ['2 / span 2', '1 / span 1'],
      ['4 / span 2', '1 / span 1'], ['4 / span 1', '2 / span 2'], ['2 / span 2', '3 / span 1'],
      ['2 / span 1', '2 / span 1'], ['3 / span 1', '2 / span 1']
    ],
    BENTO_3: [
      ['1 / span 1', '1 / span 2'], ['2 / span 1', '1 / span 1'], ['3 / span 2', '1 / span 1'],
      ['1 / span 1', '3 / span 1'], ['2 / span 2', '2 / span 1'], ['4 / span 1', '2 / span 1'],
      ['2 / span 1', '3 / span 1'], ['3 / span 1', '3 / span 1'], ['4 / span 1', '3 / span 1']
    ],
    BENTO_4: [
      ['1 / span 2', '1 / span 2'], ['3 / span 1', '1 / span 2'], ['4 / span 1', '1 / span 1'],
      ['1 / span 1', '3 / span 1'], ['2 / span 1', '3 / span 1'], ['3 / span 1', '3 / span 1'],
      ['4 / span 1', '2 / span 2']
    ],
    BENTO_5: [
      ['1 / span 3', '1 / span 2'], ['4 / span 1', '1 / span 2'], ['1 / span 1', '3 / span 1'],
      ['2 / span 2', '3 / span 1'], ['4 / span 1', '3 / span 1']
    ],
    BENTO_6: [
      ['1 / span 2', '1 / span 1'], ['3 / span 1', '1 / span 1'], ['4 / span 1', '1 / span 1'],
      ['5 / span 1', '1 / span 1'], ['6 / span 2', '1 / span 1'], ['1 / span 1', '2 / span 1'],
      ['2 / span 1', '2 / span 1'], ['1 / span 1', '3 / span 1'], ['2 / span 1', '3 / span 1'],
      ['1 / span 2', '4 / span 1'], ['3 / span 3', '2 / span 2'], ['3 / span 1', '4 / span 1'],
      ['4 / span 1', '4 / span 1'], ['5 / span 1', '4 / span 1'], ['6 / span 1', '2 / span 1'],
      ['7 / span 1', '2 / span 1'], ['6 / span 1', '3 / span 1'], ['7 / span 1', '3 / span 1'],
      ['6 / span 2', '4 / span 1']
    ],
    BENTO_7: [
      ['1 / span 2', '1 / span 1'], ['3 / span 1', '1 / span 1'], ['4 / span 1', '1 / span 1'],
      ['5 / span 1', '1 / span 1'], ['6 / span 2', '1 / span 1'], ['1 / span 1', '2 / span 1'],
      ['2 / span 1', '2 / span 1'], ['1 / span 1', '3 / span 1'], ['2 / span 1', '3 / span 1'],
      ['1 / span 2', '4 / span 1'], ['3 / span 3', '2 / span 2'], ['3 / span 1', '4 / span 1'],
      ['4 / span 1', '4 / span 1'], ['5 / span 1', '4 / span 1'], ['6 / span 1', '2 / span 1'],
      ['7 / span 1', '2 / span 1'], ['6 / span 1', '3 / span 1'], ['7 / span 1', '3 / span 1'],
      ['6 / span 2', '4 / span 1']
    ]
  }
  const slots = variants[variant] || variants.BENTO_5
  if (count <= 1) return [['1 / span 6', '1 / span 4']]
  if (count === 2) return [['1 / span 3', '1 / span 4'], ['4 / span 3', '1 / span 4']]
  if (count === 3) return [['1 / span 3', '1 / span 4'], ['4 / span 3', '1 / span 2'], ['4 / span 3', '3 / span 2']]
  if (count === 4) return [['1 / span 3', '1 / span 2'], ['4 / span 3', '1 / span 2'], ['1 / span 3', '3 / span 2'], ['4 / span 3', '3 / span 2']]
  return slots
}

function getBentoGridSize() {
  const variant = normalizedDisplayVariant.value === 'DEFAULT' ? 'BENTO_5' : normalizedDisplayVariant.value
  if (variant === 'BENTO_6' || variant === 'BENTO_7') return { columns: 7, rows: 4 }
  if (variant === 'BENTO_2' || variant === 'BENTO_3' || variant === 'BENTO_4' || variant === 'BENTO_5') return { columns: 4, rows: 3 }
  return { columns: 6, rows: 4 }
}

function getBentoSlotCount() {
  return getBentoSlots(99).length
}

function getAdvancedItemStyle(index) {
  if (normalizedDisplayStyle.value === 'CAROUSEL') {
    const count = displayedAdvancedItems.value.length
    const center = Math.floor(count / 2)
    const offset = index - center
    const isPrimary = offset === 0
    const width = isPrimary ? 42 : 24
    const height = isPrimary ? 78 : 58
    const left = 50 + offset * 22 - width / 2
    return {
      left: `${left}%`,
      top: `${(100 - height) / 2}%`,
      width: `${width}%`,
      height: `${height}%`,
      zIndex: 20 - Math.abs(offset),
      opacity: Math.max(0.46, 1 - Math.abs(offset) * 0.18),
      transform: `scale(${isPrimary ? 1 : Math.max(0.82, 0.94 - Math.abs(offset) * 0.04)})`
    }
  }
  if (normalizedDisplayStyle.value !== 'BENTO') return null
  const slot = getBentoSlots(displayedAdvancedItems.value.length)[index]
  if (!slot) return null
  return { gridColumn: slot[0], gridRow: slot[1] }
}

function formatCalendarParts(date) {
  const time = date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
  const month = date.toLocaleDateString('zh-CN', { month: 'long' })
  const monthNumber = String(date.getMonth() + 1).padStart(2, '0')
  const year = String(date.getFullYear())
  const day = String(date.getDate()).padStart(2, '0')
  const weekday = date.toLocaleDateString('zh-CN', { weekday: 'long' })
  const dateText = date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'long' })
  return { time, month, monthNumber, year, day, weekday, dateText }
}

function getAdvancedImageLimit() {
  if (normalizedDisplayStyle.value === 'BENTO' && ['BENTO_6', 'BENTO_7'].includes(normalizedDisplayVariant.value)) return 19
  if (normalizedDisplayStyle.value === 'BENTO') return 9
  const cores = navigator.hardwareConcurrency || 4
  const memory = navigator.deviceMemory || 4
  if (cores <= 2 || memory <= 2) return normalizedDisplayStyle.value === 'CAROUSEL' ? 3 : 4
  if (cores <= 4 || memory <= 4) return normalizedDisplayStyle.value === 'FRAME_WALL' ? 6 : 5
  return normalizedDisplayStyle.value === 'CAROUSEL' ? 5 : 8
}

function buildAdvancedImageItems() {
  if (!props.media || props.media.mediaType !== 'IMAGE') return []
  if (isBentoLayout.value) return []
  const images = (props.mediaList || []).filter(item => item?.mediaType === 'IMAGE')
  if (!images.length) return [props.media]
  const identity = resolveMediaIdentity(props.media)
  const start = Math.max(0, images.findIndex(item => resolveMediaIdentity(item) === identity))
  const result = []
  const limit = Math.min(getAdvancedImageLimit(), images.length)
  for (let offset = 0; offset < images.length && result.length < limit; offset += 1) {
    result.push(images[(start + offset) % images.length])
  }
  return result
}

function getAdvancedImageSrc(item) {
  const identity = resolveMediaIdentity(item)
  return advancedResolvedUrls.value[identity] || ''
}

function buildBentoImagePool() {
  const images = (props.mediaList || []).filter(item => item?.mediaType === 'IMAGE')
  if (images.length) return images
  return props.media?.mediaType === 'IMAGE' ? [props.media] : []
}

function buildAdvancedImagePool() {
  return buildBentoImagePool()
}

function getCurrentImagePoolStartIndex(images) {
  const identity = mediaIdentity.value
  const index = images.findIndex(item => resolveMediaIdentity(item) === identity)
  return index >= 0 ? index : 0
}

async function resolveBentoItem(item) {
  if (!item?.url) return ''
  try {
    const resolvedUrl = await warmSecureObjectUrl(item.url)
    const imageUrl = resolvedUrl || item.url
    await decodeImage(imageUrl)
    return imageUrl
  } catch (error) {
    await decodeImage(item.url).catch(() => {})
    return item.url
  }
}

async function resolveAdvancedPoolItem(item) {
  return resolveBentoItem(item)
}

function decodeImage(src) {
  if (!src) return Promise.resolve()
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => {
      if (image.decode) {
        image.decode().then(resolve).catch(resolve)
      } else {
        resolve()
      }
    }
    image.onerror = reject
    image.src = src
  })
}

function clearBentoTimer() {
  if (bentoTimer) {
    clearTimeout(bentoTimer)
    bentoTimer = null
  }
}

function clearFrameWallTimer() {
  if (frameWallTimer) {
    clearTimeout(frameWallTimer)
    frameWallTimer = null
  }
}

function clearCarouselTimer() {
  if (carouselTimer) {
    clearTimeout(carouselTimer)
    carouselTimer = null
  }
}

function clearAdvancedTimers() {
  clearBentoTimer()
  clearFrameWallTimer()
  clearCarouselTimer()
}

async function initializeBentoDisplay() {
  clearAdvancedTimers()
  if (!isBentoLayout.value || props.media?.mediaType !== 'IMAGE') {
    bentoDisplayItems.value = []
    return
  }
  const images = buildBentoImagePool()
  if (!images.length) {
    bentoDisplayItems.value = []
    return
  }
  const slotCount = getBentoSlotCount()
  const startIndex = getCurrentImagePoolStartIndex(images)
  const initialItems = []
  for (let index = 0; index < slotCount; index += 1) {
    const media = images[(startIndex + index) % images.length]
    initialItems.push({ media, identity: resolveMediaIdentity(media), resolvedUrl: '', version: 0 })
  }
  bentoDisplayItems.value = initialItems
  bentoNextSourceIndex.value = (startIndex + slotCount) % images.length
  await Promise.all(initialItems.map(async (item, index) => {
    const resolvedUrl = await resolveBentoItem(item.media)
    bentoDisplayItems.value[index] = { ...bentoDisplayItems.value[index], resolvedUrl }
  }))
  bentoDisplayItems.value = [...bentoDisplayItems.value]
  if (!advancedReadyEmitted.value) {
    advancedReadyEmitted.value = true
    handleMediaLoaded()
  }
  scheduleBentoSwap()
}

function getFrameWallSlotCount() {
  return 8
}

async function initializeFrameWallDisplay() {
  clearAdvancedTimers()
  if (!isFrameWallLayout.value || props.media?.mediaType !== 'IMAGE') {
    frameWallDisplayItems.value = []
    return
  }
  const images = buildAdvancedImagePool()
  if (!images.length) {
    frameWallDisplayItems.value = []
    return
  }
  const slotCount = getFrameWallSlotCount()
  const startIndex = getCurrentImagePoolStartIndex(images)
  const initialItems = []
  for (let index = 0; index < slotCount; index += 1) {
    const media = images[(startIndex + index) % images.length]
    initialItems.push({ media, identity: resolveMediaIdentity(media), resolvedUrl: '', version: 0 })
  }
  frameWallDisplayItems.value = initialItems
  frameWallNextSourceIndex.value = (startIndex + slotCount) % images.length
  await Promise.all(initialItems.map(async (item, index) => {
    const resolvedUrl = await resolveAdvancedPoolItem(item.media)
    frameWallDisplayItems.value[index] = { ...frameWallDisplayItems.value[index], resolvedUrl }
  }))
  frameWallDisplayItems.value = [...frameWallDisplayItems.value]
  if (!advancedReadyEmitted.value) {
    advancedReadyEmitted.value = true
    handleMediaLoaded()
  }
  scheduleFrameWallSwap()
}

function scheduleFrameWallSwap() {
  clearFrameWallTimer()
  if (!isFrameWallLayout.value || !frameWallDisplayItems.value.length) return
  const durationSeconds = props.media?.itemDuration || props.album?.itemDuration || 10
  frameWallTimer = window.setTimeout(swapFrameWallSlots, Math.max(1, durationSeconds) * 1000)
}

function pickNextFrameWallSlot(slotCount) {
  if (slotCount <= 1) return 0
  let next = Math.floor(Math.random() * slotCount)
  if (next === frameWallPreviousSlotIndex.value) next = (next + 1) % slotCount
  frameWallPreviousSlotIndex.value = next
  return next
}

async function swapFrameWallSlots() {
  const images = buildAdvancedImagePool()
  if (!isFrameWallLayout.value || !images.length || !frameWallDisplayItems.value.length) return
  const replaceCount = Math.max(1, Math.floor(frameWallDisplayItems.value.length / 10) + 1)
  for (let count = 0; count < replaceCount; count += 1) {
    const slotIndex = pickNextFrameWallSlot(frameWallDisplayItems.value.length)
    const media = images[frameWallNextSourceIndex.value % images.length]
    frameWallNextSourceIndex.value = (frameWallNextSourceIndex.value + 1) % images.length
    const resolvedUrl = await resolveAdvancedPoolItem(media)
    const current = frameWallDisplayItems.value[slotIndex]
    frameWallDisplayItems.value[slotIndex] = {
      media,
      identity: resolveMediaIdentity(media),
      resolvedUrl,
      flipAxis: Math.random() > 0.5 ? 'x' : 'y',
      version: (current?.version || 0) + 1
    }
    frameWallDisplayItems.value = [...frameWallDisplayItems.value]
    if (count < replaceCount - 1) await new Promise(resolve => window.setTimeout(resolve, 800))
  }
  scheduleFrameWallSwap()
}

async function initializeCarouselDisplay() {
  clearAdvancedTimers()
  if (!isCarouselLayout.value || props.media?.mediaType !== 'IMAGE') {
    carouselDisplayItems.value = []
    return
  }
  const images = buildAdvancedImagePool()
  if (!images.length) {
    carouselDisplayItems.value = []
    return
  }
  carouselActiveSourceIndex.value = getCurrentImagePoolStartIndex(images)
  await updateCarouselWindow()
  if (!advancedReadyEmitted.value) {
    advancedReadyEmitted.value = true
    handleMediaLoaded()
  }
  scheduleCarouselStep()
}

function getCarouselWindowSize(images) {
  if (images.length >= 5) return 5
  if (images.length >= 3) return 3
  return images.length
}

async function updateCarouselWindow() {
  const images = buildAdvancedImagePool()
  const count = getCarouselWindowSize(images)
  const center = Math.floor(count / 2)
  const nextItems = []
  for (let index = 0; index < count; index += 1) {
    const sourceIndex = (carouselActiveSourceIndex.value + index - center + images.length) % images.length
    const media = images[sourceIndex]
    const resolvedUrl = await resolveAdvancedPoolItem(media)
    nextItems.push({
      media,
      identity: resolveMediaIdentity(media),
      resolvedUrl,
      version: carouselActiveSourceIndex.value
    })
  }
  carouselDisplayItems.value = nextItems
}

function scheduleCarouselStep() {
  clearCarouselTimer()
  if (!isCarouselLayout.value || !carouselDisplayItems.value.length) return
  const durationSeconds = props.media?.itemDuration || props.album?.itemDuration || 10
  carouselTimer = window.setTimeout(stepCarousel, Math.max(1, durationSeconds) * 1000)
}

async function stepCarousel() {
  const images = buildAdvancedImagePool()
  if (!isCarouselLayout.value || !images.length) return
  carouselActiveSourceIndex.value = (carouselActiveSourceIndex.value + 1) % images.length
  await updateCarouselWindow()
  scheduleCarouselStep()
}

function scheduleBentoSwap() {
  clearBentoTimer()
  if (!isBentoLayout.value || !bentoDisplayItems.value.length) return
  const durationSeconds = props.media?.itemDuration || props.album?.itemDuration || 10
  bentoTimer = window.setTimeout(swapOneBentoSlot, Math.max(1, durationSeconds) * 1000)
}

function pickNextBentoSlot(slotCount) {
  if (slotCount <= 1) return 0
  let next = Math.floor(Math.random() * slotCount)
  if (next === bentoPreviousSlotIndex.value) {
    next = (next + 1) % slotCount
  }
  bentoPreviousSlotIndex.value = next
  return next
}

async function swapOneBentoSlot() {
  const images = buildBentoImagePool()
  if (!isBentoLayout.value || !images.length || !bentoDisplayItems.value.length) return
  const slotIndex = pickNextBentoSlot(bentoDisplayItems.value.length)
  const media = images[bentoNextSourceIndex.value % images.length]
  bentoNextSourceIndex.value = (bentoNextSourceIndex.value + 1) % images.length
  const resolvedUrl = await resolveBentoItem(media)
  const current = bentoDisplayItems.value[slotIndex]
    bentoDisplayItems.value[slotIndex] = {
    media,
    identity: resolveMediaIdentity(media),
    resolvedUrl,
    flipAxis: Math.random() > 0.5 ? 'x' : 'y',
    version: (current?.version || 0) + 1
  }
  bentoDisplayItems.value = [...bentoDisplayItems.value]
  scheduleBentoSwap()
}

async function warmAdvancedImages() {
  const items = advancedImageItems.value
  if (!isAdvancedImageLayout.value || !items.length) {
    advancedResolvedUrls.value = {}
    return
  }
  const identitySet = new Set(items.map(item => resolveMediaIdentity(item)))
  const nextUrls = Object.fromEntries(
    Object.entries(advancedResolvedUrls.value).filter(([identity]) => identitySet.has(identity))
  )
  advancedResolvedUrls.value = nextUrls

  await Promise.all(items.map(async item => {
    const identity = resolveMediaIdentity(item)
    if (!item?.url || nextUrls[identity]) return
    try {
      const resolvedUrl = await warmSecureObjectUrl(item.url)
      advancedResolvedUrls.value = {
        ...advancedResolvedUrls.value,
        [identity]: resolvedUrl || item.url
      }
    } catch (error) {
      advancedResolvedUrls.value = {
        ...advancedResolvedUrls.value,
        [identity]: item.url
      }
    }
  }))
}

function handleAdvancedImageLoaded(index) {
  const next = new Set(advancedLoadedIndexes.value)
  next.add(index)
  advancedLoadedIndexes.value = next
  if (index === primaryAdvancedIndex.value && !advancedReadyEmitted.value) {
    advancedReadyEmitted.value = true
    handleMediaLoaded()
  }
}

function handleAdvancedImageError(index) {
  const next = new Set(advancedFailedIndexes.value)
  next.add(index)
  advancedFailedIndexes.value = next
  if (index === primaryAdvancedIndex.value) {
    handleMediaError()
  }
}

function resolveConcreteImageTransition(style, identity) {
  if (!style || style === 'NONE') {
    return 'NONE'
  }
  if (style === 'RANDOM') {
    const hash = Math.abs(String(identity || '').split('').reduce((sum, char) => ((sum * 31) + char.charCodeAt(0)) | 0, 0))
    return RANDOM_IMAGE_TRANSITIONS[hash % RANDOM_IMAGE_TRANSITIONS.length]
  }
  return style
}

function handleMediaLoaded() {
  loadErrorMessage.value = ''
  emit('loaded')
}

function handleMediaError() {
  loadErrorMessage.value = buildMediaErrorMessage()
  emit('error', loadErrorMessage.value)
}

function buildMediaErrorMessage() {
  if (!props.media) {
    return '当前媒体加载失败'
  }

  if (mediaResolveError.value) {
    return `${props.media.fileName || '当前媒体'} 鉴权加载失败`
  }

  if (props.media.mediaType === 'IMAGE') {
    return `图片加载失败：${props.media.fileName || '未命名文件'}`
  }

  if (props.media.mediaType === 'VIDEO') {
    return `视频播放失败：${resolveMediaElementErrorMessage('视频', videoRef.value)}`
  }

  if (props.media.mediaType === 'AUDIO') {
    return `音频播放失败：${resolveMediaElementErrorMessage('音频', audioRef.value)}`
  }

  return `媒体加载失败：${props.media.fileName || props.media.mediaType || '未知媒体'}`
}

function resolveMediaElementErrorMessage(mediaLabel, element) {
  const media = props.media
  const errorCode = element?.error?.code
  const detail = mediaErrorMap[errorCode]
  if (detail) {
    return `${detail}：${media?.fileName || '未命名文件'}`
  }
  return media?.fileName ? `${media.fileName} ${mediaLabel}无法播放` : `当前${mediaLabel}无法播放`
}

watch(
  () => [mediaIdentity.value, props.media?.mediaType],
  () => {
    loadErrorMessage.value = ''
    const nextMediaType = props.media?.mediaType || ''
    advancedLoadedIndexes.value = new Set()
    advancedFailedIndexes.value = new Set()
    advancedReadyEmitted.value = false
    enableImageTransition.value = !isAdvancedImageLayout.value && previousMediaType.value === 'IMAGE' && nextMediaType === 'IMAGE'
    concreteImageTransition.value = enableImageTransition.value
      ? resolveConcreteImageTransition(normalizedTransitionStyle.value, mediaIdentity.value)
      : 'NONE'
    previousMediaType.value = nextMediaType
  },
  { immediate: true }
)

watch(
  () => [mediaIdentity.value, normalizedDisplayStyle.value, normalizedDisplayVariant.value, props.mediaList.map(item => resolveMediaIdentity(item)).join('|')],
  () => {
    if (isBentoLayout.value) {
      initializeBentoDisplay()
    } else if (isFrameWallLayout.value) {
      initializeFrameWallDisplay()
    } else if (isCarouselLayout.value) {
      initializeCarouselDisplay()
    } else {
      clearAdvancedTimers()
      warmAdvancedImages()
    }
  },
  { immediate: true }
)

watch(isCalendarLayout, value => {
  if (value && !advancedReadyEmitted.value) {
    advancedReadyEmitted.value = true
    handleMediaLoaded()
  }
}, { immediate: true })

clockTimer = setInterval(() => {
  now.value = new Date()
}, 1000)

function handleViewportResize() {
  viewportVersion.value += 1
}

window.addEventListener('resize', handleViewportResize)

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
  clearAdvancedTimers()
  window.removeEventListener('resize', handleViewportResize)
})

watch(mediaResolveError, value => {
  if (value) {
    handleMediaError()
  }
})

watch(
  () => props.muted,
  value => {
    if (videoRef.value) {
      videoRef.value.muted = value
    }
    if (audioRef.value) {
      audioRef.value.muted = value
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.media-player {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  border-radius: 20px;
  background: radial-gradient(circle at top, rgba(30, 41, 59, 0.9), rgba(2, 6, 23, 0.98));
  perspective: 1200px;
  transform-style: preserve-3d;
}

.placeholder {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: rgba(255, 255, 255, 0.72);
  padding: 24px;
  text-align: center;
}

.placeholder-text {
  color: rgba(255, 255, 255, 0.6);
  word-break: break-word;
}

.image-content,
.video-content {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #000;
}

.transition-image {
  position: absolute;
  inset: 0;
  display: block;
  backface-visibility: hidden;
  transform-style: preserve-3d;
  will-change: opacity, transform, clip-path;
}

.advanced-layout {
  position: absolute;
  inset: 0;
  display: grid;
  gap: clamp(6px, 0.8vw, 12px);
  padding: clamp(10px, 1.2vw, 18px);
  animation: advancedFadeIn 650ms ease-in-out both;
}

.advanced-image {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  object-fit: cover;
  border-radius: 16px;
  background: #020617;
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.32);
}

.advanced-bento .advanced-image.flip-y,
.advanced-frame-wall .advanced-image.flip-y {
  animation: bentoCellFlip 620ms ease-in-out both;
  transform-origin: center;
}

.advanced-bento .advanced-image.flip-x,
.advanced-frame-wall .advanced-image.flip-x {
  animation: bentoCellFlipX 620ms ease-in-out both;
  transform-origin: center;
}

.advanced-bento {
  grid-template-columns: repeat(6, minmax(0, 1fr));
  grid-template-rows: repeat(4, minmax(0, 1fr));
  grid-auto-flow: dense;
}

.advanced-frame-wall {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  grid-template-rows: repeat(2, minmax(0, 1fr));
}

.calendar-layout {
  position: absolute;
  inset: 0;
  display: grid;
  grid-template-columns: 67fr 33fr;
  grid-template-rows: 1fr;
  background: #020617;
  color: #fff;
}

.calendar-media-pane {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: #000;
}

.calendar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  animation: calendarMediaFade 2500ms ease-in-out both;
}

.calendar-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: clamp(24px, 4vw, 72px);
  background: #050b18;
  text-align: center;
}

.calendar-month,
.calendar-weekday {
  padding: 8px 0;
  font-size: clamp(24px, 3vw, 42px);
  opacity: 0.9;
}

.calendar-day {
  font-size: clamp(96px, 14vw, 180px);
  font-weight: 800;
  line-height: 0.95;
}

@keyframes calendarMediaFade {
  from { opacity: 0.2; }
  to { opacity: 1; }
}

@media (orientation: portrait) {
  .calendar-layout {
    grid-template-columns: 1fr;
    grid-template-rows: 67fr 33fr;
  }

  .calendar-card {
    padding: clamp(20px, 6vw, 56px);
  }

  .calendar-month,
  .calendar-weekday {
    font-size: clamp(22px, 6vw, 40px);
  }

  .calendar-day {
    font-size: clamp(88px, 22vw, 180px);
  }
}

.time-overlay {
  position: absolute;
  right: 32px;
  bottom: 30px;
  padding: 16px 22px;
  border-radius: 22px;
  background: rgba(2, 6, 23, 0.48);
  color: #fff;
  text-align: right;
  backdrop-filter: blur(14px);
}

.time-overlay-time {
  font-size: clamp(34px, 4vw, 64px);
  font-weight: 800;
  line-height: 1;
}

.time-overlay-date {
  margin-top: 8px;
  font-size: clamp(14px, 1.4vw, 22px);
  opacity: 0.88;
}

.advanced-carousel {
  display: block;
  position: absolute;
  inset: 0;
  overflow: hidden;
  padding: 16px;
}

.advanced-carousel .advanced-image {
  position: absolute;
  transition: left 1000ms ease, transform 1000ms ease, opacity 1000ms ease;
}

.advanced-carousel .advanced-image.primary {
  z-index: 2;
}

@media (max-width: 1280px), (max-height: 720px) {
  .advanced-carousel {
    padding: 10px;
  }
}

@keyframes advancedFadeIn {
  from { opacity: 0; transform: scale(0.985); }
  to { opacity: 1; transform: scale(1); }
}

@keyframes bentoCellFlip {
  0% { opacity: 0.18; transform: rotateY(-88deg) scale(0.98); }
  55% { opacity: 0.92; transform: rotateY(8deg) scale(1.01); }
  100% { opacity: 1; transform: rotateY(0deg) scale(1); }
}

@keyframes bentoCellFlipX {
  0% { opacity: 0.18; transform: rotateX(-88deg) scale(0.98); }
  55% { opacity: 0.92; transform: rotateX(8deg) scale(1.01); }
  100% { opacity: 1; transform: rotateX(0deg) scale(1); }
}

.image-fade-enter-active,
.image-fade-leave-active,
.image-slide-enter-active,
.image-slide-leave-active,
.image-cube-enter-active,
.image-cube-leave-active,
.image-reveal-enter-active,
.image-reveal-leave-active,
.image-flip-enter-active,
.image-flip-leave-active {
  transition: opacity 650ms ease-in-out, transform 650ms ease-in-out, clip-path 650ms ease-in-out;
}

.image-fade-enter-from,
.image-fade-leave-to {
  opacity: 0;
}

.image-slide-enter-from {
  opacity: 0.4;
  transform: translateX(100%) scale(1.02);
}

.image-slide-leave-to {
  opacity: 0;
  transform: translateX(-24%) scale(0.98);
}

.image-cube-enter-from {
  opacity: 0.2;
  transform: rotateY(90deg);
  transform-origin: right center;
}

.image-cube-leave-to {
  opacity: 0.2;
  transform: rotateY(-90deg);
  transform-origin: left center;
}

.image-reveal-enter-from {
  clip-path: circle(0% at 50% 50%);
}

.image-reveal-enter-to {
  clip-path: circle(75% at 50% 50%);
}

.image-reveal-leave-to {
  opacity: 0.35;
}

.image-flip-enter-from {
  opacity: 0.2;
  transform: rotateY(-180deg);
}

.image-flip-leave-to {
  opacity: 0.2;
  transform: rotateY(180deg);
}

@media (prefers-reduced-motion: reduce) {
  .image-fade-enter-active,
  .image-fade-leave-active,
  .image-slide-enter-active,
  .image-slide-leave-active,
  .image-cube-enter-active,
  .image-cube-leave-active,
  .image-reveal-enter-active,
  .image-reveal-leave-active,
  .image-flip-enter-active,
  .image-flip-leave-active {
    transition-duration: 1ms;
  }
}

.audio-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 32px;
  text-align: center;
  color: rgba(255, 255, 255, 0.88);
}

.audio-title {
  font-size: 28px;
  line-height: 1.5;
  max-width: 720px;
  word-break: break-word;
}

.audio-element {
  width: min(480px, 100%);
}
</style>
