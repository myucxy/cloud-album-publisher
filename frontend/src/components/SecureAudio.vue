<template>
  <audio
    v-if="displaySrc"
    ref="audioRef"
    :src="displaySrc"
    controls
    :style="audioStyle"
    @play="handlePlay"
    @pause="handlePause"
    @ended="handleEnded"
    @error="emit('error', $event)"
  />
</template>

<script>
let currentPlayingAudio = null
</script>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { useSecureObjectUrl } from '@/components/useSecureObjectUrl'

const props = defineProps({
  src: {
    type: String,
    default: ''
  },
  audioStyle: {
    type: [String, Object, Array],
    default: undefined
  }
})

const emit = defineEmits(['error'])
const audioRef = ref(null)
const { resolvedSrc: displaySrc, error } = useSecureObjectUrl(() => props.src)

function handlePlay() {
  const audio = audioRef.value
  if (!audio) {
    return
  }
  if (currentPlayingAudio && currentPlayingAudio !== audio) {
    currentPlayingAudio.pause()
    currentPlayingAudio.currentTime = 0
  }
  currentPlayingAudio = audio
}

function handlePause() {
  if (currentPlayingAudio === audioRef.value) {
    currentPlayingAudio = null
  }
}

function handleEnded() {
  if (currentPlayingAudio === audioRef.value) {
    currentPlayingAudio = null
  }
}

watch(error, value => {
  if (value) {
    emit('error', value)
  }
})

watch(displaySrc, () => {
  const audio = audioRef.value
  if (currentPlayingAudio === audio && !displaySrc.value) {
    currentPlayingAudio = null
  }
})

onBeforeUnmount(() => {
  if (currentPlayingAudio === audioRef.value) {
    currentPlayingAudio = null
  }
})
</script>
