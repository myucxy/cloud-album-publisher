<template>
  <div>
    <div style="display:flex; justify-content:space-between; margin-bottom:16px">
      <a-space>
        <a-select v-model:value="filterVisibility" style="width:120px" @change="load" allow-clear placeholder="可见性">
          <a-select-option value="PUBLIC">公开</a-select-option>
          <a-select-option value="PRIVATE">私有</a-select-option>
          <a-select-option value="DEVICE_ONLY">设备专属</a-select-option>
        </a-select>
      </a-space>
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
            <a-tag :color="statusColor(album.status)">{{ album.status }}</a-tag>
            <a-tag>{{ album.visibility }}</a-tag>
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
        <a-form-item label="可见性" name="visibility">
          <a-select v-model:value="form.visibility">
            <a-select-option value="PRIVATE">私有</a-select-option>
            <a-select-option value="PUBLIC">公开</a-select-option>
            <a-select-option value="DEVICE_ONLY">设备专属</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="editingId" label="状态" name="status">
          <a-select v-model:value="form.status">
            <a-select-option value="DRAFT">草稿</a-select-option>
            <a-select-option value="PUBLISHED">已发布</a-select-option>
          </a-select>
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

const router = useRouter()
const albums = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 12
const filterVisibility = ref(undefined)
const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({ title: '', description: '', visibility: 'PRIVATE', status: 'DRAFT' })

onMounted(load)

async function load() {
  const res = await albumApi.list({ page: page.value, size: pageSize, visibility: filterVisibility.value || undefined })
  albums.value = res.data.list
  total.value = res.data.total
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { title: '', description: '', visibility: 'PRIVATE', status: 'DRAFT' })
  modalOpen.value = true
}

function openEdit(album) {
  editingId.value = album.id
  Object.assign(form, { title: album.title, description: album.description, visibility: album.visibility, status: album.status })
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

function statusColor(status) {
  return status === 'PUBLISHED' ? 'green' : 'default'
}
</script>
