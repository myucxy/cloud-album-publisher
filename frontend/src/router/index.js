import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/login', component: () => import('@/views/auth/LoginView.vue'), meta: { guest: true } },
  { path: '/register', component: () => import('@/views/auth/RegisterView.vue'), meta: { guest: true } },
  {
    path: '/',
    component: () => import('@/views/layout/MainLayout.vue'),
    meta: { requiresAuth: true },
    redirect: '/albums',
    children: [
      { path: 'albums', component: () => import('@/views/album/AlbumListView.vue') },
      { path: 'albums/:id', component: () => import('@/views/album/AlbumDetailView.vue') },
      { path: 'media', component: () => import('@/views/media/MediaListView.vue') },
      { path: 'devices', component: () => import('@/views/device/DeviceListView.vue') },
      { path: 'distributions', component: () => import('@/views/distribution/DistributionListView.vue') },
      { path: 'admin/stats', component: () => import('@/views/admin/AdminStatsView.vue'), meta: { adminOnly: true } },
      { path: 'admin/users', component: () => import('@/views/admin/UserListView.vue'), meta: { adminOnly: true } },
      { path: 'admin/reviews', component: () => import('@/views/admin/ReviewListView.vue'), meta: { adminOnly: true } },
      { path: 'admin/audit-logs', component: () => import('@/views/admin/AuditLogListView.vue'), meta: { adminOnly: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) return '/login'
  if (to.meta.guest && auth.isLoggedIn) return '/'
  if (to.meta.adminOnly && !auth.isAdmin) return '/albums'
})

export default router
