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
    if (element.textContent) elementContext += `\n- 当前内容: ${element.textContent.substring(0, 100)}`
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
          :disabled="!app?.currentVersion"
          @click="downloadSource"
        >
          <template #icon><DownloadOutlined /></template>
          下载代码
        </a-button>
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
              <button type="button" class="chat-panel__attachment-remove" @click="removePending(file.id)">
                <CloseOutlined />
              </button>
            </div>
            <span v-if="uploading" class="chat-panel__attachment-uploading">上传中...</span>
          </div>
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
                :placeholder="
                  readOnlyHint ||
                  (selectedElementInfo
                    ? `正在编辑 ${selectedElementInfo.tagName.toLowerCase()} 元素，描述您想要的修改...`
                    : '请描述你想生成的网站，越详细效果越好哦')
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
            <a-button
              v-if="isOwner && previewVisible && !isGenerating"
              :danger="isEditMode"
              :disabled="!previewReady || deployLoading || deleting"
              @click="toggleEditMode"
            >
              <template #icon><EditOutlined /></template>{{ isEditMode ? '退出编辑' : '编辑模式' }}
            </a-button>
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
          <div class="preview-panel__tabs">
            <button
              type="button"
              class="preview-panel__tab"
              :class="{ 'preview-panel__tab--active': activePanel === 'preview' }"
              @click="activePanel = 'preview'"
            >
              <EyeOutlined />预览
            </button>
            <button
              type="button"
              class="preview-panel__tab"
              :class="{ 'preview-panel__tab--active': activePanel === 'code' }"
              :title="activePanel === 'code' ? '收起代码' : '展开代码'"
              @click="activePanel = activePanel === 'code' ? 'preview' : 'code'"
            >
              <CodeOutlined />代码
            </button>
          </div>
          <div v-if="activePanel === 'preview'" class="preview-panel__actions">
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
        <div class="preview-panel__body">
          <template v-if="activePanel === 'preview'">
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
            <div v-else class="preview-panel__empty">
              <div class="preview-panel__empty-icon">⌘</div>
              <p>完成对话后，生成的网站会展示在这里</p>
            </div>

            <div v-if="streaming && previewLoading" class="preview-panel__mask">
              <a-spin />
              <span>正在生成页面...</span>
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
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.chat-panel__composer-hint {
  margin-right: auto;
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
.preview-panel__body :deep(.code-workspace) {
  height: 100%;
}
</style>
