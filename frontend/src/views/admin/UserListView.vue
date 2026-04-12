<template>
  <div>
    <div style="display:flex; justify-content:space-between; margin-bottom:16px">
      <a-typography-text type="secondary">当前后端仅支持查看、编辑昵称/头像、删除用户。</a-typography-text>
      <a-button @click="load">刷新</a-button>
    </div>

    <a-table :data-source="users" :columns="columns" row-key="id" :loading="loading"
             :pagination="{ total, current: page, pageSize, onChange: p => { page = p; load() } }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'red'">{{ record.status === 1 ? '正常' : '禁用' }}</a-tag>
        </template>
        <template v-if="column.key === 'roles'">
          <a-tag v-for="role in (record.roles || [])" :key="role" color="blue">{{ role }}</a-tag>
        </template>
        <template v-if="column.key === 'createdAt'">
          {{ record.createdAt ? record.createdAt.slice(0, 10) : '-' }}
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm title="确认删除该用户？" @confirm="deleteUser(record.id)">
              <a-button type="link" danger size="small">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="modalOpen" title="编辑用户" :width="480" @ok="submitForm" :confirm-loading="saving" ok-text="保存" cancel-text="取消">
      <a-form :model="form" layout="vertical" ref="formRef">
        <a-form-item label="用户名">
          <a-input v-model:value="form.username" disabled />
        </a-form-item>
        <a-form-item label="邮箱">
          <a-input v-model:value="form.email" disabled />
        </a-form-item>
        <a-form-item label="昵称">
          <a-input v-model:value="form.nickname" />
        </a-form-item>
        <a-form-item label="头像 URL">
          <a-input v-model:value="form.avatarUrl" placeholder="https://..." />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { adminApi } from '@/api/distribution'

const users = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 15
const loading = ref(false)
const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({ username: '', email: '', nickname: '', avatarUrl: '' })

const columns = [
  { title: 'ID', dataIndex: 'id', width: 70 },
  { title: '用户名', dataIndex: 'username' },
  { title: '邮箱', dataIndex: 'email' },
  { title: '昵称', dataIndex: 'nickname' },
  { title: '角色', key: 'roles', width: 140 },
  { title: '状态', key: 'status', width: 80 },
  { title: '注册时间', key: 'createdAt', width: 110 },
  { title: '操作', key: 'action', width: 140 }
]

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await adminApi.listUsers({ page: page.value, size: pageSize })
    users.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openEdit(record) {
  editingId.value = record.id
  Object.assign(form, {
    username: record.username,
    email: record.email,
    nickname: record.nickname || '',
    avatarUrl: record.avatarUrl || ''
  })
  modalOpen.value = true
}

async function submitForm() {
  saving.value = true
  try {
    await adminApi.updateUser(editingId.value, {
      nickname: form.nickname,
      avatarUrl: form.avatarUrl
    })
    message.success('用户已更新')
    modalOpen.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function deleteUser(id) {
  await adminApi.deleteUser(id)
  message.success('已删除')
  load()
}
</script>
