<script setup lang="ts">
import { computed } from 'vue'
import { DeleteOutlined, EditOutlined } from '@ant-design/icons-vue'
import { formatDateTime } from '@/utils/date'

const props = defineProps<{
  open: boolean
  app: API.AppVO | null
  canManage: boolean
  deleting?: boolean
  busy?: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  edit: []
  delete: []
}>()

const creatorName = computed(() => props.app?.user?.userName || '未命名用户')
</script>

<template>
  <a-modal
    :open="open"
    title="应用详情"
    :footer="null"
    :width="520"
    @update:open="emit('update:open', $event)"
  >
    <a-descriptions v-if="app" :column="1" class="app-detail-modal__info">
      <a-descriptions-item label="应用名称">{{ app.appName || '未命名应用' }}</a-descriptions-item>
      <a-descriptions-item label="创建者">
        <a-space>
          <a-avatar :src="app.user?.userAvatar || undefined" :size="28">
            {{ creatorName.slice(0, 1) }}
          </a-avatar>
          <span>{{ creatorName }}</span>
        </a-space>
      </a-descriptions-item>
      <a-descriptions-item label="创建时间">
        {{ formatDateTime(app.createTime) }}
      </a-descriptions-item>
    </a-descriptions>

    <template v-if="app && canManage">
      <a-divider />
      <a-space>
        <a-button type="primary" :disabled="busy || deleting" @click="emit('edit')">
          <template #icon><EditOutlined /></template>
          修改
        </a-button>
        <a-popconfirm
          title="确定删除该应用吗？"
          description="删除后将无法继续查看和编辑该应用。"
          ok-text="删除"
          cancel-text="取消"
          :disabled="busy || deleting"
          @confirm="emit('delete')"
        >
          <a-button danger :loading="deleting" :disabled="busy || deleting">
            <template #icon><DeleteOutlined /></template>
            删除
          </a-button>
        </a-popconfirm>
      </a-space>
    </template>
  </a-modal>
</template>

<style scoped>
.app-detail-modal__info {
  margin-top: 20px;
}
</style>
