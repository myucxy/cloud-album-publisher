<template>
  <div>
    <div style="margin-bottom:16px">
      <a-space>
        <a-button @click="openSettings">设置</a-button>
        <a-button @click="loadReviews">刷新</a-button>
      </a-space>
    </div>

    <a-table :data-source="reviews" :columns="columns" row-key="id" :loading="loading"
             :pagination="{ total, current: page, pageSize, onChange: p => { page = p; loadReviews() } }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'target'">
          <a-tag>媒体 #{{ record.mediaId }}</a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="reviewStatusColor(record.status)">{{ reviewStatusLabel(record.status) }}</a-tag>
        </template>
        <template v-if="column.key === 'rejectReason'">
          {{ record.rejectReason || '-' }}
        </template>
        <template v-if="column.key === 'createdAt'">
          {{ record.createdAt ? record.createdAt.slice(0, 16) : '-' }}
        </template>
        <template v-if="column.key === 'action'">
          <a-space v-if="record.status === 'PENDING'">
            <a-button type="link" size="small" style="color:#52c41a" @click="openReview(record, 'APPROVED')">
              通过
            </a-button>
            <a-button type="link" danger size="small" @click="openReview(record, 'REJECTED')">
              拒绝
            </a-button>
          </a-space>
          <span v-else style="color:#8c8c8c; font-size:12px">已处理</span>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="reviewModalOpen"
             :title="reviewAction === 'APPROVED' ? '通过审核' : '拒绝审核'"
             :width="440"
             @ok="submitReview" :confirm-loading="saving"
             :ok-text="reviewAction === 'APPROVED' ? '通过' : '拒绝'"
             cancel-text="取消">
      <a-form layout="vertical">
        <a-descriptions :column="1" size="small" bordered style="margin-bottom:16px">
          <a-descriptions-item label="媒体">#{{ reviewTarget?.mediaId }}</a-descriptions-item>
          <a-descriptions-item label="提交人">{{ reviewTarget?.userId }}</a-descriptions-item>
          <a-descriptions-item label="提交时间">{{ reviewTarget?.createdAt ? reviewTarget.createdAt.slice(0, 16) : '-' }}</a-descriptions-item>
        </a-descriptions>
        <a-form-item v-if="reviewAction === 'REJECTED'" label="拒绝原因" required>
          <a-textarea v-model:value="reviewComment" :rows="3" placeholder="输入拒绝原因..." />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="settingsModalOpen"
             title="审核设置"
             :width="420"
             @ok="submitSettings" :confirm-loading="settingsSaving"
             ok-text="保存"
             cancel-text="取消">
      <a-form layout="vertical">
        <a-spin :spinning="settingsLoading">
          <a-form-item style="margin-bottom:0">
            <a-checkbox v-model:checked="autoApproveEnabled">自动审核通过</a-checkbox>
          </a-form-item>
        </a-spin>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { adminApi } from '@/api/distribution'
import { DEFAULT_PAGE_SIZE } from '@/constants/pagination'

const reviews = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = DEFAULT_PAGE_SIZE
const loading = ref(false)

const reviewModalOpen = ref(false)
const saving = ref(false)
const reviewTarget = ref(null)
const reviewAction = ref('')
const reviewComment = ref('')

const settingsModalOpen = ref(false)
const settingsLoading = ref(false)
const settingsSaving = ref(false)
const autoApproveEnabled = ref(false)

const columns = [
  { title: 'ID', dataIndex: 'id', width: 70 },
  { title: '内容', key: 'target' },
  { title: '提交人', dataIndex: 'userId', width: 90 },
  { title: '状态', key: 'status', width: 90 },
  { title: '拒绝原因', key: 'rejectReason', ellipsis: true },
  { title: '提交时间', key: 'createdAt', width: 140 },
  { title: '操作', key: 'action', width: 130 }
]

onMounted(loadPage)

async function loadPage() {
  await Promise.all([loadReviews(), loadSettings()])
}

async function loadReviews() {
  loading.value = true
  try {
    const res = await adminApi.listReviews({ page: page.value, size: pageSize })
    reviews.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadSettings() {
  settingsLoading.value = true
  try {
    const res = await adminApi.getReviewSettings()
    autoApproveEnabled.value = !!res.data.autoApproveEnabled
  } finally {
    settingsLoading.value = false
  }
}

async function openSettings() {
  settingsModalOpen.value = true
  await loadSettings()
}

function openReview(record, action) {
  reviewTarget.value = record
  reviewAction.value = action
  reviewComment.value = ''
  reviewModalOpen.value = true
}

async function submitReview() {
  if (reviewAction.value === 'REJECTED' && !reviewComment.value.trim()) {
    message.warning('拒绝时请填写原因')
    return
  }
  saving.value = true
  try {
    if (reviewAction.value === 'APPROVED') {
      await adminApi.approveReview(reviewTarget.value.id)
    } else {
      await adminApi.rejectReview(reviewTarget.value.id, { rejectReason: reviewComment.value.trim() })
    }
    message.success(reviewAction.value === 'APPROVED' ? '已通过' : '已拒绝')
    reviewModalOpen.value = false
    loadReviews()
  } finally {
    saving.value = false
  }
}

async function submitSettings() {
  settingsSaving.value = true
  try {
    const res = await adminApi.updateReviewSettings({ autoApproveEnabled: autoApproveEnabled.value })
    autoApproveEnabled.value = !!res.data.autoApproveEnabled
    message.success('设置已保存')
    settingsModalOpen.value = false
  } finally {
    settingsSaving.value = false
  }
}

function reviewStatusColor(status) {
  const map = { PENDING: 'orange', APPROVED: 'green', REJECTED: 'red' }
  return map[status] || 'default'
}

function reviewStatusLabel(status) {
  const map = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝' }
  return map[status] || status
}
</script>
