<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">{{ greeting }}，{{ store.displayName }}</h2>
        <div class="page-sub">实验室资源使用与设备运维实时概览</div>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新数据</el-button>
    </div>

    <div class="stat-grid">
      <div v-for="c in cards" :key="c.label" class="stat-card">
        <div class="stat-icon" :style="{ background: c.bg, color: c.color }">
          <el-icon :size="20"><component :is="c.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-value">
            {{ c.value }}<span class="unit">{{ c.unit }}</span>
          </div>
          <div class="stat-label">{{ c.label }}</div>
        </div>
      </div>
    </div>

    <div class="grid-row">
      <div class="panel span-16">
        <div class="panel-title">近 14 天预约申请趋势</div>
        <BaseChart :option="trendOption" height="272px" />
      </div>
      <div class="panel span-8">
        <div class="panel-title">设备状态分布</div>
        <BaseChart :option="equipmentOption" height="272px" />
      </div>
    </div>

    <div class="grid-row">
      <div class="panel span-14">
        <div class="panel-title">实验室预约热度 Top 8</div>
        <BaseChart :option="labOption" height="290px" />
      </div>
      <div class="panel span-10">
        <div class="panel-title">故障类型分布</div>
        <BaseChart :option="faultOption" height="290px" />
      </div>
    </div>

    <div class="grid-row">
      <div class="panel span-14">
        <div class="panel-title">最新公告</div>
        <div v-if="notices.length" class="notice-list">
          <div v-for="n in notices" :key="n.id" class="notice-item" @click="router.push('/notices')">
            <el-tag :type="typeOf(NOTICE_TYPE, n.type)" size="small" effect="light">
              {{ labelOf(NOTICE_TYPE, n.type) }}
            </el-tag>
            <div class="notice-title">{{ n.title }}</div>
            <div class="notice-time">{{ (n.createTime || '').slice(5, 16) }}</div>
          </div>
        </div>
        <el-empty v-else description="暂无公告" :image-size="70" />
      </div>

      <div class="panel span-10">
        <div class="panel-title">快捷入口</div>
        <div class="quick-grid">
          <div v-for="q in quickLinks" :key="q.path" class="quick-item" @click="router.push(q.path)">
            <el-icon :size="19"><component :is="q.icon" /></el-icon>
            <span>{{ q.label }}</span>
          </div>
        </div>
        <div class="tip-box">
          <el-icon><InfoFilled /></el-icon>
          <span>{{ tipText }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, InfoFilled } from '@element-plus/icons-vue'
import BaseChart from '@/components/BaseChart.vue'
import { noticeApi, statsApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { NOTICE_TYPE, labelOf, typeOf } from '@/utils/dict'

const router = useRouter()
const store = useUserStore()
const loading = ref(false)
const data = ref({})
const notices = ref([])

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 19) return '下午好'
  return '晚上好'
})

const cards = computed(() => {
  const d = data.value
  if (store.isAdmin || store.isTeacher) {
    return [
      { label: '开放实验室', value: d.labTotal ?? 0, unit: '间', icon: 'School', color: '#4c6ef5', bg: '#eef2ff' },
      { label: '设备总数', value: d.equipmentTotal ?? 0, unit: '台', icon: 'Cpu', color: '#12b886', bg: '#e6fcf5' },
      { label: '今日预约', value: d.todayReservation ?? 0, unit: '场', icon: 'Calendar', color: '#f59f00', bg: '#fff9db' },
      { label: '待审批预约', value: d.pendingApproval ?? 0, unit: '条', icon: 'Stamp', color: '#e8590c', bg: '#fff4e6' },
      { label: '待处理工单', value: d.pendingRepair ?? 0, unit: '单', icon: 'Tools', color: '#e64980', bg: '#fff0f6' }
    ]
  }
  if (store.isRepairer) {
    return [
      { label: '待派单', value: d.pendingRepair ?? 0, unit: '单', icon: 'Tools', color: '#e8590c', bg: '#fff4e6' },
      { label: '设备总数', value: d.equipmentTotal ?? 0, unit: '台', icon: 'Cpu', color: '#12b886', bg: '#e6fcf5' },
      { label: '维修中设备', value: d.equipmentRepair ?? 0, unit: '台', icon: 'Warning', color: '#f59f00', bg: '#fff9db' },
      { label: '开放实验室', value: d.labTotal ?? 0, unit: '间', icon: 'School', color: '#4c6ef5', bg: '#eef2ff' }
    ]
  }
  return [
    { label: '我的进行中预约', value: d.myReservation ?? 0, unit: '条', icon: 'Tickets', color: '#4c6ef5', bg: '#eef2ff' },
    { label: '我的待处理报修', value: d.myRepair ?? 0, unit: '单', icon: 'Tools', color: '#e8590c', bg: '#fff4e6' },
    { label: '今日预约', value: d.todayReservation ?? 0, unit: '场', icon: 'Calendar', color: '#f59f00', bg: '#fff9db' },
    { label: '开放实验室', value: d.labTotal ?? 0, unit: '间', icon: 'School', color: '#12b886', bg: '#e6fcf5' }
  ]
})

const quickLinks = computed(() => {
  const all = [
    { path: '/labs', label: '预约实验室', icon: 'School', roles: ['ADMIN', 'TEACHER', 'STUDENT'] },
    { path: '/my-reservations', label: '我的预约', icon: 'Tickets', roles: ['ADMIN', 'TEACHER', 'STUDENT'] },
    { path: '/approval', label: '审批中心', icon: 'Stamp', roles: ['ADMIN', 'TEACHER'] },
    { path: '/equipments', label: '设备台账', icon: 'Cpu', roles: ['ADMIN', 'TEACHER', 'REPAIRER', 'STUDENT'] },
    { path: '/repairs', label: '报修工单', icon: 'Tools', roles: ['ADMIN', 'TEACHER', 'REPAIRER', 'STUDENT'] },
    { path: '/notices', label: '通知公告', icon: 'Bell', roles: ['ADMIN', 'TEACHER', 'REPAIRER', 'STUDENT'] }
  ]
  return all.filter((i) => i.roles.includes(store.role))
})

