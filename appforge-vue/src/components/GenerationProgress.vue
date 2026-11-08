<script setup lang="ts">
import type { ToolProgress } from '@/utils/chatHistory'
defineProps<{ items: ToolProgress[]; stopped?: boolean }>()
const labels: Record<string, string> = {
  read_directory: '查看目录',
  read_file: '读取文件',
  write_file: '写入文件',
  modify_file: '修改文件',
  delete_file: '删除文件',
  prepare: '准备构建环境',
  build: '构建网站',
}
</script>
<template>
  <ul class="generation-progress" aria-label="网站生成进度" aria-live="polite">
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
        ><small v-if="item.state === 'FAILED'">{{ item.message }}</small
        ><small v-else-if="stopped && item.state === 'RUNNING'">已结束</small>
      </div>
    </li>
  </ul>
</template>
<style scoped>
.generation-progress {
  list-style: none;
  padding: 12px 0 0;
  margin: 0;
  display: grid;
  gap: 10px;
  max-height: 360px;
  overflow: auto;
  min-width: 0;
}
.generation-progress li {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.generation-progress li > div {
  min-width: 0;
}
.generation-progress code {
  display: block;
  overflow-wrap: anywhere;
  color: #64748b;
  font-size: 12px;
}
.generation-progress small {
  display: block;
  color: #b45309;
}
.progress-status {
  display: inline-flex;
  flex: none;
  justify-content: center;
  width: 16px;
  color: #16a34a;
}
.progress-status--error {
  color: #dc2626;
}
</style>
