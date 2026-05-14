<template>
  <div>
    <div style="display:flex; justify-content:space-between; margin-bottom:16px">
      <a-space />
      <a-button type="primary" @click="openCreate"><plus-outlined /> 新建相册</a-button>
    </div>

    <a-row :gutter="[16,16]">
      <a-col v-for="album in albums" :key="album.id" :xs="24" :sm="12" :md="8" :lg="6">
        <a-card hoverable @click="router.push(`/albums/${album.id}`)">
          <template #cover>
            <SecureImage v-if="album.coverUrl" :src="album.coverUrl" alt="album cover" img-style="width:100%; height:160px; object-fit:cover" />
            <div v-else style="height:160px; background:#f0f2f5; display:flex; align-items:center; justify-content:center; color:#bbb">
              <picture-outlined style="font-size:40px" />
            </div>
          </template>
          <a-card-meta :title="album.title" :description="album.description || '暂无描述'" />
          <template #actions>
            <a-tag color="purple">{{ displayStyleLabel(album.displayStyle) }}</a-tag>
            <a-tag color="blue">{{ transitionStyleLabel(album.transitionStyle) }}</a-tag>
            <a @click.stop="openEdit(album)"><edit-outlined /></a>
            <a-popconfirm title="确认删除该相册？" @confirm.stop="deleteAlbum(album.id)">
              <a @click.stop><delete-outlined /></a>
            </a-popconfirm>
          </template>
        </a-card>
      </a-col>
    </a-row>

    <div style="margin-top:16px; text-align:right">
      <a-pagination v-model:current="page" :total="total" :page-size="pageSize" @change="load" show-quick-jumper />
    </div>

    <a-modal v-model:open="modalOpen" :title="editingId ? '编辑相册' : '新建相册'" :width="420" @ok="submitForm" :confirm-loading="saving" ok-text="保存" cancel-text="取消">
      <a-form :model="form" layout="vertical" ref="formRef">
        <a-form-item label="相册名称" name="title" :rules="[{required:true}]">
          <a-input v-model:value="form.title" />
        </a-form-item>
        <a-form-item label="描述" name="description">
          <a-textarea v-model:value="form.description" :rows="3" />
        </a-form-item>
        <a-form-item label="播放转场" name="transitionStyle">
          <a-select v-model:value="form.transitionStyle">
            <a-select-option v-for="option in TRANSITION_STYLE_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </a-select-option>
          </a-select>
          <div style="color:#8c8c8c; font-size:12px; margin-top:6px">
            转场样式按相册统一配置；客户端仅对图片应用该效果，视频会正常播放。
          </div>
        </a-form-item>
        <a-form-item label="展示布局" name="displayStyle">
          <a-select v-model:value="form.displayStyle" @change="onDisplayStyleChange">
            <a-select-option v-for="option in DISPLAY_STYLE_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </a-select-option>
          </a-select>
          <div style="color:#8c8c8c; font-size:12px; margin-top:6px">
            单图使用播放转场；Bento、相框墙、轮播墙会同时展示多张图片，视频内容仍按原视频播放器播放。
          </div>
        </a-form-item>
        <a-form-item v-if="form.displayStyle === 'BENTO'" label="Bento 样式" name="displayVariant">
          <a-radio-group v-model:value="form.displayVariant" style="width:100%">
            <div class="bento-style-grid">
              <label v-for="option in BENTO_VARIANT_OPTIONS" :key="option.value" class="bento-style-card">
                <a-radio :value="option.value">{{ option.label }}</a-radio>
                <div class="bento-preview">
                  <span
                    v-for="(slot, index) in getBentoPreviewSlots(option.value)"
                    :key="`${option.value}-${index}`"
                    :style="getBentoPreviewSlotStyle(slot, option.value)"
                  />
                </div>
              </label>
            </div>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="form.displayStyle === 'FRAME_WALL'" label="相框数量" name="displayVariant">
          <a-radio-group v-model:value="form.displayVariant" style="width:100%">
            <div class="bento-style-grid">
              <label v-for="option in FRAME_WALL_VARIANT_OPTIONS" :key="option.value" class="bento-style-card">
                <a-radio :value="option.value">{{ option.label }}</a-radio>
                <div class="bento-preview">
                  <span
                    v-for="(slot, index) in getFrameWallPreviewSlots(option.value)"
                    :key="`${option.value}-${index}`"
                    :style="getFrameWallPreviewSlotStyle(slot, option.value)"
                  />
                </div>
              </label>
            </div>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="时间日期">
          <a-switch v-model:checked="form.showTimeAndDate" checked-children="显示" un-checked-children="隐藏" />
          <div style="color:#8c8c8c; font-size:12px; margin-top:6px">
            非日历模式叠加时间日期；日历模式会使用独立日历展示。
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined, PictureOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { albumApi } from '@/api/album'
import SecureImage from '@/components/SecureImage.vue'

