import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '@/services/http'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/index.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      children: [
        { path: '', name: 'Dashboard', component: () => import('@/views/dashboard/index.vue') },
        { path: 'categories', name: 'Categories', component: () => import('@/views/categories/index.vue') },
        { path: 'questions', name: 'Questions', component: () => import('@/views/questions/index.vue') },
        { path: 'imports', name: 'Imports', component: () => import('@/views/imports/index.vue') },
        { path: 'interviews', name: 'Interviews', component: () => import('@/views/interviews/index.vue') },
        { path: 'monitor', name: 'Monitor', component: () => import('@/views/monitor/index.vue') },
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (!to.meta.public && !getToken()) {
    return { name: 'Login' }
  }
})

export default router
