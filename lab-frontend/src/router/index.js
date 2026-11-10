import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '数据看板', icon: 'DataLine' }
      },
      {
        path: 'labs',
        name: 'labs',
        component: () => import('@/views/lab/LabList.vue'),
        meta: { title: '实验室预约', icon: 'School' }
      },
      {
        path: 'calendar',
        name: 'calendar',
        component: () => import('@/views/reservation/Calendar.vue'),
        meta: { title: '预约日历', icon: 'Calendar' }
      },
      {
        path: 'my-reservations',
        name: 'myReservations',
        component: () => import('@/views/reservation/MyReservations.vue'),
        meta: { title: '我的预约', icon: 'Tickets' }
      },
      {
        path: 'approval',
        name: 'approval',
        component: () => import('@/views/reservation/Approval.vue'),
        meta: { title: '审批中心', icon: 'Stamp', roles: ['ADMIN', 'TEACHER'] }
      },
      {
        path: 'equipments',
        name: 'equipments',
        component: () => import('@/views/equipment/EquipmentList.vue'),
        meta: { title: '设备台账', icon: 'Cpu' }
      },
      {
        path: 'repairs',
        name: 'repairs',
        component: () => import('@/views/repair/RepairOrders.vue'),
        meta: { title: '报修工单', icon: 'Tools' }
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/user/UserList.vue'),
        meta: { title: '用户管理', icon: 'User', roles: ['ADMIN'] }
      },
      {
        path: 'notices',
        name: 'notices',
        component: () => import('@/views/notice/NoticeList.vue'),
        meta: { title: '通知公告', icon: 'Bell' }
      },
      {
        path: 'messages',
        name: 'messages',
        component: () => import('@/views/message/Messages.vue'),
        meta: { title: '站内信', icon: 'Message' }
      },
      {
        path: 'closures',
        name: 'closures',
        component: () => import('@/views/system/Closures.vue'),
        meta: { title: '闭馆安排', icon: 'Calendar', roles: ['ADMIN'] }
      },
      {
        path: 'logs',
        name: 'logs',
        component: () => import('@/views/system/OperationLogs.vue'),
        meta: { title: '操作审计日志', icon: 'Document', roles: ['ADMIN'] }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人中心', icon: 'Setting' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const store = useUserStore()
  if (to.meta.public) {
    return true
  }
  if (!store.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.roles && !to.meta.roles.includes(store.role)) {
    return { path: '/dashboard' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 实验室预约系统` : '高校实验室预约与设备运维系统'
})

export default router
