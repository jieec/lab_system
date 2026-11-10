<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">审批中心</h2>
        <div class="page-sub">处理学生的实验室预约申请，通过后将占用对应时段</div>
      </div>
      <el-tag v-if="pendingTotal > 0" type="warning" effect="dark" size="large" round>
        {{ pendingTotal }} 条待处理
      </el-tag>
    </div>

    <div class="panel">
      <el-tabs v-model="tab" @tab-change="onTabChange">
        <el-tab-pane label="待审批" name="PENDING" />
        <el-tab-pane label="全部预约" name="ALL" />
      </el-tabs>

      <div class="toolbar">
        <el-select v-model="query.labId" placeholder="全部实验室" clearable filterable style="width: 210px" @change="load">
          <el-option v-for="l in labs" :key="l.id" :label="l.name" :value="l.id" />
        </el-select>
        <el-date-picker
          v-model="query.date"
          type="date"
          placeholder="按日期筛选"
          value-format="YYYY-MM-DD"
          clearable
          style="width: 170px"
          @change="load"
        />
        <el-input
          v-model="query.keyword"
          placeholder="搜索申请人 / 学号 / 单号"
          :prefix-icon="Search"
          clearable
          style="width: 230px"
          @keyup.enter="load"
          @clear="load"
        />
        <el-button :icon="Search" @click="load">查询</el-button>
        <div class="spacer"></div>
        <el-button :icon="Refresh" circle @click="load" />
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="orderNo" label="预约单号" width="126">
          <template #default="{ row }"><span class="mono">{{ row.orderNo }}</span></template>
        </el-table-column>
        <el-table-column label="申请人" width="152">
          <template #default="{ row }">
            <div class="cell-strong">{{ row.userName }}</div>
            <div class="cell-sub ellipsis">{{ row.userNo }} · {{ row.dept }}</div>
          </template>
        </el-table-column>
        <el-table-column label="实验室" width="140">
          <template #default="{ row }">
            <div class="cell-strong">{{ row.labName }}</div>
            <div class="cell-sub">{{ row.building }} {{ row.room }}</div>
          </template>
        </el-table-column>
        <el-table-column label="预约时段" width="178">
          <template #default="{ row }">
            <div class="cell-strong">{{ (row.startTime || '').slice(0, 16) }}</div>
            <div class="cell-sub">至 {{ (row.endTime || '').slice(5, 16) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="headcount" label="人数" width="56" align="center" />
        <el-table-column prop="purpose" label="用途" width="122" show-overflow-tooltip />
        <el-table-column label="提交时间" width="108">
          <template #default="{ row }">
            <span class="cell-sub">{{ (row.createTime || '').slice(5, 16) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="86">
          <template #default="{ row }">
            <el-tag :type="typeOf(RESERVATION_STATUS, row.status)" size="small" effect="light">
              {{ labelOf(RESERVATION_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="136" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button size="small" type="success" plain @click="openApprove(row, true)">通过</el-button>
              <el-button size="small" type="danger" plain @click="openApprove(row, false)">驳回</el-button>
            </template>
            <el-button v-else size="small" text type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="load"
        />
      </div>
    </div>

    <el-dialog v-model="approveVisible" :title="approvePass ? '通过预约申请' : '驳回预约申请'" width="480px">
      <div v-if="current" class="summary">
        <div class="s-line"><b>{{ current.userName }}</b> 申请 <b>{{ current.labName }}</b></div>
        <div class="s-line muted">{{ current.startTime }} 至 {{ current.endTime }} · {{ current.headcount }} 人</div>
        <div class="s-line muted">用途：{{ current.purpose }}</div>
      </div>
      <el-form label-position="top">
        <el-form-item :label="approvePass ? '审批意见（选填）' : '驳回原因（必填）'">
          <el-input
            v-model="remark"
            type="textarea"
            :rows="3"
            :placeholder="approvePass ? '如：同意，请按时到场' : '如：该时段已安排教学任务'"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button :type="approvePass ? 'success' : 'danger'" :loading="submitting" @click="doApprove">
          确认{{ approvePass ? '通过' : '驳回' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="预约详情" width="500px">
      <div v-if="current">
        <div class="d-row"><span>预约单号</span><b class="mono">{{ current.orderNo }}</b></div>
        <div class="d-row"><span>申请人</span><b>{{ current.userName }}（{{ current.userNo }}）</b></div>
        <div class="d-row"><span>实验室</span><b>{{ current.labName }} · {{ current.building }} {{ current.room }}</b></div>
        <div class="d-row"><span>预约时段</span><b>{{ current.startTime }} 至 {{ current.endTime }}</b></div>
        <div class="d-row"><span>使用人数</span><b>{{ current.headcount }} 人</b></div>
        <div class="d-row"><span>用途</span><b>{{ current.purpose }}</b></div>
        <div class="d-row"><span>状态</span>
          <el-tag :type="typeOf(RESERVATION_STATUS, current.status)" size="small" effect="light">
            {{ labelOf(RESERVATION_STATUS, current.status) }}
          </el-tag>
        </div>
        <div class="d-row"><span>审批人</span><b>{{ current.approverName || '-' }}</b></div>
        <div class="d-row"><span>审批意见</span><b>{{ current.approveRemark || '-' }}</b></div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { labApi, reservationApi, statsApi } from '@/api'
import { RESERVATION_STATUS, labelOf, typeOf } from '@/utils/dict'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const tab = ref('PENDING')
const labs = ref([])
const pendingTotal = ref(0)

const query = reactive({ pageNum: 1, pageSize: 10, labId: null, keyword: '', date: '' })

const current = ref(null)
const approveVisible = ref(false)
const approvePass = ref(true)
const remark = ref('')
const submitting = ref(false)
const detailVisible = ref(false)

const statusParam = computed(() => (tab.value === 'ALL' ? '' : 'PENDING'))

async function load() {
  loading.value = true
  try {
    const res = await reservationApi.page({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: statusParam.value,
      labId: query.labId,
      keyword: query.keyword,
      date: query.date
    })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadPendingCount() {
  const res = await statsApi.dashboard()
  pendingTotal.value = res.data.pendingApproval || 0
}

function onTabChange() {
  query.pageNum = 1
  load()
}

function openApprove(row, pass) {
  current.value = row
  approvePass.value = pass
  remark.value = pass ? '同意，请按时到场并遵守实验室管理规定' : ''
  approveVisible.value = true
}

function openDetail(row) {
  current.value = row
  detailVisible.value = true
}

async function doApprove() {
  if (!approvePass.value && !remark.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  submitting.value = true
  try {
    await reservationApi.approve(current.value.id, { pass: approvePass.value, remark: remark.value })
    ElMessage.success(approvePass.value ? '已通过该预约' : '已驳回该预约')
    approveVisible.value = false
    load()
    loadPendingCount()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  load()
  loadPendingCount()
  const res = await labApi.all()
  labs.value = res.data || []
})
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
  max-width: 142px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.summary {
  background: #f8fafd;
  border-radius: 10px;
  padding: 13px 15px;
  margin-bottom: 16px;
}

.s-line {
  font-size: 13px;
  line-height: 1.9;
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
  flex: 0 0 78px;
  color: var(--muted);
  font-size: 12.5px;
}

.d-row b {
  font-weight: 500;
  color: #374151;
}
</style>
