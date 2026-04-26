<template>
  <div class="download-page">
    <div class="download-shell">
      <a-card class="hero-card" :bordered="false">
        <div class="hero-content">
          <div>
            <div class="eyebrow">Cloud Album Publisher</div>
            <h1>客户端下载</h1>
            <p>无需登录即可下载各平台客户端，请选择与你的设备匹配的安装包。</p>
          </div>
          <a-space>
            <router-link to="/login">后台登录</router-link>
            <a-button :loading="loading" @click="loadDownloads">刷新</a-button>
          </a-space>
        </div>
      </a-card>

      <a-alert
        v-if="errorMessage"
        type="error"
        show-icon
        :message="errorMessage"
        style="margin-bottom: 16px"
      />

      <a-spin :spinning="loading">
        <a-empty v-if="!platforms.length" description="暂无可下载客户端，请稍后再试" class="empty-state" />
        <div v-else class="platform-list">
          <a-card v-for="platform in platforms" :key="platform.platform" class="platform-card" :bordered="false">
            <template #title>
              <a-space>
                <span>{{ platformLabel(platform.platform) }}</span>
                <a-tag>{{ platform.platform }}</a-tag>
              </a-space>
            </template>
            <a-row :gutter="[16, 16]">
              <a-col
                v-for="item in platform.channels"
                :key="`${platform.platform}-${item.channel}`"
                :xs="24"
                :lg="12"
              >
                <a-card class="download-card">
                  <template #title>
                    <div class="download-title">
                      <span>{{ item.fileName || `${platformLabel(platform.platform)} 客户端` }}</span>
                      <a-tag :color="channelColor(item.channel)">{{ item.channel || 'stable' }}</a-tag>
                    </div>
                  </template>
                  <a-descriptions :column="1" size="small" bordered>
                    <a-descriptions-item label="版本">
                      {{ item.version || '-' }}
                      <span v-if="item.versionCode !== undefined && item.versionCode !== null" class="muted">
                        #{{ item.versionCode }}
                      </span>
                    </a-descriptions-item>
                    <a-descriptions-item label="大小">{{ formatSize(item.size) }}</a-descriptions-item>
                    <a-descriptions-item label="发布时间">{{ formatTime(item.publishedAt) }}</a-descriptions-item>
                    <a-descriptions-item v-if="item.forceUpdate" label="更新类型">
                      <a-tag color="red">强制更新</a-tag>
                    </a-descriptions-item>
                    <a-descriptions-item label="SHA-256">
                      <a-typography-text class="hash-text" copyable>
                        {{ item.sha256 || '-' }}
                      </a-typography-text>
                    </a-descriptions-item>
                  </a-descriptions>
                  <div v-if="item.releaseNotes" class="release-notes">
                    {{ item.releaseNotes }}
                  </div>
                  <div class="download-actions">
                    <a-button
                      type="primary"
                      :href="item.downloadUrl"
                      target="_blank"
                      rel="noopener noreferrer"
                      :disabled="!item.downloadUrl"
                    >
                      下载客户端
                    </a-button>
                  </div>
                </a-card>
              </a-col>
            </a-row>
          </a-card>
        </div>
      </a-spin>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { clientUpdateApi } from '@/api/client-update'

const loading = ref(false)
const errorMessage = ref('')
const downloadData = ref({ platforms: [] })

const platforms = computed(() => downloadData.value?.platforms || [])

onMounted(loadDownloads)

async function loadDownloads() {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await clientUpdateApi.listDownloads()
    downloadData.value = res.data || { platforms: [] }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || error.message || '客户端下载列表加载失败'
    downloadData.value = { platforms: [] }
  } finally {
    loading.value = false
  }
}

function platformLabel(platform) {
  const value = String(platform || '').toLowerCase()
  if (value === 'pc') return '桌面客户端'
  if (value === 'android') return 'Android 客户端'
  if (value === 'ios') return 'iOS 客户端'
  if (value === 'windows') return 'Windows 客户端'
  if (value === 'linux') return 'Linux 客户端'
  return platform || '未知平台'
}

function channelColor(channel) {
  return String(channel || '').toLowerCase() === 'stable' ? 'green' : 'blue'
}

function formatSize(size) {
  const bytes = Number(size)
  if (!Number.isFinite(bytes) || bytes <= 0) {
    return '-'
  }
  const units = ['B', 'KB', 'MB', 'GB']
  let value = bytes
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  return `${value.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`
}

function formatTime(value) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString()
}
</script>

<style scoped>
.download-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #eef2ff 0%, #f8fafc 45%, #ecfeff 100%);
  padding: 40px 20px;
}

.download-shell {
  max-width: 1120px;
  margin: 0 auto;
}

.hero-card {
  margin-bottom: 16px;
  border-radius: 20px;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.08);
}

.hero-content {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-start;
}

.eyebrow {
  color: #2563eb;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  margin-bottom: 8px;
}

h1 {
  margin: 0 0 12px;
  font-size: 36px;
  color: #0f172a;
}

p {
  margin: 0;
  color: #64748b;
  font-size: 16px;
}

.empty-state {
  padding: 72px 0;
  background: #fff;
  border-radius: 16px;
}

.platform-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.platform-card {
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.06);
}

.download-card {
  height: 100%;
}

.download-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.muted {
  color: #94a3b8;
  margin-left: 6px;
}

.hash-text {
  max-width: 100%;
  word-break: break-all;
}

.release-notes {
  margin-top: 12px;
  padding: 12px;
  color: #475569;
  background: #f8fafc;
  border-radius: 8px;
  white-space: pre-wrap;
}

.download-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 640px) {
  .download-page {
    padding: 20px 12px;
  }

  .hero-content {
    flex-direction: column;
  }

  h1 {
    font-size: 28px;
  }
}
</style>
