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
          <a-space size="small" direction="vertical">
            <a-space size="small" wrap>
              <a-tag>{{ record.loopPlay ? '循环播放' : '单次播放' }}</a-tag>
              <a-tag>{{ record.shuffle ? '随机播放' : '顺序播放' }}</a-tag>
            </a-space>
            <div style="color:#8c8c8c; font-size:12px">
              转场：{{ overrideLabel(TRANSITION_STYLE_OPTIONS, record.transitionStyle) }}
            </div>
            <div style="color:#8c8c8c; font-size:12px">
              布局：{{ displayOverrideLabel(record) }}
            </div>
            <div style="color:#8c8c8c; font-size:12px">
              时间日期：{{ timeOverrideLabel(record.showTimeAndDate) }}
            </div>
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
              <a-alert
                v-if="selectedAlbum.visibility === 'PRIVATE'"
                type="warning"
                show-icon
                message="私有相册不能下发到设备"
                description="请将相册可见性改为 PUBLIC 或 DEVICE_ONLY 后，再激活分发。"
              />
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
        <a-divider orientation="left">播放与展示覆盖</a-divider>
        <a-form-item label="播放转场">
          <a-select v-model:value="form.transitionStyle">
            <a-select-option :value="INHERIT_VALUE">使用相册设置</a-select-option>
            <a-select-option v-for="option in TRANSITION_STYLE_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="展示布局">
          <a-select v-model:value="form.displayStyle" @change="onDistributionDisplayStyleChange">
            <a-select-option :value="INHERIT_VALUE">使用相册设置</a-select-option>
            <a-select-option v-for="option in DISPLAY_STYLE_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="effectiveDistributionDisplayStyle === 'BENTO'" label="Bento 样式">
          <a-radio-group v-model:value="form.displayVariant" style="width:100%">
            <div class="distribution-layout-style-grid">
              <label class="distribution-layout-style-card">
                <a-radio :value="INHERIT_VALUE">使用相册设置</a-radio>
                <div class="distribution-layout-style-desc">沿用相册当前配置的 Bento 样式</div>
              </label>
              <label v-for="option in BENTO_VARIANT_OPTIONS" :key="option.value" class="distribution-layout-style-card">
                <a-radio :value="option.value">{{ option.label }}</a-radio>
                <div class="distribution-layout-preview">
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
        <a-form-item v-else-if="effectiveDistributionDisplayStyle === 'FRAME_WALL'" label="相框数量">
          <a-radio-group v-model:value="form.displayVariant" style="width:100%">
            <div class="distribution-layout-style-grid">
              <label class="distribution-layout-style-card">
                <a-radio :value="INHERIT_VALUE">使用相册设置</a-radio>
                <div class="distribution-layout-style-desc">沿用相册当前配置的相框数量</div>
              </label>
              <label v-for="option in FRAME_WALL_VARIANT_OPTIONS" :key="option.value" class="distribution-layout-style-card">
                <a-radio :value="option.value">{{ option.label }}</a-radio>
                <div class="distribution-layout-preview">
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
        <a-form-item v-else label="布局样式">
          <a-alert type="info" show-icon message="当前布局没有额外样式可配置" description="Bento 拼贴和相框墙支持选择具体样式；单图播放、轮播墙和日历模式只需要选择布局方式。" />
        </a-form-item>
        <a-form-item label="时间日期">
          <a-radio-group v-model:value="form.showTimeAndDate">
            <a-radio :value="INHERIT_VALUE">使用相册设置</a-radio>
            <a-radio :value="true">显示</a-radio>
            <a-radio :value="false">隐藏</a-radio>
          </a-radio-group>
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

    <a-modal v-model:open="albumPickerOpen" title="选择相册" :footer="null" :width="1320"
             :body-style="{ padding: '16px 20px' }">
      <div class="distribution-album-picker-layout distribution-album-picker-layout-fixed">
        <div class="distribution-album-picker-sidebar distribution-album-picker-pane-scroll">
          <a-card size="small" title="筛选 / 视图">
            <template #extra>
              <a-button type="link" size="small" @click="reloadAlbumPicker" :loading="albumPickerLoading">刷新</a-button>
            </template>

            <div style="display:flex; flex-direction:column; gap:8px">
              <div
                v-for="option in ALBUM_PICKER_VISIBILITY_OPTIONS"
                :key="option.key"
                :style="distributionAlbumPickerFilterStyle(option.value)"
                @click="selectAlbumPickerVisibility(option.value)"
              >
                <div style="font-weight:500">{{ option.label }}</div>
                <div style="color:#8c8c8c; font-size:12px; margin-top:4px">{{ option.description }}</div>
              </div>
            </div>
          </a-card>
        </div>

        <div class="distribution-album-picker-content">
          <div class="distribution-album-picker-main">
            <div class="distribution-album-picker-main-sticky">
              <div style="display:flex; justify-content:space-between; gap:12px; align-items:center; margin-bottom:16px; flex-wrap:wrap">
                <a-space wrap>
                  <a-tag color="blue">{{ albumPickerVisibilityLabel }}</a-tag>
                  <span style="color:#8c8c8c; font-size:12px">点击卡片预览，右侧确认选择</span>
                </a-space>
              </div>
            </div>

            <div class="distribution-album-picker-main-list distribution-album-picker-pane-scroll">
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
            </div>

            <div class="distribution-album-picker-main-sticky distribution-album-picker-main-footer">
              <a-pagination
                :current="albumPickerPage"
                :total="albumPickerTotal"
                :page-size="albumPickerPageSize"
                @change="onAlbumPickerPageChange"
                show-less-items
              />
            </div>
          </div>

          <div class="distribution-album-picker-selection distribution-album-picker-pane-scroll">
            <div style="font-weight:500; margin-bottom:12px">相册预览</div>
            <template v-if="albumPickerDraftAlbum">
              <div style="margin-bottom:16px; text-align:center; background:#fff; border-radius:8px; padding:12px">
                <SecureImage
                  v-if="albumPickerDraftAlbum.coverUrl"
                  :src="albumPickerDraftAlbum.coverUrl"
                  alt="selected album cover"
                  img-style="max-width:100%; max-height:220px; object-fit:contain"
                />
                <picture-outlined v-else style="font-size:42px; color:#bfbfbf" />
              </div>
              <div style="font-weight:500; word-break:break-all">{{ albumPickerDraftAlbum.title }}</div>
              <div style="color:#8c8c8c; font-size:12px; margin-top:8px">{{ albumPickerDraftAlbum.description || '暂无描述' }}</div>
              <a-space size="small" wrap style="margin-top:12px">
                <a-tag>{{ albumPickerDraftAlbum.visibility }}</a-tag>
                <a-tag :color="albumPickerDraftAlbum.status === 'PUBLISHED' ? 'green' : 'default'">{{ albumPickerDraftAlbum.status }}</a-tag>
              </a-space>
              <a-alert
                v-if="albumPickerDraftAlbum.visibility === 'PRIVATE'"
                style="margin-top:12px"
                type="warning"
                show-icon
                message="私有相册不能下发到设备"
                description="请将相册可见性改为 PUBLIC 或 DEVICE_ONLY 后，再激活分发。"
              />
              <div style="margin-top:16px; display:flex; justify-content:flex-end">
                <a-button type="primary" @click="confirmChooseAlbum">确认选择</a-button>
              </div>
            </template>
            <template v-else-if="selectedAlbum">
              <div style="margin-bottom:16px; text-align:center; background:#fff; border-radius:8px; padding:12px">
                <SecureImage
                  v-if="selectedAlbum.coverUrl"
                  :src="selectedAlbum.coverUrl"
                  alt="current selected album cover"
                  img-style="max-width:100%; max-height:220px; object-fit:contain"
                />
                <picture-outlined v-else style="font-size:42px; color:#bfbfbf" />
              </div>
              <div style="font-weight:500; word-break:break-all">{{ selectedAlbum.title }}</div>
              <div style="color:#8c8c8c; font-size:12px; margin-top:8px">当前已选相册</div>
            </template>
            <a-empty v-else description="请选择相册" />
          </div>
        </div>
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
import { DEFAULT_PAGE_SIZE } from '@/constants/pagination'
import {
  BENTO_VARIANT_OPTIONS,
  DISPLAY_STYLE_OPTIONS,
  FRAME_WALL_VARIANT_OPTIONS,
  TRANSITION_STYLE_OPTIONS,
  optionLabel
} from '@/constants/albumDisplayOptions'

