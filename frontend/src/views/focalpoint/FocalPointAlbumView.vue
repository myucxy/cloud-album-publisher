<template>
  <div>
    <a-page-header
      :title="(album?.title || '加载中...') + ' - 焦点管理'"
      @back="router.push('/focal-point')"
    />

    <a-card style="margin-bottom:16px">
      <div style="display:flex; align-items:center; gap:16px; flex-wrap:wrap">
        <a-tag v-if="album?.focalPointProvider" :color="getProviderColor(album.focalPointProvider)" style="font-size:14px">
          {{ album.focalPointProvider }}
        </a-tag>
        <span style="color:#8c8c8c">
          已检测: {{ detectedCount }} / {{ totalCount }}
        </span>
        <a-progress
          v-if="totalCount > 0"
          :percent="Math.round(detectedCount / totalCount * 100)"
          style="width:200px"
        />
        <a-button type="primary" size="small" @click="runDetection" :loading="processing" :disabled="!album?.focalPointProvider">
          重新检测
        </a-button>
        <a-button size="small" @click="clearAll" :disabled="detectedCount === 0">
          清除全部
        </a-button>
      </div>
    </a-card>

    <a-spin :spinning="loading">
      <div class="thumb-grid">
        <div v-for="(item, idx) in mediaItems" :key="item.id" class="thumb-grid-item">
          <a-card size="small" hoverable>
            <template #cover>
              <div
                class="thumb-container"
                @click="openDetail(item)"
              >
                <!-- 有焦点：以焦点为中心裁切预览 -->
                <template v-if="item.focalPointX != null && item.focalPointY != null">
                  <div style="width:100%; height:100%; overflow:hidden; position:relative">
                    <SecureImage
                      v-if="item.thumbnailUrl || item.url"
                      :src="item.thumbnailUrl || item.url"
                      :alt="item.fileName"
                      :img-style="thumbCropStyle(item)"
                      @load="e => onThumbLoad(e, item)"
                    />
                  </div>
                  <div
                    class="thumb-focal-dot"
                    :style="thumbDotStyle(item)"
                  />
                </template>
                <!-- 无焦点：完整显示 -->
                <template v-else>
                  <SecureImage
                    v-if="item.thumbnailUrl || item.url"
                    :src="item.thumbnailUrl || item.url"
                    :alt="item.fileName"
                    img-style="max-width:100%; max-height:100%; object-fit:contain; display:block; margin:auto"
                  />
                  <picture-outlined v-else style="font-size:32px; color:#d9d9d9" />
                </template>
              </div>
            </template>

            <a-card-meta>
              <template #title>
                <span style="font-size:12px; display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap">
                  {{ item.fileName }}
                </span>
              </template>
              <template #description>
                <a-tag v-if="item.focalPointProvider" size="small" :color="getProviderColor(item.focalPointProvider)">
                  {{ item.focalPointProvider }}
                </a-tag>
                <a-tag v-else size="small" color="default">未检测</a-tag>
              </template>
            </a-card-meta>

            <div style="margin-top:8px">
              <a-button size="small" block @click="openDetail(item)">查看 / 编辑</a-button>
            </div>
          </a-card>
        </div>
      </div>

      <a-empty v-if="!loading && mediaItems.length === 0" description="相册暂无图片" />
    </a-spin>

    <a-pagination
      v-if="pagination.total > pagination.pageSize"
      v-model:current="pagination.current"
      :page-size="pagination.pageSize"
      :total="pagination.total"
      style="margin-top:16px; text-align:center"
      @change="loadMediaItems"
    />

    <!-- Detail Modal: preview + edit combined -->
    <a-modal
      v-model:open="detailVisible"
      :title="detailItem?.fileName || '焦点详情'"
      :width="960"
      @ok="saveFocalPoint"
      @cancel="closeDetail"
      ok-text="保存焦点"
      cancel-text="关闭"
      :confirm-loading="saving"
    >
      <div v-if="detailItem" style="display:flex; gap:20px">
        <!-- Left: image with editable focal point -->
        <div style="flex:1; min-width:0">
          <div
            ref="imageContainer"
            class="focal-image-container"
            @mousedown="onContainerMouseDown"
            @mousemove="onContainerMouseMove"
            @mouseup="onContainerMouseUp"
            @mouseleave="onContainerMouseUp"
          >
            <SecureImage
              v-if="detailItem.url"
              :src="detailItem.url"
              alt="media"
              img-style="max-width:100%; max-height:500px; display:block; pointer-events:none"
              @load="onDetailImageLoad"
            />
            <div
              v-if="editorFocalX != null && editorFocalY != null"
              class="focal-point-marker"
              :class="{ dragging: isDragging }"
              :style="markerStyle"
              @mousedown.stop="onMarkerMouseDown"
            />
          </div>
          <div style="margin-top:6px; color:#8c8c8c; font-size:12px; display:flex; justify-content:space-between">
            <span>点击图片或拖动标记设置焦点</span>
            <a-button size="small" @click="resetEditor">重置为中心</a-button>
          </div>
        </div>

        <!-- Right: metadata + coordinates + crop preview -->
        <div style="width:240px; flex-shrink:0">
          <a-descriptions :column="1" size="small" bordered style="margin-bottom:16px">
            <a-descriptions-item label="文件名">{{ detailItem.fileName }}</a-descriptions-item>
            <a-descriptions-item label="类型">{{ detailItem.mediaType }}</a-descriptions-item>
            <template v-if="detailItem.focalPointProvider">
              <a-descriptions-item label="Provider">
                <a-tag :color="getProviderColor(detailItem.focalPointProvider)" size="small">{{ detailItem.focalPointProvider }}</a-tag>
              </a-descriptions-item>
              <a-descriptions-item v-if="detailItem.focalPointConfidence != null" label="置信度">
                {{ (detailItem.focalPointConfidence * 100).toFixed(0) }}%
              </a-descriptions-item>
              <a-descriptions-item v-if="detailItem.focalPointRegionType" label="区域类型">
                {{ detailItem.focalPointRegionType }}
              </a-descriptions-item>
            </template>
          </a-descriptions>

          <a-form layout="vertical" size="small">
            <a-form-item label="X 坐标">
              <a-input-number v-model:value="editorFocalX" :min="0" :max="1" :step="0.01" style="width:100%" />
            </a-form-item>
            <a-form-item label="Y 坐标">
              <a-input-number v-model:value="editorFocalY" :min="0" :max="1" :step="0.01" style="width:100%" />
            </a-form-item>
          </a-form>

          <div v-if="editorFocalX != null" style="margin-top:8px">
            <div style="font-weight:500; margin-bottom:8px; font-size:13px">裁切模拟 (3:2)</div>
            <div style="aspect-ratio:3/2; overflow:hidden; outline:1px solid #d9d9d9; border-radius:4px; position:relative; background:#000">
              <SecureImage
                v-if="detailItem.url"
                :src="detailItem.url"
                alt="crop preview"
                :img-style="cropPreviewStyle"
              />
            </div>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { albumApi } from '@/api/album'
