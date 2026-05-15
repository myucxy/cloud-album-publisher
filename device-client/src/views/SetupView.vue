<template>
  <div class="setup-page">
    <a-card class="setup-card" title="设备初始化">
      <a-alert
        :type="deviceAuth.isActivated ? 'success' : 'info'"
        show-icon
        :message="deviceAuth.isActivated ? '设备已激活，可直接进入播放器。' : '请先保存服务器地址并等待后台设备管理完成绑定。'"
        style="margin-bottom: 20px"
      />

      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="服务器地址">
              <a-input v-model:value="deviceAuth.serverBaseUrl" placeholder="192.168.1.10:8080">
                <template #suffix>
                  <a-button type="link" size="small" :loading="discovering" @click="startDiscovery">自动发现</a-button>
                </template>
              </a-input>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="设备名称">
              <a-input v-model:value="deviceAuth.deviceName" placeholder="播放器名称" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>

      <a-modal
        v-model:open="discoverModalVisible"
        title="选择服务器"
        :footer="null"
        width="400px"
        class="discover-modal"
      >
        <div v-if="discovering" class="discover-loading">
          <a-spin tip="正在扫描局域网...">
            <div class="discover-loading-content" />
          </a-spin>
        </div>
        <template v-else>
          <a-empty v-if="!discoveredServers.length" description="未发现服务器" />
          <a-radio-group v-else v-model:value="selectedServer" style="width: 100%" @change="applyDiscoveredServer">
            <div v-for="server in discoveredServers" :key="server.address" class="discover-server-item">
              <a-radio :value="server.address">
                {{ server.address }}
              </a-radio>
            </div>
          </a-radio-group>
        </template>
      </a-modal>

      <a-descriptions bordered :column="1" size="small">
        <a-descriptions-item label="设备 UID">{{ deviceAuth.deviceUid || '-' }}</a-descriptions-item>
        <a-descriptions-item label="主机名">{{ deviceAuth.hostname || '-' }}</a-descriptions-item>
        <a-descriptions-item label="平台">{{ deviceAuth.platform || '-' }}</a-descriptions-item>
        <a-descriptions-item label="设备类型">{{ deviceAuth.deviceType }}</a-descriptions-item>
        <a-descriptions-item label="设备 ID">{{ deviceAuth.deviceId || '-' }}</a-descriptions-item>
      </a-descriptions>

      <div class="actions">
        <a-space wrap>
          <a-button type="primary" :loading="saving" @click="saveAndRegister">保存并注册</a-button>
          <a-button type="primary" ghost :loading="saving" @click="goPlayer">进入播放器</a-button>
        </a-space>
      </div>
    </a-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useDeviceAuthStore } from '@/stores/deviceAuth'

const router = useRouter()
const deviceAuth = useDeviceAuthStore()
const saving = ref(false)
const discovering = ref(false)
const discoverModalVisible = ref(false)
const discoveredServers = ref([])
const selectedServer = ref('')

onMounted(async () => {
  try {
    await deviceAuth.initializeIdentity()
  } catch (error) {
    message.error(error?.message || '设备初始化失败')
  }
})

async function startDiscovery() {
  if (discovering.value) return
  discovering.value = true
  discoveredServers.value = []
  selectedServer.value = ''
  discoverModalVisible.value = true
  try {
    const servers = await deviceAuth.discoverServers()
    discoveredServers.value = servers
    if (!servers.length) {
      message.info('未发现服务器，请手动输入地址')
    }
  } catch (error) {
    message.error('扫描失败：' + (error?.message || '未知错误'))
  } finally {
    discovering.value = false
  }
}

function applyDiscoveredServer() {
  if (selectedServer.value) {
    deviceAuth.serverBaseUrl = selectedServer.value
    discoverModalVisible.value = false
    message.success('已选择服务器 ' + selectedServer.value)
  }
}

async function saveAndRegister() {
  if (saving.value) {
    return null
  }

  saving.value = true
  try {
    const activated = await deviceAuth.registerAndTryActivate()
    message.success(activated ? '设备已激活' : '设备已注册，等待后台绑定')
    return activated
  } catch (error) {
    message.error(error?.response?.data?.message || error?.message || '保存并注册失败')
    return null
  } finally {
    saving.value = false
  }
}

async function goPlayer() {
  const activated = await saveAndRegister()
  router.push(activated ? '/player' : '/activate')
}
</script>

<style scoped>
.setup-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: linear-gradient(135deg, #020617, #111827 50%, #1e293b);
}

.setup-card {
  width: min(860px, 100%);
  border-radius: 20px;
}

.actions {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

.discover-server-item {
  padding: 8px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.discover-server-item:last-child {
  border-bottom: none;
}

.discover-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 120px;
}

.discover-loading-content {
  width: 100px;
}

:global(.discover-modal .ant-modal-content) {
  background: linear-gradient(135deg, #0f172a, #1e293b);
  border: 1px solid rgba(148, 163, 184, 0.12);
}

:global(.discover-modal .ant-modal-header) {
  background: transparent;
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
}

:global(.discover-modal .ant-modal-title) {
  color: #fff;
}

:global(.discover-modal .ant-modal-close) {
  color: rgba(255, 255, 255, 0.68);
}

:global(.discover-modal .ant-modal-close:hover) {
  color: #fff;
}

:global(.discover-modal .ant-modal-body) {
  color: rgba(255, 255, 255, 0.88);
}

:global(.discover-modal .ant-empty-description) {
  color: rgba(255, 255, 255, 0.45);
}

:global(.discover-modal .ant-radio-wrapper) {
  color: rgba(255, 255, 255, 0.88);
}

:global(.discover-modal .ant-spin-text) {
  color: rgba(255, 255, 255, 0.68);
}
</style>
