<script setup lang="ts">
import type { AppInfo } from '@/api/workspace'
import { computed, ref, watch } from 'vue'
import { AppstoreOutlined, EditOutlined } from '@ant-design/icons-vue'
import { generationLabel, isOnline } from '@/utils/appStatus'
import UserInfo from '@/components/UserInfo.vue'
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

const coverFailed = ref(false)
watch(
  () => props.app.cover,
  () => {
    coverFailed.value = false
  },
)

const appName = computed(() => props.app.appName || '未命名应用')
const initial = computed(() => appName.value.slice(0, 1).toUpperCase())
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
  <article class="app-card" @click="handleOpen">
    <div class="app-card__cover">
      <img
        v-if="app.cover && !coverFailed"
        :src="app.cover"
        :alt="appName"
        loading="lazy"
        @error="coverFailed = true"
      />
      <div v-else class="app-card__placeholder">
        <AppstoreOutlined class="app-card__placeholder-icon" />
        <span class="app-card__placeholder-text">{{ initial }}</span>
      </div>

      <a-tag v-if="isFeatured" color="blue" class="app-card__featured">{{
        (app.priority ?? 0) >= 999 ? '置顶' : '精选'
      }}</a-tag>

      <div class="app-card__actions">
        <a-button type="primary" @click.stop="handleOpen">查看对话</a-button>
        <a-button
          v-if="isOnline(app) && app.deployKey"
          :href="getAppDeployUrl(app.deployKey)"
          target="_blank"
          rel="noopener noreferrer"
          @click.stop
        >
          查看作品
        </a-button>
      </div>

      <a-button
        v-if="editable"
        class="app-card__edit"
        type="text"
        shape="circle"
        aria-label="编辑应用"
        @click="handleEdit"
      >
        <template #icon>
          <EditOutlined />
        </template>
      </a-button>
    </div>

    <div class="app-card__body">
      <UserInfo :user="app.user" :title="appName" :size="40" class="app-card__creator" />
      <p v-if="app.description" class="app-card__description">{{ app.description }}</p>
      <div class="app-card__labels">
        <a-tag v-if="app.visibility === 'PRIVATE'">私有</a-tag>
        <a-tag
          v-if="app.generationStatus"
          :color="app.generationStatus === 'GENERATING' ? 'processing' : 'default'"
          >{{ generationLabel(app.generationStatus) }}</a-tag
        >
        <a-tag v-for="tag in app.tags" :key="tag" color="blue">{{ tag }}</a-tag>
      </div>
    </div>
  </article>
</template>

<style scoped>
.app-card {
  overflow: hidden;
  cursor: pointer;
  background: #fff;
  border: 1px solid #edf0f5;
  border-radius: 16px;
  box-shadow: 0 4px 18px rgba(15, 23, 42, 0.04);
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.app-card:hover {
  border-color: #d6e4ff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.1);
  transform: translateY(-4px);
}

.app-card__cover {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: #f3f6fb;
}

.app-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.app-card:hover .app-card__cover img {
  transform: scale(1.03);
}

.app-card__placeholder {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: #fff;
  background: linear-gradient(135deg, #4f7cff, #8f6bff 55%, #53c8d8);
}

.app-card__placeholder-icon {
  font-size: 28px;
  opacity: 0.9;
}

.app-card__placeholder-text {
  font-size: 34px;
  font-weight: 700;
  opacity: 0.9;
}

.app-card__featured {
  position: absolute;
  z-index: 2;
  top: 12px;
  left: 12px;
  margin: 0;
  border: none;
}

.app-card__edit {
  position: absolute;
  z-index: 2;
  top: 8px;
  right: 8px;
  color: #fff;
  background: rgba(15, 23, 42, 0.45);
  opacity: 0;
  transition: opacity 0.2s;
}

.app-card:hover .app-card__edit,
.app-card:focus-within .app-card__edit,
.app-card__edit:focus-visible {
  opacity: 1;
}

.app-card__actions {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: center;
  padding: 36px 12px;
  background: rgba(15, 23, 42, 0.36);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s;
}

.app-card:hover .app-card__actions,
.app-card:focus-within .app-card__actions {
  opacity: 1;
  pointer-events: auto;
}

@media (hover: none) {
  .app-card__actions {
    opacity: 1;
    pointer-events: auto;
  }
}

.app-card__body {
  padding: 16px 18px;
}

.app-card__description {
  margin: 12px 0 0;
  color: #7b8192;
  font-size: 13px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.app-card__labels {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 10px;
}
.app-card__labels:empty {
  display: none;
}
.app-card__labels :deep(.ant-tag) {
  margin: 0;
}

.app-card__creator {
  width: 100%;
}

.app-card__creator :deep(.user-info__title) {
  font-size: 16px;
  font-weight: 600;
}

@media (max-width: 767px) {
  .app-card__edit {
    opacity: 1;
  }
}
</style>
