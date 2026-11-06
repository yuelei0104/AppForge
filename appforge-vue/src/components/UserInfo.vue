<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    user?: { userName?: string; userAccount?: string; userAvatar?: string }
    size?: number
    /** 传入应用标题时，昵称作为第二行展示。 */
    title?: string
  }>(),
  { size: 28 },
)
const name = computed(() => props.user?.userName || props.user?.userAccount || '未命名用户')
const titleText = computed(() => props.title || name.value)
</script>

<template>
  <span class="user-info">
    <a-avatar
      :src="user?.userAvatar || undefined"
      :size="size"
      :alt="name"
      class="user-info__avatar"
    >
      {{ name.slice(0, 1) }}
    </a-avatar>
    <span class="user-info__text">
      <span class="user-info__title" :title="titleText">{{ titleText }}</span>
      <span v-if="title" class="user-info__subtitle" :title="name">{{ name }}</span>
    </span>
  </span>
</template>

<style scoped>
.user-info {
  display: inline-flex;
  gap: 10px;
  align-items: center;
  max-width: 100%;
  min-width: 0;
  vertical-align: middle;
}
.user-info__avatar {
  flex: none;
  background: #eef0ff;
  color: #6067c9;
}
.user-info__text {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}
.user-info__title,
.user-info__subtitle {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.5;
}
.user-info__title {
  color: #262b3b;
}
.user-info__subtitle {
  font-size: 13px;
  color: #7b8192;
}
</style>
