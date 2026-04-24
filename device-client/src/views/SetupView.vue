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
              <a-input v-model:value="deviceAuth.serverBaseUrl" placeholder="192.168.1.10:8080" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="设备名称">
              <a-input v-model:value="deviceAuth.deviceName" placeholder="播放器名称" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>

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
          <a-button v-if="deviceAuth.isActivated" danger @click="resetActivation">清除设备令牌</a-button>
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

onMounted(async () => {
  try {
    await deviceAuth.initializeIdentity()
  } catch (error) {
    message.error(error?.message || '设备初始化失败')
  }
})

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

function resetActivation() {
  deviceAuth.clearDeviceSession()
  message.success('设备令牌已清除')
  router.push('/setup')
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
</style>
