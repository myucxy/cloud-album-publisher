<template>
  <div>
    <div style="display:flex; justify-content:space-between; margin-bottom:16px">
      <a-space>
        <a-select v-model:value="filterStatus" style="width:140px" @change="load" allow-clear placeholder="状态筛选">
          <a-select-option value="DRAFT">草稿</a-select-option>
          <a-select-option value="ACTIVE">活跃</a-select-option>
          <a-select-option value="DISABLED">已禁用</a-select-option>
        </a-select>
      </a-space>
      <a-button type="primary" @click="openCreate"><plus-outlined /> 新建分发</a-button>
    </div>

    <a-table :data-source="distributions" :columns="columns" row-key="id" :loading="loading"
             :pagination="{ total, current: page, pageSize, onChange: p => { page = p; load() } }"
             :expandable="{ expandedRowRender }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'album'">
          <div style="display:flex; align-items:center; gap:12px">
            <div style="width:52px; height:52px; border-radius:8px; overflow:hidden; background:#fafafa; display:flex; align-items:center; justify-content:center; flex-shrink:0">
              <SecureImage
                v-if="getAlbumMeta(record.albumId)?.coverUrl"
                :src="getAlbumMeta(record.albumId).coverUrl"
                alt="album cover"
                img-style="width:100%; height:100%; object-fit:cover"
              />
              <picture-outlined v-else style="font-size:24px; color:#bfbfbf" />
            </div>
            <div style="min-width:0">
              <div>{{ getAlbumMeta(record.albumId)?.title || `相册 #${record.albumId}` }}</div>
              <div style="color:#8c8c8c; font-size:12px">{{ getAlbumMeta(record.albumId)?.visibility || '未加载' }}</div>
            </div>
          </div>
        </template>
        <template v-if="column.key === 'playOptions'">
          <a-space size="small">
            <a-tag>{{ record.loopPlay ? '循环播放' : '单次播放' }}</a-tag>
            <a-tag>{{ record.shuffle ? '随机播放' : '顺序播放' }}</a-tag>
          </a-space>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button v-if="record.status !== 'ACTIVE'" type="link" size="small" @click="activate(record.id)">
              激活
            </a-button>
            <a-button v-if="record.status === 'ACTIVE'" type="link" size="small" danger @click="disable(record.id)">
              停用
            </a-button>
            <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm title="确认删除该分发配置？" @confirm="deleteDistribution(record.id)">
              <a-button type="link" danger size="small">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="modalOpen" :title="editingId ? '编辑分发' : '新建分发'"
             @ok="submitForm" :confirm-loading="saving" :width="560"
             ok-text="保存" cancel-text="取消"
             :body-style="{ maxHeight: 'calc(100vh - 260px)', overflowY: 'auto' }">
      <a-form :model="form" layout="vertical" ref="formRef">
        <a-form-item label="相册" name="albumId">
          <div style="display:flex; flex-direction:column; gap:12px">
            <div v-if="selectedAlbum" style="display:flex; flex-direction:column; gap:12px; border:1px solid #f0f0f0; border-radius:8px; padding:12px; background:#fafafa">
              <div style="display:flex; gap:12px; align-items:flex-start">
                <div style="width:72px; height:72px; border-radius:8px; overflow:hidden; background:#fff; display:flex; align-items:center; justify-content:center; flex-shrink:0">
                  <SecureImage
                    v-if="selectedAlbum.coverUrl"
                    :src="selectedAlbum.coverUrl"
                    alt="selected album cover"
                    img-style="width:100%; height:100%; object-fit:cover"
                  />
                  <picture-outlined v-else style="font-size:28px; color:#bfbfbf" />
                </div>
                <div style="flex:1; min-width:0">
                  <div style="font-weight:500">{{ selectedAlbum.title }}</div>
                  <div style="color:#8c8c8c; font-size:12px; margin-top:6px">{{ selectedAlbum.description || '暂无描述' }}</div>
                </div>
              </div>
              <a-space size="small" wrap>
                <a-tag>{{ selectedAlbum.visibility }}</a-tag>
                <a-tag :color="selectedAlbum.status === 'PUBLISHED' ? 'green' : 'default'">{{ selectedAlbum.status }}</a-tag>
              </a-space>
            </div>
            <a-empty v-else description="请选择相册" />
            <a-button @click="openAlbumPicker" :disabled="!!editingId">{{ editingId ? '编辑时不支持更换相册' : '选择相册' }}</a-button>
          </div>
        </a-form-item>
        <a-form-item label="分发名称" name="name" :rules="[{ required: true, message: '请输入分发名称' }]">
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item label="每项展示时长（秒)">
          <a-input-number v-model:value="form.itemDuration" :min="1" style="width:100%" />
        </a-form-item>
        <a-form-item>
          <a-checkbox v-model:checked="form.loopPlay">循环播放</a-checkbox>
        </a-form-item>
        <a-form-item>
          <a-checkbox v-model:checked="form.shuffle">随机播放</a-checkbox>
        </a-form-item>
        <a-form-item label="目标设备">
          <a-select
            v-model:value="form.deviceIds"
            mode="multiple"
            allow-clear
            show-search
            option-filter-prop="label"
            :options="deviceSelectOptions"
            placeholder="选择设备"
          />
        </a-form-item>
        <a-form-item label="目标设备组">
          <a-select
            v-model:value="form.groupIds"
            mode="multiple"
            allow-clear
            show-search
            option-filter-prop="label"
            :options="groupSelectOptions"
            placeholder="选择设备组"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="albumPickerOpen" title="选择相册" :footer="null" :width="640"
             :body-style="{ maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' }">
      <div style="display:flex; flex-direction:column; align-items:flex-start; margin-bottom:16px; gap:12px">
        <a-space wrap>
          <a-select v-model:value="albumPickerVisibility" style="width:140px" allow-clear placeholder="可见性" @change="reloadAlbumPicker">
            <a-select-option value="PUBLIC">公开</a-select-option>
            <a-select-option value="PRIVATE">私有</a-select-option>
            <a-select-option value="DEVICE_ONLY">设备专属</a-select-option>
          </a-select>
          <a-button @click="loadAlbumOptions" :loading="albumPickerLoading">刷新</a-button>
        </a-space>
        <span style="color:#8c8c8c; font-size:12px">点击卡片即可选中相册</span>
      </div>

      <a-spin :spinning="albumPickerLoading">
        <div v-if="albumOptions.length" class="distribution-album-picker-grid">
          <a-card v-for="albumItem in albumOptions" :key="albumItem.id" hoverable :body-style="{ padding: '12px' }" :style="albumCardStyle(albumItem)" @click="chooseAlbum(albumItem)">
            <template #cover>
              <SecureImage
                v-if="albumItem.coverUrl"
                :src="albumItem.coverUrl"
                alt="album cover"
                img-style="width:100%; height:160px; object-fit:cover"
              />
              <div v-else style="height:160px; background:#f0f2f5; display:flex; align-items:center; justify-content:center; color:#bbb">
                <picture-outlined style="font-size:40px" />
              </div>
            </template>
            <a-card-meta :title="albumItem.title" :description="albumItem.description || '暂无描述'" />
            <div style="margin-top:12px">
              <a-space size="small" wrap>
                <a-tag>{{ albumItem.visibility }}</a-tag>
                <a-tag :color="albumItem.status === 'PUBLISHED' ? 'green' : 'default'">{{ albumItem.status }}</a-tag>
              </a-space>
            </div>
          </a-card>
        </div>
        <a-empty v-else description="暂无相册" />
      </a-spin>

      <div style="margin-top:16px; text-align:right">
        <a-pagination
          :current="albumPickerPage"
          :total="albumPickerTotal"
          :page-size="albumPickerPageSize"
          @change="onAlbumPickerPageChange"
          show-less-items
        />
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, h } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, PictureOutlined } from '@ant-design/icons-vue'
import { distributionApi } from '@/api/distribution'
import { albumApi } from '@/api/album'
import { deviceApi } from '@/api/device'
import SecureImage from '@/components/SecureImage.vue'