const TRANSITION_STYLE_OPTIONS = [
  { value: 'NONE', label: '无转场' },
  { value: 'FADE', label: '淡入淡出' },
  { value: 'SLIDE', label: '滑动缩放' },
  { value: 'CUBE', label: '立方体' },
  { value: 'REVEAL', label: '圆形揭示' },
  { value: 'FLIP', label: '翻页' },
  { value: 'RANDOM', label: '随机' }
]

const DISPLAY_STYLE_OPTIONS = [
  { value: 'SINGLE', label: '单图播放' },
  { value: 'BENTO', label: 'Bento 拼贴' },
  { value: 'FRAME_WALL', label: '相框墙' },
  { value: 'CAROUSEL', label: '轮播墙' }
]

DISPLAY_STYLE_OPTIONS.push({ value: 'CALENDAR', label: '日历模式' })

const BENTO_VARIANT_OPTIONS = [
  { value: 'DEFAULT', label: '自动' },
  { value: 'BENTO_1', label: '样式 1' },
  { value: 'BENTO_2', label: '样式 2' },
  { value: 'BENTO_3', label: '样式 3' },
  { value: 'BENTO_4', label: '样式 4' },
  { value: 'BENTO_5', label: '样式 5' },
  { value: 'BENTO_6', label: '样式 6' },
  { value: 'BENTO_7', label: '样式 7' }
]

const FRAME_WALL_VARIANT_OPTIONS = [
  { value: 'FRAME_WALL_2', label: '2 张', cols: 2, rows: 1 },
  { value: 'FRAME_WALL_4', label: '4 张', cols: 2, rows: 2 },
  { value: 'FRAME_WALL_6', label: '6 张', cols: 3, rows: 2 },
  { value: 'FRAME_WALL_8', label: '8 张', cols: 4, rows: 2 }
]

const BENTO_PREVIEW_SLOTS = {
  DEFAULT: [
    [0, 0, 3, 2], [3, 0, 1, 2], [0, 2, 1, 1], [1, 2, 2, 1], [3, 2, 1, 1]
  ],
  BENTO_1: [
    [0, 0, 1, 1], [1, 0, 1, 1], [2, 0, 2, 1], [4, 2, 1, 1], [5, 2, 1, 1],
    [0, 1, 2, 2], [2, 1, 2, 1], [4, 0, 2, 2], [2, 2, 2, 1]
  ],
  BENTO_2: [
    [0, 0, 1, 2], [0, 2, 1.5, 1], [1, 0, 1.5, 1], [2.5, 0, 1.5, 1],
    [3, 1, 1, 2], [1.5, 2, 1.5, 1], [1, 1, 1, 1], [2, 1, 1, 1]
  ],
  BENTO_3: [
    [0, 0, 1, 1.6], [1, 0, 1, 1], [2, 0, 2, 1], [0, 1.6, 1, 1], [1, 1, 2, 1],
    [3, 1, 1, 1], [1, 2, 1, 0.6], [2, 2, 1, 0.6], [3, 2, 1, 0.6]
  ],
  BENTO_4: [
    [0, 0, 2, 2], [2, 0, 1, 2], [3, 0, 1, 1], [0, 2, 1, 1],
    [1, 2, 1, 1], [2, 2, 1, 1], [3, 1, 1, 2]
  ],
  BENTO_5: [
    [0, 0, 3, 2], [3, 0, 1, 2], [0, 2, 1, 1], [1, 2, 2, 1], [3, 2, 1, 1]
  ],
  BENTO_6: [
    [0, 0, 2, 1], [2, 0, 1, 1.3], [3, 0, 1, 1.3], [4, 0, 1, 1.3], [5, 0, 2, 1],
    [0, 1, 1, 1], [1, 1, 1, 1], [0, 2, 1, 1], [1, 2, 1, 1], [0, 3, 2, 1],
    [2, 1.3, 3, 1.4], [2, 2.7, 1, 1.3], [3, 2.7, 1, 1.3], [4, 2.7, 1, 1.3],
    [5, 1, 1, 1], [6, 1, 1, 1], [5, 2, 1, 1], [6, 2, 1, 1], [5, 3, 2, 1]
  ],
  BENTO_7: [
    [0, 0, 2, 1], [2, 0, 1, 1.3], [3, 0, 1, 1.3], [4, 0, 1, 1.3], [5, 0, 2, 1],
    [0, 1, 1, 1], [1, 1, 1, 1], [0, 2, 1, 1], [1, 2, 1, 1], [0, 3, 2, 1],
    [2, 1.3, 3, 1.4], [2, 2.7, 1, 1.3], [3, 2.7, 1, 1.3], [4, 2.7, 1, 1.3],
    [5, 1, 1, 1], [6, 1, 1, 0.7], [5, 2, 1, 0.7], [6, 1.7, 1, 1], [5, 2.7, 2, 1.3]
  ]
}

