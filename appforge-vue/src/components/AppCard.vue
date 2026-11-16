<script setup lang="ts">
import type { AppInfo } from '@/api/workspace'
import { computed, ref, watch } from 'vue'
import { EditOutlined, ArrowRightOutlined, ExportOutlined } from '@ant-design/icons-vue'
import { generationLabel, isOnline } from '@/utils/appStatus'
import UserInfo from '@/components/UserInfo.vue'
import { API_BASE_URL } from '@/config/env'
import { coverPending, resolveCoverUrl } from '@/utils/cover'
import { formatRelativeTime, formatDateTime } from '@/utils/date'
import { getAppDeployUrl } from '@/utils/appUrls'

const props = withDefaults(
  defineProps<{
    app: AppInfo
    /** 是否显示编辑按钮 */
    editable?: boolean
  }>(),
  {
    editable: false,
  },
)

const emit = defineEmits<{
  open: [app: AppInfo]
  edit: [app: AppInfo]
}>()

const coverUrl = computed(() => resolveCoverUrl(props.app, API_BASE_URL))
const coverFailed = ref(false)
watch(
  () => props.app.cover,
  () => {
    coverFailed.value = false
  },
)

const appName = computed(() => props.app.appName || '未命名应用')
const initial = computed(() => appName.value.slice(0, 1).toUpperCase())
const typeLabel = computed(() => {
  if (props.app.codeGenType === 'vue_project') return '多页应用'
  if (props.app.codeGenType === 'multi_file') return '轻量网站'
  return '单页站点'
})
const statusLabel = computed(() => {
  if (
    props.app.generationStatus === 'GENERATING' ||
    props.app.generationStatus === 'FAILED' ||
    props.app.generationStatus === 'STOPPED'
  )
    return generationLabel(props.app.generationStatus)
  if (isOnline(props.app)) return '已发布'
  if ((props.app.currentVersion ?? 0) > 0 || props.app.generationStatus === 'SUCCEEDED')
    return '待发布'
  return '待生成'
})
const isFeatured = computed(() => (props.app.priority ?? 0) >= 99)

function handleOpen() {
  emit('open', props.app)
}

function handleEdit(event: MouseEvent) {
  event.stopPropagation()
  emit('edit', props.app)
}
</script>

<template>
  <article class="app-card">
    <div class="app-card__cover">
      <button
        type="button"
        class="app-card__cover-link"
        :aria-label="`${editable ? '继续编辑' : '查看对话'}：${appName}`"
        @click="handleOpen"
      >
        <img
          v-if="app.cover && !coverFailed"
          :src="coverUrl"
          :alt="`${appName}页面预览`"
          loading="lazy"
          @error="coverFailed = true"
        />
        <div
          v-else
          class="app-card__placeholder"
          :class="`app-card__placeholder--${app.codeGenType || 'html'}`"
        >
          <div class="app-card__placeholder-chrome">
            <i /><i /><i /><span>{{ typeLabel }}</span>
          </div>
          <div class="app-card__placeholder-canvas">
            <span class="app-card__placeholder-kicker">{{ initial }} / {{ typeLabel }}</span>
            <strong>{{ appName }}</strong>
            <span class="app-card__placeholder-line" />
            <span class="app-card__placeholder-line app-card__placeholder-line--short" />
            <span class="app-card__placeholder-cta">第一版 · 可编辑</span>
          </div>
          <span v-if="app.coverEnabled && coverPending(app.coverStatus)" class="app-card__placeholder-note"
            >正在准备封面</span
          >
        </div>
      </button>
      <span v-if="isFeatured" class="app-card__featured">{{
        (app.priority ?? 0) >= 999 ? '置顶作品' : '精选作品'
      }}</span>
      <span
        v-if="app.coverEnabled && (coverPending(app.coverStatus) || app.coverStatus === 'FAILED')"
        class="app-card__cover-status"
        aria-live="polite"
        >{{
          app.coverStatus === 'FAILED' ? '封面暂未更新' : app.cover ? '封面更新中' : '封面生成中'
        }}</span
      >
      <a-button
        v-if="editable"
        class="app-card__edit"
        type="text"
        shape="circle"
        aria-label="编辑应用信息"
        @click="handleEdit"
        ><EditOutlined
      /></a-button>
    </div>
    <div class="app-card__body">
      <div class="app-card__title-row">
        <h3 :title="appName">{{ appName }}</h3>
        <span
          class="app-card__status"
          :class="{
            'app-card__status--online': statusLabel === '已发布',
            'app-card__status--error': statusLabel === '生成失败',
            'app-card__status--busy': statusLabel === '生成中',
          }"
          ><i />{{ statusLabel }}</span
        >
      </div>
      <p class="app-card__description">
        {{ app.description || '一个正在实现的想法，进入查看创作详情。' }}
      </p>
      <div class="app-card__labels">
        <a-tag v-if="app.visibility === 'PRIVATE'">私有</a-tag
        ><a-tag v-for="tag in app.tags" :key="tag">{{ tag }}</a-tag>
      </div>
      <div class="app-card__meta">
        <UserInfo :user="app.user" :size="18" class="app-card__creator" />
        <span :title="formatDateTime(app.updateTime || app.createTime)">{{
          app.updateTime || app.createTime
            ? `${formatRelativeTime(app.updateTime || app.createTime)}更新`
            : '尚未更新'
        }}</span>
        <span v-if="app.currentVersion">v{{ app.currentVersion }}</span>
      </div>
      <div class="app-card__actions">
        <button type="button" class="app-card__continue" @click="handleOpen">
          {{ editable ? '继续编辑' : '进入作品' }}<ArrowRightOutlined />
        </button>
        <a
          v-if="isOnline(app) && app.deployKey"
          class="app-card__visit"
          :href="getAppDeployUrl(app.deployKey)"
          target="_blank"
          rel="noopener noreferrer"
          >访问 <ExportOutlined
        /></a>
      </div>
    </div>  </article>
