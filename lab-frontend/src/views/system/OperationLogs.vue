<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">操作审计日志</h2>
        <div class="page-sub">记录审批、改状态、删用户等关键写操作，便于追溯与答辩演示</div>
      </div>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="module" placeholder="全部模块" clearable style="width: 170px" @change="load(1)">
          <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索操作人 / 操作 / 路径"
          :prefix-icon="Search"
          clearable
          style="width: 260px"
          @keyup.enter="load(1)"
          @clear="load(1)"
        />
        <el-button :icon="Search" @click="load(1)">查询</el-button>
        <el-button :icon="Refresh" @click="load()">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="createTime" label="操作时间" width="170" />
        <el-table-column prop="operatorName" label="操作人" width="120" />
        <el-table-column prop="module" label="模块" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.module || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="操作" min-width="140" />
        <el-table-column prop="uri" label="请求路径" min-width="190" show-overflow-tooltip />
        <el-table-column prop="ip" label="来源 IP" width="130" />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.success === 1 ? 'success' : 'danger'" size="small" effect="light">
              {{ row.success === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时(ms)" width="100" align="right" />
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
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { logApi } from '@/api'

const modules = ['预约管理', '报修运维', '用户管理', '实验室管理', '设备台账', '闭馆管理', '通知公告', '登录认证']
const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const module = ref('')
const keyword = ref('')

async function load(page) {
  if (page) pageNum.value = page
  loading.value = true
  try {
    const res = await logApi.page({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      module: module.value || undefined,
      keyword: keyword.value || undefined
    })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(() => load())
</script>
