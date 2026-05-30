<template>
  <div>
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px">
      <h3 style="margin:0">焦点检测管理</h3>
      <router-link to="/focal-point/llm-configs">
        <a-button>LLM 模型配置</a-button>
      </router-link>
    </div>

    <a-spin :spinning="loading">
      <a-row :gutter="[16,16]">
        <a-col v-for="album in albums" :key="album.id" :xs="24" :sm="12" :md="8" :lg="6">
          <a-card hoverable style="height:100%">
            <template #cover>
              <div style="height:160px; overflow:hidden; display:flex; align-items:center; justify-content:center; background:#fafafa">
                <SecureImage
                  v-if="album.coverUrl"
                  :src="album.coverUrl"
                  :alt="album.title"
                  img-style="width:100%; height:100%; object-fit:cover"
                />
                <picture-outlined v-else style="font-size:48px; color:#d9d9d9" />
              </div>
            </template>

            <a-card-meta :title="album.title" :description="album.description || '无描述'" />

            <div style="margin-top:12px">
              <div style="display:flex; align-items:center; gap:8px; margin-bottom:8px">
                <a-tag v-if="album.focalPointProvider" :color="getProviderColor(album.focalPointProvider)">
                  {{ album.focalPointProvider }}
                </a-tag>
                <a-tag v-else color="default">未配置</a-tag>
                <a-tag :color="album.focalPointEnabled ? 'green' : 'default'">
                  {{ album.focalPointEnabled ? '已启用' : '未启用' }}
                </a-tag>
              </div>

              <div style="color:#8c8c8c; font-size:12px; margin-bottom:12px">
                已检测: {{ album.focalPointDetectedCount || 0 }} / {{ album.focalPointTotalCount || 0 }}
                <a-progress
                  v-if="album.focalPointTotalCount > 0"
                  :percent="Math.round((album.focalPointDetectedCount || 0) / album.focalPointTotalCount * 100)"
                  size="small"
                  :show-info="false"
                  style="margin-top:4px"
                />
              </div>
            </div>

            <template #actions>
              <a-tooltip title="配置模型">
                <setting-outlined @click.stop="openConfig(album)" />
              </a-tooltip>
              <a-tooltip title="执行检测">
                <play-circle-outlined @click.stop="runDetection(album)" :class="{ 'spin-icon': album._processing }" />
              </a-tooltip>
              <a-tooltip title="查看详情">
                <eye-outlined @click.stop="viewDetail(album)" />
              </a-tooltip>
            </template>
          </a-card>
        </a-col>
      </a-row>

      <a-empty v-if="!loading && albums.length === 0" description="暂无相册" />
    </a-spin>

    <a-pagination
      v-if="total > pageSize"
      v-model:current="page"
      :page-size="pageSize"
      :total="total"
      style="margin-top:16px; text-align:center"
      @change="loadAlbums"
    />

    <!-- Config Modal -->
    <a-modal
      v-model:open="configVisible"
      :title="'配置焦点检测 - ' + (configAlbum?.title || '')"
      @ok="saveConfig"
      @cancel="configVisible = false"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="configSaving"
    >
      <a-form layout="vertical">
        <a-form-item label="启用焦点检测">
          <a-switch v-model:checked="configForm.focalPointEnabled" />
        </a-form-item>
        <a-form-item label="检测 Provider">
          <a-select v-model:value="configForm.focalPointProvider" style="width:100%">
            <a-select-option v-for="p in providers" :key="p" :value="p">{{ p }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="configForm.focalPointProvider === 'VISION_LLM'" label="LLM 模型配置">
          <a-select v-model:value="configForm.visionLlmConfigId" style="width:100%" placeholder="选择 LLM 配置" allow-clear>
            <a-select-option v-for="c in llmConfigs" :key="c.id" :value="c.id">
              {{ c.name }} ({{ c.modelName }})
            </a-select-option>
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
import { albumApi } from '@/api/album'
import { focalPointApi } from '@/api/focal-point'
import SecureImage from '@/components/SecureImage.vue'
import {
  PictureOutlined,
  SettingOutlined,
  PlayCircleOutlined,
  EyeOutlined
} from '@ant-design/icons-vue'

const router = useRouter()

const albums = ref([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = 20

const providers = ref([])
const llmConfigs = ref([])

const configVisible = ref(false)
const configAlbum = ref(null)
const configSaving = ref(false)
const configForm = reactive({
  focalPointEnabled: false,
  focalPointProvider: null,
  visionLlmConfigId: null
})

onMounted(() => {
  loadAlbums()
  loadProviders()
  loadLlmConfigs()
})

const loadAlbums = async () => {
  loading.value = true
  try {
    const res = await albumApi.list({ page: page.value, size: pageSize })
    albums.value = (res.data.list || []).map(a => ({ ...a, _processing: false }))
    total.value = res.data.total || 0
  } catch (e) {
    message.error('加载相册列表失败')
  } finally {
    loading.value = false
  }
}

const loadProviders = async () => {
  try {
    const res = await focalPointApi.getProviders()
    providers.value = res.data || []
  } catch (e) {
    console.error('Failed to load providers:', e)
  }
}

const loadLlmConfigs = async () => {
  try {
    const res = await focalPointApi.listLlmConfigs()
    llmConfigs.value = res.data || []
  } catch (e) {
    console.error('Failed to load LLM configs:', e)
  }
}

const openConfig = (album) => {
  configAlbum.value = album
  configForm.focalPointEnabled = album.focalPointEnabled || false
  configForm.focalPointProvider = album.focalPointProvider || (providers.value.length > 0 ? providers.value[0] : null)
  configForm.visionLlmConfigId = null
  configVisible.value = true
}

const saveConfig = async () => {
  configSaving.value = true
  try {
    await focalPointApi.updateAlbumSettings(configAlbum.value.id, {
      focalPointEnabled: configForm.focalPointEnabled,
      focalPointProvider: configForm.focalPointProvider
    })
    message.success('配置已保存')
    configVisible.value = false
    await loadAlbums()
  } catch (e) {
    message.error('保存失败: ' + (e.response?.data?.message || e.message))
  } finally {
    configSaving.value = false
  }
}

const runDetection = async (album) => {
  if (!album.focalPointProvider) {
    message.warning('请先配置检测 Provider')
    return
  }
  album._processing = true
  try {
    const res = await focalPointApi.batchProcess(album.id, {
      providerType: album.focalPointProvider
    })
    const result = res.data
    message.success(`处理完成: 总计 ${result.totalItems}, 成功 ${result.processedItems}, 跳过 ${result.skippedItems}, 失败 ${result.failedItems}`)
    await loadAlbums()
  } catch (e) {
    message.error('检测失败: ' + (e.response?.data?.message || e.message))
  } finally {
    album._processing = false
  }
}

const viewDetail = (album) => {
  router.push(`/focal-point/albums/${album.id}`)
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
.spin-icon {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
