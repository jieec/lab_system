<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">用户管理</h2>
        <div class="page-sub">共 {{ total }} 个账号，新增用户初始密码为 123456</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openForm()">新增用户</el-button>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索账号 / 姓名 / 学工号"
          :prefix-icon="Search"
          clearable
          style="width: 250px"
          @keyup.enter="load"
          @clear="load"
        />
        <el-select v-model="query.role" placeholder="全部角色" clearable style="width: 150px" @change="load">
          <el-option v-for="(v, k) in ROLE_LABEL" :key="k" :label="v" :value="k" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px" @change="load">
          <el-option label="正常" :value="1" />
          <el-option label="已停用" :value="0" />
        </el-select>
        <el-button :icon="Search" @click="load">查询</el-button>
        <div class="spacer"></div>
        <el-button :icon="Refresh" circle @click="load" />
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="avatar" :style="{ background: avatarColor(row.role) }">{{ row.realName?.slice(0, 1) }}</div>
              <div>
                <div class="cell-strong">{{ row.realName }}</div>
                <div class="cell-sub">{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="userNo" label="学号 / 工号" width="130">
          <template #default="{ row }"><span class="mono">{{ row.userNo }}</span></template>
        </el-table-column>
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="roleType(row.role)" size="small" effect="light">{{ ROLE_LABEL[row.role] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dept" label="学院 / 部门" min-width="140" show-overflow-tooltip />
        <el-table-column prop="phone" label="联系电话" width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === 1 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="276" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openForm(row)">编辑</el-button>
            <el-button size="small" text @click="resetPwd(row)">重置密码</el-button>
            <el-button size="small" text :type="row.status === 1 ? 'warning' : 'success'" @click="toggle(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
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

    <el-dialog v-model="formVisible" :title="form.id ? '编辑用户' : '新增用户'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="登录账号" prop="username">
              <el-input v-model="form.username" :disabled="!!form.id" placeholder="如 s2024001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="真实姓名" prop="realName">
              <el-input v-model="form.realName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色" prop="role">
              <el-select v-model="form.role" style="width: 100%">
                <el-option v-for="(v, k) in ROLE_LABEL" :key="k" :label="v" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="学号 / 工号" prop="userNo">
              <el-input v-model="form.userNo" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="学院 / 部门">
              <el-input v-model="form.dept" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="form.phone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="form.email" />
            </el-form-item>
          </el-col>
          <el-col :span="24" v-if="!form.id">
            <el-alert type="info" :closable="false" show-icon title="新增用户初始密码为 123456，请提醒用户首次登录后修改" />
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
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { userApi } from '@/api'
import { ROLE_LABEL } from '@/utils/dict'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', role: '', status: null })

const formVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({
  id: null, username: '', realName: '', role: 'STUDENT', userNo: '', dept: '', phone: '', email: ''
})
const rules = {
  username: [{ required: true, message: '请填写登录账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请填写真实姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  userNo: [{ required: true, message: '请填写学号/工号', trigger: 'blur' }]
}

function roleType(role) {
  return { ADMIN: 'danger', TEACHER: 'primary', STUDENT: 'success', REPAIRER: 'warning' }[role] || 'info'
}

function avatarColor(role) {
  const map = {
    ADMIN: 'linear-gradient(135deg,#e8590c,#f59f00)',
    TEACHER: 'linear-gradient(135deg,#4c6ef5,#7c93ff)',
    STUDENT: 'linear-gradient(135deg,#12b886,#38d9a9)',
    REPAIRER: 'linear-gradient(135deg,#7048e8,#9775fa)'
  }
  return map[role] || 'linear-gradient(135deg,#adb5bd,#ced4da)'
}

async function load() {
  loading.value = true
  try {
    const res = await userApi.page({ ...query })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function openForm(row) {
  formVisible.value = true
  if (row) {
    Object.assign(form, { ...row })
  } else {
    Object.assign(form, {
      id: null, username: '', realName: '', role: 'STUDENT', userNo: '', dept: '', phone: '', email: ''
    })
  }
}

async function saveForm() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.id) {
        await userApi.update({ ...form })
      } else {
        await userApi.save({ ...form })
      }
      ElMessage.success('保存成功')
      formVisible.value = false
      load()
    } finally {
      saving.value = false
    }
  })
}

function resetPwd(row) {
  ElMessageBox.confirm(`确定将「${row.realName}」的密码重置为 123456 吗？`, '重置密码', { type: 'warning' })
    .then(async () => {
      await userApi.resetPassword(row.id)
      ElMessage.success('密码已重置为 123456')
    })
    .catch(() => {})
}

function toggle(row) {
  const next = row.status === 1 ? 0 : 1
  ElMessageBox.confirm(
    `确定${next === 1 ? '启用' : '停用'}账号「${row.realName}」吗？`,
    next === 1 ? '启用账号' : '停用账号',
    { type: 'warning' }
  )
    .then(async () => {
      await userApi.toggleStatus(row.id, next)
      ElMessage.success('操作成功')
      load()
    })
    .catch(() => {})
}

function remove(row) {
  ElMessageBox.confirm(`确定删除用户「${row.realName}」吗？`, '删除确认', { type: 'warning' })
    .then(async () => {
      await userApi.remove(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  color: #fff;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}

.cell-strong {
  font-size: 13px;
  color: #1f2937;
}

.cell-sub {
  font-size: 11.5px;
  color: #9aa4b8;
  margin-top: 2px;
}
</style>
