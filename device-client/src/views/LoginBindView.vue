<template>
  <div class="activate-page">
    <div class="activate-layout">
      <a-card class="summary-card" title="等待管理员绑定设备">
        <a-descriptions :column="1" size="small">
          <a-descriptions-item label="设备 UID">{{ deviceAuth.deviceUid || '-' }}</a-descriptions-item>
          <a-descriptions-item label="设备名称">{{ deviceAuth.deviceName || '-' }}</a-descriptions-item>
          <a-descriptions-item label="设备类型">{{ deviceAuth.deviceType }}</a-descriptions-item>
          <a-descriptions-item label="服务器地址">{{ deviceAuth.serverBaseUrl }}</a-descriptions-item>
        </a-descriptions>
        <a-alert style="margin-top: 16px" type="info" show-icon message="当前设备已注册到服务器，请在后台设备管理中完成绑定。绑定完成后会自动进入播放器。" />
      </a-card>

      <a-card class="form-card" title="绑定状态检查">
        <a-space direction="vertical" style="width: 100%">
          <a-button type="primary" block size="large" :loading="loading" @click="checkBinding">
            立即检查绑定状态
          </a-button>
          <a-button block @click="router.push('/setup')">返回设置</a-button>
        </a-space>
      </a-card>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useDeviceAuthStore } from '@/stores/deviceAuth'

const router = useRouter()
const deviceAuth = useDeviceAuthStore()
const loading = ref(false)
let pollTimer = null

onMounted(async () => {
  await deviceAuth.initializeIdentity()
  deviceAuth.saveSettings()
  await checkBinding()
  pollTimer = window.setInterval(() => {
    checkBinding(false)
  }, 15000)
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})

async function checkBinding(showToast = true) {
  loading.value = true
  try {
    const activated = await deviceAuth.registerAndTryActivate()
    if (activated) {
      if (showToast) {
        message.success('设备已激活')
      }
      router.push('/player')
      return
    }
    if (showToast) {
      message.info('设备已注册，等待后台绑定')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.activate-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: linear-gradient(160deg, #020617, #0f172a 45%, #1d4ed8);
}

.activate-layout {
  width: min(980px, 100%);
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(360px, 420px);
  gap: 20px;
}

.summary-card,
.form-card {
  border-radius: 20px;
}

@media (max-width: 900px) {
  .activate-layout {
    grid-template-columns: 1fr;
  }
}
</style>
