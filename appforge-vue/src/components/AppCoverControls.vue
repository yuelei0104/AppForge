<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { getCoverStatus, regenerateCover, setCoverMode, type CoverStatus } from '@/api/cover'
import { coverPending, coverStatusLabel } from '@/utils/cover'
const props = defineProps<{
  appId: string
  version: number
  active: boolean
  busy?: boolean
  initialManualUrl?: string
}>()
const emit = defineEmits<{ changed: [] }>()
const state = ref<CoverStatus>(),
  loading = ref(false),
  error = ref(''),
  stopped = ref(false)
const manualUrl = ref('')
let timer: ReturnType<typeof setTimeout> | undefined, abort: AbortController | undefined
let deadline = 0,
  epoch = 0,
  requestId = '',
  observed = ''
function stop() {
  clearTimeout(timer)
  abort?.abort()
  abort = undefined
}
function schedule() {
  clearTimeout(timer)
  if (!props.active || !coverPending(state.value?.status)) return
  if (Date.now() >= deadline) {
    stopped.value = true
    return
  }
  timer = setTimeout(() => {
    if (document.hidden) schedule()
    else void refresh()
  }, 3000)
}
async function refresh() {
  const current = epoch
  abort?.abort()
  const controller = new AbortController()
  abort = controller
  try {
    const res = await getCoverStatus(props.appId, controller.signal)
    if (current !== epoch || controller.signal.aborted) return
    if (res.data.code !== 0) throw new Error(res.data.message || '读取封面状态失败')
    state.value = res.data.data
    error.value = ''
    const next = `${state.value.mode}:${state.value.status}:${state.value.revision}`
    if (observed && observed !== next) emit('changed')
    observed = next
    schedule()
  } catch (e) {
    if (current === epoch && !controller.signal.aborted) {
      error.value = e instanceof Error ? e.message : '读取封面状态失败'
      stopped.value = true
    }
  }
}
function manualRefresh() {
  stopped.value = false
  deadline = Date.now() + 120000
  void refresh()
}
async function run(
  action: () => Promise<{ data: { code: number; message: string } }>,
  regenerate = false,
) {
  if (loading.value) return
  const current = epoch
  loading.value = true
  error.value = ''
  stop()
  try {
    const res = await action()
    if (current !== epoch) return
    if (res.data.code !== 0) {
      if (regenerate) requestId = ''
      throw new Error(res.data.message || '更新封面失败')
    }
    if (regenerate) requestId = ''
    emit('changed')
    manualRefresh()
  } catch (e) {
    if (current === epoch) {
      error.value = e instanceof Error ? e.message : '更新封面失败'
      stopped.value = true
    }
  } finally {
    if (current === epoch) loading.value = false
  }
}
function generate() {
  requestId ||= crypto.randomUUID()
  void run(() => regenerateCover(props.appId, props.version, requestId), true)
}
function mode(value: 'AUTO' | 'MANUAL') {
  void run(() => setCoverMode(props.appId, value, value === 'MANUAL' ? manualUrl.value : undefined))
}
watch(
  () => [props.appId, props.active, props.version] as const,
  () => {
    epoch++
    stop()
    loading.value = false
    requestId = ''
    observed = ''
    state.value = undefined
    error.value = ''
    stopped.value = false
    manualUrl.value = props.initialManualUrl || ''
    deadline = Date.now() + 120000
    if (props.active && props.appId) void refresh()
  },
  { immediate: true },
)
function visibility() {
  if (!document.hidden && props.active && !stopped.value) void refresh()
}
document.addEventListener('visibilitychange', visibility)
onBeforeUnmount(() => {
  epoch++
  stop()
  document.removeEventListener('visibilitychange', visibility)
})
</script>
<template>
  <section class="cover-controls" aria-label="应用封面">
    <strong>应用封面</strong>
    <p v-if="state && !state.enabled">自动封面尚未启用，请先完成后端配置。</p>
    <template v-else-if="state?.enabled">
      <p aria-live="polite">
        {{ state.mode === 'MANUAL' ? '自定义封面' : coverStatusLabel(state.status)
        }}<span v-if="state.version"> · v{{ state.version }}</span>
      </p>
      <p v-if="state.queuePaused" class="cover-error">
        截图队列已暂停，请管理员检查 COS 权限与配置。
      </p>
      <p v-if="state.mode === 'AUTO' && state.errorMessage" class="cover-error">
        {{ state.errorMessage }}
      </p>
      <a-space wrap>
        <a-button
          v-if="state.mode === 'AUTO'"
          :loading="loading"
          :disabled="busy || !version || coverPending(state.status) || state.queuePaused"
          @click="generate"
          >重新生成封面</a-button
        >
        <a-button v-else :loading="loading" :disabled="busy" @click="mode('AUTO')"
          >使用自动截图</a-button
        >
        <a-button v-if="stopped" :disabled="loading" @click="manualRefresh">刷新封面状态</a-button>
      </a-space>
      <details class="cover-custom">
        <summary>自定义封面链接</summary>
        <label :for="`cover-url-${appId}`">图片地址（留空使用默认封面）</label>
        <a-input
          :id="`cover-url-${appId}`"
          v-model:value="manualUrl"
          :maxlength="512"
          placeholder="https://example.com/cover.jpg"
          :disabled="loading || busy"
        />
        <a-button :loading="loading" :disabled="busy" @click="mode('MANUAL')"
          >保存自定义封面</a-button
        >
      </details>
    </template>
    <p v-if="error" role="alert" class="cover-error">{{ error }}</p>
    <a-button v-if="error && !state" @click="manualRefresh">重试读取状态</a-button>
  </section>
</template>
<style scoped>
.cover-controls {
  margin-top: 20px;
  overflow-wrap: anywhere;
}
.cover-controls p {
  margin: 8px 0;
  color: #737e90;
}
.cover-controls .cover-error {
  color: #b45309;
}
.cover-custom {
  margin-top: 12px;
}
.cover-custom summary {
  cursor: pointer;
  color: #1677ff;
}
.cover-custom label {
  display: block;
  margin: 10px 0 6px;
}
.cover-custom button {
  margin-top: 8px;
}
</style>
