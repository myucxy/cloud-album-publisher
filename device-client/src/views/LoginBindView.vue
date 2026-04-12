<template>
  <div class="activate-page">
    <div class="activate-layout">
      <a-card class="summary-card" title="设备摘要">
        <a-descriptions :column="1" size="small">
          <a-descriptions-item label="设备 UID">{{ deviceAuth.deviceUid || '-' }}</a-descriptions-item>
          <a-descriptions-item label="设备名称">{{ deviceAuth.deviceName || '-' }}</a-descriptions-item>
          <a-descriptions-item label="设备类型">{{ deviceAuth.deviceType }}</a-descriptions-item>
          <a-descriptions-item label="服务器地址">{{ deviceAuth.serverBaseUrl }}</a-descriptions-item>
        </a-descriptions>
        <a-alert style="margin-top: 16px" type="info" show-icon message="登录成功后将自动完成设备绑定并签发设备访问令牌。" />
      </a-card>

      <a-card class="form-card" title="登录并激活设备">
        <a-form layout="vertical" :model="form" @finish="submit">
          <a-form-item label="用户名" name="username" :rules="[{ required: true, message: '请输入用户名' }]">
            <a-input v-model:value="form.username" placeholder="请输入用户名" size="large" />
          </a-form-item>
          <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
            <a-input-password v-model:value="form.password" placeholder="请输入密码" size="large" />
          </a-form-item>
          <a-space direction="vertical" style="width: 100%">
            <a-button type="primary" html-type="submit" block size="large" :loading="loading">
              登录并激活
            </a-button>
            <a-button block @click="router.push('/setup')">返回设置</a-button>
          </a-space>
        </a-form>
      </a-card>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useDeviceAuthStore } from '@/stores/deviceAuth'

const router = useRouter()
const deviceAuth = useDeviceAuthStore()
const loading = ref(false)
const form = reactive({
  username: '',
  password: ''
})

onMounted(async () => {
  await deviceAuth.initializeIdentity()
  deviceAuth.saveSettings()
})

async function submit() {
  loading.value = true
  try {
    await deviceAuth.activateDevice(form)
    message.success('设备已激活')
    router.push('/player')
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
