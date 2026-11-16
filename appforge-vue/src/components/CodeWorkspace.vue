<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import { getVersionFiles } from '@/api/workspace'
import { buildDiffRows } from '@/utils/versionDiff'

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
const query = ref('')
const viewMode = ref<'code' | 'diff'>('diff')

const allFiles = computed(() => {
  const merged: Record<string, string> = { ...files.value, ...props.liveFiles }
  for (const path of Object.keys(props.deletedFiles)) delete merged[path]
  return merged
})
const paths = computed(() => Object.keys(allFiles.value).sort((a, b) => a.localeCompare(b)))
const filteredPaths = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  return keyword ? paths.value.filter((path) => path.toLowerCase().includes(keyword)) : paths.value
})
const content = computed(() => allFiles.value[selected.value] ?? '')
const liveChanged = computed(() => {
  const path = selected.value
  return Boolean(
    path && props.liveFiles[path] != null && files.value[path] != null && props.liveFiles[path] !== files.value[path],
  )
})
const diffRows = computed(() => {
  if (!liveChanged.value) return []
  return buildDiffRows(files.value[selected.value] || '', props.liveFiles[selected.value] || '')
})
const lineCount = computed(() => (content.value ? content.value.replace(/\n$/, '').split('\n').length : 0))
const truncated = computed(() => !!props.truncatedFiles[selected.value])

async function copyCode() {
  if (!content.value) return
  try {
    await navigator.clipboard.writeText(content.value)
    message.success('已复制当前文件')
  } catch {
    message.warning('浏览器未允许复制，请手动选择')
  }
}
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
watch(selected, () => {
  viewMode.value = 'diff'
})
</script>

<template>
  <div class="code-workspace">
    <div class="code-workspace__bar">
      <span class="code-workspace__title">代码</span>
      <a-input
        v-model:value="query"
        class="code-workspace__search"
        size="small"
        placeholder="搜索文件"
        allow-clear
      />
      <a-tag v-if="generating" color="processing">生成中</a-tag>
      <a-tag v-else-if="version">v{{ version }}</a-tag>
      <button type="button" class="code-workspace__refresh" :disabled="loading" @click="load">
        {{ loading ? '读取中' : '刷新' }}
      </button>
    </div>
    <div v-if="error" class="code-workspace__error">{{ error }}</div>
    <div v-else class="code-workspace__body">
      <aside class="code-workspace__files">
        <button
          v-for="path in filteredPaths"
          :key="path"
          class="code-workspace__file"
          :class="{ 'code-workspace__file--active': path === selected }"
          type="button"
          @click="selected = path"
        >
          <span>{{ path }}</span>
          <i v-if="liveFiles[path] != null" class="code-workspace__changed">M</i>
        </button>
        <p v-if="!filteredPaths.length && !loading" class="code-workspace__empty">
          {{ query ? '没有匹配的文件' : '生成完成后在这里查看源码' }}
        </p>
      </aside>
      <section class="code-workspace__viewer">
        <div class="code-workspace__path">
          <span class="code-workspace__path-name">{{ selected || '未选择文件' }}</span>
          <span v-if="liveChanged" class="code-workspace__modified">已更新</span>
          <span v-if="truncated" class="code-workspace__truncated">内容过大，仅展示部分</span>
          <span class="code-workspace__spacer" />
          <span class="code-workspace__linecount">{{ lineCount }} 行</span>
          <div v-if="liveChanged" class="code-workspace__modes">
            <button
              type="button"
              :class="{ 'is-active': viewMode === 'diff' }"
              @click="viewMode = 'diff'"
            >
              Diff
            </button>
            <button
              type="button"
              :class="{ 'is-active': viewMode === 'code' }"
              @click="viewMode = 'code'"
            >
              代码
            </button>
          </div>
          <button type="button" class="code-workspace__copy" :disabled="!selected" @click="copyCode">
            复制
          </button>
        </div>
        <div v-if="selected && liveChanged && viewMode === 'diff'" class="code-workspace__diff">
          <template v-for="(row, index) in diffRows" :key="index">
            <div v-if="row.removed" class="diff-line diff-line--remove">
              <span class="diff-line__num">{{ row.leftLine }}</span><code>- {{ row.left }}</code>
            </div>
            <div v-if="row.added" class="diff-line diff-line--add">
              <span class="diff-line__num">{{ row.rightLine }}</span><code>+ {{ row.right }}</code>
            </div>
            <div
              v-if="!row.removed && !row.added"
              class="diff-line"
            >
              <span class="diff-line__num">{{ row.rightLine }}</span><code>{{ row.right }}</code>
            </div>
          </template>
        </div>
        <pre v-else-if="selected"><code class="hljs" v-html="highlighted"></code></pre>
        <p v-else class="code-workspace__empty">选择左侧文件查看代码</p>
      </section>
    </div>
  </div></template>

<style scoped>
.code-workspace {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  color: #d7d1c7;
  background: #1b1918;
}

.code-workspace__bar {
  display: flex;
  flex: none;
  gap: 8px;
  align-items: center;
  padding: 7px 10px;
  background: #24211f;
  border-bottom: 1px solid #37312c;
}

