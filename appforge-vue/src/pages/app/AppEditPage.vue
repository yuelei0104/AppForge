<script setup lang="ts">
import type { AppInfo } from '@/api/workspace'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ArrowLeftOutlined, DeleteOutlined, SaveOutlined } from '@ant-design/icons-vue'
import {
  deleteApp,
  deleteAppByAdmin,
  getAppVoById,
  getAppVoByIdByAdmin,
  updateApp,
  updateAppByAdmin,
} from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import UserInfo from '@/components/UserInfo.vue'
import { formatDateTime } from '@/utils/date'
import { isOnline } from '@/utils/appStatus'
import { getAppDeployUrl, getAppPreviewUrl, isValidCoverUrl } from '@/utils/appUrls'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const appId = String(route.params.appId ?? '')
const app = ref<AppInfo | null>(null)
const loading = ref(true)
const saving = ref(false)
const deleting = ref(false)

const form = reactive({
  appName: '',
  cover: '',
  priority: 0,
  description: '',
  tags: [] as string[],
  visibility: 'PRIVATE' as 'PRIVATE' | 'PUBLIC',
})

const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')
const isOwner = computed(
  () =>
    app.value?.userId != null &&
    loginUserStore.loginUser.id != null &&
    String(app.value.userId) === String(loginUserStore.loginUser.id),
)
const canManage = computed(() => !!app.value && (isAdmin.value || isOwner.value))
const backPath = computed(() => (isAdmin.value ? '/admin/appManage' : '/'))

function initForm(data: AppInfo) {
  form.appName = data.appName || ''
  form.cover = data.cover || ''
  form.priority = data.priority ?? 0
  form.description = data.description || ''
  form.tags = [...(data.tags || [])]
  form.visibility = data.visibility === 'PUBLIC' ? 'PUBLIC' : 'PRIVATE'
}

