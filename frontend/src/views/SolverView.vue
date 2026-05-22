<template>
  <div class="solver-view">
    <!-- 左侧会话列表 -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <h3>📝 问题求解</h3>
        <el-button type="primary" size="small" circle @click="newConversation" title="新建问题">
          <el-icon><Plus /></el-icon>
        </el-button>
      </div>

      <!-- 模型切换 -->
      <div class="model-switch">
        <el-select v-model="selectedModel" size="small" style="width: 100%">
          <el-option label="通义千问 (DashScope)" value="dashscope" />
          <el-option label="DeepSeek" value="deepseek" />
        </el-select>
      </div>

      <!-- 隐私模式 -->
      <div class="privacy-switch">
        <el-switch
          v-model="incognitoMode"
          active-text="无痕"
          inactive-text="普通"
          size="small"
          @change="onIncognitoChange"
        />
      </div>

      <div class="conversation-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: conv.id === currentConversationId }"
          @click="switchConversation(conv)"
        >
          <span class="conv-title">{{ conv.title || '新问题' }}</span>
          <el-tag v-if="conv.conversationMode === 'INCOGNITO'" size="small" type="danger" class="incognito-tag">无痕</el-tag>
          <el-button
            class="conv-delete"
            size="small"
            text
            type="danger"
            :icon="Delete"
            @click.stop="handleDeleteConversation(conv.id)"
          />
        </div>
        <el-empty v-if="conversations.length === 0" description="暂无问题" :image-size="60" />
      </div>

      <div class="sidebar-footer">
        <el-button text @click="sidebarCollapsed = !sidebarCollapsed">
          <el-icon><Fold /></el-icon>
        </el-button>
      </div>
    </aside>

    <!-- 右侧聊天区域 -->
    <main class="chat-main">
      <header class="chat-header">
        <el-button text @click="sidebarCollapsed = !sidebarCollapsed" class="toggle-btn" :class="{ 'always-show': sidebarCollapsed }">
          <el-icon><Expand /></el-icon>
        </el-button>
        <span class="current-title">问题求解助手</span>
        <el-tag type="success" size="small">解题模式</el-tag>
        <el-tag size="small" style="margin-left: 6px">{{ selectedModel === 'deepseek' ? 'DeepSeek' : '通义千问' }}</el-tag>
      </header>

      <div class="message-area" ref="messageArea">
        <div v-if="!currentConversationId" class="welcome">
          <h1>📝 问题求解助手</h1>
          <p>你好！我是你的学习辅导老师，我会引导你<b>独立思考和分步解题</b>，而不是直接给出答案~</p>
          <div class="tips">
            <h4>💡 使用建议</h4>
            <ul>
              <li>直接输入你的问题，我会引导你逐步思考</li>
              <li>可以上传题目截图（描述题目内容即可）</li>
              <li>跟随引导步骤，尝试自己解答</li>
              <li>支持数学、物理、编程等各学科</li>
            </ul>
          </div>
          <div class="quick-actions">
            <el-button
              v-for="q in quickQuestions"
              :key="q"
              size="small"
              round
              @click="sendMessage(q)"
            >{{ q }}</el-button>
          </div>
        </div>

        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          class="message"
          :class="msg.role === 'USER' ? 'user-msg' : 'assistant-msg'"
        >
          <div class="msg-avatar">
            <el-avatar :size="32" :icon="msg.role === 'USER' ? UserFilled : Service" />
          </div>
          <div class="msg-content">
            <div class="msg-text" v-html="renderMsg(msg)"></div>
          </div>
        </div>

        <div v-if="streaming" class="message assistant-msg">
          <div class="msg-avatar">
            <el-avatar :size="32" :icon="Service" />
          </div>
          <div class="msg-content">
            <div class="msg-text streaming-text" v-html="renderMsg({ content: streamContent })"></div>
          </div>
        </div>
      </div>

      <footer class="input-area">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          placeholder="输入你的问题，Enter 发送，Shift+Enter 换行..."
          resize="none"
          :disabled="loading"
          @keydown.enter.exact.prevent="sendMessage(inputText)"
        />
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!inputText.trim()"
          @click="sendMessage(inputText)"
        >
          <el-icon><Promotion /></el-icon>
        </el-button>
      </footer>
    </main>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { Plus, Delete, Fold, Expand, Promotion, Loading, UserFilled, Service } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getConversations, createConversation, getMessages, deleteConversation } from '../api/chat'
