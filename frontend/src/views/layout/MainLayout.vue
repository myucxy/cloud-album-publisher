<template>
  <a-layout style="min-height: 100vh">
    <a-layout-sider v-model:collapsed="collapsed" collapsible>
      <div class="logo">{{ collapsed ? '云影' : '智控云影' }}</div>
      <a-menu theme="dark" mode="inline" :selected-keys="[route.path]" @click="onMenuClick">
        <a-menu-item key="/albums">
          <picture-outlined />
          <span>相册管理</span>
        </a-menu-item>
        <a-menu-item key="/media">
          <file-image-outlined />
          <span>媒体管理</span>
        </a-menu-item>
        <a-menu-item key="/distributions">
          <deployment-unit-outlined />
          <span>内容分发</span>
        </a-menu-item>
        <a-menu-item key="/devices">
          <desktop-outlined />
          <span>设备管理</span>
        </a-menu-item>
        <a-menu-item v-if="isAdmin" key="/admin/users">
          <team-outlined />
          <span>用户管理</span>
        </a-menu-item>
      </a-menu>
    </a-layout-sider>

    <a-layout>
      <a-layout-header style="background:#fff; padding: 0 24px; display:flex; align-items:center; justify-content:space-between">
        <span style="font-weight:600; font-size:16px">{{ pageTitle }}</span>
        <a-dropdown>
          <a-space style="cursor:pointer">
            <user-outlined />
            <span>{{ authStore.username }}</span>
          </a-space>
          <template #overlay>
            <a-menu>
              <a-menu-item @click="openChangePasswordModal">修改密码</a-menu-item>
              <a-menu-item @click="handleLogout">退出登录</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </a-layout-header>

      <a-layout-content style="margin: 24px; min-height: 280px">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>

  <a-modal
    v-model:open="changePasswordOpen"
    title="修改密码"
    :width="420"
    @ok="submitChangePassword"
    :confirm-loading="changePasswordLoading"
    ok-text="确认修改"
    cancel-text="取消"
  >
    <a-form :model="changePasswordForm" :rules="changePasswordRules" layout="vertical" ref="changePasswordFormRef">
      <a-form-item label="旧密码" name="oldPassword">
        <a-input-password v-model:value="changePasswordForm.oldPassword" placeholder="请输入当前密码" />
      </a-form-item>
      <a-form-item label="新密码" name="newPassword">
        <a-input-password v-model:value="changePasswordForm.newPassword" placeholder="请输入新密码" />
      </a-form-item>
      <a-form-item label="确认新密码" name="confirmPassword">
        <a-input-password v-model:value="changePasswordForm.confirmPassword" placeholder="请再次输入新密码" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  PictureOutlined, FileImageOutlined, DesktopOutlined, DeploymentUnitOutlined,
  TeamOutlined, UserOutlined
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const collapsed = ref(false)
const changePasswordOpen = ref(false)
const changePasswordLoading = ref(false)
const changePasswordFormRef = ref()
const changePasswordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const isAdmin = computed(() => authStore.isAdmin)

const titleMap = {
  '/albums': '相册管理',
  '/media': '媒体管理',
  '/devices': '设备管理',
  '/distributions': '内容分发',
  '/admin/stats': '管理统计',
  '/admin/users': '用户管理',
  '/admin/reviews': '内容审核',
  '/admin/audit-logs': '审计日志'
}
const pageTitle = computed(() => {
  for (const [prefix, title] of Object.entries(titleMap)) {
    if (route.path.startsWith(prefix)) return title
  }
  return '智控云影'
})

const changePasswordRules = {
  oldPassword: [{ required: true, message: '请输入旧密码' }],
  newPassword: [
    { required: true, message: '请输入新密码' },
    { min: 6, message: '新密码至少6位' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码' },
    {
      validator: (_, value) => (
        value === changePasswordForm.newPassword
          ? Promise.resolve()
          : Promise.reject('两次输入的新密码不一致')
      )
    }
  ]
}

function onMenuClick({ key }) {
  router.push(key)
}

function resetChangePasswordForm() {
  Object.assign(changePasswordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
  changePasswordFormRef.value?.clearValidate()
}

function openChangePasswordModal() {
  resetChangePasswordForm()
  changePasswordOpen.value = true
}

async function submitChangePassword() {
  await changePasswordFormRef.value.validate()
  changePasswordLoading.value = true
  try {
    await authApi.changePassword({
      oldPassword: changePasswordForm.oldPassword,
      newPassword: changePasswordForm.newPassword
    })
    message.success('密码修改成功')
    changePasswordOpen.value = false
    resetChangePasswordForm()
  } finally {
    changePasswordLoading.value = false
  }
}

async function handleLogout() {
  await authStore.logout()
  message.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.logo {
  height: 64px;
  line-height: 64px;
  text-align: center;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  background: rgba(255,255,255,0.1);
  overflow: hidden;
  white-space: nowrap;
}
</style>
