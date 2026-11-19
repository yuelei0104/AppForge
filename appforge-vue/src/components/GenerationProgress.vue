<script setup lang="ts">
import { computed } from 'vue'
import type { ToolProgress } from '@/utils/chatHistory'

const props = defineProps<{ items: ToolProgress[]; stopped?: boolean; active?: boolean }>()

const labels: Record<string, string> = {
  read_directory: '查看目录',
  read_file: '读取文件',
  write_file: '写入文件',
  modify_file: '修改文件',
  delete_file: '删除文件',
  prepare: '准备构建环境',
  build: '构建网站',
  guardrail: '安全检查',
}

const runningItem = computed(() => props.items.find((item) => item.state === 'RUNNING'))
const failedCount = computed(() => props.items.filter((item) => item.state === 'FAILED').length)
const changedPaths = computed(() => {
  const paths = new Set<string>()
  for (const item of props.items) {
    if (['write_file', 'modify_file', 'delete_file'].includes(item.operation)) {
      paths.add(item.path || item.id)
    }
  }
  return paths.size
})
const readCount = computed(
  () => props.items.filter((item) => item.operation === 'read_file').length,
)
const summary = computed(() => {
  if (failedCount.value) return `${failedCount.value} 个步骤失败，已保留可用版本`
  if (props.active && runningItem.value) {
    return `正在${labels[runningItem.value.operation] || '处理文件'}`
  }
  if (props.active) return '正在生成第一版'
  if (props.stopped) return `已停止，保留 ${changedPaths.value} 个文件变更`
  if (!changedPaths.value) return '检查完成，没有文件变更'
  return `已更新 ${changedPaths.value} 个文件`
})
const meta = computed(() => {
  const parts: string[] = []
  if (changedPaths.value) parts.push(`+${changedPaths.value} 文件`)
  if (readCount.value) parts.push(`读取 ${readCount.value} 次`)
  if (props.items.length) parts.push(`${props.items.length} 步`)
  return parts.join(' · ')
})
</script>

<template>
  <div class="generation-result" aria-live="polite">
    <div class="generation-result__head">
      <span
        class="generation-result__dot"
        :class="{
          'generation-result__dot--busy': active && !failedCount,
          'generation-result__dot--error': failedCount,
        }"
      />
      <div>
        <strong>{{ summary }}</strong>
        <small v-if="meta">{{ meta }}</small>
      </div>
      <span v-if="active" class="generation-result__live">LIVE</span>
    </div>
    <details class="generation-result__details" :open="Boolean(active || failedCount)">
      <summary>查看执行记录 · {{ items.length }} 步</summary>
      <ul class="generation-progress" aria-label="网站生成进度">
        <li v-for="item in items" :key="item.id">
          <a-spin v-if="item.state === 'RUNNING' && !stopped" size="small" />
          <span
            v-else
            class="progress-status"
            :class="{ 'progress-status--error': item.state === 'FAILED' }"
            >{{ item.state === 'SUCCEEDED' ? '✓' : item.state === 'FAILED' ? '!' : '—' }}</span
          >
          <div>
            <span>{{ labels[item.operation] || '处理文件' }}</span
            ><code v-if="item.path">{{ item.path }}</code
            ><small v-if="item.state === 'FAILED' || item.operation === 'guardrail'">{{
              item.message
            }}</small
            ><small v-else-if="stopped && item.state === 'RUNNING'">已结束</small>
          </div>
        </li>
      </ul>
    </details>
  </div>
</template>

<style scoped>
.generation-result {
  padding: 11px 12px;
  margin-top: 10px;
  background: #f6efe4;
  border: 1px solid #e2d5c3;
  border-radius: 7px;
}

.generation-result__head {
  display: flex;
  gap: 9px;
  align-items: flex-start;
}

.generation-result__head > div {
  display: grid;
  flex: 1;
  gap: 2px;
  min-width: 0;
}

.generation-result__head strong {
  color: var(--forge-ink);
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
}

.generation-result__head small {
  color: var(--forge-muted-2);
  font-family: var(--forge-font-mono);
  font-size: 7px;
  letter-spacing: 0.02em;
}

.generation-result__dot {
  flex: none;
  width: 6px;
  height: 6px;
  margin-top: 4px;
  background: var(--forge-green);
  border-radius: 50%;
}

.generation-result__dot--busy {
  background: var(--forge-orange);
  box-shadow: 0 0 0 3px rgb(242 91 53 / 13%);
}

.generation-result__dot--error {
  background: var(--forge-red);
}

.generation-result__live {
  color: var(--forge-orange-deep);
  font-family: var(--forge-font-mono);
  font-size: 7px;
  letter-spacing: 0.1em;
}

.generation-result__details {
  margin-top: 9px;
  border-top: 1px solid #e4d7c5;
}

.generation-result__details summary {
  padding-top: 7px;
  color: var(--forge-muted);
  font-size: 9px;
  cursor: pointer;
  list-style: none;
}

.generation-result__details summary::-webkit-details-marker {
  display: none;
}

.generation-result__details summary::before {
  margin-right: 5px;
  content: '+';
  font-family: var(--forge-font-mono);
}

.generation-result__details[open] summary::before {
  content: '−';
}

.generation-progress {
  display: grid;
  gap: 8px;
  padding: 9px 0 0;
  margin: 0;
  overflow: auto;
  list-style: none;
  max-height: 260px;
}

.generation-progress li {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.generation-progress li > div {
  min-width: 0;
}

.generation-progress li span {
  color: var(--forge-muted);
  font-size: 10px;
}

.generation-progress code {
  display: block;
  overflow-wrap: anywhere;
  color: var(--forge-muted-2);
  font-family: var(--forge-font-mono);
  font-size: 8px;
}

.generation-progress small {
  display: block;
  color: var(--forge-red);
  font-size: 9px;
}

.progress-status {
  display: inline-flex;
  flex: none;
  justify-content: center;
  width: 14px;
  color: var(--forge-green);
  font-family: var(--forge-font-mono);
  font-size: 9px;
}

.progress-status--error {
  color: var(--forge-red);
}
</style>
