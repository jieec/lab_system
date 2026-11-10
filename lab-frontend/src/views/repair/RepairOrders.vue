<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">报修工单</h2>
        <div class="page-sub">报修 → 派单 → 维修 → 验收，全流程可追溯</div>
      </div>
      <el-button type="warning" :icon="Tools" @click="router.push('/equipments')">去设备台账报修</el-button>
    </div>

    <div class="panel">
      <el-tabs v-model="scope" @tab-change="onTabChange">
        <el-tab-pane v-if="store.isAdmin || store.isTeacher" label="全部工单" name="all" />
        <el-tab-pane label="我的报修" name="mine" />
        <el-tab-pane v-if="store.isAdmin || store.isRepairer" label="维修任务" name="assigned" />
      </el-tabs>

      <div class="toolbar">
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 150px" @change="load">
          <el-option v-for="(v, k) in REPAIR_STATUS" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索工单号 / 故障描述"
          :prefix-icon="Search"
          clearable
          style="width: 240px"
          @keyup.enter="load"
          @clear="load"
        />
        <el-button :icon="Search" @click="load">查询</el-button>
        <div class="spacer"></div>
        <div class="flow-tip">
          <span v-for="(s, i) in flow" :key="s" class="flow-step">
            {{ s }}<i v-if="i < flow.length - 1" class="arrow">→</i>
          </span>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="orderNo" label="工单号" width="130">
          <template #default="{ row }"><span class="mono">{{ row.orderNo }}</span></template>
        </el-table-column>
        <el-table-column label="设备" width="160">
          <template #default="{ row }">
            <div class="cell-strong">{{ row.equipmentName }}</div>
            <div class="cell-sub">{{ row.equipmentCode }} · {{ row.labName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="故障" width="178">
          <template #default="{ row }">
            <div class="fault-line">
              <el-tag size="small" effect="plain" type="info">{{ row.faultType }}</el-tag>
              <el-tag size="small" effect="light" :type="typeOf(URGENCY, row.urgency)">
                {{ labelOf(URGENCY, row.urgency) }}
              </el-tag>
            </div>
            <div class="cell-sub ellipsis">{{ row.faultDesc }}</div>
          </template>
        </el-table-column>
        <el-table-column label="报修人" width="100">
          <template #default="{ row }">{{ row.reporterName }}</template>
        </el-table-column>
        <el-table-column label="维修员" width="100">
          <template #default="{ row }">
            <span :class="{ muted: !row.repairerName }">{{ row.repairerName || '未派单' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="typeOf(REPAIR_STATUS, row.status)" size="small" effect="light">
              {{ labelOf(REPAIR_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="110">
          <template #default="{ row }">
            <span class="cell-sub">{{ (row.createTime || '').slice(5, 16) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="196" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canAssign(row)" size="small" type="primary" plain @click="openAssign(row)">派单</el-button>
            <el-button v-if="canStart(row)" size="small" type="warning" plain @click="startRepair(row)">开始维修</el-button>
            <el-button v-if="canFinish(row)" size="small" type="success" plain @click="openFinish(row)">提交结果</el-button>
            <el-button v-if="canVerify(row)" size="small" type="success" plain @click="openVerify(row)">验收</el-button>
            <el-button size="small" text type="primary" @click="openDetail(row)">详情</el-button>
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

    <!-- 派单 -->
    <el-dialog v-model="assignVisible" title="指派维修人员" width="440px">
      <div v-if="current" class="mini-summary">
        <b>{{ current.equipmentName }}</b> · {{ current.faultType }}
        <div class="muted">{{ current.faultDesc }}</div>
      </div>
      <el-form label-width="84px">
        <el-form-item label="维修人员">
          <el-select v-model="repairerId" placeholder="选择维修员" style="width: 100%">
            <el-option v-for="r in repairers" :key="r.id" :label="`${r.realName}（${r.userNo}）`" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="doAssign">确认派单</el-button>
      </template>
    </el-dialog>

    <!-- 提交维修结果 -->
    <el-dialog v-model="finishVisible" title="提交维修结果" width="500px">
      <el-form label-position="top">
        <el-form-item label="维修处理说明" required>
          <el-input v-model="finishForm.repairDesc" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="说明故障原因与处理方式" />
        </el-form-item>
        <el-form-item label="维修费用（元）">
          <el-input-number v-model="finishForm.cost" :min="0" :precision="2" :step="50" style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="finishVisible = false">取消</el-button>
        <el-button type="success" :loading="submitting" @click="doFinish">提交，等待验收</el-button>
      </template>
    </el-dialog>

    <!-- 验收 -->
    <el-dialog v-model="verifyVisible" title="工单验收" width="480px">
      <div v-if="current" class="mini-summary">
        <b>{{ current.equipmentName }}</b>
        <div class="muted">维修说明：{{ current.repairDesc || '-' }}</div>
        <div class="muted">维修费用：¥{{ current.cost ?? 0 }}</div>
      </div>
      <el-form label-position="top">
        <el-form-item label="维修满意度">
          <el-rate v-model="verifyForm.score" show-text :texts="['很差', '较差', '一般', '满意', '非常满意']" />
        </el-form-item>
        <el-form-item label="验收意见">
          <el-input v-model="verifyForm.verifyRemark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="verifyVisible = false">取消</el-button>
        <el-button type="success" :loading="submitting" @click="doVerify">确认验收</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-drawer v-model="detailVisible" size="520px" title="工单详情">
      <div v-if="current" class="detail">
        <div class="d-head">
          <div>
            <div class="d-title">{{ current.equipmentName }}</div>
            <div class="d-sub mono">{{ current.orderNo }} · {{ current.equipmentCode }}</div>
          </div>
          <el-tag :type="typeOf(REPAIR_STATUS, current.status)" effect="dark">
            {{ labelOf(REPAIR_STATUS, current.status) }}
          </el-tag>
        </div>

        <div class="d-grid">
          <div class="dg"><span>所属实验室</span><b>{{ current.labName }}</b></div>
          <div class="dg"><span>故障类型</span><b>{{ current.faultType }}</b></div>
          <div class="dg"><span>紧急程度</span><b>{{ labelOf(URGENCY, current.urgency) }}</b></div>
          <div class="dg"><span>报修人</span><b>{{ current.reporterName }}</b></div>
          <div class="dg"><span>维修员</span><b>{{ current.repairerName || '未派单' }}</b></div>
          <div class="dg"><span>维修费用</span><b>¥{{ current.cost ?? 0 }}</b></div>
        </div>

        <div class="d-block">
          <div class="db-label">故障描述</div>
          <div class="db-text">{{ current.faultDesc }}</div>
        </div>
        <div v-if="current.repairDesc" class="d-block">
          <div class="db-label">维修说明</div>
          <div class="db-text">{{ current.repairDesc }}</div>
        </div>
        <div v-if="current.verifyRemark || current.score" class="d-block">
          <div class="db-label">验收结果</div>
          <div class="db-text">
            <el-rate :model-value="current.score || 0" disabled size="small" />
            {{ current.verifyRemark || '' }}
          </div>
        </div>

        <div class="panel-title" style="margin-top: 22px">流转记录</div>
        <el-timeline v-if="logs.length">
          <el-timeline-item
            v-for="l in logs"
            :key="l.id"
            :timestamp="l.createTime"
            :type="logType(l.action)"
            placement="top"
          >
            <div class="log-title">{{ logLabel(l.action) }} · {{ l.operatorName }}</div>
            <div class="log-content">{{ l.content }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无流转记录" :image-size="60" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Tools } from '@element-plus/icons-vue'
import { repairApi, userApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { REPAIR_STATUS, URGENCY, labelOf, typeOf } from '@/utils/dict'

const router = useRouter()
const store = useUserStore()

const flow = ['报修', '派单', '维修中', '待验收', '已完成']

const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const scope = ref(store.isAdmin || store.isTeacher ? 'all' : store.isRepairer ? 'assigned' : 'mine')
const status = ref('')
const keyword = ref('')

const current = ref(null)
const logs = ref([])
const detailVisible = ref(false)
const submitting = ref(false)

const assignVisible = ref(false)
const repairers = ref([])
const repairerId = ref(null)

const finishVisible = ref(false)
const finishForm = reactive({ repairDesc: '', cost: 0 })

const verifyVisible = ref(false)
const verifyForm = reactive({ score: 5, verifyRemark: '' })

function canAssign(row) {
  return store.isAdmin && row.status === 'PENDING'
}

function canStart(row) {
  if (row.status !== 'ASSIGNED') return false
  return store.isAdmin || (store.isRepairer && row.repairerId === store.user?.id)
}

function canFinish(row) {
  if (row.status !== 'REPAIRING') return false
  return store.isAdmin || (store.isRepairer && row.repairerId === store.user?.id)
}

function canVerify(row) {
  if (row.status !== 'DONE') return false
  return store.isAdmin || store.isTeacher || row.reporterId === store.user?.id
}

function logLabel(action) {
  return { CREATE: '提交报修', ASSIGN: '派单', START: '开始维修', FINISH: '维修完成', VERIFY: '验收通过' }[action] || action
}

function logType(action) {
  return { CREATE: 'danger', ASSIGN: 'warning', START: 'primary', FINISH: 'success', VERIFY: 'success' }[action] || 'info'
}

async function load() {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: status.value,
      keyword: keyword.value
    }
    if (scope.value === 'mine') params.scope = 'mine'
    if (scope.value === 'assigned') params.scope = 'assigned'
    const res = await repairApi.page(params)
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  pageNum.value = 1
  load()
}

async function openAssign(row) {
  current.value = row
  repairerId.value = null
  if (!repairers.value.length) {
    const res = await userApi.options('REPAIRER')
    repairers.value = res.data || []
  }
  assignVisible.value = true
}

async function doAssign() {
  if (!repairerId.value) {
    ElMessage.warning('请选择维修人员')
    return
  }
  submitting.value = true
  try {
    await repairApi.assign(current.value.id, repairerId.value)
    ElMessage.success('派单成功')
    assignVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

function startRepair(row) {
  ElMessageBox.confirm('确认开始维修该设备吗？', '开始维修', { type: 'info' })
    .then(async () => {
      await repairApi.start(row.id)
      ElMessage.success('已开始维修')
      load()
    })
    .catch(() => {})
}

function openFinish(row) {
  current.value = row
  finishForm.repairDesc = ''
  finishForm.cost = 0
  finishVisible.value = true
}

async function doFinish() {
  if (!finishForm.repairDesc.trim()) {
    ElMessage.warning('请填写维修处理说明')
    return
  }
  submitting.value = true
  try {
    await repairApi.finish(current.value.id, { ...finishForm })
    ElMessage.success('已提交，等待报修人验收')
    finishVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

function openVerify(row) {
  current.value = row
  verifyForm.score = 5
  verifyForm.verifyRemark = ''
  verifyVisible.value = true
}

async function doVerify() {
  submitting.value = true
  try {
    await repairApi.verify(current.value.id, { ...verifyForm })
    ElMessage.success('验收完成，设备已恢复空闲')
    verifyVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function openDetail(row) {
  current.value = row
  detailVisible.value = true
  const res = await repairApi.logs(row.id)
  logs.value = res.data || []
}

onMounted(load)
</script>

<style scoped>
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

.ellipsis {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 230px;
}

.fault-line {
  display: flex;
  gap: 6px;
  margin-bottom: 2px;
}

.flow-tip {
  display: flex;
  gap: 4px;
  font-size: 11.5px;
  color: #9aa4b8;
  align-items: center;
}

.flow-step {
  display: flex;
  align-items: center;
  gap: 4px;
}

.arrow {
  font-style: normal;
  color: #c9d0de;
}

.mini-summary {
  background: #f8fafd;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 16px;
  font-size: 13px;
  line-height: 1.8;
}

.d-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.d-title {
  font-size: 16px;
  font-weight: 600;
}

.d-sub {
  font-size: 12px;
  color: var(--muted);
  margin-top: 4px;
}

.d-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}

.dg {
  background: #f8fafd;
  border-radius: 9px;
  padding: 10px 12px;
}

.dg span {
  display: block;
  font-size: 11.5px;
  color: var(--muted);
  margin-bottom: 4px;
}

.dg b {
  font-size: 13px;
  font-weight: 500;
}

.d-block {
  margin-bottom: 14px;
}

.db-label {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 6px;
}

.db-text {
  font-size: 13px;
  line-height: 1.8;
  color: #374151;
  background: #fbfcfe;
  border: 1px solid var(--border);
  border-radius: 9px;
  padding: 11px 13px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.log-title {
  font-size: 13px;
  font-weight: 500;
}

.log-content {
  font-size: 12.5px;
  color: #6b7280;
  margin-top: 4px;
  line-height: 1.7;
}
</style>
