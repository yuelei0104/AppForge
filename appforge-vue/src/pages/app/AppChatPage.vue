<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  CloudUploadOutlined,
  CopyOutlined,
  DownOutlined,
  EditOutlined,
  HomeOutlined,
  LinkOutlined,
  SendOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import logoUrl from '@/assets/logo.png'
import { deployApp, getAppVoById } from '@/api/appController'
import { API_BASE_URL } from '@/request'
import { useLoginUserStore } from '@/stores/loginUser'
import { getAppPreviewUrl } from '@/utils/appUrls'

interface ChatMessage {
  id: string
  role: 'user' | 'ai'
  content: string
  time: string
  status?: 'streaming' | 'done' | 'error'
}

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const appId = computed(() => String(route.params.appId ?? ''))
const isViewMode = computed(() => route.query.view === '1')
const app = ref<API.AppVO | null>(null)
const appLoading = ref(true)
const messages = ref<ChatMessage[]>([])
const input = ref('')
const streaming = ref(false)
const messagesRef = ref<HTMLElement | null>(null)

const previewVisible = ref(false)
const previewLoading = ref(false)
const previewUrl = ref('')
const previewKey = ref(0)

const deployLoading = ref(false)
const deployModalOpen = ref(false)
const deployUrl = ref('')

let eventSource: EventSource | null = null
let messageSeed = 0
let loadSequence = 0

const appName = computed(() => app.value?.appName || '未命名应用')
const isOwner = computed(
  () =>
    app.value?.userId != null &&
    loginUserStore.loginUser.id != null &&
    String(app.value.userId) === String(loginUserStore.loginUser.id),
)
const readOnlyHint = computed(() =>
  app.value && !isOwner.value ? '无法在别人的作品下对话哦～' : undefined,
)

function nextMessageId() {
  messageSeed += 1
  return `message-${messageSeed}`
}

function formatTime() {
  const date = new Date()
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function scrollToBottom() {
  await nextTick()
  const container = messagesRef.value
  if (container) {
    container.scrollTop = container.scrollHeight
  }
}

function closeStream() {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

function finishStream(aiMessage: ChatMessage, success: boolean) {
  if (!streaming.value) {
    return
  }
  streaming.value = false
  previewLoading.value = false
  closeStream()

  if (success) {
    aiMessage.status = 'done'
    previewUrl.value = `${getAppPreviewUrl(appId.value)}?t=${Date.now()}`
    previewVisible.value = true
    previewKey.value += 1
    message.success('应用生成完成')
  } else {
    aiMessage.status = 'error'
    if (!aiMessage.content) {
      aiMessage.content = '生成失败，请稍后重试。'
    }
    message.error('生成失败，请稍后重试')
  }
  void scrollToBottom()
}

function openEventSource(userMessage: string, aiMessage: ChatMessage) {
  const url = `${API_BASE_URL}/app/chat/gen/code?appId=${encodeURIComponent(
    appId.value,
  )}&message=${encodeURIComponent(userMessage)}`

  const es = new EventSource(url, { withCredentials: true })
  eventSource = es

  es.onmessage = (event) => {
    if (eventSource !== es || !streaming.value) {
      return
    }
    try {
      const payload = JSON.parse(event.data) as { d?: string }
      if (payload.d) {
        aiMessage.content += payload.d
      }
    } catch {
      aiMessage.content += event.data ?? ''
    }
    void scrollToBottom()
  }

  es.addEventListener('done', () => {
    if (eventSource === es) {
      finishStream(aiMessage, true)
    }
  })

  es.onerror = () => {
    if (eventSource === es && streaming.value) {
      finishStream(aiMessage, false)
    }
  }
}

function sendMessage(rawMessage: string) {
  if (!isOwner.value) {
    message.warning(readOnlyHint.value || '应用信息尚未加载')
    return
  }
  const content = rawMessage.trim()
  if (!content) {
    message.warning('请输入消息内容')
    return
  }
  if (streaming.value) {
    message.warning('AI 正在生成中，请稍候')
    return
  }

  const userMessage: ChatMessage = {
    id: nextMessageId(),
    role: 'user',
    content,
    time: formatTime(),
    status: 'done',
  }
  const aiMessage = reactive<ChatMessage>({
    id: nextMessageId(),
    role: 'ai',
    content: '',
    time: formatTime(),
    status: 'streaming',
  })
  messages.value.push(userMessage, aiMessage)
  input.value = ''
  streaming.value = true
  previewLoading.value = true
  void scrollToBottom()

  openEventSource(content, aiMessage)
}

function handleSend() {
  sendMessage(input.value)
}

function handleComposerKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
    event.preventDefault()
    handleSend()
  }
}

