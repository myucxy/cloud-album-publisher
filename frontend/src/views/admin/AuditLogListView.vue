<template>
  <div>
    <div style="display:flex; justify-content:space-between; margin-bottom:16px; gap:12px; flex-wrap:wrap">
      <a-space wrap>
        <a-input-number v-model:value="filters.userId" :min="1" placeholder="用户 ID" style="width:120px" />
        <a-input v-model:value="filters.action" placeholder="操作类型" style="width:160px" />
        <a-input v-model:value="filters.resourceType" placeholder="资源类型" style="width:160px" />
        <a-select v-model:value="filters.result" allow-clear placeholder="结果" style="width:120px">
          <a-select-option value="SUCCESS">SUCCESS</a-select-option>
          <a-select-option value="FAIL">FAIL</a-select-option>
        </a-select>
        <a-button type="primary" @click="search">查询</a-button>
        <a-button @click="reset">重置</a-button>
      </a-space>
      <a-button @click="load">刷新</a-button>
    </div>

    <a-table :data-source="logs" :columns="columns" row-key="id" :loading="loading"
             :pagination="{ total, current: page, pageSize, onChange: p => { page = p; load() } }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'result'">
          <a-tag :color="record.result === 'SUCCESS' ? 'green' : 'red'">{{ record.result }}</a-tag>
        </template>
        <template v-if="column.key === 'createdAt'">
          {{ formatTime(record.createdAt) }}
        </template>
        <template v-if="column.key === 'detail'">
          <span :title="record.detail || ''">{{ record.detail || '-' }}</span>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminApi } from '@/api/distribution'

const logs = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 15
const loading = ref(false)
const filters = reactive({
  userId: undefined,
  action: '',
  resourceType: '',
  result: undefined
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '用户ID', dataIndex: 'userId', width: 90 },
  { title: '用户名', dataIndex: 'username', width: 120 },
  { title: '操作类型', dataIndex: 'action', width: 150 },
  { title: '资源类型', dataIndex: 'resourceType', width: 120 },
  { title: '结果', key: 'result', width: 100 },
  { title: 'IP', dataIndex: 'ip', width: 140 },
  { title: '详情', key: 'detail', ellipsis: true },
  { title: '时间', key: 'createdAt', width: 170 }
]

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await adminApi.listAuditLogs({
      page: page.value,
      size: pageSize,
      userId: filters.userId || undefined,
      action: filters.action || undefined,
      resourceType: filters.resourceType || undefined,
      result: filters.result || undefined
    })
    logs.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  filters.userId = undefined
  filters.action = ''
  filters.resourceType = ''
  filters.result = undefined
  page.value = 1
  load()
}

function formatTime(value) {
  return value ? value.slice(0, 19).replace('T', ' ') : '-'
}
</script>
