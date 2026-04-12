<template>
  <div class="auth-wrapper">
    <a-card title="注册智控云影" style="width: 420px">
      <a-form :model="form" :rules="rules" ref="formRef" @finish="onSubmit" layout="vertical">
        <a-form-item label="用户名" name="username">
          <a-input v-model:value="form.username" placeholder="4-20位字母数字" size="large" />
        </a-form-item>
        <a-form-item label="邮箱" name="email">
          <a-input v-model:value="form.email" placeholder="your@email.com" size="large" />
        </a-form-item>
        <a-form-item label="昵称" name="nickname">
          <a-input v-model:value="form.nickname" placeholder="可选" size="large" />
        </a-form-item>
        <a-form-item label="密码" name="password">
          <a-input-password v-model:value="form.password" placeholder="8位以上" size="large" />
        </a-form-item>
        <a-form-item label="确认密码" name="confirm">
          <a-input-password v-model:value="form.confirm" placeholder="再次输入密码" size="large" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" block size="large" :loading="loading">
            注册
          </a-button>
        </a-form-item>
        <div style="text-align:center">
          已有账号？<router-link to="/login">立即登录</router-link>
        </div>
      </a-form>
    </a-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', email: '', nickname: '', password: '', confirm: '' })

const rules = {
  username: [
    { required: true, message: '请输入用户名' },
    { min: 4, max: 20, message: '4-20位' }
  ],
  email: [
    { required: true, message: '请输入邮箱' },
    { type: 'email', message: '邮箱格式不正确' }
  ],
  password: [
    { required: true, message: '请输入密码' },
    { min: 8, message: '密码至少8位' }
  ],
  confirm: [
    { required: true, message: '请确认密码' },
    {
      validator: (_, value) =>
        value === form.password ? Promise.resolve() : Promise.reject('两次密码不一致')
    }
  ]
}

async function onSubmit() {
  loading.value = true
  try {
    await authStore.register({ username: form.username, email: form.email, nickname: form.nickname, password: form.password })
    message.success('注册成功')
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}
</style>