const tipText = computed(() => {
  if (store.isRepairer) return '收到派单后请及时开始维修，完成后填写处理说明提交验收。'
  if (store.canApprove) return '待审批预约超过 24 小时未处理会提示申请人，请及时审批。'
  return '预约开始前 30 分钟可取消，请按时到场并遵守实验室管理规定。'
})

const trendOption = computed(() => {
  const list = data.value.trend || []
  return {
    grid: { left: 38, right: 18, top: 24, bottom: 30 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: list.map((i) => i.label),
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#e5e9f2' } },
      axisLabel: { color: '#8b95a8', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f0f3f9' } },
      axisLabel: { color: '#8b95a8', fontSize: 11 }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: list.map((i) => i.value),
        lineStyle: { width: 3, color: '#4c6ef5' },
        itemStyle: { color: '#4c6ef5' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(76,110,245,0.28)' },
              { offset: 1, color: 'rgba(76,110,245,0.01)' }
            ]
          }
        }
      }
    ]
  }
})

const equipmentOption = computed(() => {
  const map = { IDLE: '空闲', IN_USE: '使用中', REPAIR: '维修中', SCRAPPED: '已报废' }
  const colors = { IDLE: '#12b886', IN_USE: '#4c6ef5', REPAIR: '#f59f00', SCRAPPED: '#adb5bd' }
  const list = data.value.equipmentStatus || []
  return {
    tooltip: { trigger: 'item', formatter: '{b}：{c} 台（{d}%）' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 11, color: '#6b7280' } },
    series: [
      {
        type: 'pie',
        radius: ['48%', '70%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 6 },
        label: { show: false },
        data: list.map((i) => ({
          name: map[i.label] || i.label,
          value: i.value,
          itemStyle: { color: colors[i.label] || '#94a3b8' }
        }))
      }
    ]
  }
})

const labOption = computed(() => {
  const list = [...(data.value.labUsage || [])].reverse()
  return {
    grid: { left: 8, right: 34, top: 10, bottom: 10, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f0f3f9' } },
      axisLabel: { color: '#8b95a8', fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      data: list.map((i) => i.label),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#5b6577', fontSize: 11.5 }
    },
    series: [
      {
        type: 'bar',
        barWidth: 13,
        data: list.map((i) => i.value),
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 1, y2: 0,
            colorStops: [
              { offset: 0, color: '#93a7ec' },
              { offset: 1, color: '#4c6ef5' }
            ]
          }
        },
        label: { show: true, position: 'right', color: '#8b95a8', fontSize: 11 }
      }
    ]
  }
})

const faultOption = computed(() => {
  const palette = ['#4c6ef5', '#22b8cf', '#f59f00', '#e64980']
  const list = data.value.faultType || []
  return {
    tooltip: { trigger: 'item', formatter: '{b}：{c} 单（{d}%）' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 11, color: '#6b7280' } },
    series: [
      {
        type: 'pie',
        radius: '62%',
        center: ['50%', '44%'],
        itemStyle: { borderColor: '#fff', borderWidth: 3 },
        label: { show: true, fontSize: 11, color: '#6b7280', formatter: '{b}\n{c} 单' },
        data: list.map((i, idx) => ({
          name: i.label,
          value: i.value,
          itemStyle: { color: palette[idx % palette.length] }
        }))
      }
    ]
  }
})

async function load() {
  loading.value = true
  try {
    const [res, nres] = await Promise.all([statsApi.dashboard(), noticeApi.latest(5)])
    data.value = res.data || {}
    notices.value = nres.data || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(31, 45, 90, 0.07);
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 44px;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: -0.5px;
}

.unit {
  font-size: 12px;
  color: var(--muted);
  font-weight: 400;
  margin-left: 3px;
}

.stat-label {
  font-size: 12px;
  color: var(--muted);
  margin-top: 6px;
}

.grid-row {
  display: grid;
  grid-template-columns: repeat(24, 1fr);
  gap: 14px;
  margin-bottom: 14px;
}

.span-16 { grid-column: span 16; }
.span-14 { grid-column: span 14; }
.span-10 { grid-column: span 10; }
.span-8 { grid-column: span 8; }

@media (max-width: 1200px) {
  .span-16, .span-14, .span-10, .span-8 {
    grid-column: span 24;
  }
}

.notice-list {
  display: flex;
  flex-direction: column;
}

.notice-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 6px;
  border-bottom: 1px dashed #eef1f7;
  cursor: pointer;
  transition: background 0.15s;
}

.notice-item:last-child {
  border-bottom: none;
}

.notice-item:hover {
  background: #f8fafd;
}

.notice-title {
  flex: 1;
  font-size: 13px;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-time {
  font-size: 11.5px;
  color: #9aa4b8;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 13px 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
  transition: all 0.18s;
}

.quick-item:hover {
  border-color: #bcc9f3;
  background: #eef2ff;
  color: var(--brand);
  transform: translateY(-1px);
}

.tip-box {
  margin-top: 14px;
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: 12px;
  line-height: 1.7;
  color: #6b7280;
  background: #f8fafd;
  border-radius: 10px;
  padding: 11px 12px;
}

.tip-box .el-icon {
  color: var(--brand);
  margin-top: 2px;
}
</style>
