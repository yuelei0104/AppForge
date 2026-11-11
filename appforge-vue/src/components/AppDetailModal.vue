<script setup lang="ts">
import type { AppInfo } from '@/api/workspace'
import { DeleteOutlined, EditOutlined } from '@ant-design/icons-vue'
import AppCoverControls from '@/components/AppCoverControls.vue'
import UserInfo from '@/components/UserInfo.vue'
import { generationLabel, featureLabel, isOnline } from '@/utils/appStatus'
import { formatDateTime } from '@/utils/date'
import { codeGenTypeLabel } from '@/config/codeGenType'

defineProps<{
  open: boolean
  app: AppInfo | null
  canManage: boolean
  deleting?: boolean
  busy?: boolean
  isOwner?: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  edit: []
  delete: []
  offline: []
  feature: []
  'cover-change': []
}>()
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
        <UserInfo :user="app.user" />
      </a-descriptions-item>
      <a-descriptions-item label="可见范围">{{
        app.visibility === 'PUBLIC' ? '公开' : '私有'
      }}</a-descriptions-item>
      <a-descriptions-item label="生成状态"
        >{{ generationLabel(app.generationStatus) }} · v{{
          app.currentVersion || 0
        }}</a-descriptions-item
      >
      <a-descriptions-item label="部署状态">{{
        isOnline(app) ? `在线 · v${app.deployedVersion || 0}` : '已下线'
      }}</a-descriptions-item>
      <a-descriptions-item label="精选申请"
        >{{ featureLabel(app.featureStatus)
        }}<span v-if="app.featureReason"> · {{ app.featureReason }}</span></a-descriptions-item
      >
      <a-descriptions-item label="创建时间">
        {{ formatDateTime(app.createTime) }}
      </a-descriptions-item>
      <a-descriptions-item label="生成类型">
        <a-tag v-if="app.codeGenType" color="blue">{{ codeGenTypeLabel(app.codeGenType) }}</a-tag>
        <span v-else>未知类型</span>
      </a-descriptions-item>
    </a-descriptions>

    <template v-if="app && canManage">
      <AppCoverControls
        v-if="open"
        :app-id="String(app.id)"
        :version="app.currentVersion || 0"
        :active="open"
        :busy="busy || deleting"
        :initial-manual-url="app.coverMode === 'MANUAL' ? app.cover : ''"
        @changed="emit('cover-change')"
      />
      <a-divider />
      <a-space wrap>
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
        <a-popconfirm
          v-if="isOnline(app)"
          title="确认下线应用？"
          description="下线后部署链接将无法访问，之后可以重新部署。"
          @confirm="emit('offline')"
        >
          <a-button :disabled="busy || deleting">下线应用</a-button>
        </a-popconfirm>
        <a-button
          v-if="
            isOwner &&
            app.visibility === 'PUBLIC' &&
            !['PENDING', 'APPROVED'].includes(app.featureStatus || '')
          "
          :disabled="busy || deleting"
          @click="emit('feature')"
          >申请精选</a-button
        >
      </a-space>
    </template>
  </a-modal>
</template>

<style scoped>
.app-detail-modal__info {
  margin-top: 20px;
}
</style>
