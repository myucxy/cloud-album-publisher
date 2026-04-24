<template>
  <div class="device-page">
    <div class="device-page-header">
      <div class="device-page-heading">
        <div class="device-page-title">设备管理</div>
        <a-typography-text type="secondary">
          支持未绑定设备发现、后台绑定、重命名、状态维护，以及设备分组与成员增删。
        </a-typography-text>
      </div>
      <a-space wrap>
        <a-tag color="blue">已绑定 {{ devices.length }}</a-tag>
        <a-tag color="green">在线 {{ onlineDeviceCount }}</a-tag>
        <a-tag color="orange">待绑定 {{ unboundDevices.length }}</a-tag>
        <a-button @click="loadAll">刷新</a-button>
      </a-space>
    </div>

    <div class="device-layout">
      <div class="device-layout-side">
        <a-card class="section-card compact-card">
          <template #title>
            <div class="card-title-row">
              <div>
                <div class="card-title">设备分组</div>
                <div class="card-hint">先维护分组，再将内容统一分发到一组设备。</div>
              </div>
              <a-button size="small" @click="openGroupModal()">新建设备组</a-button>
            </div>
          </template>
            <a-spin :spinning="groupLoading">
              <div class="group-filter-list">
                <div
                  role="button"
                  tabindex="0"
                  class="group-filter-item"
                  :class="{ active: selectedGroupId === null }"
                  @click="selectGroup(null)"
                  @keydown.enter.prevent="selectGroup(null)"
                  @keydown.space.prevent="selectGroup(null)"
                >
                  <div class="group-filter-main">
                    <div class="group-filter-name">全部分组</div>
                    <div class="group-filter-desc">查看所有设备</div>
                  </div>
                  <a-badge :count="devices.length" :number-style="{ backgroundColor: '#1677ff' }" />
                </div>

                <a-empty v-if="groups.length === 0" description="暂无设备组" />

                <div
                  v-for="group in groups"
                  :key="group.id"
                  role="button"
                  tabindex="0"
                  class="group-filter-item"
                  :class="{ active: selectedGroupId === group.id }"
                  @click="selectGroup(group.id)"
                  @keydown.enter.prevent="selectGroup(group.id)"
                  @keydown.space.prevent="selectGroup(group.id)"
                >
                  <div class="group-filter-main">
                    <div class="group-filter-name" :title="group.name || `设备组 #${group.id}`">{{ group.name || `设备组 #${group.id}` }}</div>
                    <div class="group-filter-desc" :title="group.description || '暂无分组描述'">{{ group.description || '暂无分组描述' }}</div>
                  </div>
                  <div class="group-filter-side">
                    <a-tag color="blue">{{ group.deviceCount || 0 }} 台</a-tag>
                    <a-space size="small" @click.stop>
                      <a-button type="link" size="small" @click="openGroupModal(group)">编辑</a-button>
                      <a-dropdown :trigger="['click']">
                        <a-button type="link" size="small">更多</a-button>
                        <template #overlay>
                          <a-menu>
                            <a-menu-item @click="openMemberModal(group, 'add')">加成员</a-menu-item>
                            <a-menu-item @click="openMemberModal(group, 'remove')">移成员</a-menu-item>
                            <a-menu-divider />
                            <a-menu-item danger>
                              <a-popconfirm title="确认删除该分组？" @confirm="deleteGroup(group.id)">
                                <span>删除分组</span>
                              </a-popconfirm>
                            </a-menu-item>
                          </a-menu>
                        </template>
                      </a-dropdown>
                    </a-space>
                  </div>
                </div>
              </div>
            </a-spin>
        </a-card>
      </div>

      <a-card class="section-card primary-card">
        <template #title>
          <div class="card-title-row">
            <div>
              <div class="card-title">设备列表</div>
              <div class="card-hint">{{ deviceListHint }}</div>
            </div>
            <a-radio-group v-model:value="deviceFilter" size="small">
              <a-radio-button value="ALL">全部</a-radio-button>
              <a-radio-button value="BOUND">已绑定</a-radio-button>
              <a-radio-button value="UNBOUND">未绑定</a-radio-button>
            </a-radio-group>
          </div>
        </template>
        <a-table
          :data-source="filteredDevices"
          :columns="deviceColumns"
          row-key="id"
          :loading="deviceLoading || unboundDeviceLoading"
          :pagination="false"
          :locale="{ emptyText: '暂无设备' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'identity'">
              <div class="entity-cell">
                <div class="entity-main">{{ record.name || '未命名设备' }}</div>
                <div class="entity-sub">UID：{{ record.deviceUid || '-' }}</div>
              </div>
            </template>
            <template v-if="column.key === 'status'">
              <a-tag :color="deviceStatusColor(record.status)">{{ deviceStatusLabel(record.status) }}</a-tag>
            </template>
            <template v-if="column.key === 'groups'">
              <a-space v-if="record.groupNames?.length" wrap size="small">
                <a-tag v-for="groupName in record.groupNames" :key="groupName" color="purple">{{ groupName }}</a-tag>
              </a-space>
              <span v-else class="empty-text">{{ record.status === 'UNBOUND' ? '未绑定' : '未分组' }}</span>
            </template>
            <template v-if="column.key === 'timeline'">
              <div class="meta-cell">
                <div>最近心跳：{{ formatTime(record.lastHeartbeatAt) }}</div>
                <div class="meta-sub">绑定时间：{{ record.status === 'UNBOUND' ? '-' : formatTime(record.boundAt) }}</div>
              </div>
            </template>
            <template v-if="column.key === 'action'">
              <a-space wrap size="small">
                <a-button v-if="record.status === 'UNBOUND'" type="link" size="small" @click="openBindModal(record)">绑定</a-button>
                <template v-else>
                  <a-button type="link" size="small" @click="openRenameModal(record)">重命名</a-button>
                  <a-button v-if="record.status !== 'ONLINE'" type="link" size="small" @click="changeStatus(record, 'ONLINE')">设为在线</a-button>
                  <a-button v-if="record.status !== 'OFFLINE'" type="link" size="small" @click="changeStatus(record, 'OFFLINE')">设为离线</a-button>
                  <a-popconfirm title="确认解绑该设备？" @confirm="unbindDevice(record.id)">
                    <a-button type="link" danger size="small">解绑</a-button>
                  </a-popconfirm>
                </template>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>
    </div>

    <a-modal v-model:open="bindModalOpen" title="绑定未绑定设备" :width="420" @ok="submitBind" :confirm-loading="bindSaving" ok-text="绑定" cancel-text="取消">
      <a-form :model="bindForm" layout="vertical" ref="bindFormRef">
        <a-form-item label="设备唯一 ID">
          <a-input :value="bindForm.deviceUid" disabled />
        </a-form-item>
        <a-form-item label="设备类型">
          <a-input :value="bindForm.type" disabled />
        </a-form-item>
        <a-form-item label="设备名称" name="name" :rules="[{ required: true, message: '请输入设备名称' }]">
          <a-input v-model:value="bindForm.name" placeholder="请输入绑定后的设备名称" />
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
        <a-form-item
          label="设备"
          :name="memberMode === 'add' ? 'deviceIds' : 'deviceId'"
          :rules="[{ required: true, message: '请选择设备' }]"
        >
          <a-select
            v-if="memberMode === 'add'"
            v-model:value="memberForm.deviceIds"
            mode="multiple"
            placeholder="请选择要添加的设备"
            :options="memberDeviceOptions.map(toDeviceOption)"
            :not-found-content="'没有可添加的设备'"
          />
          <a-select
            v-else
            v-model:value="memberForm.deviceId"
            placeholder="请选择要移除的设备"
            :options="memberDeviceOptions.map(toDeviceOption)"
            :not-found-content="'当前分组暂无成员'"
          />
        </a-form-item>
        <a-alert v-if="memberMode === 'add' && memberDeviceOptions.length === 0" type="info" show-icon message="当前分组已包含所有已绑定设备。" />
        <a-alert v-if="memberMode === 'remove'" type="info" show-icon message="若所选设备当前不在该分组内，接口会返回提示。" />
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { deviceApi } from '@/api/device'

