<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { compressChatMemory, getChatMemory } from '@/api/chatHistoryController'
import { formatDateTime } from '@/utils/date'
const props = defineProps<{ open: boolean; appId: string; busy?: boolean }>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()
const status = ref<API.ChatMemory>()
const loading = ref(false),
  submitting = ref(false),
  error = ref('')
let sequence = 0,
  controller: AbortController | undefined,
  timer: ReturnType<typeof setTimeout> | undefined
function stop() {
  ++sequence
  clearTimeout(timer)
  controller?.abort()
}
async function load() {
  stop()
  const token = sequence
  controller = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const res = await getChatMemory(props.appId, controller.signal)
    if (token !== sequence) return
    if (res.data.code !== 0 || !res.data.data) throw new Error(res.data.message || '读取记忆失败')
    status.value = res.data.data
    if (status.value.running) timer = setTimeout(() => void load(), 2000)
  } catch (cause) {
    if (token === sequence) error.value = cause instanceof Error ? cause.message : '读取记忆失败'
  } finally {
    if (token === sequence) loading.value = false
  }
}
async function compress() {
  if (props.busy || submitting.value || status.value?.running || !status.value?.enabled) return
  stop()
  const token = sequence
  controller = new AbortController()
  submitting.value = true
  error.value = ''
  try {
    const res = await compressChatMemory(props.appId, controller.signal)
    if (token !== sequence) return
    if (res.data.code !== 0 || !res.data.data) throw new Error(res.data.message || '整理记忆失败')
    status.value = res.data.data
    message.success('已提交整理任务，原始对话会完整保留')
    timer = setTimeout(() => void load(), 1000)
  } catch (cause) {
    if (token === sequence) error.value = cause instanceof Error ? cause.message : '整理记忆失败'
  } finally {
    if (token === sequence) submitting.value = false
  }
}
watch(
  () => [props.open, props.appId] as const,
  ([open]) => {
    stop()
    status.value = undefined
    loading.value = false
    submitting.value = false
    error.value = ''
    if (open) void load()
  },
)
onBeforeUnmount(stop)
</script>
<template>
  <a-modal
    :open="open"
    title="智能记忆"
    :footer="null"
    :width="640"
    @update:open="emit('update:open', $event)"
  >
    <a-alert
      type="info"
      show-icon
      message="摘要用于后续生成的上下文，原始聊天记录仍完整保留，可随时导出。"
    />
    <a-alert
      v-if="error || status?.lastError"
      class="memory-space"
      type="warning"
      show-icon
      :message="error || status?.lastError"
    />
    <a-spin :spinning="loading && !status">
      <div v-if="status" class="memory-space">
        <p v-if="!status.enabled">智能摘要已关闭。</p>
        <p v-else>
          累计 {{ status.triggerMessages }} 条未摘要消息后自动整理，至少保留最近
          {{ status.recentMessages }} 条消息。
        </p>
        <p>
          已摘要 {{ status.coveredMessages }} 条消息
          <a-tag v-if="status.running" color="processing">正在整理</a-tag>
        </p>
        <p v-if="status.updatedTime" class="memory-time">
          更新于 {{ formatDateTime(status.updatedTime) }}
        </p>
        <pre v-if="status.summary" class="memory-summary">{{ status.summary }}</pre>
        <a-empty v-else description="尚无摘要，继续对话后会自动整理，也可手动尝试" />
      </div>
    </a-spin>
    <div class="memory-actions">
      <a-button :loading="loading" :disabled="submitting" @click="load">刷新</a-button>
      <a-button
        type="primary"
        :loading="submitting || status?.running"
        :disabled="busy || loading || !status?.enabled || status?.running"
        @click="compress"
        >整理旧对话</a-button
      >
    </div>
    <p v-if="busy" class="memory-time">请等待本轮生成完成后再手动整理。</p>
  </a-modal>
</template>
<style scoped>
.memory-space {
  margin-top: 16px;
}
.memory-time {
  color: #64748b;
  font-size: 12px;
  margin-top: 12px;
}
.memory-summary {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 340px;
  overflow: auto;
  padding: 16px;
  border-radius: 12px;
  background: #f5f7fc;
  font: inherit;
  line-height: 1.8;
}
.memory-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 20px;
}
</style>
