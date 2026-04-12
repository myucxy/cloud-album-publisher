<template>
  <img v-if="displaySrc" :src="displaySrc" :alt="alt" :style="imgStyle" @load="emit('load', $event)" @error="emit('error', $event)" />
</template>

<script setup>
import { watch } from 'vue'
import { useSecureObjectUrl } from '@/components/useSecureObjectUrl'

const props = defineProps({
  src: {
    type: String,
    default: ''
  },
  alt: {
    type: String,
    default: ''
  },
  imgStyle: {
    type: [String, Object, Array],
    default: undefined
  }
})

const emit = defineEmits(['load', 'error'])

const { resolvedSrc: displaySrc, error } = useSecureObjectUrl(() => props.src)

watch(error, value => {
  if (value) {
    emit('error', value)
  }
})
</script>