const INHERIT_VALUE = '__INHERIT__'
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
    [3, 1, 1, 2], [0, 2.6, 2, 0.8], [2, 2, 1, 1], [0, 3.4, 3, 1]
  ],
  BENTO_4: [
    [0, 0, 2, 1.4], [2, 0, 2, 1], [4, 0, 1, 2], [0, 1.4, 1, 1.6], [1, 1.4, 2, 1],
    [3, 1, 1, 2], [4, 2, 1, 1], [1, 2.4, 2, 1]
  ],
  BENTO_5: [
    [0, 0, 1, 1], [1, 0, 2, 1.4], [3, 0, 1, 1], [4, 0, 1, 1.8], [0, 1, 1, 1.4],
    [1, 1.4, 1, 1], [2, 1.4, 2, 1.2], [0, 2.4, 2, 1], [2, 2.6, 1, 0.8], [3, 2.6, 2, 0.8]
  ],
  BENTO_6: [
    [0, 0, 2, 2], [2, 0, 1, 1], [3, 0, 2, 1], [5, 0, 1, 1.5], [2, 1, 1.5, 1.2],
    [3.5, 1, 1.5, 1.2], [0, 2, 1, 1], [1, 2, 2, 1], [3, 2.2, 3, 0.8]
  ],
  BENTO_7: [
    [0, 0, 2, 1], [2, 0, 1, 1.3], [3, 0, 1, 1.3], [4, 0, 1, 1.3], [5, 0, 2, 1],
    [0, 1, 1, 1], [1, 1, 1, 1], [0, 2, 1, 1], [1, 2, 1, 1], [0, 3, 2, 1],
    [2, 1.3, 3, 1.4], [2, 2.7, 1, 1.3], [3, 2.7, 1, 1.3], [4, 2.7, 1, 1.3],
    [5, 1, 1, 1], [6, 1, 1, 0.7], [5, 2, 1, 0.7], [6, 1.7, 1, 1], [5, 2.7, 2, 1.3]
  ]
}
const distributions = ref([])
const albumMetaMap = ref({})
const total = ref(0)
const page = ref(1)
const pageSize = DEFAULT_PAGE_SIZE
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
const albumPickerPageSize = DEFAULT_PAGE_SIZE
const albumPickerTotal = ref(0)
const albumPickerVisibility = ref(undefined)
const albumOptions = ref([])
const albumPickerDraftAlbumId = ref(null)
const devices = ref([])
const groups = ref([])
const form = reactive({
  albumId: null,
  name: '',
  loopPlay: true,
  shuffle: false,
  itemDuration: 10,
  transitionStyle: INHERIT_VALUE,
  displayStyle: INHERIT_VALUE,
  displayVariant: INHERIT_VALUE,
  showTimeAndDate: INHERIT_VALUE,
  deviceIds: [],
  groupIds: []
})

