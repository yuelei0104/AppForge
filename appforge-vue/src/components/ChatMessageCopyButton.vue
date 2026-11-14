<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { CheckOutlined, CopyOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { copyText } from '@/utils/clipboard'

const props = defineProps<{ content: string; label: string }>()
const copying = ref(false)
const copied = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined
let active = true
function resetFeedback() {
  if (timer) clearTimeout(timer)
  timer = undefined
  copied.value = false
}
watch(() => props.content, resetFeedback)
onBeforeUnmount(() => {
  active = false
  resetFeedback()
})

async function handleCopy() {
  if (copying.value || !props.content.trim()) return
  const text = props.content
  resetFeedback()
  copying.value = true
  try {
    await copyText(text)
    if (!active) return
    if (props.content === text) {
      copied.value = true
      timer = setTimeout(resetFeedback, 2000)
    }
    message.success('已复制消息原文')
  } catch {
    if (active) message.error('复制失败，请选中文字后手动复制')
  } finally {
    if (active) copying.value = false
  }
}
</script>

<template>
  <button
    type="button"
    class="message-copy"
    :class="{ 'message-copy--done': copied }"
    :disabled="copying || !content.trim()"
    :aria-label="label"
    :aria-busy="copying"
    :title="content.trim() ? '复制原文，保留换行和代码' : '这条消息暂无可复制的文字'"
    @click.stop="handleCopy"
  >
    <LoadingOutlined v-if="copying" aria-hidden="true" />
    <CheckOutlined v-else-if="copied" aria-hidden="true" />
    <CopyOutlined v-else aria-hidden="true" />
    <span>{{ copying ? '复制中' : copied ? '已复制' : '复制' }}</span>
  </button>
</template>

<style scoped>
.message-copy {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 28px;
  padding: 3px 6px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #82798f;
  font-size: 11px;
  line-height: 18px;
  cursor: pointer;
}
.message-copy:hover:not(:disabled),
.message-copy:focus-visible {
  color: var(--forge-accent);
  border-color: #e6e0fb;
  background: var(--forge-accent-soft);
}
.message-copy--done {
  color: #278260;
}
.message-copy:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
