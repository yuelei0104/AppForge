<script setup lang="ts">
import {
  listAppChatHistory,
  getChatStatistics,
  exportChatMarkdown,
} from '@/api/chatHistoryController'
import { validateMarkdownExport } from '@/utils/chatExport'
import ChatMemoryModal from '@/components/ChatMemoryModal.vue'
import GenerationProgress from '@/components/GenerationProgress.vue'
import request from '@/request'
import { prependHistory, oldestCursor, updateProgress, type ChatMessage } from '@/utils/chatHistory'
import { stopGeneration, undeployApp, applyFeatured, type AppInfo } from '@/api/workspace'
import { generationLabel } from '@/utils/appStatus'
import { generationModeLabel } from '@/utils/generationMode'
import VersionHistoryDrawer from '@/components/VersionHistoryDrawer.vue'
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  CloudUploadOutlined,
  DownOutlined,
  EditOutlined,
  HomeOutlined,
  HistoryOutlined,
  DownloadOutlined,
  BulbOutlined,
  StopOutlined,
  InfoCircleOutlined,
  LinkOutlined,
  SendOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import aiAvatarUrl from '@/assets/aiAvatar.svg'
import MarkdownContent from '@/components/MarkdownContent.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import {
  deleteApp,
  deleteAppByAdmin,
  deployApp,
  getAppVoById,
  getAppVoByIdByAdmin,
} from '@/api/appController'
import AppDetailModal from '@/components/AppDetailModal.vue'
import { API_BASE_URL } from '@/config/env'
import { useLoginUserStore } from '@/stores/loginUser'
import { getAppDeployUrlFromResponse, getAppPreviewUrl } from '@/utils/appUrls'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const appId = computed(() => String(route.params.appId ?? ''))
const app = ref<AppInfo | null>(null)
const appLoading = ref(true)
const messages = ref<ChatMessage[]>([])
const historyLoading = ref(false)
const historyLoaded = ref(false)
const historyError = ref('')
const hasMoreHistory = ref(false)
const hasAnyHistory = ref(false)
let historyCursor: ReturnType<typeof oldestCursor>
let historyAbort: AbortController | undefined
let initialPromptSent = false
let followLatest = true
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
const detailsOpen = ref(false)
const deleting = ref(false)
const historyOpen = ref(false)
const memoryOpen = ref(false)
const statistics = ref<API.ChatStatistics>()
const exporting = ref(false)
let statisticsAbort: AbortController | undefined,
  exportAbort: AbortController | undefined,
  sourceAbort: AbortController | undefined
