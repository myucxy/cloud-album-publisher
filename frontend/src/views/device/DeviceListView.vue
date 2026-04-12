<template>
  <div>
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; gap:12px; flex-wrap:wrap">
      <a-typography-text type="secondary">支持设备绑定、重命名、状态维护，以及设备分组与成员增删。</a-typography-text>
      <a-button @click="loadAll">刷新</a-button>
    </div>

    <div style="display:flex; flex-direction:column; gap:16px">
      <a-card>
        <template #title>
          <div style="display:flex; align-items:center; justify-content:space-between; gap:12px">
            <span>设备分组</span>
            <a-button size="small" @click="openGroupModal()">新建设备组</a-button>
          </div>
        </template>
        <a-table :data-source="groups" :columns="groupColumns" row-key="id" :loading="groupLoading" :pagination="false">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'createdAt'">
              {{ formatTime(record.createdAt) }}
            </template>
            <template v-if="column.key === 'action'">
              <a-space>
                <a-button type="link" size="small" @click="openGroupModal(record)">编辑</a-button>
                <a-button type="link" size="small" @click="openMemberModal(record, 'add')">加成员</a-button>
                <a-button type="link" size="small" @click="openMemberModal(record, 'remove')">移成员</a-button>
                <a-popconfirm title="确认删除该分组？" @confirm="deleteGroup(record.id)">
                  <a-button type="link" danger size="small">删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-card>
        <template #title>
          <div style="display:flex; align-items:center; justify-content:space-between; gap:12px">
            <span>设备列表</span>
            <a-button type="primary" size="small" @click="openBindModal">绑定设备</a-button>
          </div>
        </template>
        <a-table :data-source="devices" :columns="deviceColumns" row-key="id" :loading="deviceLoading" :pagination="false">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="deviceStatusColor(record.status)">{{ deviceStatusLabel(record.status) }}</a-tag>
            </template>
            <template v-if="column.key === 'lastHeartbeatAt'">
              {{ formatTime(record.lastHeartbeatAt) }}
            </template>
            <template v-if="column.key === 'boundAt'">
              {{ formatTime(record.boundAt) }}
            </template>
            <template v-if="column.key === 'action'">
              <a-space>
                <a-button type="link" size="small" @click="openRenameModal(record)">重命名</a-button>
                <a-button v-if="record.status !== 'ONLINE'" type="link" size="small" @click="changeStatus(record, 'ONLINE')">设为在线</a-button>
                <a-button v-if="record.status !== 'OFFLINE'" type="link" size="small" @click="changeStatus(record, 'OFFLINE')">设为离线</a-button>
                <a-popconfirm title="确认解绑该设备？" @confirm="unbindDevice(record.id)">
                  <a-button type="link" danger size="small">解绑</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>
    </div>

    <a-modal v-model:open="bindModalOpen" title="绑定设备" :width="440" @ok="submitBind" :confirm-loading="bindSaving" ok-text="绑定" cancel-text="取消">
      <a-form :model="bindForm" layout="vertical" ref="bindFormRef">
        <a-form-item label="设备唯一 ID" name="deviceUid" :rules="[{ required: true, message: '请输入设备唯一ID' }]">
          <a-input v-model:value="bindForm.deviceUid" placeholder="例如：device-001" />
        </a-form-item>
        <a-form-item label="设备类型" name="type" :rules="[{ required: true, message: '请输入设备类型' }]">
          <a-input v-model:value="bindForm.type" placeholder="例如：TV / ANDROID / IOS / WINDOWS" />
        </a-form-item>
        <a-form-item label="设备名称" name="name">
          <a-input v-model:value="bindForm.name" placeholder="可选，不填时默认使用设备唯一ID" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="renameModalOpen" title="重命名设备" :width="400" @ok="submitRename" :confirm-loading="renameSaving" ok-text="保存" cancel-text="取消">
      <a-form :model="renameForm" layout="vertical" ref="renameFormRef">
        <a-form-item label="设备名称" name="name" :rules="[{ required: true, message: '请输入设备名称' }]">
          <a-input v-model:value="renameForm.name" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="groupModalOpen" :title="editingGroupId ? '编辑分组' : '新建设备组'" :width="440" @ok="submitGroup" :confirm-loading="groupSaving" ok-text="保存" cancel-text="取消">
      <a-form :model="groupForm" layout="vertical" ref="groupFormRef">
        <a-form-item label="分组名称" name="name" :rules="[{ required: true, message: '请输入分组名称' }]">
          <a-input v-model:value="groupForm.name" />
        </a-form-item>
        <a-form-item label="分组描述" name="description">
          <a-textarea v-model:value="groupForm.description" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="memberModalOpen" :title="memberMode === 'add' ? '添加分组成员' : '移除分组成员'"
             :width="440"
             @ok="submitMember" :confirm-loading="memberSaving"
             :ok-text="memberMode === 'add' ? '添加' : '移除'"
             cancel-text="取消">
      <a-form :model="memberForm" layout="vertical" ref="memberFormRef">
        <a-form-item label="目标分组">
          <a-input :value="memberGroupName" disabled />
        </a-form-item>
        <a-form-item label="设备" name="deviceId" :rules="[{ required: true, message: '请选择设备' }]">
          <a-select v-model:value="memberForm.deviceId" placeholder="请选择设备">
            <a-select-option v-for="device in devices" :key="device.id" :value="device.id">
              {{ device.name || device.deviceUid }}（#{{ device.id }}）
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-alert v-if="memberMode === 'remove'" type="info" show-icon message="若所选设备当前不在该分组内，接口会返回提示。" />
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { deviceApi } from '@/api/device'

