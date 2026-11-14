<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import AuthShell from '@/components/AuthShell.vue'
import { resetPassword } from '@/api/account'
import { authErrorMessage, safeAuthRedirect, validatePasswordReset } from '@/utils/auth'
import { useLoginUserStore } from '@/stores/loginUser'
const route = useRoute()
const store = useLoginUserStore()
const form = reactive({ userAccount: '', resetCode: '', newPassword: '', checkPassword: '' })
const loading = ref(false)
const completed = ref(false)
const errorMessage = ref('')
const onwardQuery = computed(() => {
  const value = safeAuthRedirect(route.query.redirect)
  return value === '/' ? {} : { redirect: value }
})
function clearSecrets() {
  form.resetCode = ''
  form.newPassword = ''
  form.checkPassword = ''
}
onBeforeUnmount(clearSecrets)
async function handleReset() {
  if (loading.value) return
  errorMessage.value = ''
  loading.value = true
  try {
    validatePasswordReset(form)
    const res = await resetPassword({ ...form, resetCode: form.resetCode.trim() })
    if (res.data.code !== 0 || res.data.data !== true)
      throw new Error(res.data.message || '重置失败，请稍后重试')
    clearSecrets()
    store.setLoginUser(null)
    completed.value = true
  } catch (error) {
    errorMessage.value = authErrorMessage(error, '重置失败，请检查网络后重试')
  } finally {
    loading.value = false
  }
}
</script>
<template>
  <AuthShell
    :title="completed ? '密码已重置' : '忘记密码？'"
    :description="
      completed
        ? '请使用新密码重新登录，继续你的创作。'
        : '核验身份后，使用管理员提供的重置码设置新密码。'
    "
  >
    <div v-if="completed" class="reset-complete" role="status">
      <span class="complete-mark" aria-hidden="true">✓</span>
      <p>旧密码及旧登录状态已失效。</p>
      <router-link :to="{ path: '/user/login', query: onwardQuery }" class="reset-login"
        >返回登录</router-link
      >
    </div>
    <template v-else>
      <div class="recovery-help">
        <strong>还没有重置码？</strong>
        <p>
          请通过你已知的站点管理渠道联系管理员，提供登录账号并完成身份核验。管理员会提供一个 10
          分钟内有效、只能使用一次的重置码。
        </p>
        <details>
          <summary>管理员在哪里生成重置码？</summary>
          <p>
            管理员登录后，在「用户管理」找到对应账号，点击「重置密码」。若没有管理员能够登录，请联系部署维护者协助恢复。
          </p>
        </details>
      </div>
      <a-alert v-if="errorMessage" :message="errorMessage" type="error" show-icon role="alert" />
      <a-form :model="form" layout="vertical" @finish="handleReset">
        <a-form-item
          label="登录账号"
          name="userAccount"
          :rules="[{ required: true, message: '请输入登录账号' }]"
        >
          <a-input
            v-model:value="form.userAccount"
            placeholder="请输入要找回的账号"
            autocomplete="username"
            aria-label="登录账号"
            :disabled="loading"
          />
        </a-form-item>
        <a-form-item
          label="一次性重置码"
          name="resetCode"
          :rules="[{ required: true, message: '请输入重置码' }]"
        >
          <a-input-password
            v-model:value="form.resetCode"
            placeholder="粘贴管理员提供的 32 位重置码"
            autocomplete="off"
            aria-label="一次性重置码"
            :disabled="loading"
            :maxlength="64"
          />
        </a-form-item>
        <a-form-item
          label="新密码"
          name="newPassword"
          :rules="[{ required: true, message: '请输入新密码' }]"
        >
          <a-input-password
            v-model:value="form.newPassword"
            placeholder="设置新密码（8～64 位）"
            autocomplete="new-password"
            aria-label="新密码"
            :disabled="loading"
            :maxlength="64"
          />
        </a-form-item>
        <a-form-item
          label="确认新密码"
          name="checkPassword"
          :rules="[{ required: true, message: '请再次输入新密码' }]"
        >
          <a-input-password
            v-model:value="form.checkPassword"
            placeholder="再次输入新密码"
            autocomplete="new-password"
            aria-label="确认新密码"
            :disabled="loading"
            :maxlength="64"
          />
        </a-form-item>
        <a-button class="auth-submit" type="primary" html-type="submit" :loading="loading"
          >重置密码</a-button
        >
      </a-form>
      <p class="auth-switch">
        记起密码了？<router-link :to="{ path: '/user/login', query: onwardQuery }"
          >返回登录</router-link
        >
      </p>
    </template>
  </AuthShell>
</template>
<style scoped>
.recovery-help {
  background: #f7f5ff;
  padding: 16px;
  border: 1px solid #eae5ff;
  border-radius: 12px;
  margin-bottom: 24px;
  font-size: 12px;
  line-height: 1.9;
  color: var(--forge-muted);
}
.recovery-help strong {
  color: var(--forge-text);
  font-weight: 500;
}
.recovery-help p {
  margin: 8px 0 0;
}
.recovery-help summary {
  color: var(--forge-accent);
  cursor: pointer;
  margin-top: 10px;
}
.reset-complete {
  text-align: center;
}
.complete-mark {
  display: inline-grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: #eeecff;
  color: var(--forge-accent);
  font-size: 28px;
}
.reset-complete p {
  color: var(--forge-muted);
  line-height: 1.8;
  margin: 20px 0;
}
.reset-login {
  display: block;
  padding: 12px;
  background: var(--forge-accent);
  color: #fff;
  border-radius: 10px;
}
</style>
