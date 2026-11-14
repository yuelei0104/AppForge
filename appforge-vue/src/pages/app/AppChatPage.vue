<script setup lang="ts">
import {
  listAppChatHistory,
  getChatStatistics,
  exportChatMarkdown,
} from '@/api/chatHistoryController'
import { VisualEditor, type ElementInfo } from '@/utils/visualEditor'
import { validateMarkdownExport } from '@/utils/chatExport'
import { streamErrorMessage } from '@/utils/guardrail'
import ChatMemoryModal from '@/components/ChatMemoryModal.vue'
import GenerationProgress from '@/components/GenerationProgress.vue'
import CodeWorkspace from '@/components/CodeWorkspace.vue'
import { prependHistory, oldestCursor, updateProgress, type ChatMessage } from '@/utils/chatHistory'
import { stopGeneration, undeployApp, applyFeatured, type AppInfo } from '@/api/workspace'
import { generationLabel } from '@/utils/appStatus'
import { codeGenTypeLabel } from '@/config/codeGenType'
import VersionHistoryDrawer from '@/components/VersionHistoryDrawer.vue'
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  DesktopOutlined,
  MobileOutlined,
  ReloadOutlined,
  MoreOutlined,
  MenuUnfoldOutlined,
  CheckCircleOutlined,
  LoadingOutlined,
  CloudUploadOutlined,
  DownOutlined,
  EditOutlined,
  HomeOutlined,
  HistoryOutlined,
  DownloadOutlined,
  PaperClipOutlined,
  CodeOutlined,
  EyeOutlined,
  CloseOutlined,
  MenuFoldOutlined,
  FileTextOutlined,
  BulbOutlined,
  StopOutlined,
  InfoCircleOutlined,
  LinkOutlined,
  SendOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import aiAvatarUrl from '@/assets/aiAvatar.svg'
import MarkdownContent from '@/components/MarkdownContent.vue'
import ChatMessageCopyButton from '@/components/ChatMessageCopyButton.vue'
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
const previewIframe = ref<HTMLIFrameElement | null>(null)
const previewReady = ref(false)
const isEditMode = ref(false)
const selectedElementInfo = ref<ElementInfo | null>(null)
const activePanel = ref<'preview' | 'code'>('preview')
// 仅控制视图；预览、代码和生成仍使用现有接口与 SSE 事件。
const previewDevice = ref<'desktop' | 'mobile'>('desktop')
const mobilePane = ref<'chat' | 'preview'>('chat')
const chatCollapsed = ref(false)
const chatWidth = ref(38)
const workspaceBody = ref<HTMLElement>()
const isResizing = ref(false)
function startResize(event: PointerEvent) {
  if (event.button !== 0) return
  event.preventDefault()
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
  isResizing.value = true
}
function resizeWorkspace(event: PointerEvent) {
  if (!isResizing.value || !workspaceBody.value) return
  const rect = workspaceBody.value.getBoundingClientRect()
  chatWidth.value = Math.min(58, Math.max(28, ((event.clientX - rect.left) / rect.width) * 100))
}
function stopResize() {
  isResizing.value = false
}
function resizeWithKeyboard(event: KeyboardEvent) {
  if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
    event.preventDefault()
    chatWidth.value = Math.min(
      58,
      Math.max(28, chatWidth.value + (event.key === 'ArrowLeft' ? -2 : 2)),
    )
  } else if (event.key === 'Home') {
    event.preventDefault()
    chatWidth.value = 38
  }
}
function refreshPreview() {
  if (!previewVisible.value) return
  previewUrl.value = previewAddress(true)
  previewKey.value += 1
}
const latestUserMessage = computed(() =>
  [...messages.value].reverse().find((item) => item.role === 'user'),
)
function restoreLastPrompt() {
  if (!isOwner.value || isGenerating.value || !historyLoaded.value || historyLoading.value) return
  if (latestUserMessage.value) input.value = latestUserMessage.value.content
  mobilePane.value = 'chat'
  chatCollapsed.value = false
}
const generationSummary = computed(() => {
  if (appLoading.value) return '正在加载应用信息'
  if (deployLoading.value) return '正在发布应用'
  if (!isGenerating.value) {
    if (app.value?.generationStatus === 'FAILED')
      return app.value?.currentVersion
        ? '生成未完成，已有版本仍可查看'
        : '生成未完成，请调整需求后重试'
    if (app.value?.generationStatus === 'STOPPED') return '已停止生成，不完整的修改未保存'
    if (app.value?.currentVersion) return `已保存版本 v${app.value.currentVersion}`
    return '描述想法，开始你的第一次创作'
  }
  const latest = [...messages.value].reverse().find((item) => item.role === 'ai')
  const progress = latest?.status === 'streaming' ? latest.progress : undefined
  const running = [...(progress || [])].reverse().find((item) => item.state === 'RUNNING')
  const labels: Record<string, string> = {
    read_directory: '正在查看项目结构',
    read_file: '正在读取文件',
    write_file: '正在写入文件',
    modify_file: '正在修改文件',
    delete_file: '正在整理文件',
    prepare: '正在准备构建环境',
    build: '正在构建网站',
    guardrail: '正在进行安全检查',
  }
  return running
    ? labels[running.operation] || '正在生成应用'
    : streaming.value
      ? 'AI 正在生成，请稍候'
      : '生成任务进行中，正在同步状态'
})
const liveFiles = ref<Record<string, string>>({})
const deletedFiles = ref<Record<string, boolean>>({})
const truncatedFiles = ref<Record<string, boolean>>({})

