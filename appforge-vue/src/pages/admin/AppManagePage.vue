<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'
import { AppstoreOutlined, DeleteOutlined, EditOutlined, StarOutlined } from '@ant-design/icons-vue'
import {
  deleteAppByAdmin,
  listAppVoByPageByAdmin,
  updateAppByAdmin,
} from '@/api/appController'
import { formatDateTime } from '@/utils/date'

const router = useRouter()

const data = ref<API.AppVO[]>([])
const total = ref(0)
const loading = ref(false)

const searchParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  appName: '',
  userId: undefined,
  priority: undefined,
  codeGenType: undefined,
})

const columns: TableColumnsType = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 190, ellipsis: true },
  { title: '应用名称', dataIndex: 'appName', key: 'appName', width: 180, ellipsis: true },
  { title: '封面', dataIndex: 'cover', key: 'cover', width: 100 },
  { title: '初始化 Prompt', dataIndex: 'initPrompt', key: 'initPrompt', ellipsis: true },
  { title: '生成类型', dataIndex: 'codeGenType', key: 'codeGenType', width: 120 },
  { title: '部署标识', dataIndex: 'deployKey', key: 'deployKey', width: 120 },
  { title: '优先级', dataIndex: 'priority', key: 'priority', width: 100 },
  { title: '创建用户', key: 'user', width: 150 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 250, fixed: 'right' },
]

const pagination = computed<TablePaginationConfig>(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
  showTotal: (count: number) => `共 ${count} 条`,
}))

