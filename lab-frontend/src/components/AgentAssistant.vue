<template>
  <div class="agent-root">
    <button v-if="!open" class="agent-launcher" @click="open = true" aria-label="打开实验室智能助手">
      <span>✦</span> 智能助手
    </button>
    <section v-show="open" class="agent-panel" aria-label="实验室智能助手">
      <header class="agent-header">
        <div><strong>✦ 实验室智能助手</strong><small>{{ mode === 'rules' ? '规则演示模式' : '业务工具 · 流式对话' }}</small></div>
        <div class="agent-header-actions">
          <button @click="clear" :disabled="busy || confirming" title="清理会话">新对话</button>
          <button @click="open = false" aria-label="收起助手">×</button>
        </div>
      </header>
      <div class="agent-mode">
        <label><input v-model="ruleMode" type="checkbox" :disabled="busy"> 使用规则演示（不调用付费模型）</label>
        <span>{{ statusText }}</span>
      </div>
      <div ref="scrollArea" class="agent-messages" role="log" aria-live="polite">
        <div v-if="!messages.length" class="agent-welcome">
          <h3>把预约和报修交给助手</h3>
          <p>查询使用真实业务数据；提交、取消等操作先展示确认卡片。</p>
          <button v-for="example in examples" :key="example" @click="draft = example">{{ example }}</button>
          <p class="agent-hint">规则模式需按示例提供完整条件。模型模式支持多轮追问。会话在内存保留30分钟，服务重启后失效。</p>
        </div>
        <article v-for="(message, index) in messages" :key="index" :class="['agent-message', message.role]">
          <div class="agent-speaker">{{ message.role === 'user' ? '你' : '实验室助手' }}</div>
          <div v-if="message.modeNote" class="agent-mode-note">{{ message.modeNote }}</div>
          <details v-if="message.traces?.length" class="agent-traces">
            <summary>工具调用轨迹（{{ message.traces.length }} 条）</summary>
            <div v-for="(trace, traceIndex) in message.traces" :key="traceIndex" class="agent-trace">
              <div>{{ toolLabels[trace.name] || trace.name }} · {{ trace.state === 'running' ? '正在调用' : trace.state === 'success' ? '成功' : '未完成' }} <span v-if="trace.durationMs != null">{{ trace.durationMs }}ms</span></div>
              <div v-if="trace.message">{{ trace.message }}</div>
              <details v-if="trace.result"><summary>查看真实返回</summary><pre>{{ format(trace.result) }}</pre></details>
            </div>
          </details>
          <p v-if="message.text" class="agent-text">{{ message.text }}</p>
          <div v-for="action in message.actions || []" :key="action.id" class="agent-card">
            <strong>待确认操作</strong>
            <dl><template v-for="(value, key) in action.details" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></template></dl>
            <p class="agent-hint">卡片10分钟内有效，提交时会再次检查业务规则。</p>
            <button class="agent-primary" :disabled="busy || !!confirming || action.done || action.cancelled" @click="confirmAction(action)">
              {{ action.done ? '已确认执行' : action.cancelled ? '已放弃' : confirming === action.id ? '正在提交…' : '确认执行' }}
            </button>
            <button v-if="!action.done && !action.cancelled" :disabled="busy || !!confirming" @click="action.cancelled = true">放弃</button>
            <p v-if="action.error" class="agent-error">{{ action.error }}</p>
            <div v-if="action.result" class="agent-result">
              {{ action.result.message }} <span v-if="action.result.orderNo">· {{ action.result.orderNo }}</span>
              <button v-if="action.result.path" @click="viewResult(action.result.path)">查看业务记录</button>
            </div>
          </div>
          <p v-if="message.error" class="agent-error">{{ message.error }}</p>
          <p v-if="busy && index === messages.length - 1" class="agent-working">{{ progress || '正在处理…' }}</p>
        </article>
      </div>
      <form class="agent-composer" @submit.prevent="send">
        <textarea v-model="draft" maxlength="2000" rows="2" :disabled="busy || !!confirming" placeholder="例如：预约 明天 14:00-16:00 4人 用途：数据库实验" aria-label="给助手发送消息" @keydown.enter.exact.prevent="send" />
        <div><span>Enter 发送 · Shift+Enter 换行</span><button class="agent-primary" type="submit" :disabled="busy || !!confirming || !draft.trim()">{{ busy ? '处理中…' : '发送' }}</button></div>
      </form>
    </section>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { agentApi, streamAgent } from '@/api/agent'