interface PendingAttachment {
  id: string
  name: string
  kind: string
  url: string
  size: number
}
const pendingFiles = ref<PendingAttachment[]>([])
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

function pickFiles() {
  if (!isGenerating.value && isOwner.value) fileInput.value?.click()
}

async function handleFilesSelected(event: Event) {
  const inputEl = event.target as HTMLInputElement
  const files = Array.from(inputEl.files || [])
  inputEl.value = ''
  if (!files.length || !isOwner.value) return
  if (pendingFiles.value.length + files.length > 5) {
    message.warning('一次最多添加 5 个附件')
    return
  }
  uploading.value = true
  try {
    for (const file of files) {
      const form = new FormData()
      form.append('file', file)
      form.append('appId', appId.value)
      const response = await fetch(`${API_BASE_URL}/file/upload`, {
        method: 'POST',
        credentials: 'include',
        body: form,
      })
      const body = (await response.json()) as {
        code: number
        message?: string
        data?: { id: string | number; name: string; kind: string; url: string; size: number }
      }
      if (!response.ok || body.code !== 0 || !body.data) {
        throw new Error(body.message || '附件上传失败')
      }
      pendingFiles.value.push({ ...body.data, id: String(body.data.id) })
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '附件上传失败')
  } finally {
    uploading.value = false
  }
}

function removePending(id: string) {
  pendingFiles.value = pendingFiles.value.filter((item) => item.id !== id)
}
const visualEditor = new VisualEditor({
  onElementSelected: (info) => {
    if (isOwner.value && isEditMode.value && !isGenerating.value) selectedElementInfo.value = info
  },
})
function onPreviewLoad() {
  if (!previewIframe.value) return
  visualEditor.init(previewIframe.value)
  previewReady.value = true
  visualEditor.onIframeLoad()
}
function clearSelectedElement() {
  selectedElementInfo.value = null
  visualEditor.clearSelection()
}
function resetVisualEditor() {
  visualEditor.destroy()
  isEditMode.value = false
  selectedElementInfo.value = null
  previewReady.value = false
}
function toggleEditMode() {
  if (!isOwner.value || isGenerating.value || deployLoading.value || deleting.value) return
  if (!previewReady.value) {
    message.warning('请等待页面加载完成')
    return
  }
  isEditMode.value = visualEditor.toggleEditMode()
  if (!isEditMode.value) selectedElementInfo.value = null
}
function handleIframeMessage(event: MessageEvent) {
  visualEditor.handleIframeMessage(event)
}
window.addEventListener('message', handleIframeMessage)
function previewAddress(cacheBust = false) {
  const url = new URL(getAppPreviewUrl(appId.value), window.location.origin)
  if (isOwner.value) url.searchParams.set('visualEditor', '1')
  if (cacheBust) url.searchParams.set('t', String(Date.now()))
  return url.href
}
watch(previewKey, resetVisualEditor, { flush: 'sync' })

const deployLoading = ref(false)
const deployModalOpen = ref(false)
const deployUrl = ref('')
const detailsOpen = ref(false)
const deleting = ref(false)
const historyOpen = ref(false)
const memoryOpen = ref(false)
const statistics = ref<API.ChatStatisticsVO>()
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
  if (!isOwner.value || downloadingSource.value) return
  const id = appId.value,
    sequence = loadSequence
  const controller = new AbortController()
  sourceAbort?.abort()
  sourceAbort = controller
  downloadingSource.value = true
  try {
    // 后端直接返回 ZIP 文件流，必须用原生 fetch 才能拿到响应头和二进制内容。
    const downloadUrl = `${API_BASE_URL}/app/download/${encodeURIComponent(id)}`
    const sourceUrl = `${API_BASE_URL}/app/source/download?appId=${encodeURIComponent(id)}`
    let response = await fetch(downloadUrl, {
      method: 'GET',
      credentials: 'include',
      signal: controller.signal,
    })
    // 兼容仅保存在 workspace 版本目录中的应用：旧下载接口没有对应目录时改用源码接口。
    if (!response.ok) {
      response = await fetch(sourceUrl, {
        method: 'GET',
        credentials: 'include',
        signal: controller.signal,
      })
    }
    if (!response.ok) {
      let errorMessage = `下载失败（${response.status}）`
      try {
        const body = (await response.json()) as { message?: string }
        errorMessage = body.message || errorMessage
      } catch {
        /* 非 JSON 错误响应保留状态码提示 */
      }
      throw new Error(errorMessage)
    }
    if (sequence !== loadSequence || controller.signal.aborted) return

    const disposition = response.headers.get('Content-Disposition') || ''
    const match = disposition.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i)
    let fileName = `app-${id}.zip`
    if (match?.[1]) {
      try {
        fileName = decodeURIComponent(match[1])
      } catch {
        fileName = match[1]
      }
    }

    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    message.success('代码下载成功')
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') return
    if (sequence === loadSequence && !controller.signal.aborted)
      message.error(error instanceof Error ? error.message : '代码下载失败')
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
        previewUrl.value = previewAddress(true)
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
  previewUrl.value = previewAddress(true)
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
    previewUrl.value = previewAddress()
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
    // 版本文件是最终权威，清掉流式事件中的临时内容，避免截断标记残留。
    liveFiles.value = {}
    deletedFiles.value = {}
    truncatedFiles.value = {}
    previewUrl.value = previewAddress(true)
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

