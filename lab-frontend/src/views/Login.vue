<template>
  <div class="login-page">
    <div class="bg-orb orb-1"></div>
    <div class="bg-orb orb-2"></div>

    <div class="login-card">
      <div class="left">
        <div class="left-inner">
          <div class="logo-row">
            <div class="logo">
              <el-icon :size="22"><Reading /></el-icon>
            </div>
            <span>Lab Reservation Platform</span>
          </div>
          <h1>高校实验室<br />预约与设备运维系统</h1>
          <p class="desc">
            一站式完成实验室查询、在线预约、审批流转与设备报修运维，
            让实验资源的使用与维护都清晰可追溯。
          </p>

          <div class="feature-list">
            <div class="feature">
              <el-icon><Calendar /></el-icon>
              <div>
                <div class="f-title">在线预约</div>
                <div class="f-sub">时段冲突自动校验，审批状态实时可见</div>
              </div>
            </div>
            <div class="feature">
              <el-icon><Tools /></el-icon>
              <div>
                <div class="f-title">设备运维闭环</div>
                <div class="f-sub">报修 → 派单 → 维修 → 验收全程留痕</div>
              </div>
            </div>
            <div class="feature">
              <el-icon><DataLine /></el-icon>
              <div>
                <div class="f-title">数据看板</div>
                <div class="f-sub">使用率、故障分布、维修效率一屏掌握</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="right">
        <div class="form-wrap">
          <h2>账号登录</h2>
          <p class="form-sub">请使用学号 / 工号账号登录系统</p>

          <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="onSubmit">
            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="请输入账号" :prefix-icon="User" clearable />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                :prefix-icon="Lock"
                show-password
                @keyup.enter="onSubmit"
              />
            </el-form-item>
            <el-button type="primary" class="submit" :loading="loading" @click="onSubmit">
              登 录
            </el-button>
          </el-form>

          <div class="demo">
            <div class="demo-title">演示账号（密码均为 123456）</div>
            <div class="demo-list">
              <button v-for="a in accounts" :key="a.username" class="demo-btn" @click="fill(a)">
                <span class="dot" :style="{ background: a.color }"></span>
                {{ a.label }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '123456' })

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const accounts = [
  { username: 'admin', label: '管理员', color: '#4c6ef5' },
  { username: 't_wang', label: '教师', color: '#12b886' },
  { username: 's2021001', label: '学生', color: '#f59f00' },
  { username: 'r_zhang', label: '维修员', color: '#e8590c' }
]

function fill(a) {
  form.username = a.username
  form.password = '123456'
}

async function onSubmit() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const user = await store.login(form)
      ElMessage.success(`欢迎回来，${user.realName}`)
      router.replace(route.query.redirect || '/dashboard')
    } catch (e) {
      /* 错误提示已由拦截器处理 */
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at 20% 20%, #eef2ff 0%, #f6f8fd 45%, #eef1f8 100%);
  position: relative;
  overflow: hidden;
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.55;
}

.orb-1 {
  width: 420px;
  height: 420px;
  background: #c7d2fe;
  top: -120px;
  left: -80px;
}

.orb-2 {
  width: 360px;
  height: 360px;
  background: #dbeafe;
  bottom: -120px;
  right: -60px;
}

.login-card {
  position: relative;
  z-index: 2;
  width: 940px;
  max-width: 94vw;
  min-height: 560px;
  background: #fff;
  border-radius: 24px;
  box-shadow: 0 30px 80px rgba(31, 45, 90, 0.14);
  display: flex;
  overflow: hidden;
}

.left {
  width: 46%;
  background: linear-gradient(160deg, #1e2a4a 0%, #2c3e6b 55%, #3b5bdb 100%);
  color: #fff;
  padding: 44px 38px;
  position: relative;
}

.left::after {
  content: '';
  position: absolute;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
  right: -90px;
  bottom: -80px;
}

.left-inner {
  position: relative;
  z-index: 2;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.logo-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  letter-spacing: 1.2px;
  color: #b9c6e6;
  text-transform: uppercase;
}

.logo {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.14);
  display: flex;
  align-items: center;
  justify-content: center;
}

h1 {
  font-size: 27px;
  line-height: 1.45;
  font-weight: 600;
  margin: 30px 0 14px;
  letter-spacing: 0.5px;
}

.desc {
  font-size: 13px;
  line-height: 1.9;
  color: #b9c6e6;
  margin: 0 0 32px;
}

.feature-list {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feature {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.feature .el-icon {
  font-size: 17px;
  margin-top: 2px;
  color: #8fa7f0;
}

.f-title {
  font-size: 13.5px;
  font-weight: 500;
}

.f-sub {
  font-size: 11.5px;
  color: #93a2c4;
  margin-top: 3px;
}

.right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 44px 46px;
}

.form-wrap {
  width: 100%;
  max-width: 330px;
}

h2 {
  font-size: 22px;
  margin: 0 0 8px;
  font-weight: 600;
}

.form-sub {
  font-size: 12.5px;
  color: var(--muted);
  margin: 0 0 26px;
}

.submit {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 4px;
  margin-top: 6px;
  border-radius: 10px;
}

.demo {
  margin-top: 30px;
  padding-top: 18px;
  border-top: 1px dashed var(--border);
}

.demo-title {
  font-size: 11.5px;
  color: var(--muted);
  margin-bottom: 10px;
}

.demo-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.demo-btn {
  border: 1px solid var(--border);
  background: #fbfcfe;
  border-radius: 8px;
  padding: 6px 11px;
  font-size: 12px;
  color: #4b5563;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.18s;
}

.demo-btn:hover {
  border-color: #bcc9f3;
  background: #eef2ff;
  color: var(--brand);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

@media (max-width: 820px) {
  .left {
    display: none;
  }
  .right {
    padding: 36px 28px;
  }
}
</style>
