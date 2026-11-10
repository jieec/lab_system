<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">实验室预约</h2>
        <div class="page-sub">共 {{ total }} 间实验室，选择空闲时段提交预约申请</div>
      </div>
      <el-button v-if="store.isAdmin" type="primary" :icon="Plus" @click="openForm()">新增实验室</el-button>
    </div>

    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索实验室名称 / 编号 / 房间号"
        :prefix-icon="Search"
        clearable
        style="width: 280px"
        @keyup.enter="load"
        @clear="load"
      />
      <el-select v-model="query.building" placeholder="全部楼栋" clearable style="width: 150px" @change="load">
        <el-option v-for="b in buildings" :key="b" :label="b" :value="b" />
      </el-select>
      <el-select v-if="store.isAdmin" v-model="query.status" placeholder="全部状态" clearable style="width: 130px" @change="load">
        <el-option label="开放中" :value="1" />
        <el-option label="已停用" :value="0" />
      </el-select>
      <el-button :icon="Search" @click="load">查询</el-button>
      <div class="spacer"></div>
      <span class="muted" style="font-size: 12px">点击卡片可查看实验室详情与设备清单</span>
    </div>

    <div v-loading="loading" class="lab-grid">
      <div v-for="lab in labs" :key="lab.id" class="lab-card" :class="{ disabled: lab.status === 0 }">
        <div class="lab-cover">
          <div class="cover-code">{{ lab.code }}</div>
          <el-tag v-if="lab.needApproval === 1" size="small" effect="dark" type="warning" round class="cover-tag">需审批</el-tag>
          <el-tag v-else size="small" effect="dark" type="success" round class="cover-tag">免审批</el-tag>
          <el-tag v-if="lab.status === 0" size="small" effect="dark" type="info" round class="stop-tag">已停用</el-tag>
        </div>

        <div class="lab-body">
          <div class="lab-name">{{ lab.name }}</div>
          <div class="lab-meta">
            <span><el-icon><Location /></el-icon>{{ lab.building }} {{ lab.room }}</span>
            <span><el-icon><User /></el-icon>可容纳 {{ lab.capacity }} 人</span>
          </div>
          <div class="lab-meta">
            <span><el-icon><Clock /></el-icon>{{ lab.openTime }} - {{ lab.closeTime }}</span>
            <span><el-icon><Cpu /></el-icon>{{ lab.equipmentCount }} 台设备</span>
          </div>
          <div class="lab-desc">{{ lab.description }}</div>

          <div class="lab-foot">
            <div class="today">
              <span class="today-num">{{ lab.todayCount }}</span>
              <span class="today-label">今日已约</span>
            </div>
            <div class="actions">
              <el-button size="small" text @click="openDetail(lab)">详情</el-button>
              <template v-if="store.isAdmin">
                <el-button size="small" text type="primary" @click="openForm(lab)">编辑</el-button>
                <el-button size="small" text type="danger" @click="remove(lab)">删除</el-button>
              </template>
              <el-button
                v-else
                size="small"
                type="primary"
                :disabled="lab.status === 0"
                @click="openReserve(lab)"
              >
                立即预约
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && !labs.length" description="没有找到符合条件的实验室" style="grid-column: 1 / -1" />
    </div>

    <div class="pager">
      <el-pagination
        v-model:current-page="query.pageNum"
        :page-size="query.pageSize"
        :total="total"
        layout="prev, pager, next"
        background
        @current-change="load"
      />
    </div>

    <!-- 预约弹窗 -->
    <el-dialog v-model="reserveVisible" title="提交预约申请" width="560px" destroy-on-close>
      <div v-if="current" class="reserve-head">
        <div class="rh-name">{{ current.name }}</div>
        <div class="rh-meta">{{ current.building }} {{ current.room }} · 开放 {{ current.openTime }}-{{ current.closeTime }} · 容量 {{ current.capacity }} 人</div>
      </div>

      <el-form ref="reserveRef" :model="reserveForm" :rules="reserveRules" label-width="88px">
        <el-form-item label="预约日期" prop="date">
          <el-date-picker
            v-model="reserveForm.date"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            :disabled-date="disabledDate"
            style="width: 100%"
            @change="loadTimeline"
          />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-time-select
            v-model="reserveForm.startClock"
            start="08:00"
            step="00:30"
            end="22:00"
            placeholder="开始时间"
            style="width: 48%"
            @change="syncTime"
          />
          <span class="time-sep">至</span>
          <el-time-select
            v-model="reserveForm.endClock"
            start="08:00"
            step="00:30"
            end="22:30"
            placeholder="结束时间"
            style="width: 44%"
            @change="syncTime"
          />
        </el-form-item>
        <el-form-item label="使用人数" prop="headcount">
          <el-input-number v-model="reserveForm.headcount" :min="1" :max="current?.capacity || 100" />
          <span class="muted" style="margin-left: 10px; font-size: 12px">不超过实验室容量</span>
        </el-form-item>
        <el-form-item label="使用用途" prop="purpose">
          <el-input v-model="reserveForm.purpose" placeholder="如：课程实验、毕业设计、竞赛集训" maxlength="100" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="reserveForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="选填" />
        </el-form-item>
      </el-form>

      <div class="timeline-box">
        <div class="tb-title">
          <el-icon><Clock /></el-icon>当日已被占用时段
        </div>
        <div v-if="timeline.length" class="tb-items">
          <el-tag v-for="t in timeline" :key="t.id" size="small" type="info" effect="plain">
            {{ (t.startTime || '').slice(11, 16) }} - {{ (t.endTime || '').slice(11, 16) }}
            <span v-if="t.status === 'PENDING'" class="pending-mark">待审批</span>
          </el-tag>
        </div>
        <div v-else class="muted" style="font-size: 12px">当天暂无预约，全部时段可选</div>
      </div>

      <template #footer>
        <el-button @click="reserveVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReserve">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" size="480px" :title="current?.name || '实验室详情'">
      <div v-if="current" class="detail">
        <div class="detail-grid">
          <div class="dg-item"><span>编号</span><b>{{ current.code }}</b></div>
          <div class="dg-item"><span>位置</span><b>{{ current.building }} {{ current.room }}</b></div>
          <div class="dg-item"><span>容量</span><b>{{ current.capacity }} 人</b></div>
          <div class="dg-item"><span>开放时间</span><b>{{ current.openTime }} - {{ current.closeTime }}</b></div>
          <div class="dg-item"><span>负责人</span><b>{{ current.managerName || '-' }}</b></div>
          <div class="dg-item"><span>审批方式</span><b>{{ current.needApproval === 1 ? '需审批' : '免审批' }}</b></div>
        </div>
        <p class="detail-desc">{{ current.description }}</p>

        <div class="panel-title" style="margin-top: 20px">设备清单（{{ labEquipments.length }} 台）</div>
        <el-table :data="labEquipments" size="small" max-height="380">
          <el-table-column prop="name" label="设备名称" min-width="120" />
          <el-table-column prop="model" label="型号" min-width="100" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="typeOf(EQUIPMENT_STATUS, row.status)" size="small" effect="light">
                {{ labelOf(EQUIPMENT_STATUS, row.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>

    <!-- 管理员表单 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑实验室' : '新增实验室'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="96px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="实验室编号" prop="code">
              <el-input v-model="form.code" placeholder="如 LAB-A301" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="实验室名称" prop="name">
              <el-input v-model="form.name" placeholder="如 计算机基础实验室" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所在楼栋" prop="building">
              <el-input v-model="form.building" placeholder="如 实验楼A" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="房间号" prop="room">
              <el-input v-model="form.room" placeholder="如 301" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="可容纳人数" prop="capacity">
              <el-input-number v-model="form.capacity" :min="1" :max="500" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-select v-model="form.managerId" placeholder="选择负责人" clearable style="width: 100%">
                <el-option v-for="t in teachers" :key="t.id" :label="t.realName" :value="t.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开放开始">
              <el-time-select v-model="form.openTime" start="06:00" step="00:30" end="23:00" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开放结束">
              <el-time-select v-model="form.closeTime" start="06:00" step="00:30" end="23:30" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="审批方式">
              <el-radio-group v-model="form.needApproval">
                <el-radio :value="1">需审批</el-radio>
                <el-radio :value="0">免审批</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">开放</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="简介">
              <el-input v-model="form.description" type="textarea" :rows="3" maxlength="200" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { equipmentApi, labApi, reservationApi, userApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { EQUIPMENT_STATUS, labelOf, typeOf } from '@/utils/dict'

const store = useUserStore()
const loading = ref(false)
const labs = ref([])
const total = ref(0)
const buildings = ref([])
const query = reactive({ pageNum: 1, pageSize: 9, keyword: '', building: '', status: null })

const current = ref(null)
const timeline = ref([])

const reserveVisible = ref(false)
const submitting = ref(false)
const reserveRef = ref()
const reserveForm = reactive({
  date: '',
  startClock: '',
  endClock: '',
  startTime: '',
  endTime: '',
  headcount: 10,
  purpose: '',
  remark: ''
})
const reserveRules = {
  date: [{ required: true, message: '请选择日期', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择时间', trigger: 'change' }],
  headcount: [{ required: true, message: '请填写人数', trigger: 'blur' }],
  purpose: [{ required: true, message: '请填写使用用途', trigger: 'blur' }]
}

const detailVisible = ref(false)
const labEquipments = ref([])

const formVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const teachers = ref([])
const form = reactive({
  id: null,
  code: '',
  name: '',
  building: '',
  room: '',
  capacity: 30,
  openTime: '08:00',
  closeTime: '22:00',
  needApproval: 1,
  managerId: null,
  status: 1,
  description: ''
})
const formRules = {
  code: [{ required: true, message: '请填写实验室编号', trigger: 'blur' }],
  name: [{ required: true, message: '请填写实验室名称', trigger: 'blur' }],
  building: [{ required: true, message: '请填写所在楼栋', trigger: 'blur' }],
  room: [{ required: true, message: '请填写房间号', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const res = await labApi.page({ ...query })
    labs.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadBuildings() {
  const res = await labApi.buildings()
  buildings.value = res.data || []
}

function disabledDate(d) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const max = new Date(today)
  max.setDate(max.getDate() + 30)
  return d < today || d > max
}

function openReserve(lab) {
  current.value = lab
  Object.assign(reserveForm, {
    date: '',
    startClock: '',
    endClock: '',
    startTime: '',
    endTime: '',
    headcount: Math.min(10, lab.capacity || 10),
    purpose: '',
    remark: ''
  })
  timeline.value = []
  reserveVisible.value = true
}

function syncTime() {
  const { date, startClock, endClock } = reserveForm
  reserveForm.startTime = date && startClock ? `${date} ${startClock}:00` : ''
  reserveForm.endTime = date && endClock ? `${date} ${endClock}:00` : ''
}

async function loadTimeline() {
  timeline.value = []
  if (!current.value || !reserveForm.date) return
  const res = await reservationApi.timeline({ labId: current.value.id, date: reserveForm.date })
  timeline.value = res.data || []
}

async function submitReserve() {
  syncTime()
  await reserveRef.value.validate(async (valid) => {
    if (!valid) return
    if (!reserveForm.startTime || !reserveForm.endTime) {
      ElMessage.warning('请选择完整的开始与结束时间')
      return
    }
    submitting.value = true
    try {
      const res = await reservationApi.create({
        labId: current.value.id,
        startTime: reserveForm.startTime,
        endTime: reserveForm.endTime,
        headcount: reserveForm.headcount,
        purpose: reserveForm.purpose,
        remark: reserveForm.remark
      })
      ElMessage.success(res.msg || '提交成功')
      reserveVisible.value = false
      load()
    } catch (e) {
      loadTimeline()
    } finally {
      submitting.value = false
    }
  })
}

async function openDetail(lab) {
  current.value = lab
  detailVisible.value = true
  const res = await labApi.equipments(lab.id)
  labEquipments.value = res.data || []
}

async function openForm(lab) {
  formVisible.value = true
  if (!teachers.value.length) {
    const res = await userApi.options('TEACHER')
    teachers.value = res.data || []
  }
  if (lab) {
    Object.assign(form, { ...lab })
  } else {
    Object.assign(form, {
      id: null, code: '', name: '', building: '', room: '', capacity: 30,
      openTime: '08:00', closeTime: '22:00', needApproval: 1, managerId: null,
      status: 1, description: ''
    })
  }
}

async function saveForm() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = { ...form }
      if (payload.id) {
        await labApi.update(payload)
      } else {
        await labApi.save(payload)
      }
      ElMessage.success('保存成功')
      formVisible.value = false
      load()
      loadBuildings()
    } finally {
      saving.value = false
    }
  })
}

function remove(lab) {
  ElMessageBox.confirm(`确定删除「${lab.name}」吗？删除后不可恢复。`, '删除确认', { type: 'warning' })
    .then(async () => {
      await labApi.remove(lab.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

onMounted(() => {
  load()
  loadBuildings()
})
</script>

<style scoped>
.lab-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
  gap: 16px;
  min-height: 200px;
}

.lab-card {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  transition: transform 0.2s, box-shadow 0.2s;
  display: flex;
  flex-direction: column;
}

.lab-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 14px 32px rgba(31, 45, 90, 0.09);
}

.lab-card.disabled {
  opacity: 0.72;
}

.lab-cover {
  height: 78px;
  background: linear-gradient(120deg, #eef2ff 0%, #e8eefc 45%, #e3f0ff 100%);
  position: relative;
  display: flex;
  align-items: flex-end;
  padding: 12px 16px;
  gap: 8px;
}

.lab-cover::after {
  content: '';
  position: absolute;
  right: -18px;
  top: -26px;
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: rgba(76, 110, 245, 0.09);
}

.cover-code {
  position: absolute;
  top: 14px;
  left: 16px;
  font-family: Consolas, monospace;
  font-size: 12px;
  color: #4c6ef5;
  letter-spacing: 0.6px;
  background: rgba(255, 255, 255, 0.75);
  padding: 2px 8px;
  border-radius: 6px;
}

.stop-tag {
  position: absolute;
  top: 14px;
  right: 16px;
}

.cover-tag {
  position: absolute;
  right: 16px;
  bottom: 10px;
  z-index: 2;
}

.lab-body {
  padding: 14px 16px 12px;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.lab-name {
  font-size: 15.5px;
  font-weight: 600;
  margin-bottom: 10px;
}

.lab-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #7b8698;
  margin-bottom: 7px;
  flex-wrap: wrap;
}

.lab-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.lab-desc {
  font-size: 12px;
  color: #9aa4b8;
  line-height: 1.7;
  margin: 4px 0 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 34px;
}

.lab-foot {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px dashed #eef1f7;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.today {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.today-num {
  font-size: 17px;
  font-weight: 600;
  color: var(--brand);
}

.today-label {
  font-size: 11.5px;
  color: #9aa4b8;
}

.actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 22px;
}

.reserve-head {
  background: #f8fafd;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 18px;
}

.rh-name {
  font-size: 14.5px;
  font-weight: 600;
}

.rh-meta {
  font-size: 12px;
  color: var(--muted);
  margin-top: 5px;
}

.time-sep {
  margin: 0 8px;
  color: #9aa4b8;
  font-size: 12px;
}

.timeline-box {
  border-top: 1px dashed var(--border);
  padding-top: 14px;
}

.tb-title {
  font-size: 12.5px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 9px;
}

.tb-items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.pending-mark {
  color: #f59f00;
  margin-left: 4px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.dg-item {
  background: #f8fafd;
  border-radius: 9px;
  padding: 10px 12px;
}

.dg-item span {
  display: block;
  font-size: 11.5px;
  color: var(--muted);
  margin-bottom: 4px;
}

.dg-item b {
  font-size: 13.5px;
  font-weight: 500;
}

.detail-desc {
  font-size: 12.5px;
  line-height: 1.9;
  color: #6b7280;
  margin: 16px 0 0;
}
</style>