async function loadApp() {
  const sequence = ++loadSequence
  closeStream()
  streaming.value = false
  previewLoading.value = false
  previewVisible.value = false
  previewUrl.value = ''
  app.value = null
  messages.value = []
  input.value = ''
  if (!appId.value) {
    message.error('应用不存在')
    await router.replace('/')
    return
  }
  appLoading.value = true
  try {
    const res = await getAppVoById({ id: appId.value })
    if (sequence !== loadSequence) {
      return
    }
    if (res.data.code === 0 && res.data.data) {
      app.value = res.data.data
      if (isViewMode.value || !isOwner.value) {
        previewUrl.value = getAppPreviewUrl(appId.value)
        previewVisible.value = true
        previewKey.value += 1
      } else if (res.data.data.initPrompt) {
        sendMessage(res.data.data.initPrompt)
        // 首次创建自动生成后也切换为浏览地址，刷新不会重复调用模型。
        void router.replace({ path: route.path, query: { ...route.query, view: '1' } })
      }
    } else if (res.data.code === 40101) {
      message.error('无权访问该应用')
      await router.replace('/noAuth')
    } else {
      message.error(res.data.message || '应用不存在')
      await router.replace('/')
    }
  } catch {
    if (sequence !== loadSequence) {
      return
    }
    message.error('获取应用信息失败，请稍后重试')
    await router.replace('/')
  } finally {
    if (sequence === loadSequence) {
      appLoading.value = false
    }
  }
}

async function handleDeploy() {
  if (!app.value || !isOwner.value) {
    return
  }
  deployLoading.value = true
  try {
    const res = await deployApp({ appId: appId.value })
    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = res.data.data
      deployModalOpen.value = true
    } else {
      message.error(res.data.message || '部署失败')
    }
  } catch {
    message.error('部署失败，请稍后重试')
  } finally {
    deployLoading.value = false
  }
}

async function copyDeployUrl() {
  try {
    await navigator.clipboard.writeText(deployUrl.value)
    message.success('部署地址已复制')
  } catch {
    message.warning('复制失败，请手动复制')
  }
}

function openPreview() {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank', 'noopener,noreferrer')
  }
}

function handleAppMenuClick({ key }: { key: string | number }) {
  if (key === 'edit') {
    if (isOwner.value) {
      void router.push(`/app/edit/${appId.value}`)
    }
  } else if (key === 'home') {
    void router.push('/')
  }
}

watch(
  () => route.params.appId,
  () => void loadApp(),
  { immediate: true },
)

onBeforeUnmount(() => {
  loadSequence += 1
  closeStream()
  streaming.value = false
})
</script>