const ALBUM_PICKER_VISIBILITY_OPTIONS = [
  { key: 'all', value: undefined, label: '全部相册', description: '显示全部可选相册' },
  { key: 'public', value: 'PUBLIC', label: '公开', description: '优先推荐给公开展示场景' },
  { key: 'device-only', value: 'DEVICE_ONLY', label: '设备专属', description: '适合仅设备端可见内容' },
  { key: 'private', value: 'PRIVATE', label: '私有', description: '可选择但激活时不可下发' }
]

const columns = [
  { title: '名称', dataIndex: 'name', width: 150, ellipsis: true },
  { title: '相册', key: 'album', width: 240 },
  { title: '播放配置', key: 'playOptions', width: 240 },
  { title: '单项时长', dataIndex: 'itemDuration', width: 100 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 150 }
]

const deviceSelectOptions = computed(() => devices.value.map(item => ({ value: item.id, label: item.name || `设备 #${item.id}` })))
const groupSelectOptions = computed(() => groups.value.map(item => ({ value: item.id, label: item.name || `设备组 #${item.id}` })))
const deviceNameMap = computed(() => Object.fromEntries(devices.value.map(item => [item.id, item.name || `设备 #${item.id}`])))
const groupNameMap = computed(() => Object.fromEntries(groups.value.map(item => [item.id, item.name || `设备组 #${item.id}`])))
const albumPickerDraftAlbum = computed(() => {
  if (!albumPickerDraftAlbumId.value) {
    return null
  }
  return albumOptions.value.find(item => item.id === albumPickerDraftAlbumId.value)
    || albumMetaMap.value[albumPickerDraftAlbumId.value]
    || null
})
const albumPickerVisibilityLabel = computed(() => {
  const current = ALBUM_PICKER_VISIBILITY_OPTIONS.find(option => option.value === albumPickerVisibility.value)
  return current?.label || '全部相册'
})
const effectiveDistributionDisplayStyle = computed(() => {
  if (form.displayStyle !== INHERIT_VALUE) {
    return form.displayStyle
  }
  return selectedAlbum.value?.displayStyle || 'SINGLE'
})

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
    transitionStyle: INHERIT_VALUE,
    displayStyle: INHERIT_VALUE,
    displayVariant: INHERIT_VALUE,
    showTimeAndDate: INHERIT_VALUE,
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
    transitionStyle: toFormOverride(record.transitionStyle),
    displayStyle: toFormOverride(record.displayStyle),
    displayVariant: toFormOverride(record.displayVariant),
    showTimeAndDate: record.showTimeAndDate === null || record.showTimeAndDate === undefined ? INHERIT_VALUE : record.showTimeAndDate,
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

