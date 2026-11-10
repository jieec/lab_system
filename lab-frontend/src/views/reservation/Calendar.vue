<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">预约日历</h2>
        <div class="page-sub">按周查看实验室时段占用情况，绿色为已通过，橙色为待审批</div>
      </div>
      <div class="legend">
        <span><i class="dot approved"></i>已通过</span>
        <span><i class="dot pending"></i>待审批</span>
        <span><i class="dot finished"></i>已完成</span>
      </div>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="labId" placeholder="选择实验室" filterable style="width: 240px" @change="load">
          <el-option v-for="l in labs" :key="l.id" :label="`${l.name}（${l.code}）`" :value="l.id" />
        </el-select>
        <el-button-group>
          <el-button :icon="ArrowLeft" @click="shiftWeek(-1)">上一周</el-button>
          <el-button @click="thisWeek">本周</el-button>
          <el-button @click="shiftWeek(1)">下一周<el-icon class="el-icon--right"><ArrowRight /></el-icon></el-button>
        </el-button-group>
        <div class="week-label">{{ weekLabel }}</div>
        <div class="spacer"></div>
        <span class="muted" style="font-size: 12px">点击色块查看预约详情</span>
      </div>

      <div v-loading="loading" class="calendar">
        <div class="cal-head">
          <div class="time-col"></div>
          <div
            v-for="d in days"
            :key="d.date"
            class="day-head"
            :class="{ today: d.isToday }"
          >
            <div class="dw">{{ d.weekday }}</div>
            <div class="dd">{{ d.md }}</div>
          </div>
        </div>

        <div class="cal-body" :style="{ height: hours.length * ROW_H + 'px' }">
          <div class="time-axis">
            <div v-for="h in hours" :key="h" class="hour-label" :style="{ height: ROW_H + 'px' }">
              {{ String(h).padStart(2, '0') }}:00
            </div>
          </div>

          <div class="days">
            <div v-for="(d, i) in days" :key="d.date" class="day-col" :class="{ today: d.isToday }">
              <div v-for="h in hours" :key="h" class="hour-line" :style="{ height: ROW_H + 'px' }"></div>
              <div
                v-for="e in eventsByDay[i]"
                :key="e.id"
                class="event"
                :class="e.status === 'PENDING' ? 'pending' : e.status === 'FINISHED' ? 'finished' : 'approved'"
                :style="{ top: e.top + 'px', height: e.height + 'px' }"
                @click="openEvent(e)"
              >
                <div class="ev-time">{{ e.start }} - {{ e.end }}</div>
                <div class="ev-user">{{ e.userName }}</div>
                <div class="ev-purpose">{{ e.purpose }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="eventVisible" title="预约信息" width="460px">
      <div v-if="event" class="ev-detail">
        <div class="d-row"><span>预约人</span><b>{{ event.userName }}（{{ event.userNo }}）</b></div>
        <div class="d-row"><span>实验室</span><b>{{ event.labName }} · {{ event.building }} {{ event.room }}</b></div>
        <div class="d-row"><span>时段</span><b>{{ event.startTime }} 至 {{ event.endTime }}</b></div>
        <div class="d-row"><span>人数</span><b>{{ event.headcount }} 人</b></div>
        <div class="d-row"><span>用途</span><b>{{ event.purpose }}</b></div>
        <div class="d-row"><span>状态</span>
          <el-tag :type="typeOf(RESERVATION_STATUS, event.status)" size="small" effect="light">
            {{ labelOf(RESERVATION_STATUS, event.status) }}
          </el-tag>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { labApi, reservationApi } from '@/api'
import { RESERVATION_STATUS, labelOf, typeOf } from '@/utils/dict'

const ROW_H = 54
const HOUR_START = 8
const HOUR_END = 22
const hours = Array.from({ length: HOUR_END - HOUR_START }, (_, i) => HOUR_START + i)

const labs = ref([])
const labId = ref(null)
const loading = ref(false)
const eventsByDay = ref([[], [], [], [], [], [], []])
const eventVisible = ref(false)
const event = ref(null)

const weekStart = ref(startOfWeek(new Date()))

function startOfWeek(d) {
  const date = new Date(d)
  date.setHours(0, 0, 0, 0)
  const day = date.getDay() || 7
  date.setDate(date.getDate() - day + 1)
  return date
}

function fmt(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

const days = computed(() => {
  const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const todayStr = fmt(new Date())
  return weekdays.map((w, i) => {
    const d = new Date(weekStart.value)
    d.setDate(d.getDate() + i)
    return {
      date: fmt(d),
      weekday: w,
      md: `${d.getMonth() + 1}/${d.getDate()}`,
      isToday: fmt(d) === todayStr
    }
  })
})

const weekLabel = computed(() => {
  const s = weekStart.value
  const e = new Date(s)
  e.setDate(e.getDate() + 6)
  return `${s.getFullYear()} 年 ${s.getMonth() + 1} 月 ${s.getDate()} 日 - ${e.getMonth() + 1} 月 ${e.getDate()} 日`
})

function toMinutes(dt) {
  const t = (dt || '').slice(11, 16)
  const [h, m] = t.split(':').map(Number)
  return h * 60 + m
}

function buildEvents(list) {
  const minTop = HOUR_START * 60
  const maxBottom = HOUR_END * 60
  return list
    .map((r) => {
      const s = Math.max(toMinutes(r.startTime), minTop)
      const e = Math.min(toMinutes(r.endTime), maxBottom)
      if (e <= s) return null
      return {
        ...r,
        start: (r.startTime || '').slice(11, 16),
        end: (r.endTime || '').slice(11, 16),
        top: ((s - minTop) / 60) * ROW_H,
        height: Math.max(((e - s) / 60) * ROW_H - 4, 26)
      }
    })
    .filter(Boolean)
}

async function load() {
  if (!labId.value) return
  loading.value = true
  try {
    const results = await Promise.all(
      days.value.map((d) => reservationApi.timeline({ labId: labId.value, date: d.date }))
    )
    eventsByDay.value = results.map((res) => buildEvents(res.data || []))
  } finally {
    loading.value = false
  }
}

function shiftWeek(n) {
  const d = new Date(weekStart.value)
  d.setDate(d.getDate() + n * 7)
  weekStart.value = d
  load()
}

function thisWeek() {
  weekStart.value = startOfWeek(new Date())
  load()
}

function openEvent(e) {
  event.value = e
  eventVisible.value = true
}

onMounted(async () => {
  const res = await labApi.all()
  labs.value = res.data || []
  if (labs.value.length) {
    // 默认展示当天预约最密集的实验室，便于快速了解占用情况
    const sorted = [...labs.value].sort((a, b) => (b.todayCount || 0) - (a.todayCount || 0))
    labId.value = sorted[0].id
    load()
  }
})
</script>

<style scoped>
.legend {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #6b7280;
  align-items: center;
}

.legend span {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 9px;
  height: 9px;
  border-radius: 3px;
  display: inline-block;
}

.dot.approved { background: #12b886; }
.dot.pending { background: #f59f00; }
.dot.finished { background: #ced4da; }

.week-label {
  font-size: 13px;
  color: #4b5563;
  font-weight: 500;
}

.calendar {
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
}

.cal-head {
  display: flex;
  background: #f8fafd;
  border-bottom: 1px solid var(--border);
}

.time-col {
  width: 64px;
  flex: 0 0 64px;
}

.day-head {
  flex: 1;
  text-align: center;
  padding: 9px 0;
  border-left: 1px solid var(--border);
}

.day-head.today {
  background: #eef2ff;
}

.dw {
  font-size: 11.5px;
  color: #8b95a8;
}

.dd {
  font-size: 13.5px;
  font-weight: 600;
  margin-top: 3px;
}

.day-head.today .dd {
  color: var(--brand);
}

.cal-body {
  display: flex;
  position: relative;
  overflow-y: auto;
}

.time-axis {
  width: 64px;
  flex: 0 0 64px;
}

.hour-label {
  font-size: 11px;
  color: #a0a9ba;
  text-align: right;
  padding-right: 9px;
  border-top: 1px solid #f2f5fa;
  line-height: 1;
  padding-top: 3px;
}

.days {
  flex: 1;
  display: flex;
  position: relative;
}

.day-col {
  flex: 1;
  position: relative;
  border-left: 1px solid var(--border);
}

.day-col.today {
  background: #fbfcff;
}

.hour-line {
  border-top: 1px solid #f2f5fa;
}

.event {
  position: absolute;
  left: 4px;
  right: 4px;
  border-radius: 7px;
  padding: 5px 7px;
  font-size: 11px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
  border-left: 3px solid transparent;
}

.event:hover {
  transform: scale(1.02);
  box-shadow: 0 6px 16px rgba(31, 45, 90, 0.12);
  z-index: 3;
}

.event.approved {
  background: #e6fcf5;
  border-left-color: #12b886;
  color: #0b7285;
}

.event.pending {
  background: #fff9db;
  border-left-color: #f59f00;
  color: #a06a00;
}

.event.finished {
  background: #f4f6fa;
  border-left-color: #c3cad8;
  color: #6b7280;
}

.ev-time {
  font-weight: 600;
  font-size: 10.5px;
}

.ev-user {
  margin-top: 2px;
  font-size: 11px;
}

.ev-purpose {
  margin-top: 1px;
  font-size: 10.5px;
  opacity: 0.75;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.d-row {
  display: flex;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px dashed #f0f3f9;
  font-size: 13px;
}

.d-row:last-child { border-bottom: none; }

.d-row span {
  width: 70px;
  flex: 0 0 70px;
  color: var(--muted);
  font-size: 12.5px;
}

.d-row b { font-weight: 500; color: #374151; }
</style>