<template>
  <div class="app-chat-page">
    <div class="app-chat-page__topbar">
      <div class="app-chat-page__topbar-left">
        <a-button type="text" class="app-chat-page__back" @click="router.push('/')">
          <template #icon>
            <ArrowLeftOutlined />
          </template>
        </a-button>

        <a-dropdown :trigger="['click']">
          <button type="button" class="app-chat-page__app-name">
            <span>{{ appName }}</span>
            <DownOutlined />
          </button>
          <template #overlay>
            <a-menu @click="handleAppMenuClick">
              <a-menu-item v-if="isOwner" key="edit">
                <EditOutlined />
                编辑应用信息
              </a-menu-item>
              <a-menu-item key="home">
                <HomeOutlined />
                返回首页
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>

        <a-tag v-if="streaming" color="processing">AI 生成中</a-tag>
      </div>

      <a-button
        v-if="isOwner"
        type="primary"
        :loading="deployLoading"
        :disabled="!app || streaming"
        @click="handleDeploy"
      >
        <template #icon>
          <CloudUploadOutlined />
        </template>
        部署
      </a-button>
    </div>

    <div class="app-chat-page__body">
      <section class="chat-panel">
        <div ref="messagesRef" class="chat-panel__messages">
          <a-spin v-if="appLoading" class="chat-panel__loading" />
          <div v-else-if="!messages.length" class="chat-panel__empty">
            <div class="chat-panel__empty-icon">AI</div>
            <p v-if="isViewMode">正在浏览应用，暂未保存历史对话。</p>
            <p v-else>描述你的想法，AI 会帮你生成一个完整的网站应用。</p>
          </div>

          <div
            v-for="item in messages"
            :key="item.id"
            class="chat-message"
            :class="item.role === 'user' ? 'chat-message--user' : 'chat-message--ai'"
          >
            <a-avatar
              v-if="item.role === 'user'"
              :src="loginUserStore.loginUser.userAvatar || undefined"
              :size="32"
              class="chat-message__avatar"
            >
              <template v-if="!loginUserStore.loginUser.userAvatar">
                <UserOutlined />
              </template>
            </a-avatar>
            <a-avatar v-else :src="logoUrl" :size="32" class="chat-message__avatar" />

            <div class="chat-message__body">
              <div class="chat-message__bubble">
                <div class="chat-message__content">
                  {{ item.content
                  }}<span v-if="item.status === 'streaming'" class="chat-message__cursor" />
                </div>
              </div>
              <div class="chat-message__meta">
                <span>{{ item.role === 'user' ? '我' : 'AI 助手' }}</span>
                <span>{{ item.time }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="chat-panel__composer">
          <a-tooltip :title="readOnlyHint">
            <div>
              <a-textarea
                v-model:value="input"
                :auto-size="{ minRows: 2, maxRows: 6 }"
                :maxlength="2000"
                :disabled="streaming || appLoading || !isOwner"
                :placeholder="
                  readOnlyHint || '描述你想创建或修改的内容，例如：把页面配色改成暗黑风格'
                "
                @keydown="handleComposerKeydown"
              />
            </div>
          </a-tooltip>
          <div class="chat-panel__composer-footer">
            <span class="chat-panel__composer-hint">{{
              readOnlyHint || 'Ctrl / ⌘ + Enter 发送'
            }}</span>
            <a-button
              type="primary"
              :disabled="!input.trim() || streaming || appLoading || !isOwner"
              :loading="streaming"
              @click="handleSend"
            >
              <template #icon>
                <SendOutlined />
              </template>
              发送
            </a-button>
          </div>
        </div>
      </section>

      <section class="preview-panel">
        <div class="preview-panel__header">
          <span class="preview-panel__title">
            <LinkOutlined />
            生成效果
          </span>
          <a-button v-if="previewVisible" size="small" @click="openPreview">新窗口打开</a-button>
        </div>
        <div class="preview-panel__body">
          <iframe
            v-if="previewVisible"
            :key="previewKey"
            class="preview-panel__iframe"
            :src="previewUrl"
            title="应用预览"
            sandbox="allow-forms allow-modals allow-scripts allow-same-origin"
          />
          <div v-else class="preview-panel__empty">
            <div class="preview-panel__empty-icon">⌘</div>
            <p>完成对话后，生成的网站会展示在这里</p>
          </div>

          <div v-if="streaming && previewLoading" class="preview-panel__mask">
            <a-spin />
            <span>正在生成页面...</span>
          </div>
        </div>
      </section>
    </div>

    <a-modal v-model:open="deployModalOpen" title="部署成功" :footer="null" width="520px">
      <p class="deploy-modal__desc">你的应用已部署成功，可以通过以下地址访问：</p>
      <a-space direction="vertical" style="width: 100%">
        <a-input :value="deployUrl" readonly>
          <template #addonAfter>
            <a-button type="text" size="small" @click="copyDeployUrl">
              <template #icon>
                <CopyOutlined />
              </template>
            </a-button>
          </template>
        </a-input>
        <a-button type="primary" block :href="deployUrl" target="_blank">打开部署应用</a-button>
      </a-space>
    </a-modal>
  </div>
</template>

<style scoped>
.app-chat-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 560px;
  height: calc(100vh - 64px - 63px - 48px);
}

