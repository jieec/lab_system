<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">站内信</h2>
        <div class="page-sub">审批结果、工单派发、签到与信用分变动都会在这里通知你</div>
      </div>
      <el-button :disabled="!unread" @click="readAll">
        <el-icon><Reading /></el-icon>
        <span style="margin-left: 6px">全部标为已读</span>
      </el-button>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-radio-group v-model="isRead" @change="load(1)">
          <el-radio-button value="">全部消息</el-radio-button>
          <el-radio-button value="0">未读（{{ unread }}）</el-radio-button>
          <el-radio-button value="1">已读</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" @click="load()">刷新</el-button>
      </div>

      <div v-loading="loading" class="msg-list">
        <div v-for="m in list" :key="m.id" class="msg-card" :class="{ unread: !m.isRead }" @click="open(m)">
          <div class="msg-dot" :class="typeClass(m.type)"></div>
          <div class="msg-main">
            <div class="msg-title">
              <span>{{ m.title }}</span>
              <el-tag v-if="!m.isRead" type="danger" size="small" effect="light">未读</el-tag>
              <el-tag size="small" effect="plain" type="info">{{ typeLabel(m.type) }}</el-tag>
            </div>
            <div class="msg-content">{{ m.content }}</div>
            <div class="msg-time">{{ m.createTime }}</div>
          </div>
        </div>
        <el-empty v-if="!loading && !list.length" description="暂无消息" />
      </div>

      <div class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { messageApi } from '@/api'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const unread = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const isRead = ref('')

const TYPE_LABEL = {
  RESERVATION: '预约',
  REPAIR: '报修',
  CREDIT: '信用分',
  SYSTEM: '系统'
}
const TYPE_CLASS = {
  RESERVATION: 't-blue',
  REPAIR: 't-orange',
  CREDIT: 't-red',
  SYSTEM: 't-gray'
}
const typeLabel = (t) => TYPE_LABEL[t] || '消息'
const typeClass = (t) => TYPE_CLASS[t] || 't-gray'

async function loadUnread() {
  try {
    const res = await messageApi.unreadCount()
    unread.value = res.data || 0
  } catch (e) { /* 忽略 */ }
}

async function load(page) {
  if (page) pageNum.value = page
  loading.value = true
  try {
    const res = await messageApi.page({ pageNum: pageNum.value, pageSize: pageSize.value, isRead: isRead.value })
    list.value = res.data.records || []
    total.value = res.data.total || 0
    await loadUnread()
  } finally {
    loading.value = false
  }
}

async function open(m) {
  if (!m.isRead) {
    await messageApi.markRead(m.id)
    m.isRead = 1
    await loadUnread()
  }
}

async function readAll() {
  await messageApi.markAllRead()
  ElMessage.success('已全部标为已读')
  load()
}

onMounted(() => load())
</script>

<style scoped>
.msg-list {
  min-height: 220px;
}

.msg-card {
  display: flex;
  gap: 14px;
  padding: 16px 14px;
  border-bottom: 1px solid #f0f3f9;
  cursor: pointer;
  transition: background 0.2s;
  border-radius: 8px;
}

.msg-card:hover {
  background: #f7f9fd;
}

.msg-card.unread {
  background: #f5f8ff;
}

.msg-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 7px;
  flex: 0 0 8px;
  background: #c2c9d6;
}

.msg-dot.t-blue { background: #4c6ef5; }
.msg-dot.t-orange { background: #f59f00; }
.msg-dot.t-red { background: #e64980; }
.msg-dot.t-gray { background: #9aa4b8; }

.msg-main {
  flex: 1;
  min-width: 0;
}

.msg-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.msg-content {
  font-size: 12.5px;
  color: #5b6478;
  margin-top: 7px;
  line-height: 1.6;
}

.msg-time {
  font-size: 11.5px;
  color: #a3abbb;
  margin-top: 7px;
}
</style>