const distributions = ref([])
const albumMetaMap = ref({})
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const filterStatus = ref(undefined)
const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref(null)
const formRef = ref()
const selectedAlbum = ref(null)
const albumPickerOpen = ref(false)
const albumPickerLoading = ref(false)
const albumPickerPage = ref(1)
const albumPickerPageSize = 12
const albumPickerTotal = ref(0)
const albumPickerVisibility = ref(undefined)
const albumOptions = ref([])
const devices = ref([])
const groups = ref([])
const form = reactive({
  albumId: null,
  name: '',
  loopPlay: true,
  shuffle: false,
  itemDuration: 10,
  deviceIds: [],
  groupIds: []
})

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '相册', key: 'album', width: 260 },
  { title: '播放配置', key: 'playOptions', width: 180 },
  { title: '单项时长', dataIndex: 'itemDuration', width: 100 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 180 }
]

const deviceSelectOptions = computed(() => devices.value.map(item => ({ value: item.id, label: item.name || `设备 #${item.id}` })))
const groupSelectOptions = computed(() => groups.value.map(item => ({ value: item.id, label: item.name || `设备组 #${item.id}` })))
const deviceNameMap = computed(() => Object.fromEntries(devices.value.map(item => [item.id, item.name || `设备 #${item.id}`])))
const groupNameMap = computed(() => Object.fromEntries(groups.value.map(item => [item.id, item.name || `设备组 #${item.id}`])))

const expandedRowRender = (record) => {
  const deviceText = (record.deviceIds || []).length
    ? record.deviceIds.map(id => deviceNameMap.value[id] || `设备 #${id}`).join('、')
    : '未绑定设备'
  const groupText = (record.groupIds || []).length
    ? record.groupIds.map(id => groupNameMap.value[id] || `设备组 #${id}`).join('、')
    : '未绑定设备组'
  return h('div', { style: 'color:#8c8c8c; padding:4px 0' }, [
    h('div', `设备：${deviceText}`),
    h('div', { style: 'margin-top:4px' }, `设备组：${groupText}`)
  ])
}

