<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">通知公告</h2>
        <div class="page-sub">实验室管理制度、设备维护安排与系统公告</div>
      </div>
      <el-button v-if="store.isAdmin" type="primary" :icon="Plus" @click="openForm()">发布公告</el-button>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索公告标题"
          :prefix-icon="Search"
          clearable
          style="width: 260px"
          @keyup.enter="load"
          @clear="load"
        />
        <el-button :icon="Search" @click="load">查询</el-button>
      </div>

      <div v-loading="loading" class="notice-list">
        <div v-for="n in list" :key="n.id" class="notice-card" @click="openDetail(n)">
          <div class="n-left">
            <el-tag :type="typeOf(NOTICE_TYPE, n.type)" effect="light" size="small">
              {{ labelOf(NOTICE_TYPE, n.type) }}
            </el-tag>
          </div>
          <div class="n-main">
            <div class="n-title">{{ n.title }}</div>
            <div class="n-preview">{{ n.content }}</div>
            <div class="n-meta">
              <span>{{ n.publisherName }}</span>
              <span>{{ n.createTime }}</span>
            </div>
          </div>
          <div v-if="store.isAdmin" class="n-actions" @click.stop>
            <el-button size="small" text type="primary" @click="openForm(n)">编辑</el-button>
            <el-button size="small" text type="danger" @click="remove(n)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!loading && !list.length" description="暂无公告" />
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

    <el-dialog v-model="detailVisible" :title="current?.title" width="620px">
      <div v-if="current" class="article">
        <div class="article-meta">
          <el-tag :type="typeOf(NOTICE_TYPE, current.type)" size="small" effect="light">
            {{ labelOf(NOTICE_TYPE, current.type) }}
          </el-tag>
          <span>{{ current.publisherName }}</span>
          <span>{{ current.createTime }}</span>
        </div>
        <div class="article-body">{{ current.content }}</div>
      </div>
    </el-dialog>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑公告' : '发布公告'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="80" show-word-limit />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio-button value="NOTICE">通知</el-radio-button>
            <el-radio-button value="MAINTENANCE">维护</el-radio-button>
            <el-radio-button value="SYSTEM">系统</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="7" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">发布</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
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
import { noticeApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { NOTICE_TYPE, labelOf, typeOf } from '@/utils/dict'

const store = useUserStore()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')

const current = ref(null)
const detailVisible = ref(false)
const formVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({ id: null, title: '', content: '', type: 'NOTICE', status: 1 })
const rules = {
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  content: [{ required: true, message: '请填写内容', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const res = await noticeApi.page({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function openDetail(n) {
  current.value = n
  detailVisible.value = true
}

function openForm(n) {
  formVisible.value = true
  if (n) {
    Object.assign(form, { ...n })
  } else {
    Object.assign(form, { id: null, title: '', content: '', type: 'NOTICE', status: 1 })
  }
}

async function saveForm() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.id) {
        await noticeApi.update({ ...form })
      } else {
        await noticeApi.save({ ...form })
      }
      ElMessage.success('保存成功')
      formVisible.value = false
      load()
    } finally {
      saving.value = false
    }
  })
}

function remove(n) {
  ElMessageBox.confirm(`确定删除公告「${n.title}」吗？`, '删除确认', { type: 'warning' })
    .then(async () => {
      await noticeApi.remove(n.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.notice-list {
  display: flex;
  flex-direction: column;
  min-height: 120px;
}

.notice-card {
  display: flex;
  gap: 14px;
  padding: 16px 8px;
  border-bottom: 1px solid #f2f5fa;
  cursor: pointer;
  transition: background 0.15s;
  align-items: flex-start;
}

.notice-card:hover {
  background: #fafbfe;
}

.n-left {
  padding-top: 2px;
}

.n-main {
  flex: 1;
  min-width: 0;
}

.n-title {
  font-size: 14.5px;
  font-weight: 500;
  color: #1f2937;
  margin-bottom: 6px;
}

.n-preview {
  font-size: 12.5px;
  color: #8b95a8;
  line-height: 1.8;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.n-meta {
  display: flex;
  gap: 14px;
  font-size: 11.5px;
  color: #a8b0c0;
  margin-top: 8px;
}

.n-actions {
  display: flex;
  align-items: center;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.article-meta {
  display: flex;
  gap: 14px;
  align-items: center;
  font-size: 12px;
  color: #9aa4b8;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 16px;
}

.article-body {
  font-size: 13.5px;
  line-height: 2;
  color: #374151;
  white-space: pre-wrap;
}
</style>
