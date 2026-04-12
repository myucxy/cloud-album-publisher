import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: () => (localStorage.getItem('device_access_token') ? '/player' : '/setup')
  },
  {
    path: '/setup',
    component: () => import('@/views/SetupView.vue')
  },
  {
    path: '/activate',
    component: () => import('@/views/LoginBindView.vue'),
    meta: { guestOnly: true }
  },
  {
    path: '/player',
    component: () => import('@/views/PlayerView.vue'),
    meta: { requiresDeviceToken: true }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const hasDeviceToken = Boolean(localStorage.getItem('device_access_token'))

  if (to.meta.requiresDeviceToken && !hasDeviceToken) {
    return '/setup'
  }

  if (to.meta.guestOnly && hasDeviceToken) {
    return '/player'
  }
})

export default router
