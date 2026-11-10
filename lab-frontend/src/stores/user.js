import { defineStore } from 'pinia'
import { authApi } from '@/api'

const ROLE_LABEL = {
  ADMIN: '系统管理员',
  TEACHER: '教师',
  STUDENT: '学生',
  REPAIRER: '维修员'
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null')
  }),
  getters: {
    role: (state) => state.user?.role || '',
    roleLabel: (state) => ROLE_LABEL[state.user?.role] || '访客',
    isAdmin: (state) => state.user?.role === 'ADMIN',
    isTeacher: (state) => state.user?.role === 'TEACHER',
    isRepairer: (state) => state.user?.role === 'REPAIRER',
    canApprove: (state) => ['ADMIN', 'TEACHER'].includes(state.user?.role),
    displayName: (state) => state.user?.realName || state.user?.username || ''
  },
  actions: {
    async login(form) {
      const res = await authApi.login(form)
      this.token = res.data.token
      this.user = res.data.user
      localStorage.setItem('token', this.token)
      localStorage.setItem('user', JSON.stringify(this.user))
      return res.data.user
    },
    async refreshProfile() {
      const res = await authApi.profile()
      this.user = res.data
      localStorage.setItem('user', JSON.stringify(this.user))
      return this.user
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
