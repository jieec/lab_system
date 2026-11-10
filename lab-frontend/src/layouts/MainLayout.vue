<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-logo">
          <el-icon><Reading /></el-icon>
        </div>
        <div class="brand-text">
          <div class="brand-title">实验室预约与运维</div>
          <div class="brand-sub">Laboratory Platform</div>
        </div>
      </div>

      <el-scrollbar class="menu-scroll">
        <el-menu
          :default-active="route.path"
          router
          class="menu"
          background-color="transparent"
          text-color="#a9b4c9"
          active-text-color="#ffffff"
        >
          <template v-for="group in visibleGroups" :key="group.title">
            <div class="menu-group-title">{{ group.title }}</div>
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
              <el-badge
                v-if="item.path === '/approval' && stats.pendingApproval"
                :value="stats.pendingApproval"
                class="menu-badge"
                type="danger"
              />
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>

      <div class="sidebar-foot">
        <div class="foot-line">当前身份</div>
        <div class="foot-role">{{ store.roleLabel }}</div>
      </div>
    </aside>

    <div class="main">
      <header class="header">
        <div class="header-left">
          <div class="header-title">{{ route.meta.title }}</div>
          <div class="header-date">{{ today }}</div>
        </div>
        <div class="header-right">
          <el-tooltip content="站内信" placement="bottom">
            <el-badge :value="unread" :hidden="!unread" :max="99" class="bell-badge">
              <el-button circle text @click="router.push('/messages')">
                <el-icon :size="18"><Message /></el-icon>
              </el-button>
            </el-badge>
          </el-tooltip>
          <el-tooltip content="通知公告" placement="bottom">
            <el-button circle text @click="router.push('/notices')">
              <el-icon :size="18"><Bell /></el-icon>
            </el-button>
          </el-tooltip>
          <el-dropdown trigger="click" @command="onCommand">
            <div class="user-chip">
              <div class="avatar">{{ store.displayName.slice(0, 1) }}</div>
              <div class="user-meta">
                <div class="user-name">{{ store.displayName }}</div>
                <div class="user-role">{{ store.roleLabel }}</div>
              </div>
              <el-icon class="caret"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><Setting /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Message } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { messageApi, statsApi } from '@/api'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const stats = ref({})
const unread = ref(0)
let unreadTimer = null

async function loadUnread() {
  try {
    const res = await messageApi.unreadCount()
    unread.value = res.data || 0
  } catch (e) {
    /* 未登录或接口异常时静默，不影响主界面 */
  }
}

const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long'
})

const groups = [
  {
    title: '概览',
    items: [{ path: '/dashboard', title: '数据看板', icon: 'DataLine' }]
  },
  {
    title: '预约管理',
    items: [
      { path: '/labs', title: '实验室预约', icon: 'School' },
      { path: '/calendar', title: '预约日历', icon: 'Calendar' },
      { path: '/my-reservations', title: '我的预约', icon: 'Tickets' },
      { path: '/approval', title: '审批中心', icon: 'Stamp', roles: ['ADMIN', 'TEACHER'] }
    ]
  },
  {
    title: '设备运维',
    items: [
      { path: '/equipments', title: '设备台账', icon: 'Cpu' },
      { path: '/repairs', title: '报修工单', icon: 'Tools' }
    ]
  },
  {
    title: '系统',
    items: [
      { path: '/messages', title: '站内信', icon: 'Message' },
      { path: '/notices', title: '通知公告', icon: 'Bell' },
      { path: '/users', title: '用户管理', icon: 'User', roles: ['ADMIN'] },
      { path: '/closures', title: '闭馆安排', icon: 'Calendar', roles: ['ADMIN'] },
      { path: '/logs', title: '操作审计日志', icon: 'Document', roles: ['ADMIN'] },
      { path: '/profile', title: '个人中心', icon: 'Setting' }
    ]
  }
]

const visibleGroups = computed(() =>
  groups
    .map((g) => ({
      ...g,
      items: g.items.filter((i) => !i.roles || i.roles.includes(store.role))
    }))
    .filter((g) => g.items.length)
)

function onCommand(cmd) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
      .then(() => {
        store.logout()
        ElMessage.success('已退出登录')
        router.push('/login')
      })
      .catch(() => {})
  }
}

// 路由变化后刷新角标：在站内信页标记已读时，返回其他页面能立刻看到最新数量
watch(() => route.path, loadUnread)

onMounted(async () => {
  loadUnread()
  unreadTimer = setInterval(loadUnread, 60 * 1000)
  try {
    const res = await statsApi.dashboard()
    stats.value = res.data
  } catch (e) {
    /* 忽略看板预加载失败 */
  }
})

onUnmounted(() => {
  if (unreadTimer) clearInterval(unreadTimer)
})
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 232px;
  flex: 0 0 232px;
  background: linear-gradient(180deg, #1b2233 0%, #232d45 100%);
  display: flex;
  flex-direction: column;
  color: #fff;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 18px 18px;
}

.brand-logo {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: linear-gradient(135deg, #4c6ef5, #7c93ff);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  box-shadow: 0 6px 16px rgba(76, 110, 245, 0.4);
}

.brand-title {
  font-size: 14.5px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.brand-sub {
  font-size: 10.5px;
  color: #7c8aa5;
  letter-spacing: 0.6px;
  margin-top: 2px;
  text-transform: uppercase;
}

.menu-scroll {
  flex: 1;
  overflow: hidden;
}

.menu {
  border-right: none;
  padding: 4px 10px 12px;
}

.menu-group-title {
  font-size: 11px;
  color: #63708a;
  padding: 14px 12px 6px;
  letter-spacing: 1px;
}

.menu :deep(.el-menu-item) {
  height: 42px;
  line-height: 42px;
  border-radius: 9px;
  margin-bottom: 2px;
  font-size: 13.5px;
}

.menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.06) !important;
  color: #fff;
}

.menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, #4c6ef5, #5f7cf7) !important;
  box-shadow: 0 6px 16px rgba(76, 110, 245, 0.35);
}

.menu-badge {
  margin-left: 6px;
  transform: translateY(-2px);
}

.sidebar-foot {
  padding: 14px 20px 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
}

.foot-line {
  font-size: 11px;
  color: #63708a;
}

.foot-role {
  font-size: 13px;
  color: #cfd8ea;
  margin-top: 4px;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.header {
  height: 64px;
  flex: 0 0 64px;
  background: #fff;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 26px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.header-date {
  font-size: 12px;
  color: var(--muted);
  margin-top: 3px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.bell-badge :deep(.el-badge__content) {
  top: 4px;
  right: 10px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 10px 5px 6px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s;
}

.user-chip:hover {
  background: #f4f6fb;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: linear-gradient(135deg, #4c6ef5, #7c93ff);
  color: #fff;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.2;
}

.user-role {
  font-size: 11px;
  color: var(--muted);
  margin-top: 2px;
}

.caret {
  color: #9aa4b8;
  font-size: 12px;
}

.content {
  flex: 1;
  overflow-y: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
