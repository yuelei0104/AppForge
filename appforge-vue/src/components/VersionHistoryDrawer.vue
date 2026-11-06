<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { listVersions, getVersionFiles, rollbackVersion, type AppVersion } from '@/api/workspace'
import { buildDiffRows } from '@/utils/versionDiff'
import { formatDateTime } from '@/utils/date'

const props = defineProps<{
  open: boolean
  appId: string
  currentVersion?: number
  busy?: boolean
}>()
const emit = defineEmits<{ 'update:open': [value: boolean]; restored: []; loaded: [] }>()
const versions = ref<AppVersion[]>([])
const loading = ref(false),
  comparing = ref(false),
  restoring = ref(false)
const before = ref<number>(),
  after = ref<number>()
const filename = ref('index.html')
const leftFiles = ref<Record<string, string>>({}),
  rightFiles = ref<Record<string, string>>({})
let sequence = 0,
  compareSequence = 0
const options = computed(() =>
  versions.value.map((v) => ({
    label: `v${v.version}${v.kind === 'ROLLBACK' ? ` · 回退自 v${v.sourceVersion}` : v.kind === 'IMPORT' ? ' · 导入' : ''}`,
    value: v.version,
  })),
)
const rows = computed(() =>
  buildDiffRows(leftFiles.value[filename.value] || '', rightFiles.value[filename.value] || ''),
)
const removed = computed(() => rows.value.filter((row) => row.removed).length)
const added = computed(() => rows.value.filter((row) => row.added).length)
const selected = computed(() => versions.value.find((v) => v.version === after.value))
const compareReady = ref(false)
async function load() {
  const token = ++sequence
  ++compareSequence
  versions.value = []
  leftFiles.value = {}
  rightFiles.value = {}
  compareReady.value = false
  loading.value = true
  try {
    const res = await listVersions(props.appId)
    if (token !== sequence) return
    if (res.data.code !== 0) throw new Error(res.data.message)
    versions.value = res.data.data || []
    emit('loaded')
    after.value = versions.value[0]?.version
    before.value = versions.value[1]?.version ?? after.value
    if (after.value) await compare()
  } catch (error) {
    if (token === sequence) message.error(error instanceof Error ? error.message : '加载版本失败')
  } finally {
    if (token === sequence) loading.value = false
  }
}
async function compare() {
  if (!before.value || !after.value) return
  const token = ++compareSequence
  compareReady.value = false
  comparing.value = true
  try {
    const [a, b] = await Promise.all([
      getVersionFiles(props.appId, before.value),
      getVersionFiles(props.appId, after.value),
    ])
    if (token !== compareSequence) return
    if (a.data.code !== 0 || b.data.code !== 0)
      throw new Error(a.data.message || b.data.message || '读取版本失败')
    leftFiles.value = a.data.data
    rightFiles.value = b.data.data
    compareReady.value = true
  } catch (error) {
    if (token === compareSequence)
      message.error(error instanceof Error ? error.message : '版本对比失败')
  } finally {
    if (token === compareSequence) comparing.value = false
  }
}
async function restore() {
  if (!after.value || restoring.value || props.busy || !compareReady.value) return
  restoring.value = true
  try {
    const res = await rollbackVersion(props.appId, after.value)
    if (res.data.code !== 0) throw new Error(res.data.message)
    message.success(`已恢复为新版本 v${res.data.data}，已部署网站保持不变`)
    emit('restored')
    await load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '回退失败')
  } finally {
    restoring.value = false
  }
}
watch(
  () => [props.open, props.appId] as const,
  ([open]) => {
    if (open) void load()
    else {
      ++sequence
      ++compareSequence
    }
  },
)
</script>

<template>
  <a-drawer
    :open="open"
    title="版本历史与代码对比"
    width="min(1100px, 100vw)"
    @update:open="emit('update:open', $event)"
  >
    <a-alert
      message="每次成功生成都会保存完整版本。回退会创建新版本；需要重新部署才会更新已发布的网站。"
      type="info"
      show-icon
      class="version-help"
    />
    <a-spin :spinning="loading">
      <a-empty v-if="!loading && !versions.length" description="成功生成后，这里会保存版本记录" />
      <template v-if="versions.length">
        <div class="version-controls">
          <label
            >原版本<a-select v-model:value="before" :options="options" @change="compare"
          /></label>
          <label
            >目标版本<a-select v-model:value="after" :options="options" @change="compare"
          /></label>
          <label
            >代码文件<a-select
              v-model:value="filename"
              :options="
                ['index.html', 'style.css', 'script.js'].map((value) => ({ value, label: value }))
              "
          /></label>
          <a-popconfirm
            title="确认恢复此版本？"
            description="会创建一个新版本，不会覆盖历史或直接修改已部署网站。"
            @confirm="restore"
          >
            <a-button
              type="primary"
              :loading="restoring"
              :disabled="
                busy || comparing || restoring || !compareReady || after === currentVersion
              "
              >恢复目标版本</a-button
            >
          </a-popconfirm>
        </div>
        <p v-if="selected" class="version-summary">
          {{ formatDateTime(selected.createTime) }} · {{ selected.summary }}
        </p>
        <a-spin :spinning="comparing">
          <div class="version-diff" v-if="compareReady">
            <div class="version-diff__head">
              <span>v{{ before }} · {{ removed }} 行删除</span
              ><span>v{{ after }} · {{ added }} 行新增</span>
            </div>
            <div class="version-diff__scroll">
              <div v-for="(row, i) in rows" :key="i" class="version-diff__row">
                <div
                  class="version-diff__cell"
                  :class="{ 'version-diff__cell--removed': row.removed }"
                >
                  <span class="version-diff__number">{{ row.leftLine }}</span
                  ><code>{{ row.left || ' ' }}</code>
                </div>
                <div class="version-diff__cell" :class="{ 'version-diff__cell--added': row.added }">
                  <span class="version-diff__number">{{ row.rightLine }}</span
                  ><code>{{ row.right || ' ' }}</code>
                </div>
              </div>
              <a-empty v-if="!rows.length" description="此文件为空或未包含在这两个版本中" />
            </div>
          </div>
        </a-spin>
      </template>
    </a-spin>
  </a-drawer>
</template>

<style scoped>
.version-help {
  margin-bottom: 20px;
}
.version-controls {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  flex-wrap: wrap;
}
.version-controls label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  min-width: 150px;
}
.version-summary {
  color: #7b8192;
  margin: 16px 0;
  overflow-wrap: anywhere;
}
.version-diff {
  border: 1px solid #e6e8ef;
  border-radius: 8px;
  overflow: hidden;
}
.version-diff__head {
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #f7f8fa;
  padding: 12px;
  gap: 24px;
  font-weight: 600;
}
.version-diff__scroll {
  overflow: auto;
  max-height: 65vh;
}
.version-diff__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  min-width: 700px;
}
.version-diff__cell {
  display: flex;
  gap: 12px;
  padding: 2px 10px;
  min-width: 0;
  line-height: 24px;
  border-right: 1px solid #eee;
}
.version-diff__cell code {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
}
.version-diff__number {
  flex: 0 0 32px;
  color: #98a2b3;
  text-align: right;
  font-size: 12px;
}
.version-diff__cell--removed {
  background: #fff0f0;
}
.version-diff__cell--added {
  background: #ebf9f0;
}
@media (max-width: 767px) {
  .version-controls label {
    min-width: 120px;
  }
}
</style>