const devices = ref([])
const groups = ref([])
const deviceLoading = ref(false)
const groupLoading = ref(false)

const bindModalOpen = ref(false)
const bindSaving = ref(false)
const bindFormRef = ref()
const bindForm = reactive({ deviceUid: '', type: '', name: '' })

const renameModalOpen = ref(false)
const renameSaving = ref(false)
const renameTargetId = ref(null)
const renameFormRef = ref()
const renameForm = reactive({ name: '' })

const groupModalOpen = ref(false)
const groupSaving = ref(false)
const editingGroupId = ref(null)
const groupFormRef = ref()
const groupForm = reactive({ name: '', description: '' })

const memberModalOpen = ref(false)
const memberSaving = ref(false)
const memberFormRef = ref()
const memberMode = ref('add')
const memberGroupId = ref(null)
const memberGroupName = ref('')
const memberForm = reactive({ deviceId: undefined })

const deviceColumns = [
  { title: 'ID', dataIndex: 'id', width: 70 },
  { title: '设备唯一ID', dataIndex: 'deviceUid', width: 140 },
  { title: '名称', dataIndex: 'name', width: 140 },
  { title: '类型', dataIndex: 'type', width: 100 },
  { title: '状态', key: 'status', width: 100 },
  { title: '最近心跳', key: 'lastHeartbeatAt', width: 170 },
  { title: '绑定时间', key: 'boundAt', width: 170 },
  { title: '操作', key: 'action', width: 240 }
]

const groupColumns = [
  { title: 'ID', dataIndex: 'id', width: 70 },
  { title: '名称', dataIndex: 'name', width: 130 },
  { title: '描述', dataIndex: 'description', ellipsis: true },
  { title: '设备数', dataIndex: 'deviceCount', width: 90 },
  { title: '创建时间', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 220 }
]

onMounted(loadAll)

async function loadAll() {
  await Promise.all([loadDevices(), loadGroups()])
}

async function loadDevices() {
  deviceLoading.value = true
  try {
    const res = await deviceApi.list()
    devices.value = res.data || []
  } finally {
    deviceLoading.value = false
  }
}

async function loadGroups() {
  groupLoading.value = true
  try {
    const res = await deviceApi.listGroups()
    groups.value = res.data || []
  } finally {
    groupLoading.value = false
  }
}

function openBindModal() {
  Object.assign(bindForm, { deviceUid: '', type: '', name: '' })
  bindModalOpen.value = true
}

async function submitBind() {
  await bindFormRef.value.validate()
  bindSaving.value = true
  try {
    await deviceApi.bind({
      deviceUid: bindForm.deviceUid,
      type: bindForm.type,
      name: bindForm.name || undefined
    })
    message.success('设备已绑定')
    bindModalOpen.value = false
    await loadDevices()
  } finally {
    bindSaving.value = false
  }
}

function openRenameModal(record) {
  renameTargetId.value = record.id
  renameForm.name = record.name || ''
  renameModalOpen.value = true
}

async function submitRename() {
  await renameFormRef.value.validate()
  renameSaving.value = true
  try {
    await deviceApi.rename(renameTargetId.value, { name: renameForm.name })
    message.success('设备名称已更新')
    renameModalOpen.value = false
    await loadDevices()
  } finally {
    renameSaving.value = false
  }
}

async function changeStatus(record, status) {
  await deviceApi.updateStatus(record.id, { status })
  message.success('设备状态已更新')
  await loadDevices()
}

async function unbindDevice(id) {
  await deviceApi.unbind(id)
  message.success('设备已解绑')
  await loadAll()
}

function openGroupModal(record) {
  editingGroupId.value = record?.id || null
  Object.assign(groupForm, {
    name: record?.name || '',
    description: record?.description || ''
  })
  groupModalOpen.value = true
}

async function submitGroup() {
  await groupFormRef.value.validate()
  groupSaving.value = true
  try {
    const payload = {
      name: groupForm.name,
      description: groupForm.description || undefined
    }
    if (editingGroupId.value) {
      await deviceApi.updateGroup(editingGroupId.value, payload)
      message.success('分组已更新')
    } else {
      await deviceApi.createGroup(payload)
      message.success('分组已创建')
    }
    groupModalOpen.value = false
    await loadGroups()
  } finally {
    groupSaving.value = false
  }
}

async function deleteGroup(id) {
  await deviceApi.deleteGroup(id)
  message.success('分组已删除')
  await loadGroups()
}

function openMemberModal(group, mode) {
  memberMode.value = mode
  memberGroupId.value = group.id
  memberGroupName.value = `${group.name}（#${group.id}）`
  memberForm.deviceId = undefined
  memberModalOpen.value = true
}

async function submitMember() {
  await memberFormRef.value.validate()
  memberSaving.value = true
  try {
    if (memberMode.value === 'add') {
      await deviceApi.addGroupMember(memberGroupId.value, { deviceId: memberForm.deviceId })
      message.success('成员已添加')
    } else {
      await deviceApi.removeGroupMember(memberGroupId.value, memberForm.deviceId)
      message.success('成员已移除')
    }
    memberModalOpen.value = false
    await loadGroups()
  } finally {
    memberSaving.value = false
  }
}

function deviceStatusColor(status) {
  const map = { ONLINE: 'green', OFFLINE: 'default', UNBOUND: 'red' }
  return map[status] || 'default'
}

function deviceStatusLabel(status) {
  const map = { ONLINE: '在线', OFFLINE: '离线', UNBOUND: '未绑定' }
  return map[status] || status
}

function formatTime(value) {
  return value ? value.slice(0, 19).replace('T', ' ') : '-'
}
</script>
