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
      :key="`image-${media.id}`"
      class="image-content"
      :src="mediaResolvedUrl"
      :alt="media.fileName"
      @load="handleMediaLoaded"
      @error="handleMediaError"
    />
    <video
      v-else-if="media.mediaType === 'VIDEO' && mediaResolvedUrl"
      :key="`video-${media.id}`"
      ref="videoRef"
      class="video-content"
      :src="mediaResolvedUrl"
      autoplay
      playsinline
      controls
      @loadeddata="handleMediaLoaded"
      @ended="$emit('ended')"
      @error="handleMediaError"
    />
    <div v-else class="placeholder">
      <a-empty description="暂不支持的媒体类型" />
      <div class="placeholder-text">{{ media.mediaType }} / {{ media.fileName }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useSecureObjectUrl } from '@/components/useSecureObjectUrl'

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
  }
})

const emit = defineEmits(['ended', 'loaded', 'error'])

const videoRef = ref(null)
const loadErrorMessage = ref('')
const mediaUrl = computed(() => props.media?.url || '')
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
    return `视频播放失败：${resolveVideoErrorMessage()}`
  }

  return `媒体加载失败：${props.media.fileName || props.media.mediaType || '未知媒体'}`
}

function resolveVideoErrorMessage() {
  const media = props.media
  const errorCode = videoRef.value?.error?.code
  const detail = mediaErrorMap[errorCode]
  if (detail) {
    return `${detail}（${media?.fileName || '未命名文件'}）`
  }
  return media?.fileName ? `${media.fileName} 无法播放` : '当前视频无法播放'
}

watch(
  () => props.media?.id,
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
</style>
