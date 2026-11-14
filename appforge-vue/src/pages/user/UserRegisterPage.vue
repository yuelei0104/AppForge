<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import AuthShell from '@/components/AuthShell.vue'
import { validateNickname } from '@/utils/accountValidation'
import { authErrorMessage, safeAuthRedirect } from '@/utils/auth'
import { userRegister } from '@/api/userController'
const route = useRoute()
const router = useRouter()
const formState = reactive({ userAccount: '', userName: '', userPassword: '', checkPassword: '' })
const loading = ref(false)
const errorMessage = ref('')
const redirect = computed(() => safeAuthRedirect(route.query.redirect))
const onwardQuery = computed(() => (redirect.value === '/' ? {} : { redirect: redirect.value }))
function clearPasswords() {
  formState.userPassword = ''
  formState.checkPassword = ''
}
onBeforeUnmount(clearPasswords)
async function handleSubmit() {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    if (formState.userPassword !== formState.checkPassword) throw new Error('两次输入密码不一致')
    const res = await userRegister({ ...formState, userName: validateNickname(formState.userName) })
    if (res.data.code !== 0 || !res.data.data)
      throw new Error(res.data.message || '注册失败，请重试')
    clearPasswords()
    message.success('注册成功，请登录')
    await router.replace({ path: '/user/login', query: onwardQuery.value })
  } catch (error) {
    errorMessage.value = authErrorMessage(error, '暂时无法注册，请检查网络后重试')
  } finally {
    loading.value = false
  }
}
</script>
<template>
  <AuthShell title="创建 AppForge 账号" description="给自己起个昵称，让每一次创作都有你的名字。">
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
          placeholder="设置登录账号"
          autocomplete="username"
          aria-label="账号"
          :disabled="loading"
        />
      </a-form-item>
      <a-form-item
        label="昵称"
        name="userName"
        :rules="[{ required: true, message: '请输入昵称' }]"
      >
        <a-input
          v-model:value="formState.userName"
          placeholder="昵称（2～24 个字符）"
          autocomplete="nickname"
          aria-label="昵称"
          :disabled="loading"
        />
      </a-form-item>
      <a-form-item
        label="密码"
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, max: 64, message: '密码需为 8～64 位' },
        ]"
      >
        <a-input-password
          v-model:value="formState.userPassword"
          placeholder="设置密码（8～64 位）"
          autocomplete="new-password"
          aria-label="密码"
          :disabled="loading"
        />
      </a-form-item>
      <a-form-item
        label="确认密码"
        name="checkPassword"
        :rules="[{ required: true, message: '请再次输入密码' }]"
      >
        <a-input-password
          v-model:value="formState.checkPassword"
          placeholder="再次输入密码"
          autocomplete="new-password"
          aria-label="确认密码"
          :disabled="loading"
        />
      </a-form-item>
      <a-button class="auth-submit" type="primary" html-type="submit" :loading="loading"
        >创建账号</a-button
      >
    </a-form>
    <p class="auth-switch">
      已有账号？<router-link :to="{ path: '/user/login', query: onwardQuery }"
        >返回登录</router-link
      >
    </p>
  </AuthShell>
</template>
