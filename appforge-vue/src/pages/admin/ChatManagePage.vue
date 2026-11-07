<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'
import { listAllChatHistoryByPageForAdmin, removeChatHistory } from '@/api/chatHistoryController'
import { formatDateTime } from '@/utils/date'
import MarkdownContent from '@/components/MarkdownContent.vue'

const router = useRouter()
const records = ref<API.ChatHistory[]>([])
const total = ref(0)
const loading = ref(false)
const errorText = ref('')
const selected = ref<API.ChatHistory | null>(null)
const deleting = ref<string>()
let requestSequence = 0
const filters = reactive<API.ChatHistoryQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'descend',
})
const columns: TableColumnsType = [
  { title: '消息 ID', dataIndex: 'id', key: 'id', width: 190, ellipsis: true },
  { title: '消息内容', dataIndex: 'message', key: 'message', width: 300, ellipsis: true },
  { title: '消息类型', dataIndex: 'messageType', key: 'messageType', width: 100 },
  { title: '应用 ID', dataIndex: 'appId', key: 'appId', width: 190 },
  { title: '用户 ID', dataIndex: 'userId', key: 'userId', width: 190 },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 180,
    sorter: true,
    defaultSortOrder: 'descend',
  },
  { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 180 },
  { title: '操作', key: 'action', width: 160, fixed: 'right' },
]
const pagination = computed<TablePaginationConfig>(() => ({
  current: filters.pageNum,
  pageSize: filters.pageSize,
  total: total.value,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50'],
  showTotal: (count) => `共 ${count} 条`,
}))

async function fetchData() {
  const sequence = ++requestSequence
  loading.value = true
  errorText.value = ''
  try {
    const body = Object.fromEntries(
      Object.entries(filters).filter(([, value]) => value !== '' && value != null),
    )
    const res = await listAllChatHistoryByPageForAdmin(body)
    if (sequence !== requestSequence) return
    if (res.data.code !== 0 || !res.data.data)
      throw new Error(res.data.message || '获取对话列表失败')
    records.value = res.data.data.records ?? []
    total.value = Number(res.data.data.totalRow ?? 0)
    if (!records.value.length && total.value > 0 && (filters.pageNum ?? 1) > 1) {
      filters.pageNum = Math.ceil(total.value / (filters.pageSize ?? 10))
      await fetchData()
    }
  } catch (error) {
    if (sequence === requestSequence)
      errorText.value = error instanceof Error ? error.message : '获取对话列表失败，请重试'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}
function search() {
  filters.pageNum = 1
  void fetchData()
}
function reset() {
  filters.id = filters.appId = filters.userId = filters.message = filters.messageType = undefined
  search()
}
function tableChange(
  page: TablePaginationConfig,
  _filters: unknown,
  sorter: { order?: string } | { order?: string }[],
) {
  const order = Array.isArray(sorter) ? sorter[0]?.order : sorter.order
  filters.sortOrder = order || 'descend'
  filters.pageNum = page.current ?? 1
  filters.pageSize = page.pageSize ?? 10
  void fetchData()
}
async function remove(record: API.ChatHistory) {
  if (record.id == null || deleting.value) return
  deleting.value = String(record.id)
  try {
    const res = await removeChatHistory(String(record.id))
    if (res.data !== true) throw new Error('删除失败，请刷新后重试')
    message.success('删除成功')
    await fetchData()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败，请重试')
  } finally {
    deleting.value = undefined
  }
}
onMounted(fetchData)
onBeforeUnmount(() => {
  requestSequence += 1
})
</script>

<template>
  <a-card :bordered="false" title="对话管理">
    <a-form class="chat-manage__search" layout="inline" :model="filters" @finish="search">
      <a-form-item label="消息 ID"
        ><a-input v-model:value="filters.id" placeholder="输入消息 ID" allow-clear
      /></a-form-item>
      <a-form-item label="应用 ID"
        ><a-input v-model:value="filters.appId" placeholder="输入应用 ID" allow-clear
      /></a-form-item>
      <a-form-item label="用户 ID"
        ><a-input v-model:value="filters.userId" placeholder="输入用户 ID" allow-clear
      /></a-form-item>
      <a-form-item label="消息内容"
        ><a-input v-model:value="filters.message" placeholder="搜索消息内容" allow-clear
      /></a-form-item>
      <a-form-item label="消息类型">
        <a-select
          v-model:value="filters.messageType"
          placeholder="全部类型"
          allow-clear
          style="width: 130px"
          :options="[
            { label: '用户', value: 'user' },
            { label: 'AI', value: 'ai' },
          ]"
        />
      </a-form-item>
      <a-form-item
        ><a-space
          ><a-button type="primary" html-type="submit">查询</a-button
          ><a-button @click="reset">重置</a-button></a-space
        ></a-form-item
      >
    </a-form>
    <a-alert v-if="errorText" :message="errorText" type="error" show-icon class="chat-manage__error"
      ><template #action
        ><a-button size="small" @click="fetchData">重试</a-button></template
      ></a-alert
    >
    <a-table
      :columns="columns"
      :data-source="records"
      :loading="loading"
      :pagination="pagination"
      :row-key="(record: API.ChatHistory) => String(record.id)"
      :scroll="{ x: 1490 }"
      @change="tableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'messageType'"
          ><a-tag :color="record.messageType === 'user' ? 'blue' : 'purple'">{{
            record.messageType === 'user' ? '用户' : 'AI'
          }}</a-tag></template
        >
        <template v-else-if="column.key === 'appId'"
          ><a-button
            type="link"
            class="chat-manage__app"
            @click="router.push(`/app/chat/${String(record.appId)}`)"
            >{{ record.appId }}</a-button
          ></template
        >
        <template v-else-if="column.key === 'createTime' || column.key === 'updateTime'">{{
          formatDateTime(record[column.key])
        }}</template>
        <template v-else-if="column.key === 'action'">
          <a-space
            ><a-button type="link" size="small" @click="selected = record">查看</a-button>
            <a-popconfirm
              title="确定删除这条对话记录吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="remove(record)"
            >
              <a-button
                type="link"
                size="small"
                danger
                :disabled="!!deleting"
                :loading="deleting === String(record.id)"
                >删除</a-button
              >
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
    <a-modal
      :open="!!selected"
      title="对话详情"
      :footer="null"
      :width="760"
      @cancel="selected = null"
    >
      <a-descriptions v-if="selected" :column="1" size="small">
        <a-descriptions-item label="消息 ID">{{ selected.id }}</a-descriptions-item>
        <a-descriptions-item label="应用 ID">{{ selected.appId }}</a-descriptions-item>
        <a-descriptions-item label="用户 ID">{{ selected.userId }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{
          formatDateTime(selected.createTime)
        }}</a-descriptions-item>
      </a-descriptions>
      <div class="chat-manage__detail">
        <MarkdownContent v-if="selected?.messageType === 'ai'" :content="selected.message ?? ''" />
        <pre v-else>{{ selected?.message }}</pre>
      </div>
    </a-modal>
  </a-card>
</template>

<style scoped>
.chat-manage__search {
  gap: 12px 0;
  margin-bottom: 20px;
}
.chat-manage__error {
  margin-bottom: 16px;
}
.chat-manage__app {
  padding: 0;
}
.chat-manage__detail {
  max-height: 60vh;
  overflow: auto;
  margin-top: 16px;
  padding: 16px;
  background: #f7f8fb;
  border-radius: 8px;
}
.chat-manage__detail pre {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