import { renderMarkdown } from '../utils/markdown'

// ---- 状态 ----
const conversations = ref([])
const currentConversationId = ref(null)
const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const streaming = ref(false)
const streamContent = ref('')
const sidebarCollapsed = ref(false)
const selectedModel = ref('dashscope')
const incognitoMode = ref(false)

const quickQuestions = [
  '解方程：2x² + 3x - 5 = 0',
  '什么是牛顿第二定律？',
  '写一个 Python 函数判断素数',
  '如何写好英语作文的开头？'
]

onMounted(async () => {
  await loadConversations()
  if (conversations.value.length > 0) {
    switchConversation(conversations.value[0])
  } else {
    await newConversation()
  }
  window.addEventListener('beforeunload', cleanupIncognitoOnClose)
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', cleanupIncognitoOnClose)
})

function cleanupIncognitoOnClose() {
  conversations.value.forEach(c => {
    if (c.conversationMode === 'INCOGNITO' || c.threadId === 'INCOGNITO') {
      try {
        navigator.sendBeacon(`/api/solver/conversations/${c.id}`, new Blob())
      } catch (_) {}
    }
  })
}

async function loadConversations() {
  try {
    const res = await fetch('/api/solver/conversations')
    const data = await res.json()
    conversations.value = data?.data || []
  } catch (e) {
    conversations.value = []
  }
}

async function newConversation() {
  try {
    const mode = incognitoMode.value ? 'INCOGNITO' : 'NORMAL'
    const res = await fetch(`/api/solver/conversations?firstMessage=新问题&mode=${mode}`, { method: 'POST' })
    const data = await res.json()
    const conv = data?.data
    if (!conv) {
      ElMessage.error('创建会话失败')
      return
    }
    conversations.value.unshift(conv)
    switchConversation(conv)
  } catch (e) {
    ElMessage.error('创建会话失败: ' + e.message)
  }
}

async function switchConversation(conv) {
  if (!conv || !conv.id) return
  currentConversationId.value = conv.id
  if (conv.conversationMode === 'INCOGNITO' || conv.threadId === 'INCOGNITO') {
    incognitoMode.value = true
  }
  try {
    const res = await fetch(`/api/solver/conversations/${conv.id}/messages`)
    const data = await res.json()
    messages.value = (data?.data || []).map(m => ({ ...m, sources: [] }))
  } catch (e) {
    messages.value = []
  }
  await nextTick()
  scrollToBottom()
}

function onIncognitoChange() {
  if (!currentConversationId.value) newConversation()
}

async function handleDeleteConversation(id) {
  await fetch(`/api/solver/conversations/${id}`, { method: 'DELETE' })
  conversations.value = conversations.value.filter(c => c.id !== id)
  if (currentConversationId.value === id) {
    if (conversations.value.length > 0) {
      switchConversation(conversations.value[0])
    } else {
      currentConversationId.value = null
      messages.value = []
    }
  }
}