const router = useRouter()
const open = ref(false), draft = ref(''), busy = ref(false), confirming = ref(''), sessionId = ref('')
const mode = ref('rules'), ruleMode = ref(false), statusText = ref('正在检查模型配置'), messages = ref([])
const progress = ref(''), scrollArea = ref(null)
let controller
const examples = ['我的预约', '预约 明天 14:00-16:00 4人 用途：数据库实验', '推荐 明天 4人 120分钟', '查设备 显微镜', '我的报修', '数据统计']
const toolLabels = { query_my_credit: '查询信用与奖惩', search_labs: '查询可用实验室', recommend_slots: '推荐时段', query_my_reservations: '查询本人预约', search_equipment: '查询设备', query_my_repairs: '查询工单', dashboard_summary: '汇总数据', prepare_reservation: '生成预约卡片', prepare_cancel: '生成取消卡片', prepare_fault: '生成报修卡片' }
const format = (value) => JSON.stringify(value, null, 2)
async function scrollBottom() { await nextTick(); if (scrollArea.value) scrollArea.value.scrollTop = scrollArea.value.scrollHeight }

async function send() {
  if (busy.value || confirming.value || !draft.value.trim()) return
  const text = draft.value.trim()
  draft.value = ''
  messages.value.push({ role: 'user', text })
  // Keep a local reference to the reactive object so streaming updates render immediately.
  messages.value.push({ role: 'assistant', text: '', traces: [], actions: [] })
  const reply = messages.value[messages.value.length - 1]
  busy.value = true; progress.value = ''; controller = new AbortController()
  scrollBottom()
  try {
    await streamAgent({ sessionId: sessionId.value || null, message: text, ruleMode: ruleMode.value }, (event, data) => {
      if (event === 'session') sessionId.value = data.sessionId
      if (event === 'mode') { mode.value = data.mode; reply.modeNote = data.message }
      if (event === 'status') progress.value = data.message
      if (event === 'delta') reply.text += data.text
      if (event === 'reset') reply.text = ''
      if (event === 'tool') reply.traces.push(data)
      if (event === 'action') reply.actions.push(data)
      if (event === 'error') reply.error = data.message
      scrollBottom()
    }, controller.signal)
  } catch (error) {
    if (error.name !== 'AbortError') reply.error = error.message || '助手连接失败'
  } finally { busy.value = false; progress.value = ''; controller = null; scrollBottom() }
}

async function confirmAction(action) {
  if (busy.value || confirming.value || action.done || action.cancelled) return
  confirming.value = action.id; action.error = ''
  try {
    const response = await agentApi.confirm({ sessionId: sessionId.value, actionId: action.id })
    action.done = true; action.result = response.data
  } catch (error) { action.error = error.message || '提交失败，请核对业务页面后重试' }
  finally { confirming.value = ''; scrollBottom() }
}

function viewResult(path) {
  if (!['/my-reservations', '/repairs'].includes(path)) return
  // A full navigation refreshes mounted business lists after assistant writes.
  window.location.assign(router.resolve(path).href)
}
async function clear() {
  if (busy.value || confirming.value) return
  if (sessionId.value) {
    try { await agentApi.clear(sessionId.value) }
    catch { /* Expired sessions are already gone; clearing local UI is sufficient. */ }
  }
  sessionId.value = ''; messages.value = []
}
onMounted(async () => {
  try {
    const { data } = await agentApi.status()
    statusText.value = data.message; mode.value = data.mode; ruleMode.value = !data.configured
  } catch { statusText.value = '暂时无法检查配置，可尝试规则模式'; ruleMode.value = true }
})
onUnmounted(() => controller?.abort())
</script>

