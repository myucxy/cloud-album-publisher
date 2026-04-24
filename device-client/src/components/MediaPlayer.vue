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
    <img
      v-else-if="media.mediaType === 'IMAGE' && mediaResolvedUrl"
      :key="`image-${mediaIdentity}`"
      class="image-content"
      :src="mediaResolvedUrl"
      :alt="media.fileName"
      @load="handleMediaLoaded"
      @error="handleMediaError"
    />
    <video
      v-else-if="media.mediaType === 'VIDEO' && mediaResolvedUrl"
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
      v-else-if="media.mediaType === 'AUDIO' && mediaResolvedUrl"
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
    <div v-else class="placeholder">
      <a-empty description="暂不支持的媒体类型" />
      <div class="placeholder-text">{{ media.mediaType }} / {{ media.fileName }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useSecureObjectUrl } from '@/components/useSecureObjectUrl'
import { resolveMediaIdentity } from '@/stores/player'

const props = defineProps({
  media: {
    type: Object,
    default: null
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
  }
})

const emit = defineEmits(['ended', 'loaded', 'error'])

const videoRef = ref(null)
const audioRef = ref(null)
const loadErrorMessage = ref('')
const mediaUrl = computed(() => props.media?.url || '')
const mediaIdentity = computed(() => resolveMediaIdentity(props.media))
const { resolvedSrc: mediaResolvedUrl, error: mediaResolveError } = useSecureObjectUrl(mediaUrl)
const mediaErrorMap = {
  1: '媒体加载被中止',
  2: '网络异常，无法加载媒体',
  3: '媒体解码失败，无法播放',
  4: '媒体地址不可用或格式不受支持'
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
  () => mediaIdentity.value,
  () => {
    loadErrorMessage.value = ''
  },
  { immediate: true }
)

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