.code-workspace__title {
  color: #f2ece1;
  font-family: var(--forge-font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
}

.code-workspace__search {
  width: 150px;
  margin-left: 4px;
}

.code-workspace__search :deep(.ant-input-affix-wrapper) {
  height: 24px;
  padding: 0 7px;
  background: #1b1918;
  border-color: #3b342e;
}

.code-workspace__search :deep(.ant-input) {
  color: #d7d1c7;
  font-size: 10px;
  background: transparent;
}

.code-workspace__search :deep(.ant-input::placeholder) {
  color: #7f7569;
}

.code-workspace__refresh {
  padding: 3px 7px;
  margin-left: auto;
  color: #a89e91;
  font-size: 9px;
  cursor: pointer;
  background: transparent;
  border: 1px solid #3b342e;
  border-radius: 4px;
}

.code-workspace__refresh:hover {
  color: #f2ece1;
  border-color: #6b5d4e;
}

.code-workspace__body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.code-workspace__files {
  flex: none;
  width: 205px;
  padding: 5px 0;
  overflow: auto;
  background: #1e1b19;
  border-right: 1px solid #37312c;
}

.code-workspace__file {
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 6px 10px;
  overflow: hidden;
  color: #9b9184;
  font-family: var(--forge-font-mono);
  font-size: 9px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.code-workspace__file:hover,
.code-workspace__file--active {
  color: #f2ece1;
  background: #2c2723;
}

.code-workspace__file--active {
  box-shadow: inset 2px 0 0 var(--forge-orange);
}

.code-workspace__changed {
  flex: none;
  color: var(--forge-orange);
  font-family: var(--forge-font-mono);
  font-size: 7px;
  font-style: normal;
}

.code-workspace__viewer {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

.code-workspace__path {
  display: flex;
  flex: none;
  gap: 7px;
  align-items: center;
  padding: 5px 9px;
  overflow: hidden;
  color: #9b9184;
  font-family: var(--forge-font-mono);
  font-size: 8px;
  border-bottom: 1px solid #37312c;
}

.code-workspace__path-name {
  overflow: hidden;
  color: #d7d1c7;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code-workspace__modified {
  padding: 2px 5px;
  color: var(--forge-orange);
  background: rgb(242 91 53 / 10%);
  border-radius: 3px;
}

.code-workspace__truncated {
  color: #d6a74a;
}

.code-workspace__spacer {
  flex: 1;
}

.code-workspace__linecount {
  color: #70675c;
  white-space: nowrap;
}

.code-workspace__modes {
  display: flex;
  overflow: hidden;
  border: 1px solid #3b342e;
  border-radius: 4px;
}

.code-workspace__modes button {
  padding: 2px 6px;
  color: #8d8376;
  font-family: var(--forge-font-mono);
  font-size: 7px;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.code-workspace__modes button.is-active {
  color: #1b1918;
  background: #d7d1c7;
}

.code-workspace__copy {
  padding: 2px 6px;
  color: #a89e91;
  font-size: 8px;
  cursor: pointer;
  background: transparent;
  border: 1px solid #3b342e;
  border-radius: 4px;
}

.code-workspace__copy:hover {
  color: #f2ece1;
}

.code-workspace__viewer pre {
  flex: 1;
  padding: 11px;
  margin: 0;
  overflow: auto;
  font-family: var(--forge-font-mono);
  font-size: 10px;
  line-height: 1.6;
}

.code-workspace__viewer code {
  padding: 0;
  background: transparent;
}

.code-workspace__diff {
  flex: 1;
  overflow: auto;
  font-family: var(--forge-font-mono);
  font-size: 9px;
  line-height: 1.6;
}

.diff-line {
  display: flex;
  gap: 10px;
  padding: 1px 10px;
  white-space: pre;
}

.diff-line__num {
  flex: none;
  width: 28px;
  color: #675f55;
  text-align: right;
  user-select: none;
}

.diff-line code {
  color: #c8c0b4;
  white-space: pre;
}

.diff-line--remove {
  background: rgb(178 58 44 / 18%);
}

.diff-line--remove code {
  color: #e7a196;
}

.diff-line--add {
  background: rgb(47 125 90 / 18%);
}

.diff-line--add code {
  color: #a8d6b8;
}

.code-workspace__empty,
.code-workspace__error {
  padding: 16px;
  color: #8d8376;
  font-size: 11px;
}

.code-workspace__error {
  color: #e7a196;
}
.code-workspace {
  width: 100%;
  max-width: 100%;
  overflow: hidden;
}

.code-workspace__body {
  width: 100%;
  min-width: 0;
  overflow: hidden;
}

.code-workspace__viewer pre,
.code-workspace__diff {
  min-width: 0;
}
@media (max-width: 600px) {
  .code-workspace__files {
    width: 112px;
  }

  .code-workspace__file {
    padding: 6px 8px;
    font-size: 8px;
  }

  .code-workspace__bar {
    gap: 5px;
    padding: 6px 8px;
  }

  .code-workspace__search {
    width: 110px;
  }

  .code-workspace__viewer pre,
  .code-workspace__diff {
    font-size: 9px;
  }
}</style>