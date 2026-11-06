<script setup lang="ts">
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
import { formatDateTime } from '@/utils/date'
import logoUrl from '@/assets/logo.png'
import { getAppDeployUrl, getAppPreviewUrl, isValidCoverUrl } from '@/utils/appUrls'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const appId = String(route.params.appId ?? '')
const app = ref<API.AppVO | null>(null)
const loading = ref(true)
const saving = ref(false)
const deleting = ref(false)

const form = reactive({
  appName: '',
  cover: '',
  priority: 0,
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

function initForm(data: API.AppVO) {
  form.appName = data.appName || ''
  form.cover = data.cover || ''
  form.priority = data.priority ?? 0
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
  saving.value = true
  try {
    const id = app.value.id ?? appId
    const res = isAdmin.value
      ? await updateAppByAdmin({
          id,
          appName,
          cover,
          priority: form.priority,
        })
      : await updateApp({ id, appName, cover })
    if (res.data.code === 0) {
      message.success('保存成功')
      await router.push(
        isAdmin.value ? '/admin/appManage' : { path: `/app/chat/${appId}`, query: { view: '1' } },
      )
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
  void router.push({ path: `/app/chat/${appId}`, query: { view: '1' } })
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
        </a-button>
        <div>
          <h1 class="app-edit-page__title">编辑应用信息</h1>
          <p class="app-edit-page__desc">
            {{ isAdmin ? '管理员可修改应用名称、封面和优先级' : '修改自己的应用名称和封面' }}
          </p>
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
      <a-card v-if="app" title="基本信息" :bordered="false" class="app-edit-page__card">
        <a-form layout="vertical">
          <a-form-item label="应用名称" required>
            <a-input
              v-model:value="form.appName"
              :maxlength="256"
              show-count
              placeholder="请输入应用名称"
            />
          </a-form-item>

          <a-form-item label="应用封面" extra="支持 HTTP / HTTPS 图片链接，建议尺寸：400×300">
            <a-input v-model:value="form.cover" :maxlength="512" placeholder="请输入封面图片 URL" />
            <div
              v-if="form.cover.trim() && isValidCoverUrl(form.cover)"
              class="app-edit-page__cover-preview"
            >
              <a-image :src="form.cover.trim()" :width="180" alt="应用封面预览" />
            </div>
          </a-form-item>
          <a-form-item
            label="优先级"
            :extra="isAdmin ? '优先级 99 表示精选应用，0 表示普通应用' : '只有管理员可以修改优先级'"
          >
            <a-input-number
              v-model:value="form.priority"
              :min="0"
              :precision="0"
              :disabled="!isAdmin"
            />
          </a-form-item>

          <a-form-item label="初始提示词" extra="初始提示词不可修改">
            <a-textarea :value="app.initPrompt" :rows="4" disabled />
          </a-form-item>

          <a-form-item label="生成类型" extra="生成类型不可修改">
            <a-input :value="app.codeGenType || '-'" disabled />
          </a-form-item>

          <a-form-item label="部署标识" extra="部署标识由系统生成，不可修改">
            <a-input :value="app.deployKey || ''" placeholder="尚未部署" disabled />
          </a-form-item>

          <a-space wrap>
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

      <a-card
        v-if="app"
        title="应用信息"
        :bordered="false"
        class="app-edit-page__card app-edit-page__metadata"
      >
        <a-descriptions bordered :column="{ xs: 1, sm: 2 }">
          <a-descriptions-item label="应用 ID">{{ app.id }}</a-descriptions-item>
          <a-descriptions-item label="创建者">
            <span class="app-edit-page__user">
              <a-avatar :src="app.user?.userAvatar || undefined" :size="24">
                {{ (app.user?.userName || app.user?.userAccount || '用').slice(0, 1) }}
              </a-avatar>
              {{ app.user?.userName || app.user?.userAccount || '-' }}
            </span>
          </a-descriptions-item>

          <a-descriptions-item label="创建时间">
            {{ formatDateTime(app.createTime) }}
          </a-descriptions-item>
          <a-descriptions-item label="更新时间">{{
            formatDateTime(app.updateTime)
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
                v-if="app.deployKey"
                :href="getAppDeployUrl(app.deployKey)"
                target="_blank"
                rel="noopener noreferrer"
                >查看作品</a
              >
            </a-space>
          </a-descriptions-item>
        </a-descriptions>

        <div class="app-edit-page__brand">
          <img :src="logoUrl" alt="AppForge" />
          <span>AppForge · 用一句话生成应用</span>
        </div>
      </a-card>
    </a-spin>
  </div>
</template>

<style scoped>
.app-edit-page {
  max-width: 820px;
  margin: 0 auto;
}

.app-edit-page__header {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.app-edit-page__header-left {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.app-edit-page__title {
  margin: 2px 0 0;
  font-size: 24px;
  font-weight: 700;
  color: #101828;
}

.app-edit-page__desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: #98a2b3;
}

.app-edit-page__header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.app-edit-page__card {
  border-radius: 16px;
  box-shadow: 0 6px 24px rgba(15, 23, 42, 0.05);
}

.app-edit-page__cover-preview {
  padding: 16px;
  margin-top: 12px;
  background: #f8fafc;
  border: 1px solid #edf0f5;
  border-radius: 8px;
}

.app-edit-page__metadata {
  margin-top: 20px;
}

.app-edit-page__user {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.app-edit-page__brand {
  display: flex;
  gap: 10px;
  align-items: center;
  padding-top: 18px;
  margin-top: 8px;
  font-size: 12px;
  color: #aab4c5;
  border-top: 1px dashed #edf0f5;
}

.app-edit-page__brand img {
  width: 24px;
  height: 24px;
  border-radius: 6px;
}

@media (max-width: 767px) {
  .app-edit-page__header {
    align-items: stretch;
  }

  .app-edit-page__header-actions {
    justify-content: flex-end;
  }
}
</style>
