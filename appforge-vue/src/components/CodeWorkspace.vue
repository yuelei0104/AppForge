<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import { getVersionFiles } from '@/api/workspace'

const props = defineProps<{
  appId: string
  version?: number
  liveFiles: Record<string, string>
  deletedFiles: Record<string, boolean>
  truncatedFiles: Record<string, boolean>
  generating: boolean
}>()

const files = ref<Record<string, string>>({})
const selected = ref('')
const loading = ref(false)
const error = ref('')

const allFiles = computed(() => {
  const merged: Record<string, string> = { ...files.value, ...props.liveFiles }
  for (const path of Object.keys(props.deletedFiles)) delete merged[path]
  return merged
})
const paths = computed(() => Object.keys(allFiles.value).sort((a, b) => a.localeCompare(b)))
const content = computed(() => allFiles.value[selected.value] ?? '')
const truncated = computed(() => !!props.truncatedFiles[selected.value])
const language = computed(() => {
  const path = selected.value.toLowerCase()
  if (path.endsWith('.vue')) return 'xml'
  if (path.endsWith('.js') || path.endsWith('.mjs')) return 'javascript'
  if (path.endsWith('.ts')) return 'typescript'
  if (path.endsWith('.css')) return 'css'
  if (path.endsWith('.json')) return 'json'
  if (path.endsWith('.html')) return 'xml'
  if (path.endsWith('.md')) return 'markdown'
  return 'plaintext'
})
const highlighted = computed(() => {
  if (!content.value) return ''
  try {
    return hljs.highlight(content.value, { language: language.value }).value
  } catch {
    return content.value.replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' })[c] || c)
  }
})

async function load() {
  error.value = ''
  if (!props.version) {
    files.value = {}
    return
  }
  loading.value = true
  try {
    const res = await getVersionFiles(props.appId, props.version)
    if (res.data.code !== 0) throw new Error(res.data.message || '读取代码失败')
    files.value = res.data.data || {}
    if (!selected.value || !allFiles.value[selected.value]) {
      selected.value =
        allFiles.value['src/App.vue'] != null
          ? 'src/App.vue'
          : paths.value[0] || ''
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '读取代码失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.appId, props.version],
  () => {
    selected.value = ''
    void load()
  },
  { immediate: true },
)
watch(paths, (list) => {
  if (!selected.value || !list.includes(selected.value)) selected.value = list[0] || ''
})
</script>

<template>
  <div class="code-workspace">
    <div class="code-workspace__bar">
      <span class="code-workspace__title">代码</span>
      <a-tag v-if="generating" color="processing">生成中</a-tag>
      <a-tag v-else-if="version">v{{ version }}</a-tag>
      <a-button size="small" type="text" :loading="loading" @click="load">刷新</a-button>
    </div>
    <div v-if="error" class="code-workspace__error">{{ error }}</div>
    <div v-else class="code-workspace__body">
      <aside class="code-workspace__files">
        <button
          v-for="path in paths"
          :key="path"
          class="code-workspace__file"
          :class="{ 'code-workspace__file--active': path === selected }"
          type="button"
          @click="selected = path"
        >
          {{ path }}
        </button>
        <p v-if="!paths.length && !loading" class="code-workspace__empty">生成完成后在这里查看源码</p>
      </aside>
      <section class="code-workspace__viewer">
        <div class="code-workspace__path">
          {{ selected || '未选择文件' }}
          <span v-if="truncated" class="code-workspace__truncated">内容过大，仅展示部分</span>
        </div>
        <pre v-if="selected"><code class="hljs" v-html="highlighted"></code></pre>
        <p v-else class="code-workspace__empty">选择左侧文件查看代码</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.code-workspace {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  background: #0d1117;
  color: #c9d1d9;
}
.code-workspace__bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid #21262d;
  background: #161b22;
}
.code-workspace__title {
  font-weight: 600;
}
.code-workspace__bar .ant-btn {
  margin-left: auto;
  color: #8b949e;
}
.code-workspace__body {
  display: flex;
  min-height: 0;
  flex: 1;
}
.code-workspace__files {
  width: 220px;
  overflow: auto;
  border-right: 1px solid #21262d;
  padding: 6px 0;
  flex: none;
}
.code-workspace__file {
  display: block;
  width: 100%;
  overflow: hidden;
  padding: 6px 12px;
  border: 0;
  background: transparent;
  color: #8b949e;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  font-size: 12px;
}
.code-workspace__file:hover,
.code-workspace__file--active {
  background: #21262d;
  color: #e6edf3;
}
.code-workspace__viewer {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}
.code-workspace__path {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-bottom: 1px solid #21262d;
  color: #8b949e;
  font-size: 12px;
}
.code-workspace__truncated {
  color: #d29922;
}
.code-workspace__viewer pre {
  margin: 0;
  overflow: auto;
  flex: 1;
  padding: 12px;
  font-size: 12px;
  line-height: 1.55;
}
.code-workspace__viewer code {
  background: transparent;
  padding: 0;
}
.code-workspace__empty,
.code-workspace__error {
  padding: 16px;
  color: #8b949e;
  font-size: 13px;
}
.code-workspace__error {
  color: #f85149;
}
</style>
