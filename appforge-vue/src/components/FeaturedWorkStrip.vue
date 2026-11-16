<script setup lang="ts">
import type { AppInfo } from '@/api/workspace'
import { API_BASE_URL } from '@/config/env'
import { resolveCoverUrl } from '@/utils/cover'

defineProps<{ apps: AppInfo[] }>()
const emit = defineEmits<{ open: [app: AppInfo] }>()

function typeLabel(value?: string) {
  if (value === 'vue_project') return '多页应用'
  if (value === 'multi_file') return '轻量网站'
  return '单页站点'
}
</script>

<template>
  <div class="work-strip" aria-label="最近完成的作品">
    <span class="work-strip__label">刚刚完成 / CURATED</span>
    <button
      v-for="app in apps"
      :key="String(app.id)"
      type="button"
      class="work-strip__item"
      @click="emit('open', app)"
    >
      <span class="work-strip__cover">
        <img
          v-if="app.cover"
          :src="resolveCoverUrl(app, API_BASE_URL)"
          :alt="`${app.appName || '应用'}预览`"
          loading="lazy"
        />
        <span v-else class="work-strip__fallback">
          <i /><i /><i />
          <b>{{ (app.appName || 'A').slice(0, 1) }}</b>
        </span>
      </span>
      <span class="work-strip__copy">
        <strong>{{ app.appName || '未命名应用' }}</strong>
        <small>{{ typeLabel(app.codeGenType) }} · {{ app.currentVersion ? `v${app.currentVersion}` : '草稿' }}</small>
      </span>
      <span class="work-strip__arrow">↗</span>
    </button>
  </div>
</template>

<style scoped>
.work-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 18px;
  text-align: left;
}

.work-strip__label {
  grid-column: 1 / -1;
  color: var(--forge-muted-2);
  font-family: var(--forge-font-mono);
  font-size: 7px;
  letter-spacing: 0.12em;
}

.work-strip__item {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
  padding: 6px;
  text-align: left;
  cursor: pointer;
  background: rgb(255 253 249 / 68%);
  border: 1px solid var(--forge-line);
  border-radius: 7px;
  transition:
    border-color 0.18s,
    transform 0.18s;
}

.work-strip__item:hover {
  border-color: var(--forge-orange);
  transform: translateY(-2px);
}

.work-strip__cover {
  position: relative;
  flex: none;
  width: 52px;
  height: 38px;
  overflow: hidden;
  background: #e7dfd2;
  border-radius: 4px;
}

.work-strip__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.work-strip__fallback {
  position: relative;
  display: block;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #242127, #4b2a20);
}

.work-strip__fallback i {
  display: inline-block;
  width: 3px;
  height: 3px;
  margin: 3px 0 0 3px;
  background: #736b60;
  border-radius: 50%;
}

.work-strip__fallback b {
  position: absolute;
  right: 4px;
  bottom: 1px;
  color: #fffdf9;
  font-family: var(--forge-font-display);
  font-size: 20px;
  line-height: 1;
}

.work-strip__copy {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.work-strip__copy strong {
  overflow: hidden;
  color: var(--forge-ink);
  font-size: 10px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.work-strip__copy small {
  overflow: hidden;
  color: var(--forge-muted-2);
  font-size: 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.work-strip__arrow {
  margin-left: auto;
  color: var(--forge-muted-2);
  font-size: 10px;
}

@media (max-width: 700px) {
  .work-strip {
    grid-template-columns: 1fr;
  }
  .work-strip__label {
    margin-top: 2px;
  }
}
</style>
