<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { issuePasswordResetCode } from '@/api/account'
import { authErrorMessage } from '@/utils/auth'
const props = defineProps<{ open: boolean; user: API.UserVO | null }>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()
const verified = ref(false)
const loading = ref(false)
const code = ref('')
const errorMessage = ref('')
const issued = ref(false)
const remaining = ref(0)
let timer: ReturnType<typeof setInterval> | undefined
const codeExpired = computed(() => issued.value && remaining.value === 0)
function clearCode() {
  code.value = ''
  remaining.value = 0
  if (timer) clearInterval(timer)
  timer = undefined
}
watch(
  () => props.open,
  () => {
    clearCode()
    verified.value = false
    issued.value = false
    errorMessage.value = ''
  },
)
onBeforeUnmount(clearCode)
async function generate() {
  if (!verified.value || !props.user?.id || loading.value) return
  const id = props.user.id
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await issuePasswordResetCode(id, verified.value)
    if (res.data.code !== 0 || !res.data.data?.resetCode)
      throw new Error(res.data.message || '生成失败，请稍后重试')
    if (!props.open || props.user?.id !== id) return
    clearCode()
    code.value = res.data.data.resetCode
    issued.value = true
    const expiresAt = Date.now() + res.data.data.expiresInSeconds * 1000
    remaining.value = res.data.data.expiresInSeconds
    timer = setInterval(() => {
      remaining.value = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000))
      if (!remaining.value) {
        clearCode()
        verified.value = false
      }
    }, 1000)
  } catch (error) {
    errorMessage.value = authErrorMessage(error, '生成失败，请检查网络后重试')
  } finally {
    loading.value = false
  }
}
async function copyCode() {
  if (!code.value || !remaining.value) return
  try {
    await navigator.clipboard.writeText(code.value)
    message.success('重置码已复制，请通过可信渠道交给该用户')
  } catch {
    message.error('复制失败，请展开重置码后手动复制')
  }
}
</script>
<template>
  <a-modal
    :open="open"
    title="重置用户密码"
    :footer="null"
    :closable="!loading"
    :mask-closable="!loading"
    :keyboard="!loading"
    @cancel="emit('update:open', false)"
  >
    <p class="reset-target">
      <span
        >账号：<strong>{{ user?.userAccount }}</strong></span
      >
      <span>昵称：{{ user?.userName || '未设置' }}</span>
    </p>
    <a-alert
      type="info"
      show-icon
      message="先核验身份，再生成重置码"
      description="请通过可信渠道确认申请人是该账号本人。重置码可以用于修改密码，仅交给对应用户；重新生成会使旧码失效。"
    />
    <a-alert v-if="errorMessage" type="error" show-icon :message="errorMessage" role="alert" />
    <div v-if="code" class="reset-code-box">
      <label for="issued-reset-code">一次性重置码</label>
      <a-input-password
        id="issued-reset-code"
        :value="code"
        readonly
        autocomplete="off"
        aria-label="生成的重置码"
      />
      <p>约 {{ Math.ceil(remaining / 60) }} 分钟内有效，仅可使用一次。</p>
      <a-button @click="copyCode">复制重置码</a-button>
      <p>请用户打开登录页 → 忘记密码，填写对应账号、重置码和新密码。</p>
    </div>
    <template v-else>
      <a-alert v-if="codeExpired" type="warning" message="重置码已过期，请重新核验后生成。" />
      <a-checkbox v-model:checked="verified" :disabled="loading" class="verified-checkbox"
        >我已核验该用户的身份</a-checkbox
      >
      <a-button type="primary" block :disabled="!verified" :loading="loading" @click="generate"
        >生成一次性重置码</a-button
      >
    </template>
    <p class="reset-code-note">
      关闭此窗口后无法再次查看当前码。若用户未收到，请重新核验并生成新码。
    </p>
  </a-modal>
</template>
<style scoped>
.reset-target {
  display: grid;
  gap: 6px;
  margin-bottom: 18px;
  font-size: 14px;
}
.reset-target span {
  color: var(--forge-muted);
  font-size: 12px;
}
.verified-checkbox {
  display: flex;
  align-items: flex-start;
  margin: 22px 0 18px;
}
.reset-code-box {
  padding: 18px;
  border: 1px solid var(--forge-border);
  border-radius: 12px;
  margin-top: 20px;
}
.reset-code-box label {
  display: block;
  margin-bottom: 10px;
  font-weight: 500;
}
.reset-code-box p,
.reset-code-note {
  font-size: 12px;
  color: var(--forge-muted);
  line-height: 1.9;
  margin: 12px 0 0;
}
:deep(.ant-alert) {
  margin-bottom: 12px;
}
</style>