const router = useRouter()
const albums = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 12
const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({ title: '', description: '', visibility: 'PUBLIC', transitionStyle: 'NONE', displayStyle: 'SINGLE', displayVariant: 'DEFAULT', showTimeAndDate: false, status: 'PUBLISHED' })

onMounted(load)

async function load() {
  const res = await albumApi.list({ page: page.value, size: pageSize })
  albums.value = res.data.list
  total.value = res.data.total
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { title: '', description: '', visibility: 'PUBLIC', transitionStyle: 'NONE', displayStyle: 'SINGLE', displayVariant: 'DEFAULT', showTimeAndDate: false, status: 'PUBLISHED' })
  modalOpen.value = true
}

function openEdit(album) {
  editingId.value = album.id
  Object.assign(form, {
    title: album.title,
    description: album.description,
    visibility: 'PUBLIC',
    transitionStyle: album.transitionStyle || 'NONE',
    displayStyle: album.displayStyle || 'SINGLE',
    displayVariant: album.displayVariant || 'DEFAULT',
    showTimeAndDate: Boolean(album.showTimeAndDate),
    status: 'PUBLISHED'
  })
  modalOpen.value = true
}

async function submitForm() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (editingId.value) {
      await albumApi.update(editingId.value, form)
      message.success('相册已更新')
    } else {
      await albumApi.create(form)
      message.success('相册已创建')
    }
    modalOpen.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function deleteAlbum(id) {
  await albumApi.remove(id)
  message.success('已删除')
  load()
}

function transitionStyleLabel(value) {
  return TRANSITION_STYLE_OPTIONS.find(option => option.value === value)?.label || '无转场'
}

function displayStyleLabel(value) {
  return DISPLAY_STYLE_OPTIONS.find(option => option.value === value)?.label || '单图播放'
}

function getBentoPreviewSlots(value) {
  return BENTO_PREVIEW_SLOTS[value] || BENTO_PREVIEW_SLOTS.DEFAULT
}

function getBentoPreviewSlotStyle(slot, value) {
  const slots = getBentoPreviewSlots(value)
  const maxX = Math.max(...slots.map(item => item[0] + item[2]))
  const maxY = Math.max(...slots.map(item => item[1] + item[3]))
  return {
    left: `${slot[0] / maxX * 100}%`,
    top: `${slot[1] / maxY * 100}%`,
    width: `${slot[2] / maxX * 100}%`,
    height: `${slot[3] / maxY * 100}%`
  }
}

function onDisplayStyleChange(style) {
  if (style === 'BENTO' && !form.displayVariant?.startsWith('BENTO_')) {
    form.displayVariant = 'DEFAULT'
  } else if (style === 'FRAME_WALL' && !form.displayVariant?.startsWith('FRAME_WALL_')) {
    form.displayVariant = 'FRAME_WALL_8'
  } else if (style !== 'BENTO' && style !== 'FRAME_WALL') {
    form.displayVariant = 'DEFAULT'
  }
}

function getFrameWallPreviewSlots(value) {
  const option = FRAME_WALL_VARIANT_OPTIONS.find(o => o.value === value) || FRAME_WALL_VARIANT_OPTIONS[3]
  const { cols, rows } = option
  const slots = []
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      slots.push([c, r, 1, 1])
    }
  }
  return slots
}

function getFrameWallPreviewSlotStyle(slot, value) {
  const option = FRAME_WALL_VARIANT_OPTIONS.find(o => o.value === value) || FRAME_WALL_VARIANT_OPTIONS[3]
  const { cols, rows } = option
  return {
    left: `${slot[0] / cols * 100}%`,
    top: `${slot[1] / rows * 100}%`,
    width: `${slot[2] / cols * 100}%`,
    height: `${slot[3] / rows * 100}%`
  }
}

</script>

<style scoped>
.bento-style-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.bento-style-card {
  display: block;
  padding: 10px;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  cursor: pointer;
}

.bento-preview {
  position: relative;
  height: 86px;
  margin-top: 8px;
  overflow: hidden;
  border-radius: 8px;
  background: #f5f7fb;
}

.bento-preview span {
  position: absolute;
  padding: 2px;
  background: transparent;
}

.bento-preview span::after {
  content: '';
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 5px;
  background: linear-gradient(135deg, #91caff, #b7eb8f);
}
</style>

