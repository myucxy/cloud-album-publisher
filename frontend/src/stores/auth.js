import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(localStorage.getItem('access_token') || '')
  const userId = ref(localStorage.getItem('user_id') || '')
  const username = ref(localStorage.getItem('username') || '')
  const roles = ref(JSON.parse(localStorage.getItem('roles') || '[]'))

  const isLoggedIn = computed(() => !!accessToken.value)
  const isAdmin = computed(() => roles.value.includes('ROLE_ADMIN'))

  async function login(data) {
    const res = await authApi.login(data)
    setTokens(res.data)
  }

  async function register(data) {
    const res = await authApi.register(data)
    setTokens(res.data)
  }

  function setTokens(data) {
    accessToken.value = data.accessToken
    userId.value = String(data.userId)
    username.value = data.username
    roles.value = data.roles || []
    localStorage.setItem('access_token', data.accessToken)
    localStorage.setItem('refresh_token', data.refreshToken)
    localStorage.setItem('user_id', data.userId)
    localStorage.setItem('username', data.username)
    localStorage.setItem('roles', JSON.stringify(data.roles || []))
  }

  async function logout() {
    try { await authApi.logout() } catch { /* ignore */ }
    accessToken.value = ''
    userId.value = ''
    username.value = ''
    roles.value = []
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
    localStorage.removeItem('user_id')
    localStorage.removeItem('username')
    localStorage.removeItem('roles')
  }

  return { accessToken, userId, username, roles, isLoggedIn, isAdmin, login, register, logout }
})
