<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import AuthShell from '@/components/AuthShell.vue'
import { userLogin } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'
import { authErrorMessage, safeAuthRedirect } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const store = useLoginUserStore()
const formState = reactive({ userAccount: '', userPassword: '' })
const loading = ref(false)
const errorMessage = ref('')
const redirect = computed(() => safeAuthRedirect(route.query.redirect))
const onwardQuery = computed(() => (redirect.value === '/' ? {} : { redirect: redirect.value }))
onBeforeUnmount(() => {
  formState.userPassword = ''
})

async function handleSubmit() {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await userLogin({ ...formState })
    if (res.data.code !== 0 || !res.data.data?.id)
      throw new Error(res.data.message || '登录失败，请重试')
    // 登录响应已由服务端校验并脱敏，立即同步身份，保留原有站内跳转。
    store.setLoginUser(res.data.data)
    formState.userPassword = ''
    message.success('登录成功')
    await router.replace(redirect.value)
  } catch (error) {
    errorMessage.value = authErrorMessage(error, '暂时无法登录，请检查网络后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell title="登录 AppForge" description="欢迎回来，继续实现你的下一个想法。">
    <a-alert v-if="errorMessage" :message="errorMessage" type="error" show-icon role="alert" />
    <a-form :model="formState" layout="vertical" @finish="handleSubmit">
      <a-form-item
        label="账号"
        name="userAccount"
        :rules="[
          { required: true, message: '请输入账号' },
          { min: 4, message: '账号不能少于 4 位' },
        ]"
      >
        <a-input
          v-model:value="formState.userAccount"
          placeholder="请输入账号"
          autocomplete="username"
          aria-label="账号"
          :disabled="loading"
        />
      </a-form-item>
      <a-form-item
        label="密码"
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码不能少于 8 位' },
        ]"
      >
        <a-input-password
          v-model:value="formState.userPassword"
          placeholder="请输入密码"
          autocomplete="current-password"
          aria-label="密码"
          :disabled="loading"
        />
      </a-form-item>
      <div class="auth-links">
        <router-link :to="{ path: '/user/register', query: onwardQuery }">创建新账号</router-link
        ><router-link :to="{ path: '/user/forgot-password', query: onwardQuery }"
          >忘记密码？</router-link
        >
      </div>
      <a-button class="auth-submit" type="primary" html-type="submit" :loading="loading"
        >登录</a-button
      >
    </a-form>
    <p class="auth-switch">
      还没想好从哪里开始？<router-link to="/about">查看使用指南</router-link>
    </p>
  </AuthShell>
</template>
