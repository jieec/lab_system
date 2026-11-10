<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">设备台账</h2>
        <div class="page-sub">共 {{ total }} 台设备，支持按实验室、类别、状态筛选</div>
      </div>
      <el-button v-if="store.isAdmin" type="primary" :icon="Plus" @click="openForm()">新增设备</el-button>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索设备名称 / 编号 / 型号"
          :prefix-icon="Search"
          clearable
          style="width: 240px"
          @keyup.enter="load"
          @clear="load"
        />
        <el-select v-model="query.labId" placeholder="全部实验室" clearable filterable style="width: 200px" @change="load">
          <el-option v-for="l in labs" :key="l.id" :label="l.name" :value="l.id" />
        </el-select>
        <el-select v-model="query.category" placeholder="全部类别" clearable style="width: 150px" @change="load">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="load">
          <el-option v-for="(v, k) in EQUIPMENT_STATUS" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button :icon="Search" @click="load">查询</el-button>
        <div class="spacer"></div>
        <el-button :icon="Refresh" circle @click="load" />
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="code" label="资产编号" width="130">
          <template #default="{ row }"><span class="mono">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column label="设备名称" min-width="180">
          <template #default="{ row }">
            <div class="cell-strong">{{ row.name }}</div>
            <div class="cell-sub">{{ row.brand }} {{ row.model }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="类别" width="110" />
        <el-table-column label="所属实验室" width="146">
          <template #default="{ row }">
            <div class="cell-strong">{{ row.labName || '-' }}</div>
            <div class="cell-sub">{{ row.labRoom }}</div>
          </template>
        </el-table-column>
        <el-table-column label="价值" width="100" align="right">
          <template #default="{ row }">
            <span class="price">¥{{ formatMoney(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="typeOf(EQUIPMENT_STATUS, row.status)" size="small" effect="light">
              {{ labelOf(EQUIPMENT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="keeper" label="保管人" width="96" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="warning"
              plain
              :disabled="row.status === 'SCRAPPED' || row.status === 'REPAIR'"
              @click="openRepair(row)"
            >
              报修
            </el-button>
            <el-button size="small" text type="primary" @click="openDetail(row)">详情</el-button>
            <template v-if="store.isAdmin">
              <el-button size="small" text type="primary" @click="openForm(row)">编辑</el-button>
              <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
            </template>
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

    <!-- 报修 -->
    <el-dialog v-model="repairVisible" title="设备报修" width="520px" destroy-on-close>
      <div v-if="current" class="repair-head">
        <div class="rh-name">{{ current.name }}</div>
        <div class="rh-meta">{{ current.code }} · {{ current.labName }} · {{ current.brand }} {{ current.model }}</div>
      </div>
      <el-form ref="repairRef" :model="repairForm" :rules="repairRules" label-width="86px">
        <el-form-item label="故障类型" prop="faultType">
          <el-select v-model="repairForm.faultType" style="width: 100%">
            <el-option label="硬件故障" value="硬件故障" />
            <el-option label="软件故障" value="软件故障" />
            <el-option label="耗材缺失" value="耗材缺失" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="紧急程度">
          <el-radio-group v-model="repairForm.urgency">
            <el-radio-button value="LOW">一般</el-radio-button>
            <el-radio-button value="NORMAL">较急</el-radio-button>
            <el-radio-button value="HIGH">紧急</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="故障描述" prop="faultDesc">
          <el-input
            v-model="repairForm.faultDesc"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="请描述故障现象，便于维修人员准备工具与配件"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="repairVisible = false">取消</el-button>
        <el-button type="warning" :loading="submitting" @click="submitRepair">提交报修</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="设备详情" width="480px">
      <div v-if="current">
        <div class="d-row"><span>资产编号</span><b class="mono">{{ current.code }}</b></div>
        <div class="d-row"><span>设备名称</span><b>{{ current.name }}</b></div>
        <div class="d-row"><span>类别</span><b>{{ current.category }}</b></div>
        <div class="d-row"><span>品牌型号</span><b>{{ current.brand }} {{ current.model }}</b></div>
        <div class="d-row"><span>所属实验室</span><b>{{ current.labName }} {{ current.labRoom }}</b></div>
        <div class="d-row"><span>购置日期</span><b>{{ current.purchaseDate || '-' }}</b></div>
        <div class="d-row"><span>设备价值</span><b>¥{{ formatMoney(current.price) }}</b></div>
        <div class="d-row"><span>保管人</span><b>{{ current.keeper || '-' }}</b></div>
        <div class="d-row"><span>当前状态</span>
          <el-tag :type="typeOf(EQUIPMENT_STATUS, current.status)" size="small" effect="light">
            {{ labelOf(EQUIPMENT_STATUS, current.status) }}
          </el-tag>
        </div>
        <div class="d-row"><span>备注</span><b>{{ current.description || '-' }}</b></div>
      </div>
    </el-dialog>

    <!-- 管理表单 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑设备' : '新增设备'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="92px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="资产编号" prop="code">
              <el-input v-model="form.code" placeholder="如 EQ010001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备名称" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备类别" prop="category">
              <el-select v-model="form.category" filterable allow-create default-first-option style="width: 100%">
                <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属实验室" prop="labId">
              <el-select v-model="form.labId" filterable style="width: 100%">
                <el-option v-for="l in labs" :key="l.id" :label="l.name" :value="l.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌">
              <el-input v-model="form.brand" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="型号">
              <el-input v-model="form.model" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="购置日期">
              <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备价值">
              <el-input-number v-model="form.price" :min="0" :precision="2" :step="1000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="保管人">
              <el-input v-model="form.keeper" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option v-for="(v, k) in EQUIPMENT_STATUS" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.description" type="textarea" :rows="2" maxlength="200" show-word-limit />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { equipmentApi, labApi, repairApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { EQUIPMENT_STATUS, labelOf, typeOf } from '@/utils/dict'

const store = useUserStore()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const labs = ref([])
const categories = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', labId: null, category: '', status: '' })

const current = ref(null)
const detailVisible = ref(false)

const repairVisible = ref(false)
const submitting = ref(false)
const repairRef = ref()
const repairForm = reactive({ faultType: '硬件故障', urgency: 'NORMAL', faultDesc: '' })
const repairRules = {
  faultType: [{ required: true, message: '请选择故障类型', trigger: 'change' }],
  faultDesc: [{ required: true, message: '请描述故障现象', trigger: 'blur' }]
}

const formVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({
  id: null, code: '', name: '', category: '', labId: null, brand: '', model: '',
  purchaseDate: '', price: 0, keeper: '', status: 'IDLE', description: ''
})
const formRules = {
  code: [{ required: true, message: '请填写资产编号', trigger: 'blur' }],
  name: [{ required: true, message: '请填写设备名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择设备类别', trigger: 'change' }],
  labId: [{ required: true, message: '请选择所属实验室', trigger: 'change' }]
}

const categoryOptions = computed(() => {
  const base = ['计算设备', '仪器仪表', '实验装置', '网络设备', '多媒体设备', '分析仪器', '机械加工', '办公设备']
  return [...new Set([...base, ...categories.value])]
})

function formatMoney(v) {
  if (v === null || v === undefined) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load() {
  loading.value = true
  try {
    const res = await equipmentApi.page({ ...query })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function openDetail(row) {
  current.value = row
  detailVisible.value = true
}

function openRepair(row) {
  current.value = row
  Object.assign(repairForm, { faultType: '硬件故障', urgency: 'NORMAL', faultDesc: '' })
  repairVisible.value = true
}

async function submitRepair() {
  await repairRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await repairApi.create({ equipmentId: current.value.id, ...repairForm })
      ElMessage.success('报修已提交，等待管理员派单')
      repairVisible.value = false
      load()
    } finally {
      submitting.value = false
    }
  })
}

function openForm(row) {
  formVisible.value = true
  if (row) {
    Object.assign(form, { ...row })
  } else {
    Object.assign(form, {
      id: null, code: '', name: '', category: '', labId: null, brand: '', model: '',
      purchaseDate: '', price: 0, keeper: '', status: 'IDLE', description: ''
    })
  }
}

async function saveForm() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.id) {
        await equipmentApi.update({ ...form })
      } else {
        await equipmentApi.save({ ...form })
      }
      ElMessage.success('保存成功')
      formVisible.value = false
      load()
    } finally {
      saving.value = false
    }
  })
}

function remove(row) {
  ElMessageBox.confirm(`确定删除设备「${row.name}」吗？`, '删除确认', { type: 'warning' })
    .then(async () => {
      await equipmentApi.remove(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

onMounted(async () => {
  load()
  const [l, c] = await Promise.all([labApi.all(), equipmentApi.categories()])
  labs.value = l.data || []
  categories.value = c.data || []
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

.price {
  font-size: 12.5px;
  color: #4b5563;
}

.repair-head {
  background: #fff9db;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 16px;
}

.rh-name {
  font-size: 14.5px;
  font-weight: 600;
}

.rh-meta {
  font-size: 12px;
  color: #9a7b2f;
  margin-top: 5px;
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
  width: 84px;
  flex: 0 0 84px;
  color: var(--muted);
  font-size: 12.5px;
}

.d-row b { font-weight: 500; color: #374151; }
</style>
