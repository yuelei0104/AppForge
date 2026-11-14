<script setup lang="ts">
import type { AppInfo } from '@/api/workspace'
import { computed, ref, watch } from 'vue'
import {
  AppstoreOutlined,
  EditOutlined,
  ArrowRightOutlined,
  ExportOutlined,
} from '@ant-design/icons-vue'
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
        <div v-else class="app-card__placeholder">
          <div class="app-card__placeholder-window">
            <span /><span /><span />
            <div class="app-card__placeholder-content">
              <AppstoreOutlined /><strong>{{ initial }}</strong
              ><small>{{
                app.coverEnabled && coverPending(app.coverStatus) ? '正在准备预览' : '暂无页面预览'
              }}</small>
            </div>
          </div>
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
      <div class="app-card__identity">
        <UserInfo :user="app.user" :title="appName" :size="32" class="app-card__creator" />
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
        <span :title="formatDateTime(app.updateTime || app.createTime)">{{
          app.updateTime || app.createTime
            ? `${formatRelativeTime(app.updateTime || app.createTime)}更新`
            : '尚未更新'
        }}</span
        ><span v-if="app.currentVersion">v{{ app.currentVersion }}</span>
      </div>
      <div class="app-card__actions">
        <a-button type="text" class="app-card__continue" @click="handleOpen"
          >{{ editable ? '继续编辑' : '查看对话' }}<ArrowRightOutlined
        /></a-button>
        <a-button
          v-if="isOnline(app) && app.deployKey"
          size="small"
          :href="getAppDeployUrl(app.deployKey)"
          target="_blank"
          rel="noopener noreferrer"
          >查看作品<ExportOutlined
        /></a-button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.app-card {
  min-width: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--forge-border);
  border-radius: 14px;
  transition:
    border-color 0.2s,
    box-shadow 0.2s,
    transform 0.2s;
}
.app-card:hover {
  border-color: #d6cfee;
  box-shadow: 0 8px 30px #3f34650d;
  transform: translateY(-3px);
}
.app-card__cover {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: #f4f3f8;
  border-bottom: 1px solid #f0edf7;
}
.app-card__cover-link {
  display: block;
  width: 100%;
  height: 100%;
  padding: 0;
  border: 0;
  background: none;
  cursor: pointer;
}
.app-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}
.app-card:hover .app-card__cover img {
  transform: scale(1.025);
}
.app-card__placeholder {
  display: grid;
  place-items: center;
  height: 100%;
  background: radial-gradient(ellipse at top left, #eee9ff, transparent 70%), #f3f5fb;
}
.app-card__placeholder-window {
  width: 68%;
  height: 72%;
  padding: 10px;
  text-align: left;
  background: #ffffffb3;
  border: 1px solid #fff;
  border-radius: 9px;
  box-shadow: 0 10px 25px #4c41700a;
}
.app-card__placeholder-window > span {
  display: inline-block;
  width: 5px;
  height: 5px;
  margin-right: 4px;
  background: #dedbea;
  border-radius: 50%;
}
.app-card__placeholder-content {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 5px;
  height: calc(100% - 16px);
  color: #b5adcf;
  font-size: 15px;
}
.app-card__placeholder-content strong {
  font-size: 24px;
  font-weight: 500;
  line-height: 1;
}
.app-card__placeholder-content small {
  font-size: 10px;
}
.app-card__featured {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 4px 8px;
  color: #6c5798;
  background: #fffffff2;
  border: 1px solid #e9e3fa;
  border-radius: 6px;
  font-size: 10px;
  pointer-events: none;
}
.app-card__cover-status {
  position: absolute;
  bottom: 8px;
  left: 10px;
  padding: 4px 8px;
  color: #7b738b;
  background: #fffffff2;
  border-radius: 6px;
  font-size: 11px;
  pointer-events: none;
}
.app-card__edit {
  position: absolute;
  top: 8px;
  right: 8px;
  color: #736781;
  background: #ffffffed;
}
.app-card__body {
  padding: 18px 18px 12px;
}
.app-card__identity {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.app-card__creator {
  flex: 1;
  min-width: 0;
}
.app-card__creator :deep(.user-info__title) {
  font-size: 14px;
  font-weight: 600;
}
.app-card__creator :deep(.user-info__subtitle) {
  font-size: 11px;
}
.app-card__status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #9a8c6c;
  font-size: 10px;
  white-space: nowrap;
}
.app-card__status i {
  width: 5px;
  height: 5px;
  background: currentColor;
  border-radius: 50%;
}
.app-card__status--online {
  color: #4c9680;
}
.app-card__status--error {
  color: #ca746c;
}
.app-card__status--busy {
  color: #7e65c1;
}
.app-card__description {
  display: -webkit-box;
  min-height: 36px;
  margin: 14px 0 8px;
  overflow: hidden;
  color: #787284;
  font-size: 12px;
  line-height: 1.6;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
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
  color: #8a809c;
  background: #f7f5fb;
  border: 0;
  font-size: 10px;
}
.app-card__meta {
  display: flex;
  justify-content: space-between;
  margin-top: 14px;
  color: #82798f;
  font-size: 10px;
}
.app-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 10px;
  margin-top: 10px;
  border-top: 1px solid #f2eff6;
}
.app-card__continue {
  color: var(--forge-accent);
  padding-left: 0;
  font-size: 12px;
}
.app-card__actions :deep(.ant-btn-sm) {
  color: #7c748b;
  font-size: 11px;
  border-color: #eae5f0;
  box-shadow: none;
}
</style>