</template>

<style scoped>
.app-card {
  min-width: 0;
  overflow: hidden;
  background: var(--forge-card);
  border: 1px solid #d8ccbb;
  border-radius: 9px;
  transition:
    border-color 0.2s,
    box-shadow 0.2s,
    transform 0.2s;
}

.app-card:hover {
  border-color: #b8a891;
  box-shadow: 0 18px 40px rgb(66 48 29 / 10%);
  transform: translateY(-3px);
}

.app-card__cover {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: #e7ded1;
  border-bottom: 1px solid #e3d8c8;
}

.app-card__cover-link {
  display: block;
  width: 100%;
  height: 100%;
  padding: 0;
  cursor: pointer;
  background: none;
  border: 0;
}

.app-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s ease;
}

.app-card:hover .app-card__cover img {
  transform: scale(1.03);
}

.app-card__placeholder {
  position: relative;
  display: flex;
  height: 100%;
  flex-direction: column;
  padding: 12px;
  overflow: hidden;
  background: #eae1d4;
}

.app-card__placeholder::after {
  position: absolute;
  right: -24px;
  bottom: -34px;
  width: 110px;
  height: 110px;
  content: '';
  background: rgb(242 91 53 / 16%);
  border-radius: 50%;
}

.app-card__placeholder--vue_project {
  background: linear-gradient(145deg, #e9dfd1, #d9c9b5);
}

.app-card__placeholder--multi_file {
  background: linear-gradient(145deg, #e2e7d7, #ccd7c0);
}

.app-card__placeholder--multi_file::after {
  background: rgb(47 125 90 / 14%);
}

.app-card__placeholder--html {
  background: linear-gradient(145deg, #e3dde5, #d4c9db);
}

.app-card__placeholder--html::after {
  background: rgb(47 63 168 / 12%);
}

.app-card__placeholder-chrome {
  display: flex;
  gap: 4px;
  align-items: center;
  padding-bottom: 8px;
  border-bottom: 1px solid rgb(23 21 26 / 10%);
}

.app-card__placeholder-chrome i {
  width: 4px;
  height: 4px;
  background: rgb(23 21 26 / 26%);
  border-radius: 50%;
}

.app-card__placeholder-chrome span {
  margin-left: 4px;
  color: var(--forge-muted);
  font-family: var(--forge-font-mono);
  font-size: 7px;
  letter-spacing: 0.06em;
}

.app-card__placeholder-canvas {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
  padding: 12px 6px 4px;
}

.app-card__placeholder-kicker {
  margin-bottom: 7px;
  color: var(--forge-orange-deep);
  font-family: var(--forge-font-mono);
  font-size: 7px;
  letter-spacing: 0.1em;
}

.app-card__placeholder-canvas strong {
  display: -webkit-box;
  overflow: hidden;
  color: var(--forge-ink);
  font-family: var(--forge-font-display);
  font-size: clamp(17px, 2vw, 27px);
  font-weight: 700;
  line-height: 1.05;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.app-card__placeholder-line {
  width: 76%;
  height: 3px;
  margin-top: 10px;
  background: rgb(23 21 26 / 14%);
  border-radius: 2px;
}

.app-card__placeholder-line--short {
  width: 44%;
  margin-top: 5px;
}

.app-card__placeholder-cta {
  align-self: flex-start;
  padding: 4px 7px;
  margin-top: 12px;
  color: var(--forge-ink);
  font-family: var(--forge-font-mono);
  font-size: 6px;
  letter-spacing: 0.08em;
  background: rgb(255 253 249 / 60%);
  border: 1px solid rgb(23 21 26 / 15%);
  border-radius: 3px;
}

.app-card__placeholder-note {
  position: absolute;
  right: 10px;
  bottom: 9px;
  z-index: 1;
  color: var(--forge-muted);
  font-size: 8px;
}

.app-card__featured {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 1;
  padding: 4px 7px;
  color: var(--forge-ink);
  font-family: var(--forge-font-mono);
  font-size: 7px;
  letter-spacing: 0.04em;
  pointer-events: none;
  background: var(--forge-lime);
  border-radius: 3px;
}

.app-card__cover-status {
  position: absolute;
  bottom: 9px;
  left: 10px;
  z-index: 1;
  padding: 4px 7px;
  color: var(--forge-ink);
  font-size: 8px;
  pointer-events: none;
  background: rgb(255 253 249 / 90%);
  border-radius: 3px;
}

.app-card__edit {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  color: var(--forge-ink);
  background: rgb(255 253 249 / 88%);
}

.app-card__body {
  padding: 15px 15px 12px;
}

.app-card__title-row {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.app-card__title-row h3 {
  overflow: hidden;
  margin: 0;
  color: var(--forge-ink);
  font-size: 14px;
  font-weight: 650;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-card__status {
  display: inline-flex;
  flex: none;
  gap: 4px;
  align-items: center;
  color: #9a7b35;
  font-family: var(--forge-font-mono);
  font-size: 7px;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.app-card__status i {
  width: 4px;
  height: 4px;
  background: currentcolor;
  border-radius: 50%;
}

.app-card__status--online {
  color: var(--forge-green);
}

.app-card__status--error {
  color: var(--forge-red);
}

.app-card__status--busy {
  color: var(--forge-orange-deep);
}

.app-card__description {
  display: -webkit-box;
  min-height: 34px;
  margin: 10px 0 8px;
  overflow: hidden;
  color: var(--forge-muted);
  font-size: 11px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.app-card__labels {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.app-card__labels:empty {
  display: none;
}

.app-card__labels :deep(.ant-tag) {
  margin: 0;
  color: var(--forge-muted);
  font-size: 9px;
  background: #f2eadf;
  border: 0;
}

.app-card__meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 12px;
  color: var(--forge-muted-2);
  font-family: var(--forge-font-mono);
  font-size: 7px;
}

.app-card__meta > span:nth-last-child(1) {
  margin-left: auto;
}

.app-card__creator {
  flex: 1;
  min-width: 0;
}

.app-card__creator :deep(.user-info__text) {
  flex-direction: row;
  gap: 5px;
  align-items: center;
}

.app-card__creator :deep(.user-info__title) {
  max-width: 90px;
  color: var(--forge-muted);
  font-family: var(--forge-font-body);
  font-size: 9px;
}

.app-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 9px;
  margin-top: 9px;
  border-top: 1px solid #e8dece;
}

.app-card__continue {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 0;
  color: var(--forge-orange-deep);
  font-size: 10px;
  font-weight: 600;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.app-card__continue:hover {
  color: var(--forge-ink);
}

.app-card__visit {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  color: var(--forge-muted);
  font-size: 9px;
}

.app-card__visit:hover {
  color: var(--forge-orange-deep);
}
</style>