.app-chat-page__topbar {
  display: flex;
  flex: none;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  padding: 8px 16px;
  background: #fff;
  border: 1px solid #edf0f5;
  border-radius: 14px;
}

.app-chat-page__topbar-left {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.app-chat-page__back {
  flex: none;
}

.app-chat-page__app-name {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  max-width: 320px;
  padding: 6px 10px;
  overflow: hidden;
  font-size: 16px;
  font-weight: 600;
  color: #102a2a;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  background: #f5f8fb;
  border: none;
  border-radius: 10px;
}

.app-chat-page__app-name span {
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-chat-page__body {
  display: flex;
  flex: 1;
  gap: 16px;
  min-height: 0;
}

.chat-panel,
.preview-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #edf0f5;
  border-radius: 16px;
}

.chat-panel {
  flex: 0 0 420px;
}

.chat-panel__messages {
  flex: 1;
  min-height: 0;
  padding: 20px 18px;
  overflow-y: auto;
  background: #fbfcfe;
}

.chat-panel__loading {
  display: block;
  margin: 40px auto;
}

.chat-panel__empty,
.preview-panel__empty {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 220px;
  padding: 24px;
  font-size: 13px;
  color: #98a2b3;
  text-align: center;
}

.chat-panel__empty-icon,
.preview-panel__empty-icon {
  display: grid;
  width: 54px;
  height: 54px;
  place-items: center;
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #4f7cff, #53c8d8);
  border-radius: 16px;
}

.chat-message {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.chat-message--user {
  flex-direction: row-reverse;
}

.chat-message__avatar {
  flex: none;
}

.chat-message__body {
  max-width: calc(100% - 44px);
}

.chat-message--user .chat-message__body {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.chat-message__bubble {
  padding: 11px 14px;
  font-size: 13px;
  line-height: 1.7;
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 4px 14px 14px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.03);
}

.chat-message--user .chat-message__bubble {
  color: #fff;
  background: linear-gradient(135deg, #4f7cff, #5b8def);
  border: none;
  border-radius: 14px 4px 14px 14px;
}

.chat-message__content {
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.chat-message__cursor {
  display: inline-block;
  width: 6px;
  height: 15px;
  margin-left: 2px;
  vertical-align: -2px;
  background: #4f7cff;
  animation: blink 0.9s step-end infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.chat-message__meta {
  display: flex;
  gap: 8px;
  margin-top: 5px;
  font-size: 11px;
  color: #aab4c5;
}

.chat-panel__composer {
  flex: none;
  padding: 12px 14px 14px;
  background: #fff;
  border-top: 1px solid #f0f3f8;
}

.chat-panel__composer :deep(textarea) {
  padding: 10px 12px;
  font-size: 13px;
  background: #f8fafc;
  border: 1px solid #e8edf5;
  border-radius: 12px;
  box-shadow: none;
  resize: none;
}

.chat-panel__composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.chat-panel__composer-hint {
  font-size: 11px;
  color: #aab4c5;
}

.preview-panel {
  flex: 1;
}

.preview-panel__header {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f3f8;
}

.preview-panel__title {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  color: #344054;
}

.preview-panel__body {
  position: relative;
  flex: 1;
  min-height: 0;
  background: #f8fafc;
}

.preview-panel__iframe {
  width: 100%;
  height: 100%;
  background: #fff;
  border: none;
}

.preview-panel__mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #4f7cff;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(2px);
}

.deploy-modal__desc {
  margin-bottom: 12px;
  color: #667085;
}

@media (max-width: 991px) {
  .app-chat-page {
    height: auto;
    min-height: 0;
  }

  .app-chat-page__body {
    flex-direction: column;
  }

  .chat-panel {
    flex: none;
    height: 560px;
  }

  .preview-panel {
    min-height: 520px;
  }
}

@media (max-width: 767px) {
  .app-chat-page__topbar {
    padding: 8px 10px;
  }

  .app-chat-page__app-name {
    max-width: 180px;
    font-size: 14px;
  }

  .chat-panel {
    height: 500px;
  }

  .preview-panel {
    min-height: 420px;
  }
}
</style>