function failStream(aiMessage: ChatMessage, errorText: string) {
  if (!streaming.value) return
  streaming.value = false
  previewLoading.value = false
  closeStream()
  aiMessage.status = 'error'
  if (!aiMessage.content) aiMessage.content = `❌ ${errorText}`
  if (app.value) {
    app.value.generationStatus = 'FAILED'
    app.value.generationError = errorText
  }
  message.error(errorText)
  void refreshApp()
  void loadStatistics()
  void scrollToBottom()
}

function normalizeFileOperation(value?: string) {
  if (!value) return ''
  if (value === 'C') return 'CREATE'
  if (value === 'U') return 'UPDATE'
  if (value === 'D') return 'DELETE'
  return value
}

function openEventSource(requestId: string, aiMessage: ChatMessage) {
  const url = `${API_BASE_URL}/app/chat/gen/code?requestId=${encodeURIComponent(requestId)}`

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

  es.addEventListener('file', (event) => {
    if (eventSource !== es || !streaming.value) return
    try {
      const change = JSON.parse((event as MessageEvent).data) as {
        path?: string
        operation?: string
        content?: string | null
        truncated?: boolean
        p?: string
        o?: string
        c?: string
        t?: boolean
      }
      const path = change.path ?? change.p
      const operation = normalizeFileOperation(change.operation ?? change.o)
      if (!path) return
      if (operation === 'DELETE') {
        delete liveFiles.value[path]
        delete truncatedFiles.value[path]
        deletedFiles.value[path] = true
      } else if (operation === 'CREATE' || operation === 'UPDATE') {
        const content = change.content ?? change.c
        delete deletedFiles.value[path]
        if (typeof content === 'string') liveFiles.value[path] = content
        if (change.truncated || change.t || typeof content !== 'string') {
          truncatedFiles.value[path] = true
        } else {
          delete truncatedFiles.value[path]
        }
      }
    } catch {
      /* 文件事件不完整时等待版本文件兜底。 */
    }
  })

  es.addEventListener('done', (event) => {
    if (eventSource !== es) return
    try {
      const payload = JSON.parse((event as MessageEvent).data) as { version?: number; v?: number }
      const version = payload.version ?? payload.v
      if (version != null && app.value) app.value.currentVersion = version
    } catch {
      /* 版本号缺失时刷新应用信息兜底。 */
    }
    finishStream(aiMessage, true)
  })

  es.addEventListener('business-error', (event) => {
    if (eventSource !== es) return
    let errorText = '请求处理失败，请稍后重试'
    try {
      errorText = streamErrorMessage(JSON.parse((event as MessageEvent).data), errorText)
    } catch {
      /* 保留默认说明 */
    }
    failStream(aiMessage, errorText)
  })

  es.addEventListener('generation-error', (event) => {
    if (eventSource !== es) return
    let errorText = '模型生成失败，请稍后重试'
    try {
      errorText = streamErrorMessage(JSON.parse((event as MessageEvent).data), errorText)
    } catch {
      /* 保留默认说明 */
    }
    failStream(aiMessage, errorText)
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

async function sendMessage(rawMessage: string) {
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

  let elementContext = ''
  if (selectedElementInfo.value) {
    const element = selectedElementInfo.value
    elementContext = '选中元素信息：'
    if (element.pagePath) elementContext += `\n- 页面路径: ${element.pagePath}`
    elementContext += `\n- 标签: ${element.tagName.toLowerCase()}\n- 选择器: ${element.selector}`
    if (element.textContent)
      elementContext += `\n- 当前内容: ${element.textContent.substring(0, 100)}`
  }
  if (content.length + elementContext.length > 2000) {
    message.warning('消息与选中元素信息合计最多 2000 字，请缩短修改要求或重新选择更具体的元素')
    return
  }
  clearSelectedElement()
  visualEditor.disableEditMode()
  isEditMode.value = false

  const attachments = pendingFiles.value.map((item) => ({
    name: item.name,
    kind: item.kind,
    url: item.url,
  }))
  const userMessage: ChatMessage = {
    id: nextMessageId(),
    role: 'user',
    content,
    time: formatTime(),
    status: 'done',
    attachments,
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

  try {
    const response = await fetch(`${API_BASE_URL}/app/chat/request`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        appId: appId.value,
        message: content,
        attachmentIds: pendingFiles.value.map((item) => item.id),
        element: elementContext,
      }),
    })
    const body = (await response.json()) as {
      code: number
      message?: string
      data?: { requestId?: string }
    }
    if (!response.ok || body.code !== 0 || !body.data?.requestId) {
      throw new Error(body.message || '创建对话请求失败')
    }
    pendingFiles.value = []
    openEventSource(body.data.requestId, aiMessage)
  } catch (error) {
    streaming.value = false
    previewLoading.value = false
    aiMessage.status = 'error'
    const errorText = error instanceof Error ? error.message : '创建对话请求失败'
    aiMessage.content = errorText
    if (app.value) app.value.generationStatus = 'FAILED'
    message.error(errorText)
    void refreshApp()
  }
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
  resetVisualEditor()
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
  pendingFiles.value = []
  activePanel.value = 'preview'
  previewDevice.value = 'desktop'
  mobilePane.value = 'chat'
  chatCollapsed.value = false
  isResizing.value = false
  liveFiles.value = {}
  deletedFiles.value = {}
  truncatedFiles.value = {}
  uploading.value = false
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
        previewUrl.value = previewAddress()
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
    const url = new URL(previewUrl.value, window.location.origin)
    url.searchParams.delete('visualEditor')
    window.open(url.href, '_blank', 'noopener,noreferrer')
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
  resetVisualEditor()
  window.removeEventListener('message', handleIframeMessage)
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
        <a-button
          type="text"
          class="app-chat-page__back"
          aria-label="返回创作首页"
          @click="router.push('/')"
        >
          <template #icon>
            <ArrowLeftOutlined />
          </template>
        </a-button>

        <a-tooltip :title="chatCollapsed ? '展开对话' : '收起对话'"
          ><a-button
            class="workspace-collapse"
            type="text"
            :aria-label="chatCollapsed ? '展开对话' : '收起对话'"
            :aria-expanded="!chatCollapsed"
            @click="chatCollapsed = !chatCollapsed"
            ><MenuUnfoldOutlined v-if="chatCollapsed" /><MenuFoldOutlined v-else /></a-button
        ></a-tooltip>
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

        <a-tag
          v-if="app?.codeGenType"
          aria-label="当前生成类型"
          class="code-gen-type-tag"
          color="blue"
          :title="
            app.codeGenType === 'vue_project'
              ? '构建成功后可下载完整工程'
              : '此模式可下载原生网页源码；完整网站工程包含独立工程配置'
          "
          >{{ codeGenTypeLabel(app.codeGenType) }}</a-tag
        >

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
        <a-tag :color="isGenerating ? 'processing' : 'default'"
          >{{ generationLabel(app?.generationStatus)
          }}<template v-if="app?.currentVersion"> · v{{ app.currentVersion }}</template></a-tag
        >
      </div>

      <div class="app-chat-page__topbar-actions">
        <a-button
          v-if="isOwner"
          :loading="downloadingSource"
          :disabled="!app?.currentVersion || isGenerating || deleting || deployLoading"
          @click="downloadSource"
        >
          <template #icon><DownloadOutlined /></template>
          {{ app?.codeGenType === 'vue_project' ? '下载工程' : '下载源码' }}
        </a-button>
        <a-button v-if="canManage" class="workspace-history" @click="historyOpen = true"
          ><HistoryOutlined />版本历史</a-button
        >
        <a-dropdown :trigger="['click']" placement="bottomRight">
          <a-button aria-label="更多应用操作"><MoreOutlined />更多</a-button>
          <template #overlay>
            <a-menu>
              <a-menu-item key="detail" :disabled="!app || appLoading" @click="detailsOpen = true"
                ><InfoCircleOutlined /> 应用详情</a-menu-item
              >
              <a-menu-item v-if="canManage" key="memory" @click="openMemory"
                ><BulbOutlined /> 智能记忆</a-menu-item
              >
              <a-menu-item v-if="canManage" key="export" :disabled="exporting" @click="handleExport"
                ><DownloadOutlined /> {{ exporting ? '正在导出' : '导出对话' }}</a-menu-item
              >
              <a-menu-item
                v-if="canManage"
                key="history"
                class="workspace-history-menu"
                @click="historyOpen = true"
                ><HistoryOutlined /> 版本历史</a-menu-item
              >
            </a-menu>
          </template>
        </a-dropdown>
        <a-button
          v-if="isOwner"
          type="primary"
          :loading="deployLoading"
          :disabled="!app || isGenerating || deleting || deployLoading || actionLoading"
          @click="handleDeploy"
        >
          <template #icon><CloudUploadOutlined /></template>
          {{ app?.deploymentStatus === 'ONLINE' ? '更新发布' : '发布应用' }}
        </a-button>
      </div>
    </div>

    <div
      class="workspace-status"
      :class="{
        'workspace-status--busy': isGenerating || deployLoading,
        'workspace-status--error': app?.generationStatus === 'FAILED',
      }"
      role="status"
      aria-live="polite"
    >
      <LoadingOutlined v-if="isGenerating || deployLoading" spin /><CheckCircleOutlined
        v-else-if="app?.currentVersion && app?.generationStatus !== 'FAILED'"
      /><InfoCircleOutlined v-else /> <span>{{ generationSummary }}</span
      ><span v-if="previewVisible && isGenerating" class="workspace-status__hint"
        >右侧保留上次生成的预览</span
      >
      <a-button
        v-if="
          isOwner &&
          !isGenerating &&
          latestUserMessage &&
          ['FAILED', 'STOPPED'].includes(app?.generationStatus || '')
        "
        type="link"
        size="small"
        :disabled="!historyLoaded || historyLoading"
        @click="restoreLastPrompt"
        >编辑上次需求</a-button
      >
    </div>
    <nav class="workspace-mobile-nav" aria-label="切换工作区">
      <button type="button" :aria-pressed="mobilePane === 'chat'" @click="mobilePane = 'chat'">
        对话与需求
      </button>
      <button
        type="button"
        :aria-pressed="mobilePane === 'preview'"
        @click="mobilePane = 'preview'"
      >
        预览与代码
      </button>
    </nav>
    <a-alert
      v-if="app?.generationError && ['FAILED', 'STOPPED'].includes(app.generationStatus || '')"
      :message="app.generationError"
      :type="app.generationStatus === 'FAILED' ? 'error' : 'info'"
      show-icon
      class="app-chat-page__status"
    />
    <div
      ref="workspaceBody"
      class="app-chat-page__body"
      :class="{ 'workspace--preview': mobilePane === 'preview', 'workspace--resizing': isResizing }"
      :style="{ '--chat-width': `${chatWidth}%` }"
    >
      <section
        id="workspace-chat"
        class="chat-panel"
        :class="{ 'chat-panel--collapsed': chatCollapsed }"
        aria-label="AI 创作对话"
      >
        <div class="chat-panel__heading">
          <span><img :src="aiAvatarUrl" alt="" />创作助手</span
          ><small>{{
            appLoading ? '正在加载应用' : isOwner ? '一起完善你的想法' : '作品只读'
          }}</small>
        </div>
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
                  <div v-if="item.attachments?.length" class="chat-message__attachments">
                    <a
                      v-for="file in item.attachments"
                      :key="file.url"
                      class="chat-message__attachment"
                      :href="file.url"
                      target="_blank"
                      rel="noopener"
                    >
                      <img v-if="file.kind === 'image'" :src="file.url" :alt="file.name" />
                      <FileTextOutlined v-else />
                      <span>{{ file.name }}</span>
                    </a>
                  </div>
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
                <ChatMessageCopyButton
                  :content="item.content"
                  :label="
                    item.role === 'user'
                      ? '复制我的消息'
                      : item.status === 'streaming'
                        ? '复制当前 AI 回复'
                        : '复制 AI 回复'
                  "
                />
              </div>
            </div>
          </div>
        </div>

        <a-alert
          v-if="selectedElementInfo"
          class="selected-element-alert"
          type="info"
          closable
          @close="clearSelectedElement"
        >
          <template #message>
            <div class="selected-element-info">
              <strong>选中元素：{{ selectedElementInfo.tagName.toLowerCase() }}</strong>
              <span v-if="selectedElementInfo.id"> #{{ selectedElementInfo.id }}</span>
              <span v-if="selectedElementInfo.className">
                .{{ selectedElementInfo.className.split(' ').join('.') }}</span
              >
              <div v-if="selectedElementInfo.textContent">
                内容：{{ selectedElementInfo.textContent.substring(0, 50)
                }}{{ selectedElementInfo.textContent.length > 50 ? '...' : '' }}
              </div>
              <div v-if="selectedElementInfo.pagePath">
                页面路径：{{ selectedElementInfo.pagePath }}
              </div>
              <div>
                选择器：<code>{{ selectedElementInfo.selector }}</code>
              </div>
            </div>
          </template>
        </a-alert>

        <div class="chat-panel__composer">
          <div v-if="pendingFiles.length || uploading" class="chat-panel__attachments">
            <div v-for="file in pendingFiles" :key="file.id" class="chat-panel__attachment">
              <img v-if="file.kind === 'image'" :src="file.url" :alt="file.name" />
              <FileTextOutlined v-else />
              <span class="chat-panel__attachment-name">{{ file.name }}</span>
              <button
                type="button"
                class="chat-panel__attachment-remove"
                :aria-label="`移除附件：${file.name}`"
                @click="removePending(file.id)"
              >
                <CloseOutlined />
              </button>
            </div>
            <span v-if="uploading" class="chat-panel__attachment-uploading">上传中...</span>
          </div>
          <a-tooltip :title="readOnlyHint">
            <div>
              <a-textarea
                aria-label="向 AI 描述创建或修改需求"
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
                :placeholder="
                  readOnlyHint ||
                  (selectedElementInfo
                    ? `正在编辑 ${selectedElementInfo.tagName.toLowerCase()} 元素，描述您想要的修改...`
                    : '描述想要调整的布局、内容或功能……')
                "
                @keydown="handleComposerKeydown"
              />
            </div>
          </a-tooltip>
          <div class="chat-panel__composer-footer">
            <a-tooltip v-if="isOwner" title="添加图片或文档（最多 5 个）">
              <a-button
                size="small"
                :loading="uploading"
                :disabled="isGenerating || appLoading || !historyLoaded || historyLoading"
                @click="pickFiles"
              >
                <template #icon><PaperClipOutlined /></template>
              </a-button>
            </a-tooltip>
            <input
              ref="fileInput"
              class="chat-panel__file-input"
              type="file"
              multiple
              accept="image/png,image/jpeg,image/webp,image/gif,.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.txt,.md,.json,.csv,.xml,.yml,.yaml"
              @change="handleFilesSelected"
            />
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

      <div
        v-show="!chatCollapsed"
        class="workspace-splitter"
        role="separator"
        aria-label="调整对话与预览宽度"
        aria-orientation="vertical"
        aria-controls="workspace-chat workspace-preview"
        :aria-valuenow="Math.round(chatWidth)"
        :aria-valuemin="28"
        :aria-valuemax="58"
        tabindex="0"
        @pointerdown="startResize"
        @pointermove="resizeWorkspace"
        @pointerup="stopResize"
        @pointercancel="stopResize"
        @lostpointercapture="stopResize"
        @keydown="resizeWithKeyboard"
      >
        <span />
      </div>
      <section id="workspace-preview" class="preview-panel" aria-label="应用预览与代码">
        <div class="preview-panel__header">
          <span class="preview-panel__title">
            <LinkOutlined />
            工作预览
          </span>
          <div class="preview-panel__tabs">
            <button
              type="button"
              class="preview-panel__tab"
              :class="{ 'preview-panel__tab--active': activePanel === 'preview' }"
              :aria-pressed="activePanel === 'preview'"
              @click="activePanel = 'preview'"
            >
              <EyeOutlined />预览
            </button>
            <button
              type="button"
              class="preview-panel__tab"
              :class="{ 'preview-panel__tab--active': activePanel === 'code' }"
              :aria-pressed="activePanel === 'code'"
              :title="activePanel === 'code' ? '收起代码' : '展开代码'"
              @click="activePanel = activePanel === 'code' ? 'preview' : 'code'"
            >
              <CodeOutlined />代码
            </button>
          </div>
          <div v-if="activePanel === 'preview'" class="preview-panel__actions">
            <div class="preview-device" aria-label="预览尺寸">
              <button
                type="button"
                :aria-pressed="previewDevice === 'desktop'"
                aria-label="桌面预览"
                title="桌面预览"
                @click="previewDevice = 'desktop'"
              >
                <DesktopOutlined />
              </button>
              <button
                type="button"
                :aria-pressed="previewDevice === 'mobile'"
                aria-label="手机预览"
                title="手机预览（390px）"
                @click="previewDevice = 'mobile'"
              >
                <MobileOutlined />
              </button>
            </div>
            <a-button
              size="small"
              type="text"
              aria-label="刷新预览"
              title="刷新预览"
              :disabled="!previewVisible"
              @click="refreshPreview"
              ><ReloadOutlined
            /></a-button>
            <a-button
              v-if="isOwner && previewVisible"
              size="small"
              :danger="isEditMode"
              :disabled="isGenerating || !previewReady || deployLoading || deleting"
              @click="toggleEditMode"
            >
              <template #icon><EditOutlined /></template>{{ isEditMode ? '退出编辑' : '编辑模式' }}
            </a-button>
            <a-button v-if="previewVisible" size="small" @click="openPreview">新窗口打开</a-button>
          </div>
          <div v-else class="preview-panel__actions">
            <a-button size="small" @click="activePanel = 'preview'">
              <template #icon><MenuFoldOutlined /></template>收起代码
            </a-button>
          </div>
        </div>
        <div
          class="preview-panel__body"
          :class="{
            'preview-panel__body--mobile': previewDevice === 'mobile' && activePanel === 'preview',
          }"
        >
          <template v-if="activePanel === 'preview'">
            <div v-if="previewVisible" class="preview-panel__viewport">
              <iframe
                v-if="previewVisible"
                :key="previewKey"
                ref="previewIframe"
                class="preview-panel__iframe"
                :src="previewUrl"
                @load="onPreviewLoad"
                title="应用预览"
                sandbox="allow-forms allow-modals allow-scripts allow-same-origin"
              />
            </div>
            <div v-else class="preview-panel__empty">
              <div class="preview-panel__empty-icon"><DesktopOutlined /></div>
              <h2>{{ isGenerating ? '你的想法，正在成形' : '你的应用将在这里呈现' }}</h2>
              <p>
                {{
                  isGenerating
                    ? '可以在左侧查看生成进度；完成后会自动加载预览。'
                    : '在左侧描述需求，完成生成后即可查看和继续修改。'
                }}
              </p>
              <div class="preview-panel__steps">
                <span>01 描述需求</span><i /><span>02 生成应用</span><i /><span>03 预览发布</span>
              </div>
            </div>

            <div v-if="streaming && previewLoading && !previewVisible" class="preview-panel__mask">
              <a-spin />
              <span>{{ generationSummary }}</span>
            </div>
          </template>
          <CodeWorkspace
            v-else
            :app-id="appId"
            :version="app?.currentVersion"
            :live-files="liveFiles"
            :deleted-files="deletedFiles"
            :truncated-files="truncatedFiles"
            :generating="isGenerating"
          />
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
      @cover-change="refreshApp"
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
.preview-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.selected-element-alert {
  flex: none;
  margin: 0 16px;
}
.selected-element-info {
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.selected-element-info code {
  color: #d73a49;
}

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
  height: 100dvh;
  padding: 14px;
  background: #f4f5f9;
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

.code-gen-type-tag {
  flex: none;
  margin-inline-end: 0;
  font-size: 12px;
}

.app-chat-page__status {
  flex: none;
}

.app-chat-page__body {
  display: flex;
  flex: 1;
  gap: 0;
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
  flex: 0 0 var(--chat-width, 38%);
  min-width: 0;
}

.chat-panel__messages {
  flex: 1;
  min-height: 0;
  padding: 16px;
  overflow-y: auto;
  background: #fff;
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
  background: var(--forge-accent);
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
  color: #514573;
  background: #f0ecfa;
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
  background: var(--forge-accent);
  animation: blink 0.9s step-end infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.chat-message__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 5px;
  font-size: 11px;
  color: #82798f;
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
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.chat-panel__composer-hint {
  margin-right: auto;
  font-size: 11px;
  color: #82798f;
}

.preview-panel {
  flex: 1;
  min-width: 0;
}

.preview-panel__header {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 10px;
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

.chat-message__attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}
.chat-message__attachment {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  max-width: 190px;
  padding: 3px 6px;
  overflow: hidden;
  font-size: 11px;
  line-height: 1.4;
  color: inherit;
  text-decoration: none;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 6px;
}
.chat-message--ai .chat-message__attachment {
  background: #eef2f8;
}
.chat-message__attachment img {
  width: 20px;
  height: 20px;
  object-fit: cover;
  border-radius: 3px;
  flex: none;
}
.chat-message__attachment span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-panel__attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.chat-panel__attachment {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  max-width: 220px;
  padding: 4px 6px 4px 8px;
  font-size: 12px;
  color: #475467;
  background: #f5f8fb;
  border: 1px solid #e8edf5;
  border-radius: 8px;
}
.chat-panel__attachment img {
  width: 28px;
  height: 28px;
  object-fit: cover;
  border-radius: 4px;
  flex: none;
}
.chat-panel__attachment-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-panel__attachment-remove {
  display: grid;
  width: 18px;
  height: 18px;
  padding: 0;
  place-items: center;
  color: #98a2b3;
  background: transparent;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  flex: none;
}
.chat-panel__attachment-remove:hover {
  color: #f04438;
  background: #fee4e2;
}
.chat-panel__attachment-uploading {
  align-self: center;
  font-size: 11px;
  color: #98a2b3;
}
.chat-panel__file-input {
  display: none;
}

.preview-panel__tabs {
  display: flex;
  gap: 2px;
  margin-right: auto;
  padding: 2px;
  background: #f2f5fa;
  border-radius: 8px;
}
.preview-panel__tab {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 4px 10px;
  font-size: 12px;
  color: #667085;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}
.preview-panel__tab--active {
  color: #344054;
  background: #fff;
  box-shadow: 0 1px 3px rgba(16, 24, 40, 0.12);
}

.preview-panel__body {
  position: relative;
  flex: 1;
  min-height: 0;
  background: #f8fafc;
}

.preview-panel__viewport {
  width: 100%;
  height: 100%;
  margin: 0 auto;
  background: #fff;
  transition: width 0.2s;
}
.preview-panel__body--mobile {
  padding: 14px 0;
}
.preview-panel__body--mobile .preview-panel__viewport {
  width: min(390px, 100%);
  border: 1px solid #ddd8e8;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 5px 20px #3424450d;
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
  color: var(--forge-accent);
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(2px);
}

@media (max-width: 991px) {
  .app-chat-page {
    padding: 8px;
    height: 100dvh;
    min-height: 560px;
    gap: 8px;
  }
  .app-chat-page__body {
    min-height: 0;
  }
  .workspace-splitter,
  .workspace-collapse {
    display: none;
  }
  .chat-panel,
  .chat-panel--collapsed {
    flex: 1;
    display: flex;
    min-width: 0;
  }
  .preview-panel {
    display: none;
  }
  .workspace--preview .preview-panel {
    display: flex;
  }
  .workspace--preview .chat-panel {
    display: none;
  }
  .workspace-mobile-nav {
    display: flex;
    flex: none;
    background: #eae8f1;
    padding: 4px;
    border-radius: 10px;
    gap: 4px;
  }
  .workspace-mobile-nav button {
    flex: 1;
    padding: 8px;
    color: #8c839c;
    font-size: 12px;
    background: transparent;
    border: 0;
    border-radius: 7px;
    cursor: pointer;
  }
  .workspace-mobile-nav button[aria-pressed='true'] {
    color: var(--forge-accent);
    background: #fff;
    box-shadow: 0 1px 3px #2420300a;
  }
  .workspace-status__hint {
    display: none;
  }
  .workspace-history {
    display: none;
  }
  .app-chat-page__topbar {
    gap: 8px;
    padding: 10px;
  }
  .app-chat-page__topbar-left {
    gap: 5px;
  }
  .app-chat-page__app-name {
    max-width: 170px;
    font-size: 14px;
  }
  .app-chat-page__topbar-actions {
    width: 100%;
    justify-content: flex-end;
  }
  .preview-panel__title {
    display: none;
  }
  .preview-panel__header {
    padding: 10px;
    gap: 6px;
  }
  .preview-panel__actions {
    gap: 3px;
  }
  .chat-panel__composer-hint {
    font-size: 10px;
  }
}
@media (min-width: 992px) {
  .chat-panel--collapsed {
    display: none;
  }
}
.preview-panel__body :deep(.code-workspace) {
  height: 100%;
}

.workspace-mobile-nav {
  display: none;
}
.workspace-status {
  display: flex;
  flex: none;
  align-items: center;
  gap: 8px;
  padding: 0 6px;
  min-height: 22px;
  font-size: 11px;
  color: #756c82;
}
.workspace-status--busy {
  color: var(--forge-accent);
}
.workspace-status--error {
  color: #b76459;
}
.workspace-status__hint {
  margin-left: auto;
  color: #9b94a5;
}
.workspace-status .ant-btn {
  margin-left: auto;
  font-size: 11px;
}
.workspace-splitter {
  display: flex;
  flex: 0 0 14px;
  align-items: center;
  justify-content: center;
  cursor: col-resize;
  touch-action: none;
  border-radius: 5px;
}
.workspace-splitter span {
  width: 3px;
  height: 30px;
  background: #d6d1e0;
  border-radius: 3px;
}
.workspace-splitter:hover span,
.workspace--resizing .workspace-splitter span {
  background: var(--forge-accent);
}
.workspace--resizing {
  cursor: col-resize;
  user-select: none;
}
.workspace--resizing iframe {
  pointer-events: none;
}
.chat-panel__heading {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 13px 16px;
  border-bottom: 1px solid #f1eef6;
  color: #514861;
}
.chat-panel__heading > span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
}
.chat-panel__heading img {
  width: 24px;
  height: 24px;
}
.chat-panel__heading small {
  color: #82798f;
  font-size: 10px;
}
.app-chat-page__topbar {
  border-color: var(--forge-border);
  border-radius: 12px;
  gap: 10px;
}
.app-chat-page__topbar-left {
  flex: 1;
}
.app-chat-page__app-name {
  background: transparent;
  color: #393045;
  font-size: 15px;
  padding-left: 2px;
}
.app-chat-page__topbar-actions :deep(.ant-btn) {
  font-size: 12px;
}
.app-chat-page__topbar-left :deep(.ant-tag) {
  font-size: 10px;
  border: 0;
  color: #91849f;
  background: #f5f2f9;
}
.preview-device {
  display: flex;
  gap: 2px;
  padding: 2px;
  background: #f3f1f7;
  border-radius: 7px;
}
.preview-device button {
  display: grid;
  place-items: center;
  width: 26px;
  height: 25px;
  padding: 0;
  color: #a59bb4;
  background: transparent;
  border: 0;
  border-radius: 5px;
  cursor: pointer;
}
.preview-device button[aria-pressed='true'] {
  color: var(--forge-accent);
  background: #fff;
  box-shadow: 0 1px 3px #24203014;
}
.preview-panel__empty h2 {
  color: #665a78;
  font-size: 18px;
  margin: 6px 0 0;
  font-weight: 600;
}
.preview-panel__empty p {
  max-width: 320px;
  line-height: 1.8;
  color: #82798f;
  margin: 0;
}
.preview-panel__steps {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 20px;
  font-size: 10px;
  color: #b8aec3;
}
.preview-panel__steps i {
  width: 16px;
  height: 1px;
  background: #dbd5e3;
}
.preview-panel__empty-icon {
  color: #9380bb;
  background: #eee8f8;
  box-shadow: 0 5px 20px #67518a08;
}
.preview-panel__mask {
  inset: auto 16px 16px;
  flex-direction: row;
  padding: 12px;
  color: var(--forge-accent);
  background: #fffffff5;
  border: 1px solid #ebe6f4;
  border-radius: 10px;
  font-size: 12px;
}
.chat-panel__composer {
  margin: 10px;
  padding: 12px;
  border: 1px solid #e8e2f1;
  border-radius: 12px;
  box-shadow: 0 4px 15px #32224004;
}
.chat-panel__composer:focus-within {
  border-color: #b7a5dc;
}
.chat-panel__composer :deep(textarea) {
  padding: 0;
  background: transparent;
  border: 0;
}
.chat-message--ai .chat-message__bubble {
  background: #fff;
  box-shadow: none;
  border-color: transparent;
  padding: 3px 0;
}
.chat-message__meta {
  color: #82798f;
}
@media (max-width: 991px) {
  .workspace-mobile-nav {
    display: flex;
  }
  .workspace-splitter,
  .workspace-collapse {
    display: none !important;
  }
  .workspace-status__hint {
    display: none;
  }
  .app-chat-page__app-name {
    max-width: 140px;
  }
  .workspace-status {
    font-size: 10px;
  }
  .preview-panel__header {
    flex-wrap: wrap;
  }
}
</style>