import { focalPointApi } from '@/api/focal-point'
import SecureImage from '@/components/SecureImage.vue'
import { PictureOutlined } from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const albumId = route.params.albumId

const album = ref(null)
const mediaItems = ref([])
const loading = ref(false)
const processing = ref(false)
const saving = ref(false)
const pagination = reactive({ current: 1, pageSize: 30, total: 0 })

const detectedCount = computed(() => mediaItems.value.filter(m => m.focalPointX != null).length)
const totalCount = computed(() => mediaItems.value.length)

// Detail modal (merged preview + editor)
const detailVisible = ref(false)
const detailItem = ref(null)
const editorFocalX = ref(null)
const editorFocalY = ref(null)
const imageContainer = ref(null)
const isDragging = ref(false)

const markerStyle = computed(() => {
  if (editorFocalX.value == null || editorFocalY.value == null) return {}
  return { left: `${editorFocalX.value * 100}%`, top: `${editorFocalY.value * 100}%` }
})

// Fixed container size for thumbnail cropping
const thumbContainerW = ref(180)
const thumbContainerH = ref(120)

// Full image dimensions cache (itemId -> { w, h }) — used for consistent crop calculation
const imageDimensions = reactive({})

const onThumbLoad = (e, item) => {
  const img = e.target || e.srcElement
  if (img && img.naturalWidth && img.naturalHeight && !imageDimensions[item.id]) {
    imageDimensions[item.id] = { w: img.naturalWidth, h: img.naturalHeight }
  }
}