const devices = ref([])
const unboundDevices = ref([])
const groups = ref([])
const deviceLoading = ref(false)
const unboundDeviceLoading = ref(false)
const groupLoading = ref(false)

const bindModalOpen = ref(false)
const bindSaving = ref(false)
const bindTargetId = ref(null)
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
const memberForm = reactive({ deviceId: undefined, deviceIds: [] })

const deviceFilter = ref('ALL')
const selectedGroupId = ref(null)

const selectedGroup = computed(() => groups.value.find(item => item.id === selectedGroupId.value))
const selectedGroupName = computed(() => selectedGroup.value?.name || (selectedGroup.value ? `设备组 #${selectedGroup.value.id}` : '全部分组'))
const deviceListHint = computed(() => `当前筛选：${selectedGroupName.value} · ${deviceFilterLabel(deviceFilter.value)}`)

const onlineDeviceCount = computed(() => devices.value.filter(item => item.status === 'ONLINE').length)
const allDevices = computed(() => [...devices.value, ...unboundDevices.value])
const statusFilteredDevices = computed(() => {
  if (deviceFilter.value === 'BOUND') {
    return devices.value
  }
  if (deviceFilter.value === 'UNBOUND') {
    return unboundDevices.value
  }
  return allDevices.value
})
const filteredDevices = computed(() => {
  if (selectedGroupId.value === null) {
    return statusFilteredDevices.value
  }
  return statusFilteredDevices.value.filter(device => device.groupIds?.includes(selectedGroupId.value))
})
const memberDeviceOptions = computed(() => {
  if (memberMode.value === 'add') {
    return devices.value.filter(device => !device.groupIds?.includes(memberGroupId.value))
  }
  return devices.value.filter(device => device.groupIds?.includes(memberGroupId.value))
})

