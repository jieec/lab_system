<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">闭馆安排</h2>
        <div class="page-sub">节假日或临时闭馆，闭馆当日该实验室（或全校）不可预约</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openForm">新增闭馆</el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="生效范围"
      description="适用范围选择「全校」时，所有实验室当天均停约；选择具体实验室时仅影响该实验室。已有预约不受影响。"
      style="margin-bottom: 16px"
    />

    <div class="panel">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="closureDate" label="闭馆日期" width="150" />
        <el-table-column label="适用范围" min-width="180">
          <template #default="{ row }">
            <el-tag :type="row.labId ? 'primary' : 'danger'" size="small" effect="light">
              {{ row.labId ? row.labName || '实验室 #' + row.labId : '全校' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="闭馆原因" min-width="240" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="暂无闭馆安排" />
    </div>

    <el-dialog v-model="formVisible" title="新增闭馆安排" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="闭馆日期" prop="closureDate">
          <el-date-picker
            v-model="form.closureDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
            :disabled-date="(d) => d.getTime() < Date.now() - 86400000"
          />
        </el-form-item>
        <el-form-item label="适用范围" prop="labId">
          <el-select v-model="form.labId" placeholder="全校" clearable style="width: 100%">
            <el-option v-for="l in labs" :key="l.id" :label="l.name" :value="l.id" />
          </el-select>
          <div class="form-tip">留空表示全校闭馆</div>
        </el-form-item>
        <el-form-item label="闭馆原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="如：国庆放假、设备检修" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { closureApi, labApi } from '@/api'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const labs = ref([])
const formVisible = ref(false)
const formRef = ref()
const form = reactive({ closureDate: '', labId: null, reason: '' })
const rules = {
  closureDate: [{ required: true, message: '请选择闭馆日期', trigger: 'change' }],
  reason: [{ required: true, message: '请填写闭馆原因', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const res = await closureApi.list({})
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openForm() {
  form.closureDate = ''
  form.labId = null
  form.reason = ''
  formVisible.value = true
}

async function submit() {
  await formRef.value.validate()
  saving.value = true
  try {
    await closureApi.add({ closureDate: form.closureDate, labId: form.labId || null, reason: form.reason })
    ElMessage.success('闭馆安排已生效')
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function remove(row) {
  ElMessageBox.confirm(`确定删除 ${row.closureDate} 的闭馆安排吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await closureApi.remove(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

onMounted(async () => {
  load()
  try {
    const res = await labApi.all()
    labs.value = res.data || []
  } catch (e) { /* 忽略 */ }
})
</script>

<style scoped>
.form-tip {
  font-size: 11.5px;
  color: var(--muted);
  margin-top: 4px;
}
</style>