// Build crop style using object-fit: cover + computed object-position
function buildCropStyle(fpX, fpY, imgW, imgH, cw, ch) {
  if (!imgW || !imgH) {
    return { position: 'absolute', width: '100%', height: '100%', objectFit: 'cover', objectPosition: 'center' }
  }
  const imgRatio = imgW / imgH
  const containerRatio = cw / ch
  let renderW, renderH
  if (imgRatio > containerRatio) { renderH = ch; renderW = ch * imgRatio }
  else { renderW = cw; renderH = cw / imgRatio }
  return {
    position: 'absolute',
    width: '100%',
    height: '100%',
    objectFit: 'cover',
    objectPosition: `${cw / 2 - fpX * renderW}px ${ch / 2 - fpY * renderH}px`
  }
}

// Thumbnail crop style
const thumbCropStyle = (item) => {
  const fpX = item.focalPointX ?? 0.5
  const fpY = item.focalPointY ?? 0.5
  const dim = imageDimensions[item.id]
  return buildCropStyle(fpX, fpY, dim?.w, dim?.h, thumbContainerW.value, thumbContainerH.value)
}

// Red dot: always at container center (image cropped to center focal point)
const thumbDotStyle = () => ({ left: '50%', top: '50%' })

// When detail image loads, update the shared dimensions cache
const onDetailImageLoad = (e) => {
  const img = e.target || e.srcElement
  if (img && detailItem.value) {
    imageDimensions[detailItem.value.id] = { w: img.naturalWidth || 0, h: img.naturalHeight || 0 }
  }
}

// Crop preview in detail modal — same formula and ratio as thumbnail
const cropPreviewStyle = computed(() => {
  if (editorFocalX.value == null || editorFocalY.value == null) return {}
  const dim = detailItem.value ? imageDimensions[detailItem.value.id] : null
  return buildCropStyle(editorFocalX.value, editorFocalY.value, dim?.w, dim?.h, thumbContainerW.value, thumbContainerH.value)
})

// Drag handling
const updateFocalFromEvent = (e) => {
  const container = imageContainer.value
  if (!container) return
  const rect = container.getBoundingClientRect()
  const x = (e.clientX - rect.left) / rect.width
  const y = (e.clientY - rect.top) / rect.height
  editorFocalX.value = Math.max(0, Math.min(1, x))
  editorFocalY.value = Math.max(0, Math.min(1, y))
}

const onMarkerMouseDown = (e) => {
  e.preventDefault()
  isDragging.value = true
}

const onContainerMouseDown = (e) => {
  if (!isDragging.value) {
    updateFocalFromEvent(e)
  }
}

const onContainerMouseMove = (e) => {
  if (isDragging.value) {
    updateFocalFromEvent(e)
  }
}

const onContainerMouseUp = () => {
  isDragging.value = false
}

onMounted(async () => {
  loadAlbum()
  await loadMediaItems()
})

const loadAlbum = async () => {
  try {
    const res = await albumApi.get(albumId)
    album.value = res.data
  } catch (e) {
    message.error('加载相册信息失败')
  }
}

const loadMediaItems = async () => {
  loading.value = true
  try {
    const res = await albumApi.listContents(albumId, {
      page: pagination.current,
      size: pagination.pageSize
    })
    mediaItems.value = res.data.list || []
    pagination.total = res.data.total || 0
  } catch (e) {
    message.error('加载媒体列表失败')
  } finally {
    loading.value = false
  }
}

