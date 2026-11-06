<script setup lang="ts">
import { message } from 'ant-design-vue'
import { CopyOutlined } from '@ant-design/icons-vue'

const props = defineProps<{ open: boolean; url: string }>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()

async function copyDeployUrl() {
  try {
    await navigator.clipboard.writeText(props.url)
    message.success('部署地址已复制')
  } catch {
    message.warning('复制失败，请手动复制')
  }
}
</script>

<template>
  <a-modal
    :open="open"
    @update:open="emit('update:open', $event)"
    title="部署成功"
    :footer="null"
    :width="640"
  >
    <a-result
      status="success"
      title="网站部署成功！"
      sub-title="你的网站已经成功部署，可以通过以下链接访问："
      class="deploy-modal__result"
    >
      <template #extra>
        <a-input :value="url" readonly aria-label="部署地址">
          <template #suffix>
            <a-tooltip title="复制部署地址">
              <a-button type="text" size="small" aria-label="复制部署地址" @click="copyDeployUrl">
                <template #icon><CopyOutlined /></template>
              </a-button>
            </a-tooltip>
          </template>
        </a-input>
        <a-space class="deploy-modal__actions" wrap>
          <a-button type="primary" :href="url" target="_blank" rel="noopener noreferrer">
            访问网站
          </a-button>
          <a-button @click="emit('update:open', false)">关闭</a-button>
        </a-space>
      </template>
    </a-result>
  </a-modal>
</template>

<style scoped>
.deploy-modal__result {
  padding: 28px 16px 16px;
}

.deploy-modal__result :deep(.ant-result-icon > .anticon) {
  font-size: 52px;
}

.deploy-modal__result :deep(.ant-result-title) {
  font-size: 20px;
  font-weight: 600;
}

.deploy-modal__actions {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
