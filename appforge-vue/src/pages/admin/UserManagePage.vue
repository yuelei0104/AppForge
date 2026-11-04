<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'
import { deleteUser, listUserVoByPage } from '@/api/userController'

const data = ref<API.UserVO[]>([])
const total = ref(0)

const searchParams = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  userAccount: '',
  userName: '',
})

const columns: TableColumnsType = [
  {
    title: 'ID',
    dataIndex: 'id',
    key: 'id',
    width: 190,
    ellipsis: true,
  },
  {
    title: '账号',
    dataIndex: 'userAccount',
    key: 'userAccount',
    width: 140,
  },
  {
    title: '用户名',
    dataIndex: 'userName',
    key: 'userName',
    width: 140,
  },
  {
    title: '头像',
    dataIndex: 'userAvatar',
    key: 'userAvatar',
    width: 90,
  },
  {
    title: '简介',
    dataIndex: 'userProfile',
    key: 'userProfile',
    ellipsis: true,
  },
  {
    title: '用户角色',
    dataIndex: 'userRole',
    key: 'userRole',
    width: 110,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 180,
  },
  {
    title: '操作',
    key: 'action',
    width: 100,
    fixed: 'right',
  },
]

const pagination = computed<TablePaginationConfig>(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  showTotal: (count: number) => `共 ${count} 条`,
}))

function formatDateTime(value?: string) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

async function fetchData() {
  try {
    const params: API.UserQueryRequest = { ...searchParams }
    if (!params.userAccount) {
      delete params.userAccount
    }
    if (!params.userName) {
      delete params.userName
    }
    const res = await listUserVoByPage(params)
    if (res.data.code === 0 && res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = Number(res.data.data.totalRow ?? 0)
    } else {
      message.error('获取数据失败，' + (res.data.message ?? '未知错误'))
    }
  } catch {
    message.error('获取数据失败，请稍后重试')
  }
}

function doSearch() {
  searchParams.pageNum = 1
  void fetchData()
}

function doTableChange(page: TablePaginationConfig) {
  searchParams.pageNum = page.current ?? 1
  searchParams.pageSize = page.pageSize ?? 10
  void fetchData()
}

async function doDelete(id?: number) {
  if (!id) {
    return
  }
  try {
    const res = await deleteUser({ id })
    if (res.data.code === 0) {
      message.success('删除成功')
      await fetchData()
    } else {
      message.error('删除失败，' + (res.data.message ?? '未知错误'))
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
  <a-card id="userManagePage" :bordered="false" title="用户管理">
    <a-form
      class="user-manage-page__search"
      layout="inline"
      :model="searchParams"
      @finish="doSearch"
    >
      <a-form-item label="账号" name="userAccount">
        <a-input
          v-model:value="searchParams.userAccount"
          placeholder="输入账号"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="用户名" name="userName">
        <a-input
          v-model:value="searchParams.userName"
          placeholder="输入用户名"
          allow-clear
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>

    <a-table
      class="user-manage-page__table"
      row-key="id"
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      :scroll="{ x: 1100 }"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'userAvatar'">
          <a-avatar :src="record.userAvatar || undefined" :size="40">
            {{ (record.userName || record.userAccount || '用').slice(0, 1) }}
          </a-avatar>
        </template>
        <template v-else-if="column.dataIndex === 'userRole'">
          <a-tag :color="record.userRole === 'admin' ? 'green' : 'blue'">
            {{ record.userRole === 'admin' ? '管理员' : '普通用户' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'userProfile'">
          {{ record.userProfile || '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ formatDateTime(record.createTime) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-popconfirm
            title="确定删除该用户吗？"
            ok-text="删除"
            cancel-text="取消"
            @confirm="doDelete(record.id)"
          >
            <a-button type="link" danger>删除</a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<style scoped>
.user-manage-page__search {
  margin-bottom: 20px;
  row-gap: 12px;
}

.user-manage-page__table {
  margin-top: 8px;
}
</style>
