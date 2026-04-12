<template>
  <div>
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px">
      <a-typography-text type="secondary">展示当前系统用户、相册、待审核内容与生效中的分发规则总量。</a-typography-text>
      <a-button @click="load" :loading="loading">刷新</a-button>
    </div>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :sm="12" :xl="6">
        <a-card>
          <a-statistic title="注册用户" :value="stats.totalUsers" />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :xl="6">
        <a-card>
          <a-statistic title="相册总数" :value="stats.totalAlbums" />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :xl="6">
        <a-card>
          <a-statistic title="待审核内容" :value="stats.pendingReviews" />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :xl="6">
        <a-card>
          <a-statistic title="生效分发" :value="stats.activeDistributions" />
        </a-card>
      </a-col>
    </a-row>

    <a-card style="margin-top:16px" title="统计说明">
      <a-descriptions :column="1" size="small">
        <a-descriptions-item label="注册用户">所有已注册用户数量。</a-descriptions-item>
        <a-descriptions-item label="相册总数">系统中当前保存的相册数量。</a-descriptions-item>
        <a-descriptions-item label="待审核内容">状态为 PENDING 的审核记录数量。</a-descriptions-item>
        <a-descriptions-item label="生效分发">状态为 ACTIVE 的分发规则数量。</a-descriptions-item>
      </a-descriptions>
    </a-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { adminApi } from '@/api/distribution'

const loading = ref(false)
const stats = reactive({
  totalUsers: 0,
  totalAlbums: 0,
  pendingReviews: 0,
  activeDistributions: 0
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await adminApi.stats()
    Object.assign(stats, res.data || {})
  } finally {
    loading.value = false
  }
}
</script>
