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
  if (!app.value) {
    return
  }
  const appName = form.appName.trim()
  if (!appName) {
    message.warning('应用名称不能为空')
    return
  }
  saving.value = true
  try {
    const id = app.value.id ?? appId
    const res = isAdmin.value
      ? await updateAppByAdmin({
          id,
          appName,
          cover: form.cover.trim(),
          priority: form.priority,
        })
      : await updateApp({ id, appName })
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
  if (!app.value) {
    return
  }
  deleting.value = true
  try {
    const id = app.value.id ?? appId
    const res = isAdmin.value ? await deleteAppByAdmin({ id }) : await deleteApp({ id })
    if (res.data.code === 0) {
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
          <h1 class="app-edit-page__title">应用信息</h1>
          <p class="app-edit-page__desc">
            {{
              isAdmin ? '管理员可修改应用名称、封面和优先级' : '修改应用名称，其他信息由平台管理'
            }}
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
          <a-button danger :loading="deleting" :disabled="!app">
            <template #icon>
              <DeleteOutlined />
            </template>
            删除
          </a-button>
        </a-popconfirm>
        <a-button type="primary" :loading="saving" :disabled="!app" @click="handleSave">
          <template #icon>
            <SaveOutlined />
          </template>
          保存
        </a-button>
      </div>
    </div>

    <a-spin :spinning="loading">
      <a-card v-if="app" :bordered="false" class="app-edit-page__card">
        <a-form layout="vertical">
          <a-form-item label="应用名称" required>
            <a-input v-model:value="form.appName" :maxlength="256" placeholder="请输入应用名称" />
          </a-form-item>

          <template v-if="isAdmin">
            <a-form-item label="应用封面">
              <a-input
                v-model:value="form.cover"
                :maxlength="512"
                placeholder="请输入封面图片 URL"
              />
            </a-form-item>
            <a-form-item label="优先级" extra="优先级 99 表示精选应用，0 表示普通应用">
              <a-input-number v-model:value="form.priority" :min="0" :precision="0" />
            </a-form-item>
          </template>

          <a-form-item v-else-if="app.cover" label="应用封面">
            <a-image :src="app.cover" :width="180" />
          </a-form-item>

          <a-form-item label="初始化 Prompt">
            <a-textarea :value="app.initPrompt" :rows="4" disabled />
          </a-form-item>

          <a-form-item label="生成类型">
            <a-tag color="blue">{{ app.codeGenType || '-' }}</a-tag>
          </a-form-item>

          <a-form-item label="创建者">
            <span class="app-edit-page__user">
              <a-avatar :src="app.user?.userAvatar || undefined" :size="24">
                {{ (app.user?.userName || app.user?.userAccount || '用').slice(0, 1) }}
              </a-avatar>
              {{ app.user?.userName || app.user?.userAccount || '-' }}
            </span>
          </a-form-item>

          <a-form-item label="创建时间">
            {{ formatDateTime(app.createTime) }}
          </a-form-item>
        </a-form>

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
