<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">个人中心</h2>
        <div class="page-sub">维护个人资料与登录密码</div>
      </div>
    </div>

    <el-dialog v-model="appealVisible" title="申请信用恢复" width="520px">
      <p>请说明情况与改进措施。审核通过后至少恢复至70分，7天内最多通过一次。</p>
      <el-input v-model="appealReason" type="textarea" :rows="5" maxlength="500" placeholder="10–500字，说明情况与今后如何按时履约" />
      <template #footer><el-button @click="appealVisible=false">取消</el-button><el-button type="primary" :loading="appealSaving" @click="submitAppeal">提交申请</el-button></template>
    </el-dialog>
    <div class="profile-grid">
      <div class="panel side">
        <div class="avatar-big">{{ store.displayName.slice(0, 1) }}</div>
        <div class="p-name">{{ store.user?.realName }}</div>
        <el-tag :type="roleType(store.role)" effect="light" size="small">{{ store.roleLabel }}</el-tag>
        <div class="p-list">
          <div class="p-item"><span>登录账号</span><b>{{ store.user?.username }}</b></div>
          <div class="p-item"><span>学号 / 工号</span><b>{{ store.user?.userNo || '-' }}</b></div>
          <div class="p-item"><span>学院 / 部门</span><b>{{ store.user?.dept || '-' }}</b></div>
          <div class="p-item"><span>注册时间</span><b>{{ (store.user?.createTime || '').slice(0, 10) }}</b></div>
        </div>
      </div>

      <div class="panel main">
        <el-tabs v-model="tab">
          <el-tab-pane label="信用与预约资格" name="credit">
            <div style="padding:16px 0">
              <div style="display:flex;gap:28px;align-items:center;margin-bottom:20px">
                <strong style="font-size:42px;color:#4c6ef5">{{ creditSummary.score ?? '—' }} <small style="font-size:14px">信用分</small></strong>
                <el-tag :type="creditSummary.canReserve ? 'success' : 'danger'">{{ creditSummary.canReserve ? '满足信用预约条件' : '预约资格受限' }}</el-tag>
                <span>近7天爽约 {{ creditSummary.recentNoShows ?? 0 }} 次</span>
                <el-button text @click="loadCredit">刷新</el-button>
                <el-button type="primary" :disabled="(creditSummary.score ?? 100) >= 60" @click="appealVisible=true">申请信用恢复</el-button>
              </div>
              <div style="line-height:1.9;background:#f5f7fc;padding:16px;border-radius:10px;margin-bottom:18px">
                <p v-if="creditSummary.restrictedUntil" style="color:#b86b15">近期爽约限制预计于 {{ creditSummary.restrictedUntil }} 解除，届时仍需信用分不少于60分</p><strong>奖惩与恢复规则</strong><div v-for="rule in creditSummary.rules || []" :key="rule">{{ rule }}</div>
              </div>
              <div class="panel-title">我的恢复申请（最近10条）</div>
              <el-table :data="appealRows" stripe>
                <el-table-column prop="createTime" label="申请时间" width="165" />
                <el-table-column label="状态" width="90"><template #default="{row}">{{ {PENDING:'待审核',APPROVED:'已通过',REJECTED:'未通过'}[row.status] }}</template></el-table-column>
                <el-table-column prop="reason" label="申请说明" min-width="150" />
                <el-table-column prop="reviewReason" label="审核说明" min-width="150" />
              </el-table>
              <div class="panel-title" style="margin-top:20px">信用变动记录</div>
              <el-table :data="creditRows" stripe empty-text="暂无记录，明细从本次功能上线后开始记录">
                <el-table-column prop="createTime" label="时间" width="165" />
                <el-table-column label="变动" width="80"><template #default="{row}"><b :style="{color:row.changeAmount < 0 ? '#de5757' : '#229765'}">{{ row.changeAmount > 0 ? '+' : '' }}{{ row.changeAmount }}</b></template></el-table-column>
                <el-table-column prop="scoreAfter" label="变动后" width="85" />
                <el-table-column prop="reason" label="原因" min-width="220" />
              </el-table>
              <el-pagination v-model:current-page="creditPage" :page-size="10" :total="creditTotal" layout="total, prev, pager, next" @current-change="loadCredit" style="margin-top:16px" />
            </div>
          </el-tab-pane>
          <el-tab-pane label="基本资料" name="info">
            <el-form ref="infoRef" :model="infoForm" label-width="90px" style="max-width: 460px; margin-top: 8px">
              <el-form-item label="真实姓名" prop="realName" :rules="[{ required: true, message: '请填写姓名', trigger: 'blur' }]">
                <el-input v-model="infoForm.realName" />
              </el-form-item>
              <el-form-item label="联系电话">
                <el-input v-model="infoForm.phone" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="infoForm.email" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="savingInfo" @click="saveInfo">保存资料</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="修改密码" name="pwd">
            <el-form ref="pwdRef" :model="pwdForm" :rules="pwdRules" label-width="90px" style="max-width: 460px; margin-top: 8px">
              <el-form-item label="原密码" prop="oldPassword">
                <el-input v-model="pwdForm.oldPassword" type="password" show-password />
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input v-model="pwdForm.newPassword" type="password" show-password />
              </el-form-item>
              <el-form-item label="确认密码" prop="confirm">
                <el-input v-model="pwdForm.confirm" type="password" show-password />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="savingPwd" @click="savePwd">修改密码</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="我的概览" name="overview">
            <div class="overview">
              <div class="ov-card">
                <div class="ov-num">{{ overview.myReservation ?? 0 }}</div>
                <div class="ov-label">进行中的预约</div>
              </div>
              <div class="ov-card">
                <div class="ov-num">{{ overview.myRepair ?? 0 }}</div>
                <div class="ov-label">待处理的报修</div>
              </div>
              <div class="ov-card">
                <div class="ov-num">{{ overview.todayReservation ?? 0 }}</div>
                <div class="ov-label">今日全校预约</div>
              </div>
              <div class="ov-card">
                <div class="ov-num" :class="{ 'ov-danger': (store.user?.creditScore ?? 100) < 60 }">
                  {{ store.user?.creditScore ?? 100 }}
                </div>
                <div class="ov-label">信用分（爽约 {{ store.user?.violationCount ?? 0 }} 次）</div>
              </div>
            </div>
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="信用分规则"
              description="初始 100 分；按时签到 +2 分（上限 100），爽约 -10 分；低于 60 分将暂停预约权限。如需调整角色或所属部门，请联系系统管理员。"
              style="margin-top: 18px"
            />
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi, statsApi, creditApi } from '@/api'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const tab = ref('info')
const savingInfo = ref(false)
const savingPwd = ref(false)
const infoRef = ref()
const pwdRef = ref()
const overview = ref({})
const creditSummary = ref({}), creditRows = ref([]), creditPage = ref(1), creditTotal = ref(0)
const appealVisible=ref(false),appealReason=ref(''),appealSaving=ref(false),appealRows=ref([])
async function submitAppeal(){
  if(appealReason.value.trim().length<10){ElMessage.warning('请填写至少10字的情况说明');return}
  appealSaving.value=true
  try{await creditApi.apply(appealReason.value);appealVisible.value=false;appealReason.value='';ElMessage.success('申请已提交，请等待管理员审核');await loadCredit()}
  finally{appealSaving.value=false}
}
async function loadCredit() {
  const [summary, records, appeals] = await Promise.all([creditApi.summary(), creditApi.records({pageNum:creditPage.value,pageSize:10}),creditApi.appeals()])
  creditSummary.value = summary.data; creditRows.value = records.data.records; creditTotal.value = records.data.total; appealRows.value = appeals.data.records
}
onMounted(loadCredit)

