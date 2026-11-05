<script setup lang="ts">
import { computed, ref } from 'vue'
import { AppstoreOutlined, EditOutlined } from '@ant-design/icons-vue'
import { formatRelativeTime } from '@/utils/date'

const props = withDefaults(
  defineProps<{
    app: API.AppVO
    /** 是否显示编辑按钮 */
    editable?: boolean
    /** 是否显示创建者信息 */
    showAuthor?: boolean
  }>(),
  {
    editable: false,
    showAuthor: false,
  },
)

const emit = defineEmits<{
  open: [app: API.AppVO]
  edit: [app: API.AppVO]
}>()

const coverFailed = ref(false)

const appName = computed(() => props.app.appName || '未命名应用')
const initial = computed(() => appName.value.slice(0, 1).toUpperCase())
const authorName = computed(
  () => props.app.user?.userName || props.app.user?.userAccount || '匿名用户',
)
const isFeatured = computed(() => props.app.priority === 99)

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

      <a-tag v-if="isFeatured" color="blue" class="app-card__featured">精选</a-tag>

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
      <h3 class="app-card__title" :title="appName">{{ appName }}</h3>
      <div class="app-card__meta">
        <span>创建于 {{ formatRelativeTime(app.createTime) }}</span>
        <span v-if="showAuthor" class="app-card__author">
          <a-avatar :src="app.user?.userAvatar || undefined" :size="20">
            {{ authorName.slice(0, 1) }}
          </a-avatar>
          {{ authorName }}
        </span>
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
  height: 168px;
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
  top: 12px;
  left: 12px;
  margin: 0;
  border: none;
}

.app-card__edit {
  position: absolute;
  top: 8px;
  right: 8px;
  color: #fff;
  background: rgba(15, 23, 42, 0.45);
  opacity: 0;
  transition: opacity 0.2s;
}

.app-card:hover .app-card__edit,
.app-card__edit:focus-visible {
  opacity: 1;
}

.app-card__body {
  padding: 14px 16px 16px;
}

.app-card__title {
  margin: 0;
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
  color: rgba(0, 0, 0, 0.88);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-card__meta {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
  color: #98a2b3;
}

.app-card__author {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
  color: #667085;
}

@media (max-width: 767px) {
  .app-card__cover {
    height: 150px;
  }

  .app-card__edit {
    opacity: 1;
  }
}
</style>