const deviceColumns = [
  { title: '设备', key: 'identity' },
  { title: '类型', dataIndex: 'type', width: 100 },
  { title: '状态', key: 'status', width: 100 },
  { title: '分组', key: 'groups', width: 180 },
  { title: '时间', key: 'timeline', width: 240 },
  { title: '操作', key: 'action', width: 260, align: 'right' }
]

onMounted(loadAll)

async function loadAll() {
  await Promise.all([loadDevices(), loadUnboundDevices(), loadGroups()])
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

async function loadUnboundDevices() {
  unboundDeviceLoading.value = true
  try {
    const res = await deviceApi.listUnbound()
    unboundDevices.value = res.data || []
  } finally {
    unboundDeviceLoading.value = false
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

function selectGroup(groupId) {
  selectedGroupId.value = selectedGroupId.value === groupId ? null : groupId
}

function deviceFilterLabel(value) {
  const map = { ALL: '全部设备', BOUND: '已绑定', UNBOUND: '未绑定' }
  return map[value] || value
}

function openBindModal(record) {
  bindTargetId.value = record.id
  Object.assign(bindForm, {
    deviceUid: record.deviceUid || '',
    type: record.type || '',
    name: record.name || ''
  })
  bindModalOpen.value = true
}

async function submitBind() {
  await bindFormRef.value.validate()
  bindSaving.value = true
  try {
    await deviceApi.bindUnbound(bindTargetId.value, { name: bindForm.name })
    message.success('设备已绑定到当前账号')
    bindModalOpen.value = false
    await loadAll()
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
    await Promise.all([loadGroups(), loadDevices()])
  } finally {
    groupSaving.value = false
  }
}

async function deleteGroup(id) {
  await deviceApi.deleteGroup(id)
  message.success('分组已删除')
  if (selectedGroupId.value === id) {
    selectedGroupId.value = null
  }
  await Promise.all([loadGroups(), loadDevices()])
}

function openMemberModal(group, mode) {
  memberMode.value = mode
  memberGroupId.value = group.id
  memberGroupName.value = `${group.name}（#${group.id}）`
  memberForm.deviceId = undefined
  memberForm.deviceIds = []
  memberModalOpen.value = true
}

async function submitMember() {
  await memberFormRef.value.validate()
  memberSaving.value = true
  try {
    if (memberMode.value === 'add') {
      await Promise.all(memberForm.deviceIds.map(deviceId => deviceApi.addGroupMember(memberGroupId.value, { deviceId })))
      message.success('成员已添加')
    } else {
      await deviceApi.removeGroupMember(memberGroupId.value, memberForm.deviceId)
      message.success('成员已移除')
    }
    memberModalOpen.value = false
    await Promise.all([loadGroups(), loadDevices()])
  } finally {
    memberSaving.value = false
  }
}

function toDeviceOption(device) {
  return {
    label: `${device.name || device.deviceUid}（#${device.id}）`,
    value: device.id
  }
}

function deviceStatusColor(status) {
  const map = { ONLINE: 'green', OFFLINE: 'default', UNBOUND: 'orange' }
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

<style scoped>
.device-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.device-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.device-page-heading {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.device-page-title {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
  color: #111827;
}

.device-layout {
  display: grid;
  grid-template-columns: minmax(340px, 420px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.device-layout-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-card {
  border-radius: 16px;
}

.compact-card :deep(.ant-card-body),
.primary-card :deep(.ant-card-body) {
  padding-top: 8px;
}

.card-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.card-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.5;
}

.group-filter-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.group-filter-item {
  width: 100%;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 10px 12px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s, box-shadow 0.2s;
}

.group-filter-item:hover,
.group-filter-item.active {
  border-color: #1677ff;
  background: #f0f7ff;
}

.group-filter-item.active {
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.08);
}

.group-filter-main {
  min-width: 0;
  flex: 1;
}

.group-filter-name {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.group-filter-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.group-filter-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.entity-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  overflow: hidden;
}

.entity-main {
  font-weight: 500;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.entity-sub,
.meta-sub {
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  color: #4b5563;
}

.empty-text {
  color: #bfbfbf;
}

@media (max-width: 1200px) {
  .device-layout {
    grid-template-columns: 1fr;
  }
}
</style>