const infoForm = reactive({ realName: '', phone: '', email: '' })
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6-20 位', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, cb) => {
        if (value !== pwdForm.newPassword) cb(new Error('两次输入的密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

function roleType(role) {
  return { ADMIN: 'danger', TEACHER: 'primary', STUDENT: 'success', REPAIRER: 'warning' }[role] || 'info'
}

async function saveInfo() {
  await infoRef.value.validate(async (valid) => {
    if (!valid) return
    savingInfo.value = true
    try {
      await authApi.updateProfile({ ...infoForm })
      await store.refreshProfile()
      ElMessage.success('资料已更新')
    } finally {
      savingInfo.value = false
    }
  })
}

async function savePwd() {
  await pwdRef.value.validate(async (valid) => {
    if (!valid) return
    savingPwd.value = true
    try {
      await authApi.changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
      ElMessage.success('密码修改成功，请牢记新密码')
      pwdForm.oldPassword = ''
      pwdForm.newPassword = ''
      pwdForm.confirm = ''
    } finally {
      savingPwd.value = false
    }
  })
}

onMounted(async () => {
  const user = await store.refreshProfile()
  infoForm.realName = user.realName
  infoForm.phone = user.phone
  infoForm.email = user.email
  const res = await statsApi.dashboard()
  overview.value = res.data || {}
})
</script>

<style scoped>
.profile-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
  align-items: start;
}

@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}

.side {
  text-align: center;
  padding: 30px 22px;
}

.avatar-big {
  width: 74px;
  height: 74px;
  border-radius: 22px;
  margin: 0 auto 14px;
  background: linear-gradient(135deg, #4c6ef5, #7c93ff);
  color: #fff;
  font-size: 30px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 24px rgba(76, 110, 245, 0.28);
}

.p-name {
  font-size: 17px;
  font-weight: 600;
  margin-bottom: 8px;
}

.p-list {
  margin-top: 22px;
  text-align: left;
  border-top: 1px dashed var(--border);
  padding-top: 16px;
}

.p-item {
  display: flex;
  justify-content: space-between;
  font-size: 12.5px;
  padding: 7px 0;
}

.p-item span {
  color: var(--muted);
}

.p-item b {
  font-weight: 500;
  color: #374151;
  max-width: 150px;
  text-align: right;
}

.main {
  min-height: 420px;
}

.overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-top: 8px;
}

@media (max-width: 1100px) {
  .overview {
    grid-template-columns: repeat(2, 1fr);
  }
}

.ov-card {
  background: #f8fafd;
  border-radius: 12px;
  padding: 20px;
  text-align: center;
}

.ov-num {
  font-size: 26px;
  font-weight: 600;
  color: var(--brand);
}

.ov-danger {
  color: #f56c6c;
}

.ov-label {
  font-size: 12px;
  color: var(--muted);
  margin-top: 6px;
}
</style>