async function sendMessage(text) {
  const msg = text?.trim()
  if (!msg || loading.value) return

  if (!currentConversationId.value) await newConversation()

  inputText.value = ''
  loading.value = true

  messages.value.push({ role: 'USER', content: msg })
  await nextTick()
  scrollToBottom()

  streaming.value = true
  streamContent.value = ''

  try {
    const response = await fetch('/api/solver/solve', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        conversationId: currentConversationId.value,
        message: msg,
        model: selectedModel.value
      })
    })

    if (!response.ok) {
      const errText = await response.text()
      throw new Error(`HTTP ${response.status}: ${errText}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      streamContent.value += buffer
      buffer = ''
    }

    if (streamContent.value) {
      messages.value.push({ role: 'ASSISTANT', content: streamContent.value, sources: [] })
    }
  } catch (e) {
    ElMessage.error('请求失败: ' + e.message)
    if (streamContent.value) {
      messages.value.push({ role: 'ASSISTANT', content: streamContent.value + '\n\n[连接中断]', sources: [] })
    }
  } finally {
    streaming.value = false
    streamContent.value = ''
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

function renderMsg(msg) {
  if (msg.role === 'USER') return escapeHtml(msg.content)
  return renderMarkdown(msg.content || '')
}

function escapeHtml(text) {
  if (!text) return ''
  return text.replace(/&/g, '&').replace(/</g, '<').replace(/>/g, '>').replace(/\n/g, '<br>')
}

function scrollToBottom() {
  nextTick(() => {
    const el = document.querySelector('.solver-view .message-area')
    if (el) el.scrollTop = el.scrollHeight
  })
}
</script>

<style scoped>
.solver-view {
  display: flex;
  height: 100vh;
  width: 100vw;
  background: #f0fdf4;
}

.sidebar {
  width: 280px;
  min-width: 280px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  transition: all 0.3s;
  overflow: hidden;
}

.sidebar.collapsed {
  width: 0;
  min-width: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #eee;
}

.sidebar-header h3 {
  font-size: 15px;
  color: #303133;
  white-space: nowrap;
}

.model-switch {
  padding: 10px 16px;
  border-bottom: 1px solid #eee;
}

.privacy-switch {
  padding: 8px 16px;
  border-bottom: 1px solid #eee;
  display: flex;
  align-items: center;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conv-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
  margin-bottom: 4px;
  gap: 6px;
}

.conv-item:hover { background: #f0f2f5; }
.conv-item.active { background: #dcfce7; color: #16a34a; }

.conv-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.incognito-tag { flex-shrink: 0; }

.conv-delete {
  opacity: 0;
  transition: opacity 0.2s;
  flex-shrink: 0;
}
.conv-item:hover .conv-delete { opacity: 1; }

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid #eee;
  display: flex;
  justify-content: center;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #dcfce7;
  gap: 10px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}

.toggle-btn { flex-shrink: 0; }
.always-show { display: inline-flex !important; }

.current-title {
  flex: 1;
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.message-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.welcome {
  text-align: center;
  padding-top: 80px;
}

.welcome h1 {
  font-size: 28px;
  color: #16a34a;
  margin-bottom: 12px;
}

.welcome p {
  color: #606266;
  margin-bottom: 24px;
  line-height: 1.6;
}

.welcome .tips {
  text-align: left;
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  margin: 0 auto 24px;
  max-width: 480px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.welcome .tips h4 {
  margin-bottom: 10px;
  color: #16a34a;
}

.welcome .tips ul {
  padding-left: 20px;
  line-height: 2;
  color: #606266;
}

.quick-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;
}

.message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.user-msg { flex-direction: row-reverse; }

.msg-avatar { flex-shrink: 0; }

.msg-content {
  max-width: 75%;
  background: #fff;
  border-radius: 12px;
  padding: 12px 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

.user-msg .msg-content {
  background: #16a34a;
  color: #fff;
}

.msg-text {
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.msg-text :deep(p) { margin: 4px 0; }
.msg-text :deep(pre) {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px;
  overflow-x: auto;
  font-size: 13px;
}

.streaming-text { color: #909399; }

.input-area {
  display: flex;
  gap: 10px;
  padding: 16px 20px;
  background: #fff;
  border-top: 1px solid #dcfce7;
  align-items: flex-end;
}

.input-area :deep(.el-textarea__inner) {
  border-radius: 10px;
}
</style>