const runDetection = async () => {
  if (!album.value?.focalPointProvider) {
    message.warning('请先配置检测 Provider')
    return
  }
  processing.value = true
  try {
    const res = await focalPointApi.batchProcess(albumId, {
      providerType: album.value.focalPointProvider
    })
    const result = res.data
    message.success(`处理完成: 总计 ${result.totalItems}, 成功 ${result.processedItems}, 跳过 ${result.skippedItems}, 失败 ${result.failedItems}`)
    await loadMediaItems()
  } catch (e) {
    message.error('检测失败: ' + (e.response?.data?.message || e.message))
  } finally {
    processing.value = false
  }
}

const clearAll = () => {
  Modal.confirm({
    title: '确认清除',
    content: '确定要清除该相册所有图片的焦点数据吗？',
    onOk: async () => {
      const items = mediaItems.value.filter(m => m.focalPointX != null)
      let cleared = 0
      for (const item of items) {
        try {
          await focalPointApi.clearFocalPoint(albumId, item.id)
          cleared++
        } catch (e) {
          // skip failed
        }
      }
      message.success(`已清除 ${cleared} 项焦点数据`)
      await loadMediaItems()
    }
  })
}

const openDetail = (item) => {
  detailItem.value = item
  editorFocalX.value = item.focalPointX ?? 0.5
  editorFocalY.value = item.focalPointY ?? 0.5
  detailVisible.value = true
}

const closeDetail = () => {
  detailVisible.value = false
  detailItem.value = null
  isDragging.value = false
}

const saveFocalPoint = async () => {
  saving.value = true
  try {
    await focalPointApi.updateFocalPoint(albumId, detailItem.value.id, {
      x: editorFocalX.value,
      y: editorFocalY.value
    })
    message.success('焦点已保存')
    detailVisible.value = false
    await loadMediaItems()
  } catch (e) {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

const resetEditor = () => {
  editorFocalX.value = 0.5
  editorFocalY.value = 0.5
}

const getProviderColor = (provider) => {
  const colors = {
    'MANUAL': 'orange',
    'OPENCV': 'green',
    'ONNX': 'blue',
    'VISION_LLM': 'purple'
  }
  return colors[provider] || 'default'
}
</script>

<style scoped>
.thumb-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.thumb-grid-item {
  width: 180px;
  flex-shrink: 0;
}

.thumb-container {
  height: 120px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
  position: relative;
  cursor: pointer;
}

.thumb-focal-dot {
  position: absolute;
  width: 10px;
  height: 10px;
  margin-left: -5px;
  margin-top: -5px;
  border: 2px solid #ff4d4f;
  border-radius: 50%;
  pointer-events: none;
  background: rgba(255, 77, 79, 0.3);
  z-index: 1;
}

.focal-image-container {
  position: relative;
  display: inline-block;
  cursor: crosshair;
  outline: 1px solid #d9d9d9;
  border-radius: 4px;
  overflow: hidden;
  user-select: none;
  -webkit-user-select: none;
}

.focal-point-marker {
  position: absolute;
  width: 20px;
  height: 20px;
  margin-left: -10px;
  margin-top: -10px;
  border: 2px solid #ff4d4f;
  border-radius: 50%;
  cursor: grab;
  z-index: 10;
}

.focal-point-marker.dragging {
  cursor: grabbing;
  transform: scale(1.3);
  box-shadow: 0 0 8px rgba(255, 77, 79, 0.5);
}

.focal-point-marker::before,
.focal-point-marker::after {
  content: '';
  position: absolute;
  background: #ff4d4f;
}

.focal-point-marker::before {
  width: 2px;
  height: 14px;
  left: 9px;
  top: 3px;
}

.focal-point-marker::after {
  width: 14px;
  height: 2px;
  left: 3px;
  top: 9px;
}
</style>