function selectAlbumPickerVisibility(visibility) {
  albumPickerVisibility.value = visibility
  reloadAlbumPicker()
}

function distributionAlbumPickerFilterStyle(visibility) {
  const selected = (albumPickerVisibility.value || undefined) === (visibility || undefined)
  return selected
    ? 'border:1px solid #1677ff; border-radius:8px; padding:10px 12px; background:#e6f4ff; cursor:pointer'
    : 'border:1px solid #f0f0f0; border-radius:8px; padding:10px 12px; background:#fff; cursor:pointer'
}

function openAlbumPicker() {
  if (editingId.value) return
  albumPickerDraftAlbumId.value = form.albumId || null
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
  albumPickerDraftAlbumId.value = albumItem.id
}

function confirmChooseAlbum() {
  if (!albumPickerDraftAlbum.value) {
    message.warning('请选择相册')
    return
  }
  form.albumId = albumPickerDraftAlbum.value.id
  selectedAlbum.value = albumPickerDraftAlbum.value
  onDistributionDisplayStyleChange(form.displayStyle)
  albumPickerOpen.value = false
}

function albumCardStyle(albumItem) {
  const selected = albumPickerDraftAlbumId.value === albumItem.id
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
      transitionStyle: fromFormOverride(form.transitionStyle),
      displayStyle: fromFormOverride(form.displayStyle),
      displayVariant: fromFormOverride(form.displayVariant),
      showTimeAndDate: form.showTimeAndDate === INHERIT_VALUE ? null : form.showTimeAndDate,
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

function toFormOverride(value) {
  return value === null || value === undefined || value === '' ? INHERIT_VALUE : value
}

function fromFormOverride(value) {
  return value === INHERIT_VALUE ? null : value
}

function overrideLabel(options, value) {
  return value ? optionLabel(options, value, value) : '继承相册'
}

function displayOverrideLabel(record) {
  if (!record.displayStyle) {
    return '继承相册'
  }
  const styleLabel = optionLabel(DISPLAY_STYLE_OPTIONS, record.displayStyle, record.displayStyle)
  if (!record.displayVariant) {
    return styleLabel
  }
  const variantOptions = record.displayStyle === 'FRAME_WALL' ? FRAME_WALL_VARIANT_OPTIONS : BENTO_VARIANT_OPTIONS
  return `${styleLabel} / ${optionLabel(variantOptions, record.displayVariant, record.displayVariant)}`
}

function timeOverrideLabel(value) {
  if (value === null || value === undefined) {
    return '继承相册'
  }
  return value ? '显示' : '隐藏'
}

function onDistributionDisplayStyleChange(style) {
  const effectiveStyle = style === INHERIT_VALUE ? (selectedAlbum.value?.displayStyle || 'SINGLE') : style
  if (effectiveStyle === 'BENTO') {
    if (form.displayVariant !== INHERIT_VALUE && !String(form.displayVariant || '').startsWith('BENTO_') && form.displayVariant !== 'DEFAULT') {
      form.displayVariant = INHERIT_VALUE
    }
  } else if (effectiveStyle === 'FRAME_WALL') {
    if (form.displayVariant !== INHERIT_VALUE && !String(form.displayVariant || '').startsWith('FRAME_WALL_')) {
      form.displayVariant = INHERIT_VALUE
    }
  } else {
    form.displayVariant = INHERIT_VALUE
  }
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

function getFrameWallPreviewSlots(value) {
  const option = FRAME_WALL_VARIANT_OPTIONS.find(o => o.value === value) || FRAME_WALL_VARIANT_OPTIONS[3]
  const { cols, rows } = option
  const slots = []
  for (let r = 0; r < rows; r += 1) {
    for (let c = 0; c < cols; c += 1) {
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
.distribution-album-picker-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  min-height: 420px;
}

.distribution-album-picker-layout-fixed {
  height: calc(100vh - 260px);
  min-height: 520px;
  max-height: calc(100vh - 260px);
  align-items: stretch;
}

.distribution-album-picker-sidebar {
  width: 240px;
  flex-shrink: 0;
}

.distribution-album-picker-content {
  display: flex;
  gap: 16px;
  align-items: stretch;
  flex: 1;
  min-width: 0;
}

.distribution-album-picker-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.distribution-album-picker-selection {
  width: 300px;
  flex-shrink: 0;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 16px;
  background: #fafafa;
  display: flex;
  flex-direction: column;
}

.distribution-album-picker-pane-scroll {
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.distribution-album-picker-main-sticky {
  flex-shrink: 0;
}

.distribution-album-picker-main-footer {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
  text-align: right;
}

.distribution-album-picker-main-list {
  flex: 1;
  min-height: 0;
}

.distribution-album-picker-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.distribution-layout-style-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.distribution-layout-style-card {
  display: block;
  padding: 10px;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  cursor: pointer;
}

.distribution-layout-style-desc {
  color: #8c8c8c;
  font-size: 12px;
  margin-top: 8px;
}

.distribution-layout-preview {
  position: relative;
  height: 86px;
  margin-top: 8px;
  overflow: hidden;
  border-radius: 8px;
  background: #f5f7fb;
}

.distribution-layout-preview span {
  position: absolute;
  padding: 2px;
  background: transparent;
}

.distribution-layout-preview span::after {
  content: '';
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 5px;
  background: linear-gradient(135deg, #91caff, #b7eb8f);
}

@media (max-width: 1200px) {
  .distribution-album-picker-layout-fixed {
    height: auto;
    min-height: auto;
    max-height: none;
  }

  .distribution-album-picker-content {
    flex-direction: column;
  }

  .distribution-album-picker-selection {
    width: 100%;
  }

  .distribution-album-picker-grid {
    grid-template-columns: 1fr;
  }

  .distribution-album-picker-pane-scroll {
    overflow: visible;
  }
}
</style>