async function loadApp() {
  if (!appId) {
    message.error('应用不存在')
    await router.replace(backPath.value)
    return
  }
  loading.value = true
  try {
    const res = isAdmin.value
      ? await getAppVoByIdByAdmin({ id: appId })
      : await getAppVoById({ id: appId })
    if (res.data.code === 0 && res.data.data) {
      if (
        !isAdmin.value &&
        (res.data.data.userId == null ||
          loginUserStore.loginUser.id == null ||
          String(res.data.data.userId) !== String(loginUserStore.loginUser.id))
      ) {
        message.error('无权编辑该应用')
        await router.replace('/noAuth')
        return
      }
      app.value = res.data.data
      initForm(res.data.data)
    } else if (res.data.code === 40101) {
      message.error('无权编辑该应用')
      await router.replace('/noAuth')
    } else {
      message.error(res.data.message || '应用不存在')
      await router.replace(backPath.value)
    }
  } catch {
    message.error('获取应用信息失败，请稍后重试')
    await router.replace(backPath.value)
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!app.value || !canManage.value || saving.value || deleting.value) {
    return
  }
  const appName = form.appName.trim()
  if (!appName) {
    message.warning('应用名称不能为空')
    return
  }
  if (appName.length > 256) {
    message.warning('应用名称不能超过 256 个字符')
    return
  }
  const cover = form.cover.trim()
  if (cover.length > 512 || !isValidCoverUrl(cover)) {
    message.warning('请输入不超过 512 个字符的有效 HTTP 或 HTTPS 封面链接')
    return
  }
  if (isAdmin.value && (!Number.isInteger(form.priority) || form.priority < 0)) {
    message.warning('优先级必须为非负整数')
    return
  }
  if (form.tags.length > 5 || form.tags.some((tag) => tag.trim().length > 20)) {
    message.warning('最多 5 个标签，每个标签最多 20 字')
    return
  }
  saving.value = true
  try {
    const id = app.value.id ?? appId
    const body = {
      id,
      appName,
      cover,
      description: form.description.trim(),
      tags: form.tags,
      visibility: form.visibility,
    }
    const res = isAdmin.value
      ? await updateAppByAdmin({
          ...body,
          priority: form.visibility === 'PRIVATE' ? 0 : form.priority,
        })
      : await updateApp(body)
    if (res.data.code === 0) {
      message.success('保存成功')
      await router.push(isAdmin.value ? '/admin/appManage' : { path: `/app/chat/${appId}` })
    } else {
      message.error(res.data.message || '保存失败')
    }
  } catch {
    message.error('保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

async function handleDelete() {
  if (!app.value || !canManage.value || saving.value || deleting.value) {
    return
  }
  deleting.value = true
  try {
    const id = app.value.id ?? appId
    const res = isAdmin.value ? await deleteAppByAdmin({ id }) : await deleteApp({ id })
    if (res.data.code === 0 && res.data.data) {
      message.success('删除成功')
      await router.replace(backPath.value)
    } else {
      message.error(res.data.message || '删除失败')
    }
  } catch {
    message.error('删除失败，请稍后重试')
  } finally {
    deleting.value = false
  }
}

function handleReset() {
  if (app.value && canManage.value && !saving.value && !deleting.value) {
    initForm(app.value)
  }
}

function enterChat() {
  void router.push({ path: `/app/chat/${appId}` })
}

onMounted(() => {
  void loadApp()
})
</script>

<template>
  <div class="app-edit-page">
    <div class="app-edit-page__header">
      <div class="app-edit-page__header-left">
        <a-button type="text" @click="router.push(backPath)">
          <template #icon>
            <ArrowLeftOutlined />
          </template>
          返回
        </a-button>
        <div>
          <h1 class="app-edit-page__title">编辑应用信息</h1>
        </div>
      </div>
      <div class="app-edit-page__header-actions">
        <a-popconfirm
          title="确定删除该应用吗？"
          ok-text="删除"
          cancel-text="取消"
          @confirm="handleDelete"
        >
          <a-button danger :loading="deleting" :disabled="!canManage || saving || deleting">
            <template #icon>
              <DeleteOutlined />
            </template>
            删除
          </a-button>
        </a-popconfirm>
      </div>
    </div>

    <a-spin :spinning="loading">
      <div v-if="app" class="app-edit-page__sections">
        <a-card title="基本信息" class="app-edit-page__card">
          <a-form layout="vertical">
            <a-form-item label="应用名称" required>
              <a-input
                v-model:value="form.appName"
                :maxlength="256"
                show-count
                placeholder="请输入应用名称"
              />
            </a-form-item>

            <a-form-item label="应用描述" extra="用于公开展示及搜索，不会修改初始提示词">
              <a-textarea
                v-model:value="form.description"
                :rows="3"
                :maxlength="1000"
                show-count
                placeholder="介绍应用用途和亮点"
              />
            </a-form-item>
            <a-form-item
              label="可见范围"
              extra="私有作品及其预览、部署链接仅本人或管理员可访问；改为私有会取消精选和置顶"
            >
              <a-radio-group v-model:value="form.visibility">
                <a-radio-button value="PRIVATE">私有</a-radio-button>
                <a-radio-button value="PUBLIC">公开</a-radio-button>
              </a-radio-group>
            </a-form-item>
            <a-form-item label="应用标签" extra="最多 5 个，每个最多 20 字；输入后按回车添加">
              <a-select
                v-model:value="form.tags"
                mode="tags"
                :max-tag-count="5"
                placeholder="例如：博客、工具、数据看板"
              />
            </a-form-item>

            <a-form-item label="应用封面" extra="支持 HTTP / HTTPS 图片链接，建议尺寸：400×300">
              <a-input
                v-model:value="form.cover"
                :maxlength="512"
                placeholder="请输入封面图片 URL"
              />
              <div
                v-if="form.cover.trim() && isValidCoverUrl(form.cover)"
                class="app-edit-page__cover-preview"
              >
                <a-image :src="form.cover.trim()" alt="应用封面预览" />
              </div>
            </a-form-item>
            <a-form-item
              label="优先级"
              :extra="
                isAdmin ? '优先级 99 表示精选应用，0 表示普通应用' : '只有管理员可以修改优先级'
              "
            >
              <a-input-number
                v-model:value="form.priority"
                class="app-edit-page__priority"
                :min="0"
                :precision="0"
                :disabled="!isAdmin"
              />
            </a-form-item>

            <a-form-item label="初始提示词" extra="初始提示词不可修改">
              <a-textarea :value="app.initPrompt" :rows="4" :maxlength="2000" show-count disabled />
            </a-form-item>

            <a-form-item label="生成类型" extra="生成类型不可修改">
              <a-input :value="app.codeGenType || '-'" disabled />
            </a-form-item>

            <a-form-item label="部署标识" extra="部署标识由系统生成，不可修改">
              <a-input :value="app.deployKey || ''" placeholder="尚未部署" disabled />
            </a-form-item>

            <a-space wrap class="app-edit-page__form-actions">
              <a-button
                type="primary"
                :loading="saving"
                :disabled="!canManage || deleting || saving"
                @click="handleSave"
              >
                <template #icon><SaveOutlined /></template>
                保存修改
              </a-button>
              <a-button :disabled="!canManage || saving || deleting" @click="handleReset"
                >重置</a-button
              >
              <a-button type="link" @click="enterChat">进入对话</a-button>
            </a-space>
          </a-form>
        </a-card>

        <a-card title="应用信息" class="app-edit-page__card app-edit-page__metadata">
          <a-descriptions bordered :column="{ xs: 1, sm: 2 }">
            <a-descriptions-item label="应用 ID"
              ><span class="app-edit-page__id">{{ app.id }}</span></a-descriptions-item
            >
            <a-descriptions-item label="创建者">
              <UserInfo :user="app.user" :size="24" />
            </a-descriptions-item>

            <a-descriptions-item label="创建时间">
              {{ formatDateTime(app.createTime) }}
            </a-descriptions-item>
            <a-descriptions-item label="更新时间">{{
              formatDateTime(app.updateTime)
            }}</a-descriptions-item>
            <a-descriptions-item label="当前版本"
              >v{{ app.currentVersion || 0 }}</a-descriptions-item
            >
            <a-descriptions-item label="部署状态">{{
              isOnline(app) ? `在线 · v${app.deployedVersion || 0}` : '已下线'
            }}</a-descriptions-item>
            <a-descriptions-item label="部署时间">{{
              formatDateTime(app.deployedTime)
            }}</a-descriptions-item>
            <a-descriptions-item label="访问链接">
              <a-space wrap>
                <a :href="getAppPreviewUrl(appId)" target="_blank" rel="noopener noreferrer"
                  >查看预览</a
                >
                <a
                  v-if="isOnline(app) && app.deployKey"
                  :href="getAppDeployUrl(app.deployKey)"
                  target="_blank"
                  rel="noopener noreferrer"
                  >查看作品</a
                >
              </a-space>
            </a-descriptions-item>
          </a-descriptions>
        </a-card>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.app-edit-page {
  max-width: 1040px;
  margin: 0 auto;
}

.app-edit-page__header {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.app-edit-page__header-left {
  display: flex;
  gap: 12px;
  align-items: center;
}

.app-edit-page__title {
  margin: 0;
  font-size: 22px;
  line-height: 32px;
  font-weight: 600;
  color: #262626;
}

.app-edit-page__header-actions {
  display: flex;
  align-items: center;
}

.app-edit-page__sections {
  padding: 24px;
  background: #f7f7f7;
  border-radius: 8px;
}

.app-edit-page__card {
  overflow: hidden;
  border: 1px solid #ededed;
  border-radius: 6px;
  box-shadow: none;
}

.app-edit-page__card :deep(.ant-card-head) {
  min-height: 56px;
  padding: 0 24px;
  font-size: 16px;
  background: #fafafa;
  border-bottom: 1px solid #ededed;
}

.app-edit-page__card :deep(.ant-card-body) {
  padding: 24px;
}

.app-edit-page__card :deep(.ant-form-item) {
  margin-bottom: 28px;
}

.app-edit-page__card :deep(.ant-form-item-label > label) {
  color: #262626;
}

.app-edit-page__card :deep(.ant-form-item-extra) {
  padding-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
}

.app-edit-page__cover-preview {
  min-height: 190px;
  padding: 12px;
  margin-top: 12px;
  background: #fafafa;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
}

.app-edit-page__cover-preview :deep(.ant-image) {
  display: block;
  width: 220px;
  max-width: 100%;
}

.app-edit-page__cover-preview :deep(.ant-image-img) {
  width: 100%;
  height: 165px;
  object-fit: cover;
}

.app-edit-page__priority {
  width: 200px;
  max-width: 100%;
}

.app-edit-page__form-actions {
  margin-bottom: 8px;
}

.app-edit-page__metadata {
  margin-top: 24px;
}

.app-edit-page__metadata :deep(.ant-descriptions-item-label) {
  width: 112px;
  white-space: nowrap;
  background: #fafafa;
}

.app-edit-page__metadata :deep(.ant-descriptions-item-content) {
  overflow-wrap: anywhere;
  font-variant-numeric: tabular-nums;
}

.app-edit-page__id {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
}

@media (max-width: 767px) {
  .app-edit-page__sections {
    padding: 12px;
  }

  .app-edit-page__card :deep(.ant-card-head) {
    padding: 0 16px;
  }

  .app-edit-page__card :deep(.ant-card-body) {
    padding: 20px 16px;
  }

  .app-edit-page__metadata :deep(.ant-descriptions-item-label),
  .app-edit-page__metadata :deep(.ant-descriptions-item-content) {
    padding: 12px;
  }

  .app-edit-page__metadata :deep(.ant-descriptions-item-label) {
    width: 88px;
  }

  .app-edit-page__title {
    font-size: 20px;
  }
}
</style>
