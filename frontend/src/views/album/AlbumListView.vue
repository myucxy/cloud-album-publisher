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
            转场样式按相册统一配置；客户端仅对图片应用该效果，视频会自动跳过转场效果并正常播放。
          </div>
        </a-form-item>
        <a-form-item label="展示布局" name="displayStyle">
          <a-select v-model:value="form.displayStyle">
            <a-select-option v-for="option in DISPLAY_STYLE_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </a-select-option>
          </a-select>
          <div style="color:#8c8c8c; font-size:12px; margin-top:6px">
            单图使用播放转场；Bento、相框墙、轮播会同时展示多张图片，视频内容仍按原视频播放器播放。
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

const router = useRouter()
const albums = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 12
const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({ title: '', description: '', visibility: 'PUBLIC', transitionStyle: 'NONE', displayStyle: 'SINGLE', status: 'PUBLISHED' })

onMounted(load)

async function load() {
  const res = await albumApi.list({ page: page.value, size: pageSize })
  albums.value = res.data.list
  total.value = res.data.total
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { title: '', description: '', visibility: 'PUBLIC', transitionStyle: 'NONE', displayStyle: 'SINGLE', status: 'PUBLISHED' })
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

</script>