async function loadStatistics() {
  if (!canManage.value) return
  statisticsAbort?.abort()
  const controller = new AbortController()
  statisticsAbort = controller
  const sequence = loadSequence
  try {
    const res = await getChatStatistics(appId.value, controller.signal)
    if (sequence !== loadSequence || controller.signal.aborted) return
    if (res.data.code === 0 && res.data.data) statistics.value = res.data.data
  } catch {
    /* 统计暂不可用不阻塞对话，可点击重试。 */
  }
}
function openMemory() {
  memoryOpen.value = true
  void loadStatistics()
}
const downloadingSource = ref(false)
async function downloadSource() {
  if (!canManage.value || downloadingSource.value) return
  const id = appId.value,
    sequence = loadSequence
  const controller = new AbortController()
  sourceAbort?.abort()
  sourceAbort = controller
  downloadingSource.value = true
  try {
    const res = await request<Blob>('/app/source/download', {
      params: { appId: id },
      responseType: 'blob',
      signal: controller.signal,
    })
    if (sequence !== loadSequence || controller.signal.aborted) return
    if (!String(res.headers['content-type']).includes('application/zip')) {
      const error = JSON.parse(await res.data.text()) as { message?: string }
      throw new Error(error.message || '工程下载失败')
    }
    const url = URL.createObjectURL(res.data),
      link = document.createElement('a')
    link.href = url
    link.download = `app-${id}-source.zip`
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (error) {
    if (sequence === loadSequence && !controller.signal.aborted)
      message.error(error instanceof Error ? error.message : '工程下载失败')
  } finally {
    if (sequence === loadSequence) downloadingSource.value = false
  }
}

async function handleExport() {
  if (!canManage.value || exporting.value) return
  const id = appId.value,
    sequence = loadSequence,
    controller = new AbortController()
  exportAbort = controller
  exporting.value = true
  try {
    const res = await exportChatMarkdown(id, controller.signal)
    const blob = await validateMarkdownExport(res.data, String(res.headers['content-type'] || ''))
    if (sequence !== loadSequence || controller.signal.aborted) return
    const url = URL.createObjectURL(blob),
      link = document.createElement('a')
    link.href = url
    link.download = `app-${id}-chat.md`
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (error) {
    if (sequence === loadSequence && !controller.signal.aborted)
      message.error(error instanceof Error ? error.message : '导出失败，请稍后重试')
  } finally {
    if (sequence === loadSequence) exporting.value = false
  }
}
const actionLoading = ref(false)
const stopping = ref(false)
const isGenerating = computed(() => streaming.value || app.value?.generationStatus === 'GENERATING')
let statusTimer: ReturnType<typeof setTimeout> | undefined
let activeMessage: ChatMessage | null = null

async function refreshApp() {
  const sequence = loadSequence
  try {
    const result = isAdmin.value
      ? await getAppVoByIdByAdmin({ id: appId.value })
      : await getAppVoById({ id: appId.value })
    if (sequence === loadSequence && result.data.code === 0 && result.data.data) {
      const previousVersion = app.value?.currentVersion
      app.value = result.data.data
      if (
        !streaming.value &&
        app.value.currentVersion &&
        previousVersion !== app.value.currentVersion
      ) {
        previewUrl.value = `${getAppPreviewUrl(appId.value)}?t=${Date.now()}`
        previewVisible.value = true
        previewKey.value += 1
      }
    }
  } catch {
    /* 临时轮询失败时保留页面，SSE 仍独立处理完成和失败。 */
  }
}
function scheduleStatus() {
  clearTimeout(statusTimer)
  if (!isGenerating.value) return
  statusTimer = setTimeout(async () => {
    await refreshApp()
    scheduleStatus()
  }, 3000)
}
async function handleStop() {
  if (!isOwner.value || stopping.value) return
  stopping.value = true
  try {
    const res = await stopGeneration(appId.value)
    if (res.data.code !== 0) throw new Error(res.data.message)
    closeStream()
    streaming.value = false
    previewLoading.value = false
    if (activeMessage) activeMessage.status = 'stopped'
    if (app.value) app.value.generationStatus = 'STOPPED'
    await refreshApp()
    void loadStatistics()
    message.info('已停止生成，未保存不完整代码')
  } catch (error) {
    message.error(error instanceof Error ? error.message : '停止失败，请重试')
  } finally {
    stopping.value = false
  }
}
async function handleWorkspaceAction(kind: 'offline' | 'feature') {
  if (actionLoading.value || isGenerating.value) return
  actionLoading.value = true
  try {
    const res =
      kind === 'offline' ? await undeployApp(appId.value) : await applyFeatured(appId.value)
    if (res.data.code !== 0) throw new Error(res.data.message)
    message.success(kind === 'offline' ? '应用已下线' : '精选申请已提交')
    await refreshApp()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    actionLoading.value = false
  }
}
async function handleRestored() {
  await refreshApp()
  previewUrl.value = `${getAppPreviewUrl(appId.value)}?t=${Date.now()}`
  previewVisible.value = true
  previewKey.value += 1
}

let eventSource: EventSource | null = null
let messageSeed = 0
let loadSequence = 0

const appName = computed(() => app.value?.appName || '未命名应用')
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')
const isOwner = computed(
  () =>
    app.value?.userId != null &&
    loginUserStore.loginUser.id != null &&
    String(app.value.userId) === String(loginUserStore.loginUser.id),
)
const readOnlyHint = computed(() =>
  app.value && !isOwner.value ? '无法在别人的作品下对话哦～' : undefined,
)
const canManage = computed(() => !!app.value && (isOwner.value || isAdmin.value))

function nextMessageId() {
  messageSeed += 1
  return `message-${messageSeed}`
}

function formatTime() {
  const date = new Date()
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function scrollToBottom(force = false) {
  if (!force && (!followLatest || historyLoading.value)) return
  const sequence = loadSequence
  await nextTick()
  if (sequence !== loadSequence) return
  const container = messagesRef.value
  if (container) {
    container.scrollTop = container.scrollHeight
  }
}

function handleMessagesScroll() {
  const container = messagesRef.value
  if (container && !historyLoading.value) {
    followLatest = container.scrollHeight - container.scrollTop - container.clientHeight < 80
  }
}

async function loadChatHistory(more = false, sequence = loadSequence) {
  if (!canManage.value || historyLoading.value || (more && !historyCursor)) return false
  const id = appId.value
  historyLoading.value = true
  historyError.value = ''
  const controller = new AbortController()
  historyAbort = controller
  try {
    const res = await listAppChatHistory(
      {
        appId: id,
        pageSize: 10,
        ...(more ? historyCursor : {}),
      },
      controller.signal,
    )
    if (sequence !== loadSequence) return false
    if (res.data.code !== 0 || !res.data.data) {
      throw new Error(res.data.message || '加载对话历史失败')
    }
    const records = res.data.data.records ?? []
    const nextCursor = oldestCursor(records)
    if (records.length && !nextCursor) throw new Error('历史消息缺少分页信息，请稍后重试')
    // 在插入之前记录视口，避免加载期间的流式输出影响阅读位置。
    const container = messagesRef.value
    const previousHeight = container?.scrollHeight ?? 0
    const previousTop = container?.scrollTop ?? 0
    messages.value = prependHistory(more ? messages.value : [], records)
    hasAnyHistory.value ||= records.length > 0
    const loadedHistoryCount = messages.value.filter((item) =>
      item.id.startsWith('history-'),
    ).length
    const total = res.data.data.totalRow
    hasMoreHistory.value =
      records.length === 10 && !!nextCursor && (total == null || loadedHistoryCount < total)
    historyCursor = nextCursor
    historyLoaded.value = true
    await nextTick()
    if (sequence !== loadSequence) return false
    if (more && container) {
      container.scrollTop = previousTop + container.scrollHeight - previousHeight
      followLatest = false
    } else {
      followLatest = true
      await scrollToBottom(true)
    }
    return true
  } catch (error) {
    if (sequence !== loadSequence || controller.signal.aborted) return false
    historyError.value = error instanceof Error ? error.message : '加载对话历史失败，请重试'
    return false
  } finally {
    if (sequence === loadSequence) historyLoading.value = false
  }
}

async function initializeHistory(sequence = loadSequence) {
  void loadStatistics()
  const loaded = await loadChatHistory(false, sequence)
  if (!loaded || sequence !== loadSequence) return
  if (messages.value.length >= 2 || (app.value?.currentVersion ?? 0) > 0) {
    previewUrl.value = getAppPreviewUrl(appId.value)
    previewVisible.value = true
    previewKey.value += 1
  }
  if (
    isOwner.value &&
    !hasAnyHistory.value &&
    !initialPromptSent &&
    app.value?.initPrompt &&
    !isGenerating.value
  ) {
    initialPromptSent = true
    sendMessage(app.value.initPrompt)
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
  if (app.value) app.value.generationStatus = success ? 'SUCCEEDED' : 'FAILED'
  void refreshApp()
  void loadStatistics()

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

  es.addEventListener('progress', (event) => {
    if (eventSource !== es || !streaming.value) return
    try {
      aiMessage.progress = updateProgress(
        aiMessage.progress || [],
        JSON.parse((event as MessageEvent).data),
      )
    } catch {
      /* 不完整的进度事件不影响正文。 */
    }
    void scrollToBottom()
  })

  es.addEventListener('done', () => {
    if (eventSource === es) {
      finishStream(aiMessage, true)
    }
  })

  es.addEventListener('generation-error', (event) => {
    if (eventSource !== es) return
    let errorText = '模型生成失败，请稍后重试'
    try {
      errorText = JSON.parse((event as MessageEvent).data).message || errorText
    } catch {
      /* 保留默认说明 */
    }
    streaming.value = false
    previewLoading.value = false
    aiMessage.status = 'error'
    if (!aiMessage.content) aiMessage.content = errorText
    if (app.value) {
      app.value.generationStatus = 'FAILED'
      app.value.generationError = errorText
    }
    closeStream()
    message.error(errorText)
    void refreshApp()
    void loadStatistics()
  })
  es.addEventListener('stopped', () => {
    if (eventSource !== es) return
    streaming.value = false
    previewLoading.value = false
    aiMessage.status = 'stopped'
    if (app.value) app.value.generationStatus = 'STOPPED'
    closeStream()
    void refreshApp()
    void loadStatistics()
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
  if (!historyLoaded.value || historyLoading.value || deployLoading.value || deleting.value) {
    return
  }
  if (isGenerating.value) {
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
  activeMessage = aiMessage
  if (app.value) {
    app.value.generationStatus = 'GENERATING'
    app.value.generationError = ''
  }
  messages.value.push(userMessage, aiMessage)
  input.value = ''
  streaming.value = true
  previewLoading.value = true
  followLatest = true
  void scrollToBottom(true)

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
  statisticsAbort?.abort()
  exportAbort?.abort()
  statistics.value = undefined
  downloadingSource.value = false
  exporting.value = false
  memoryOpen.value = false
  historyAbort?.abort()
  sourceAbort?.abort()
  historyLoading.value = false
  historyLoaded.value = false
  historyError.value = ''
  hasMoreHistory.value = false
  hasAnyHistory.value = false
  historyCursor = undefined
  initialPromptSent = false
  followLatest = true
  // 兼容旧收藏地址，浏览行为改由真实历史判断。
  if ('view' in route.query) {
    const query = { ...route.query }
    delete query.view
    void router.replace({ path: route.path, query })
  }
  clearTimeout(statusTimer)
  historyOpen.value = false
  activeMessage = null
  deployModalOpen.value = false
  deployUrl.value = ''
  deployLoading.value = false
  closeStream()
  streaming.value = false
  previewLoading.value = false
  previewVisible.value = false
  previewUrl.value = ''
  detailsOpen.value = false
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
    const res = isAdmin.value
      ? await getAppVoByIdByAdmin({ id: appId.value })
      : await getAppVoById({ id: appId.value })
    if (sequence !== loadSequence) {
      return
    }
    if (res.data.code === 0 && res.data.data) {
      app.value = res.data.data
      if ((app.value.currentVersion || 0) > 0 || app.value.generationStatus === 'SUCCEEDED') {
        previewUrl.value = getAppPreviewUrl(appId.value)
        previewVisible.value = true
        previewKey.value += 1
      }
      // 他人的应用只展示网站，不请求只有创建者/管理员可读的历史。
      if (canManage.value) await initializeHistory(sequence)
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
  if (!app.value || !isOwner.value || isGenerating.value || deleting.value || deployLoading.value) {
    return
  }
  const id = appId.value
  const sequence = loadSequence
  deployLoading.value = true
  try {
    const res = await deployApp({ appId: id })
    if (sequence !== loadSequence) {
      return
    }
    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = getAppDeployUrlFromResponse(res.data.data)
      deployModalOpen.value = true
      message.success('部署成功')
      // 刷新部署标识和时间，不重新加载对话或触发代码生成。
      try {
        const info = isAdmin.value ? await getAppVoByIdByAdmin({ id }) : await getAppVoById({ id })
        if (sequence === loadSequence) {
          if (info.data.code === 0 && info.data.data) {
            app.value = info.data.data
          } else {
            message.warning('部署已成功，应用信息刷新失败，请稍后刷新页面')
          }
        }
      } catch {
        if (sequence === loadSequence) {
          message.warning('部署已成功，应用信息刷新失败，请稍后刷新页面')
        }
      }
    } else {
      message.error(res.data.message || '部署失败')
    }
  } catch {
    if (sequence === loadSequence) {
      message.error('部署失败，请稍后重试')
    }
  } finally {
    if (sequence === loadSequence) {
      deployLoading.value = false
    }
  }
}

function openPreview() {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank', 'noopener,noreferrer')
  }
}

function handleAppMenuClick({ key }: { key: string | number }) {
  if (key === 'edit') {
    handleEditApp()
  } else if (key === 'home') {
    void router.push('/')
  }
}

function handleEditApp() {
  if (!canManage.value || isGenerating.value || deployLoading.value || deleting.value) {
    return
  }
  detailsOpen.value = false
  void router.push(`/app/edit/${appId.value}`)
}

async function handleDeleteApp() {
  if (
    !app.value ||
    !canManage.value ||
    isGenerating.value ||
    deployLoading.value ||
    deleting.value
  ) {
    return
  }
  const id = appId.value
  deleting.value = true
  try {
    const res = isAdmin.value ? await deleteAppByAdmin({ id }) : await deleteApp({ id })
    if (appId.value !== id) {
      return
    }
    if (res.data.code === 0 && res.data.data) {
      detailsOpen.value = false
      closeStream()
      message.success('删除成功')
      await router.replace('/')
    } else {
      message.error(res.data.message || '删除失败')
    }
  } catch {
    message.error('删除失败，请稍后重试')
  } finally {
    deleting.value = false
  }
}

watch(
  () => route.params.appId,
  () => void loadApp(),
  { immediate: true },
)

watch(isGenerating, scheduleStatus)

onBeforeUnmount(() => {
  statisticsAbort?.abort()
  exportAbort?.abort()
  historyAbort?.abort()
  sourceAbort?.abort()
  clearTimeout(statusTimer)
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
              <a-menu-item
                v-if="canManage"
                key="edit"
                :disabled="isGenerating || deployLoading || deleting"
              >
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

        <a-tooltip
          v-if="canManage"
          :title="
            statistics
              ? `共 ${statistics.messageCount} 条消息，按用户发起请求计轮数，含失败或停止的请求`
              : '统计暂不可用，点击重试'
          "
        >
          <a-tag
            class="chat-rounds"
            role="button"
            tabindex="0"
            aria-label="刷新对话轮数"
            @click="loadStatistics"
            @keydown.enter="loadStatistics"
            @keydown.space.prevent="loadStatistics"
            >{{ statistics ? `对话 ${statistics.roundCount} 轮` : '轮数待加载' }}</a-tag
          >
        </a-tooltip>
        <a-tag
          v-if="app"
          aria-label="当前生成模式"
          :color="app.codeGenType === 'vue_project' ? 'purple' : 'default'"
          :title="
            app.codeGenType === 'vue_project'
              ? '构建成功后可下载完整工程'
              : '此模式生成原生网页；工程下载仅在完整网站工程模式提供'
          "
          >{{ generationModeLabel(app.codeGenType) }}</a-tag
        >
        <a-tag :color="isGenerating ? 'processing' : 'default'"
          >{{ generationLabel(app?.generationStatus)
          }}<template v-if="app?.currentVersion"> · v{{ app.currentVersion }}</template></a-tag
        >
      </div>

      <div class="app-chat-page__topbar-actions">
        <a-button
          v-if="canManage && app?.codeGenType === 'vue_project'"
          :loading="downloadingSource"
          :disabled="!app.currentVersion"
          @click="downloadSource"
          >下载工程</a-button
        >
        <a-button v-if="canManage" :loading="exporting" @click="handleExport"
          ><template #icon><DownloadOutlined /></template>导出对话</a-button
        >
        <a-button v-if="canManage" @click="openMemory"
          ><template #icon><BulbOutlined /></template>智能记忆</a-button
        >
        <a-button v-if="canManage" @click="historyOpen = true"
          ><template #icon><HistoryOutlined /></template>版本历史</a-button
        >
        <a-button :disabled="!app || appLoading" @click="detailsOpen = true">
          <template #icon><InfoCircleOutlined /></template>
          应用详情
        </a-button>
        <a-button
          v-if="isOwner"
          type="primary"
          :loading="deployLoading"
          :disabled="!app || isGenerating || deleting || deployLoading || actionLoading"
          @click="handleDeploy"
        >
          <template #icon><CloudUploadOutlined /></template>
          部署
        </a-button>
      </div>
    </div>

    <a-alert
      v-if="app?.generationError && ['FAILED', 'STOPPED'].includes(app.generationStatus || '')"
      :message="app.generationError"
      :type="app.generationStatus === 'FAILED' ? 'error' : 'info'"
      show-icon
      class="app-chat-page__status"
    />
    <div class="app-chat-page__body">
      <section class="chat-panel">
        <div ref="messagesRef" class="chat-panel__messages" @scroll="handleMessagesScroll">
          <div v-if="hasMoreHistory && historyLoaded" class="chat-panel__history">
            <a-button
              type="link"
              size="small"
              :loading="historyLoading"
              @click="loadChatHistory(true)"
              >加载更多历史消息</a-button
            >
          </div>
          <a-alert
            v-if="historyError"
            type="warning"
            show-icon
            :message="historyError"
            class="chat-panel__history-error"
          >
            <template #action>
              <a-button
                size="small"
                :loading="historyLoading"
                @click="historyLoaded ? loadChatHistory(true) : initializeHistory()"
                >重试</a-button
              >
            </template>
          </a-alert>
          <a-spin
            v-if="appLoading || (historyLoading && !historyLoaded)"
            class="chat-panel__loading"
          />
          <div v-else-if="!messages.length" class="chat-panel__empty">
            <img :src="aiAvatarUrl" class="chat-panel__empty-avatar" alt="AI 助手" />
            <p v-if="!canManage">对话历史仅创建者和管理员可见。</p>
            <p v-else-if="historyError">暂时无法加载历史，请重试后继续对话。</p>
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
              :src="
                app?.user?.userAvatar || (isOwner ? loginUserStore.loginUser.userAvatar : undefined)
              "
              :size="32"
              class="chat-message__avatar"
            >
              <template
                v-if="!app?.user?.userAvatar && !(isOwner && loginUserStore.loginUser.userAvatar)"
              >
                <UserOutlined />
              </template>
            </a-avatar>
            <a-avatar
              v-else
              alt="AI 助手"
              :src="aiAvatarUrl"
              :size="32"
              class="chat-message__avatar"
            />

            <div class="chat-message__body">
              <div class="chat-message__bubble">
                <div class="chat-message__content">
                  <template v-if="item.role === 'ai'">
                    <MarkdownContent :content="item.content" />
                    <GenerationProgress
                      v-if="item.progress?.length"
                      :items="item.progress"
                      :stopped="item.status === 'stopped' || item.status === 'error'"
                    />
                  </template>
                  <template v-else>{{ item.content }}</template>
                  <span v-if="item.status === 'streaming'" class="chat-message__cursor" />
                </div>
              </div>
              <div class="chat-message__meta">
                <span>{{
                  item.role === 'user'
                    ? isOwner
                      ? '我'
                      : app?.user?.userName || '创建者'
                    : 'AI 助手'
                }}</span>
                <span>{{ item.time }}</span>
                <span v-if="item.status === 'stopped'">已停止 · 未保存</span>
                <span v-if="item.status === 'error'">本次未保存</span>
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
                :disabled="
                  isGenerating ||
                  deployLoading ||
                  deleting ||
                  appLoading ||
                  !historyLoaded ||
                  historyLoading ||
                  !isOwner
                "
                :placeholder="readOnlyHint || '请描述你想生成的网站，越详细效果越好哦'"
                @keydown="handleComposerKeydown"
              />
            </div>
          </a-tooltip>
          <div class="chat-panel__composer-footer">
            <span class="chat-panel__composer-hint">{{
              readOnlyHint || 'Ctrl / ⌘ + Enter 发送'
            }}</span>
            <a-button v-if="isGenerating && isOwner" danger :loading="stopping" @click="handleStop"
              ><template #icon><StopOutlined /></template>停止生成</a-button
            >
            <a-button
              v-else
              type="primary"
              :disabled="
                !input.trim() ||
                isGenerating ||
                deployLoading ||
                deleting ||
                appLoading ||
                !historyLoaded ||
                historyLoading ||
                !isOwner
              "
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

    <AppDetailModal
      v-model:open="detailsOpen"
      :app="app"
      :can-manage="canManage"
      :deleting="deleting"
      :busy="isGenerating || deployLoading || actionLoading"
      :is-owner="isOwner"
      @edit="handleEditApp"
      @delete="handleDeleteApp"
      @offline="handleWorkspaceAction('offline')"
      @feature="handleWorkspaceAction('feature')"
    />

    <ChatMemoryModal
      v-if="canManage"
      v-model:open="memoryOpen"
      :app-id="appId"
      :busy="isGenerating"
    />
    <VersionHistoryDrawer
      v-model:open="historyOpen"
      :app-id="appId"
      :current-version="app?.currentVersion"
      :busy="isGenerating || deployLoading || actionLoading"
      @restored="handleRestored"
      @loaded="refreshApp"
    />
    <DeploySuccessModal
      v-model:open="deployModalOpen"
      :url="deployUrl"
      :is-private="app?.visibility === 'PRIVATE'"
    />
  </div>
</template>

<style scoped>
.chat-rounds {
  cursor: pointer;
}
.chat-panel__history {
  text-align: center;
  padding: 0 0 12px;
}
.chat-panel__history-error {
  margin-bottom: 12px;
}

.app-chat-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 560px;
  height: calc(100vh - 64px - 63px - 48px);
}

.app-chat-page__topbar {
  display: flex;
  flex-wrap: wrap;
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

.app-chat-page__topbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-left: auto;
}

.app-chat-page__topbar-left {
  display: flex;
  flex-wrap: wrap;
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

.app-chat-page__status {
  flex: none;
}

.app-chat-page__body {
  display: flex;
  flex: 1;
  gap: 12px;
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
  border-radius: 12px;
}

.chat-panel {
  flex: 2;
  min-width: 0;
}

.chat-panel__messages {
  flex: 1;
  min-height: 0;
  padding: 16px;
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

.chat-panel__empty-avatar {
  width: 64px;
  height: 64px;
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
  min-width: 0;
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
  background: #f7f8fb;
  border: 1px solid #edf0f5;
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
  flex: 3;
  min-width: 0;
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
