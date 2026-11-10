<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">我的预约</h2>
        <div class="page-sub">查看预约状态，支持签到、改期与开始前自助取消</div>
      </div>
      <div class="head-right">
        <el-tag v-if="credit !== null" :type="credit < 60 ? 'danger' : 'success'" effect="light" size="large">
          信用分 {{ credit }} · 累计爽约 {{ violation || 0 }} 次
        </el-tag>
        <el-button type="primary" :icon="Plus" @click="router.push('/labs')">去预约</el-button>
      </div>
    </div>

    <el-alert
      v-if="credit !== null && credit < 60"
      type="error"
      show-icon
      :closable="false"
      title="信用分低于 60，暂时无法提交新预约"
      description="按时签到可逐步恢复信用分（每次 +2），请联系管理员处理异常记录。"
      style="margin-bottom: 16px"
    />

    <div class="panel">
      <div class="toolbar">
        <el-radio-group v-model="status" @change="load">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="PENDING">待审批</el-radio-button>
          <el-radio-button value="APPROVED">已通过</el-radio-button>
          <el-radio-button value="FINISHED">已完成</el-radio-button>
          <el-radio-button value="REJECTED">已驳回</el-radio-button>
          <el-radio-button value="CANCELLED">已取消</el-radio-button>
        </el-radio-group>
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="orderNo" label="预约单号" width="150">
          <template #default="{ row }">
            <span class="mono">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="实验室" min-width="180">
          <template #default="{ row }">
            <div class="cell-strong">{{ row.labName }}</div>
            <div class="cell-sub">{{ row.building }} {{ row.room }}</div>
          </template>
        </el-table-column>
        <el-table-column label="预约时段" min-width="210">
          <template #default="{ row }">
            <div class="cell-strong">{{ (row.startTime || '').slice(0, 16) }}</div>
            <div class="cell-sub">至 {{ (row.endTime || '').slice(5, 16) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="headcount" label="人数" width="70" align="center" />
        <el-table-column prop="purpose" label="用途" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="typeOf(RESERVATION_STATUS, row.status)" size="small" effect="light">
              {{ labelOf(RESERVATION_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="签到" width="100">
          <template #default="{ row }">
            <el-tag :type="typeOf(CHECKIN_STATUS, row.checkinStatus || 'NONE')" size="small" effect="plain">
              {{ labelOf(CHECKIN_STATUS, row.checkinStatus || 'NONE') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="canCheckin(row)"
              size="small"
              text
              type="success"
              :disabled="!inCheckinWindow(row)"
              :title="inCheckinWindow(row) ? '签到后信用分 +2' : '开始前 15 分钟开放签到'"
              @click="checkin(row)"
            >
              签到
            </el-button>
            <el-button
              v-if="canReschedule(row)"
              size="small"
              text
              type="primary"
              @click="openReschedule(row)"
            >
              改期
            </el-button>
            <el-button
              v-if="['PENDING', 'APPROVED'].includes(row.status)"
              size="small"
              text
              type="danger"
              @click="cancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>

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

    <el-dialog v-model="detailVisible" title="预约详情" width="520px">
      <div v-if="detail" class="detail">
        <div class="d-row"><span>预约单号</span><b class="mono">{{ detail.orderNo }}</b></div>
        <div class="d-row"><span>实验室</span><b>{{ detail.labName }}（{{ detail.building }} {{ detail.room }}）</b></div>
        <div class="d-row"><span>预约时段</span><b>{{ detail.startTime }} 至 {{ detail.endTime }}</b></div>
        <div class="d-row"><span>使用人数</span><b>{{ detail.headcount }} 人</b></div>
        <div class="d-row"><span>使用用途</span><b>{{ detail.purpose }}</b></div>
        <div class="d-row"><span>当前状态</span>
          <el-tag :type="typeOf(RESERVATION_STATUS, detail.status)" size="small" effect="light">
            {{ labelOf(RESERVATION_STATUS, detail.status) }}
          </el-tag>
        </div>
        <div class="d-row"><span>审批人</span><b>{{ detail.approverName || '-' }}</b></div>
        <div class="d-row"><span>审批时间</span><b>{{ detail.approveTime || '-' }}</b></div>
        <div class="d-row"><span>审批意见</span><b>{{ detail.approveRemark || '-' }}</b></div>
        <div class="d-row"><span>签到状态</span>
          <el-tag :type="typeOf(CHECKIN_STATUS, detail.checkinStatus || 'NONE')" size="small" effect="plain">
            {{ labelOf(CHECKIN_STATUS, detail.checkinStatus || 'NONE') }}
          </el-tag>
        </div>
        <div class="d-row"><span>签到时间</span><b>{{ detail.checkinTime || '-' }}</b></div>
        <div class="d-row"><span>提交时间</span><b>{{ detail.createTime }}</b></div>
      </div>
    </el-dialog>

    <el-dialog v-model="rescheduleVisible" title="申请改期" width="480px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="改期后需重新审批"
        description="系统会重新检查时段冲突与闭馆安排；需审批的实验室将回到「待审批」状态。"
        style="margin-bottom: 18px"
      />
      <el-form label-width="90px">
        <el-form-item label="新开始时间">
          <el-date-picker
            v-model="rescheduleForm.startTime"
            type="datetime"
            placeholder="选择开始时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="新结束时间">
          <el-date-picker
            v-model="rescheduleForm.endTime"
            type="datetime"
            placeholder="选择结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="改期原因">
          <el-input v-model="rescheduleForm.reason" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="选填，便于审批人了解情况" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rescheduleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReschedule">提交改期</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { reservationApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { CHECKIN_STATUS, RESERVATION_STATUS, labelOf, typeOf } from '@/utils/dict'

const router = useRouter()
const store = useUserStore()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const status = ref('')
const detail = ref(null)
const detailVisible = ref(false)
const credit = ref(null)
const violation = ref(0)

const rescheduleVisible = ref(false)
const submitting = ref(false)
const rescheduleRow = ref(null)
const rescheduleForm = reactive({ startTime: '', endTime: '', reason: '' })

async function load() {
  loading.value = true
  try {
    const res = await reservationApi.my({ pageNum: pageNum.value, pageSize: pageSize.value, status: status.value })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadCredit() {
  try {
    const user = await store.refreshProfile()
    credit.value = user.creditScore ?? 100
    violation.value = user.violationCount ?? 0
  } catch (e) {
    /* 信用分为增强展示，获取失败不影响预约列表 */
  }
}

/** 后端时间格式 yyyy-MM-dd HH:mm:ss，转成可比较的时间戳 */
function parseTime(s) {
  return s ? new Date(String(s).replace(' ', 'T')).getTime() : NaN
}

function canCheckin(row) {
  return row.status === 'APPROVED' && (row.checkinStatus || 'NONE') === 'NONE'
}

function inCheckinWindow(row) {
  const now = Date.now()
  const start = parseTime(row.startTime)
  const end = parseTime(row.endTime)
  return Number.isFinite(start) && Number.isFinite(end) && now >= start - 15 * 60 * 1000 && now <= end
}

function canReschedule(row) {
  return row.status === 'APPROVED' && parseTime(row.startTime) > Date.now()
}

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

async function checkin(row) {
  try {
    await reservationApi.checkin(row.id)
    ElMessage.success('签到成功，信用分 +2')
    load()
    loadCredit()
  } catch (e) {
    /* 错误提示由请求拦截器统一处理 */
  }
}

function openReschedule(row) {
  rescheduleRow.value = row
  rescheduleForm.startTime = row.startTime
  rescheduleForm.endTime = row.endTime
  rescheduleForm.reason = ''
  rescheduleVisible.value = true
}

async function submitReschedule() {
  if (!rescheduleForm.startTime || !rescheduleForm.endTime) {
    ElMessage.warning('请选择新的起止时间')
    return
  }
  if (parseTime(rescheduleForm.endTime) <= parseTime(rescheduleForm.startTime)) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }
  submitting.value = true
  try {
    await reservationApi.reschedule(rescheduleRow.value.id, { ...rescheduleForm })
    ElMessage.success('改期申请已提交')
    rescheduleVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

function cancel(row) {
  ElMessageBox.confirm(`确定取消「${row.labName}」的这次预约吗？`, '取消预约', { type: 'warning' })
    .then(async () => {
      await reservationApi.cancel(row.id)
      ElMessage.success('已取消')
      load()
    })
    .catch(() => {})
}

onMounted(() => {
  load()
  loadCredit()
})
</script>

<style scoped>
.head-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.cell-strong {
  font-size: 13px;
  color: #1f2937;
}

.cell-sub {
  font-size: 11.5px;
  color: #9aa4b8;
  margin-top: 3px;
}

.d-row {
  display: flex;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px dashed #f0f3f9;
  font-size: 13px;
}

.d-row:last-child {
  border-bottom: none;
}

.d-row span {
  width: 78px;
  color: var(--muted);
  flex: 0 0 78px;
  font-size: 12.5px;
}

.d-row b {
  font-weight: 500;
  color: #374151;
}
</style>