async function fetchData() {
  loading.value = true
  try {
    const params: API.AppQueryRequest = { ...searchParams }
    if (!params.appName) {
      delete params.appName
    }
    if (params.id === '' || params.id == null) {
      delete params.id
    }
    if (params.userId === '' || params.userId == null) {
      delete params.userId
    }
    if (!params.cover) {
      delete params.cover
    }
    if (!params.initPrompt) {
      delete params.initPrompt
    }
    if (!params.deployKey) {
      delete params.deployKey
    }
    if (params.priority == null) {
      delete params.priority
    }
    if (!params.codeGenType) {
      delete params.codeGenType
    }
    const res = await listAppVoByPageByAdmin(params)
    if (res.data.code === 0 && res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = Number(res.data.data.totalRow ?? 0)
    } else {
      message.error(res.data.message || '获取应用列表失败')
    }
  } catch {
    message.error('获取应用列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function doSearch() {
  searchParams.pageNum = 1
  void fetchData()
}

function resetSearch() {
  searchParams.appName = ''
  searchParams.id = undefined
  searchParams.userId = undefined
  searchParams.cover = ''
  searchParams.initPrompt = ''
  searchParams.deployKey = ''
  searchParams.priority = undefined
  searchParams.codeGenType = undefined
  searchParams.pageNum = 1
  void fetchData()
}

function doTableChange(page: TablePaginationConfig) {
  searchParams.pageNum = page.current ?? 1
  searchParams.pageSize = page.pageSize ?? 10
  void fetchData()
}

function handleEdit(app: API.AppVO) {
  void router.push(`/app/edit/${String(app.id)}`)
}

async function handleFeature(app: API.AppVO) {
  if (app.id == null) {
    return
  }
  const targetPriority = app.priority === 99 ? 0 : 99
  try {
    const res = await updateAppByAdmin({ id: app.id, priority: targetPriority })
    if (res.data.code === 0) {
      message.success(targetPriority === 99 ? '已设为精选' : '已取消精选')
      await fetchData()
    } else {
      message.error(res.data.message || '操作失败')
    }
  } catch {
    message.error('操作失败，请稍后重试')
  }
}

async function handleDelete(app: API.AppVO) {
  if (app.id == null) {
    return
  }
  try {
    const res = await deleteAppByAdmin({ id: app.id })
    if (res.data.code === 0) {
      message.success('删除成功')
      await fetchData()
    } else {
      message.error(res.data.message || '删除失败')
    }
  } catch {
    message.error('删除失败，请稍后重试')
  }
}

onMounted(() => {
  void fetchData()
})
</script>

<template>
  <a-card :bordered="false" title="应用管理">
    <a-form
      class="app-manage-page__search"
      layout="inline"
      :model="searchParams"
      @finish="doSearch"
    >
      <a-form-item label="应用名称">
        <a-input v-model:value="searchParams.appName" placeholder="输入应用名称" allow-clear />
      </a-form-item>
      <a-form-item label="应用 ID">
        <a-input v-model:value="searchParams.id" placeholder="输入应用 ID" allow-clear />
      </a-form-item>
      <a-form-item label="用户 ID">
        <a-input v-model:value="searchParams.userId" placeholder="输入用户 ID" allow-clear />
      </a-form-item>
      <a-form-item label="封面">
        <a-input v-model:value="searchParams.cover" placeholder="输入封面地址" allow-clear />
      </a-form-item>
      <a-form-item label="Prompt">
        <a-input v-model:value="searchParams.initPrompt" placeholder="输入 Prompt" allow-clear />
      </a-form-item>
      <a-form-item label="部署标识">
        <a-input v-model:value="searchParams.deployKey" placeholder="输入部署标识" allow-clear />
      </a-form-item>
      <a-form-item label="优先级">
        <a-select
          v-model:value="searchParams.priority"
          placeholder="全部"
          allow-clear
          style="width: 120px"
        >
          <a-select-option :value="0">普通</a-select-option>
          <a-select-option :value="99">精选</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="生成类型">
        <a-select
          v-model:value="searchParams.codeGenType"
          placeholder="全部"
          allow-clear
          style="width: 150px"
        >
          <a-select-option value="html">HTML</a-select-option>
          <a-select-option value="multi_file">多文件</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit">搜索</a-button>
          <a-button @click="resetSearch">重置</a-button>
        </a-space>
      </a-form-item>
    </a-form>

    <a-table
      row-key="id"
      :columns="columns"
      :data-source="data"
      :loading="loading"
      :pagination="pagination"
      :scroll="{ x: 1500 }"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'cover'">
          <a-image
            v-if="record.cover"
            :src="record.cover"
            :width="72"
            :height="44"
            class="app-manage-page__cover"
          />
          <div v-else class="app-manage-page__cover-placeholder">
            <AppstoreOutlined />
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'initPrompt'">
          <span class="app-manage-page__prompt">{{ record.initPrompt || '-' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'codeGenType'">
          <a-tag :color="record.codeGenType === 'multi_file' ? 'purple' : 'blue'">
            {{ record.codeGenType === 'multi_file' ? '多文件' : 'HTML' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'priority'">
          <a-tag :color="record.priority === 99 ? 'gold' : 'default'">
            {{ record.priority === 99 ? '精选' : '普通' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'deployKey'">
          {{ record.deployKey || '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ formatDateTime(record.createTime) }}
        </template>
        <template v-else-if="column.key === 'user'">
          <span class="app-manage-page__user">
            <a-avatar :src="record.user?.userAvatar || undefined" :size="24">
              {{ (record.user?.userName || record.user?.userAccount || '用').slice(0, 1) }}
            </a-avatar>
            {{ record.user?.userName || record.user?.userAccount || '-' }}
          </span>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space :size="4">
            <a-button type="link" size="small" @click="handleEdit(record)">
              <template #icon>
                <EditOutlined />
              </template>
              编辑
            </a-button>
            <a-button type="link" size="small" @click="handleFeature(record)">
              <template #icon>
                <StarOutlined />
              </template>
              {{ record.priority === 99 ? '取消精选' : '精选' }}
            </a-button>
            <a-popconfirm
              title="确定删除该应用吗？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="handleDelete(record)"
            >
              <a-button type="link" size="small" danger>
                <template #icon>
                  <DeleteOutlined />
                </template>
                删除
              </a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<style scoped>
.app-manage-page__search {
  margin-bottom: 20px;
  row-gap: 12px;
}

.app-manage-page__cover {
  border-radius: 6px;
  object-fit: cover;
}

.app-manage-page__cover-placeholder {
  display: grid;
  width: 72px;
  height: 44px;
  color: #98a2b3;
  background: #f2f4f7;
  border-radius: 6px;
  place-items: center;
}

.app-manage-page__prompt {
  display: block;
  overflow: hidden;
  color: #667085;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-manage-page__user {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}
</style>