<style scoped>
.agent-root { position: fixed; bottom: 24px; right: 24px; z-index: 2100; font-size: 13px; color: #27344c; }
.agent-root button { font: inherit; cursor: pointer; border: 1px solid #dce3ef; border-radius: 8px; padding: 7px 11px; background: white; color: inherit; }
.agent-root button:disabled { cursor: default; opacity: .5; }
.agent-root button:hover:not(:disabled) { border-color: #647ffa; }
.agent-root .agent-launcher { background: #4c6ef5; color: white; border: none; border-radius: 24px; padding: 14px 20px; box-shadow: 0 6px 24px #263d8c30; }
.agent-launcher span { margin-right: 7px; font-size: 20px; }
.agent-panel { display: flex; flex-direction: column; width: 480px; height: min(760px, calc(100dvh - 48px)); background: #fff; border: 1px solid #e0e6f1; border-radius: 16px; box-shadow: 0 12px 60px #172c5433; overflow: hidden; }
.agent-header { background: #243550; color: white; display: flex; align-items: center; justify-content: space-between; padding: 17px; }
.agent-header strong { font-size: 16px; }.agent-header small { display: block; color: #bdcce5; margin-top: 5px; }
.agent-header-actions { display: flex; gap: 6px; }.agent-header-actions button { background: transparent; color: #fff; border-color: #52617b; }
.agent-mode { display: flex; flex-direction: column; gap: 6px; padding: 10px 16px; background: #f4f7fd; font-size: 12px; }.agent-mode span { color: #7c879a; }
.agent-messages { flex: 1; overflow-y: auto; padding: 16px; min-height: 0; }
.agent-welcome h3 { font-size: 18px; margin: 10px 0; }.agent-welcome p { color: #788398; line-height: 1.8; }.agent-welcome button { display: block; margin: 8px 0; text-align: left; }
.agent-message { margin-bottom: 18px; }.agent-message.user { background: #eef2ff; padding: 12px; border-radius: 12px; margin-left: 30px; }
.agent-speaker { color: #8793a7; font-size: 11px; margin-bottom: 7px; }.agent-text { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; margin: 8px 0; }
.agent-mode-note { font-size: 11px; color: #8b6a24; background: #fff7e5; padding: 6px 9px; border-radius: 6px; }
.agent-traces { margin: 8px 0; padding: 8px; background: #f5f7fb; font-size: 11px; border-radius: 8px; }.agent-traces summary { cursor: pointer; }.agent-trace { padding: 8px 0; border-top: 1px solid #e6eaf2; margin-top: 7px; }.agent-trace pre { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 200px; overflow-y: auto; }
.agent-card { border: 1px solid #bdcafc; padding: 13px; border-radius: 10px; margin-top: 10px; background: #fafbff; }.agent-card dl { display: grid; grid-template-columns: 70px 1fr; gap: 7px; font-size: 12px; }.agent-card dt { color: #7a8598; }.agent-card dd { margin: 0; overflow-wrap: anywhere; }.agent-card button { margin-right: 6px; }
.agent-root .agent-primary { background: #4c6ef5; border-color: #4c6ef5; color: white; }.agent-hint { font-size: 11px; color: #8b94a4; line-height: 1.7; }.agent-error { color: #c24545; font-size: 12px; line-height: 1.7; }.agent-working { font-size: 12px; color: #4c6ef5; }.agent-result { color: #25875a; line-height: 1.8; margin-top: 10px; }.agent-result button { margin-top: 6px; }
.agent-composer { border-top: 1px solid #e7ebf2; padding: 12px; }.agent-composer textarea { width: 100%; box-sizing: border-box; border: 1px solid #dce3ef; border-radius: 8px; padding: 10px; font: inherit; resize: none; outline-color: #4c6ef5; }.agent-composer > div { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; }.agent-composer span { font-size: 11px; color: #8b94a4; }
@media (max-width: 600px) { .agent-root { bottom: 12px; right: 12px; }.agent-panel { width: calc(100vw - 24px); height: calc(100dvh - 24px); } }
</style>