onMounted(async () => {
  await Promise.all([load(), loadTargetOptions()])
})

async function load() {
  loading.value = true
  try {
    const res = await distributionApi.list({ page: page.value, size: pageSize, status: filterStatus.value || undefined })
    const list = res.data.list || []
    distributions.value = list
    total.value = res.data.total
    await hydrateAlbumMeta(list.map(item => item.albumId))
  } finally {
    loading.value = false
  }
}

async function loadTargetOptions() {
  const [deviceRes, groupRes] = await Promise.all([deviceApi.list(), deviceApi.listGroups()])
  devices.value = deviceRes.data || []
  groups.value = groupRes.data || []
}

async function hydrateAlbumMeta(albumIds) {
  const ids = [...new Set((albumIds || []).filter(Boolean))].filter(id => !albumMetaMap.value[id])
  if (!ids.length) return

  const responses = await Promise.all(
    ids.map(async id => {
      try {
        const res = await albumApi.get(id)
        return res.data
      } catch {
        return null
      }
    })
  )

  const nextMap = { ...albumMetaMap.value }
  responses.forEach(item => {
    if (item?.id) {
      nextMap[item.id] = item
    }
  })
  albumMetaMap.value = nextMap
}

function getAlbumMeta(albumId) {
  return albumMetaMap.value[albumId]
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    albumId: null,
    name: '',
    loopPlay: true,
    shuffle: false,
    itemDuration: 10,
    deviceIds: [],
    groupIds: []
  })
  selectedAlbum.value = null
  modalOpen.value = true
}

async function openEdit(record) {
  editingId.value = record.id
  Object.assign(form, {
    albumId: record.albumId,
    name: record.name,
    loopPlay: record.loopPlay,
    shuffle: record.shuffle,
    itemDuration: record.itemDuration,
    deviceIds: [...(record.deviceIds || [])],
    groupIds: [...(record.groupIds || [])]
  })
  selectedAlbum.value = getAlbumMeta(record.albumId) || null
  if (!selectedAlbum.value && record.albumId) {
    await hydrateAlbumMeta([record.albumId])
    selectedAlbum.value = getAlbumMeta(record.albumId) || null
  }
  modalOpen.value = true
}

function openAlbumPicker() {
  if (editingId.value) return
  albumPickerOpen.value = true
  albumPickerPage.value = 1
  loadAlbumOptions()
}

function reloadAlbumPicker() {
  albumPickerPage.value = 1
  loadAlbumOptions()
}

async function loadAlbumOptions() {
  albumPickerLoading.value = true
  try {
    const res = await albumApi.list({
      page: albumPickerPage.value,
      size: albumPickerPageSize,
      visibility: albumPickerVisibility.value || undefined
    })
    albumOptions.value = res.data.list || []
    albumPickerTotal.value = res.data.total
    const nextMap = { ...albumMetaMap.value }
    albumOptions.value.forEach(item => {
      nextMap[item.id] = item
    })
    albumMetaMap.value = nextMap
  } finally {
    albumPickerLoading.value = false
  }
}

function onAlbumPickerPageChange(nextPage) {
  albumPickerPage.value = nextPage
  loadAlbumOptions()
}

function chooseAlbum(albumItem) {
  form.albumId = albumItem.id
  selectedAlbum.value = albumItem
  albumPickerOpen.value = false
}

function albumCardStyle(albumItem) {
  const selected = form.albumId === albumItem.id
  return selected
    ? 'border:1px solid #1677ff; box-shadow:0 0 0 2px rgba(22,119,255,0.12)'
    : 'border:1px solid #f0f0f0'
}

async function submitForm() {
  await formRef.value.validate()
  if (!form.albumId) {
    message.warning('请选择相册')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name,
      loopPlay: form.loopPlay,
      shuffle: form.shuffle,
      itemDuration: form.itemDuration,
      deviceIds: form.deviceIds,
      groupIds: form.groupIds
    }
    if (editingId.value) {
      await distributionApi.update(editingId.value, payload)
      message.success('已更新')
    } else {
      await distributionApi.create({ albumId: form.albumId, ...payload })
      message.success('已创建')
    }
    modalOpen.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function activate(id) {
  await distributionApi.activate(id)
  message.success('已激活')
  await load()
}

async function disable(id) {
  await distributionApi.disable(id)
  message.success('已停用')
  await load()
}

async function deleteDistribution(id) {
  await distributionApi.remove(id)
  message.success('已删除')
  await load()
}

function statusColor(status) {
  const map = { DRAFT: 'default', ACTIVE: 'green', DISABLED: 'red' }
  return map[status] || 'default'
}

function statusLabel(status) {
  const map = { DRAFT: '草稿', ACTIVE: '活跃', DISABLED: '已禁用' }
  return map[status] || status
}
</script>

<style scoped>
.distribution-album-picker-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

@media (max-width: 900px) {
  .distribution-album-picker-grid {
    grid-template-columns: 1fr;
  }
}
</style>
