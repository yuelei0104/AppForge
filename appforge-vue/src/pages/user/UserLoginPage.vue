<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { userLogin } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const rules: Record<string, Rule[]> = {
  userAccount: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  userPassword: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const loading = ref(false)

onMounted(() => {
  // 已登录用户不需要再次登录
  if (loginUserStore.isLoggedIn) {
    void router.replace('/')
  }
})

function getRedirectPath() {
  const redirect = route.query.redirect
  if (typeof redirect !== 'string') {
    return '/'
  }
  // 只允许站内路径，避免开放重定向
  if (!redirect.startsWith('/') || redirect.startsWith('//')) {
    return '/'
  }
  return redirect
}

async function handleSubmit() {
  loading.value = true
  try {
    const res = await userLogin({ ...formState })
    if (res.data.code === 0 && res.data.data) {
      loginUserStore.setLoginUser(res.data.data)
      message.success('登录成功')
      await router.replace(getRedirectPath())
    } else {
      message.error(res.data.message || '登录失败')
    }
  } catch {
    message.error('登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="user-login-page">
    <a-card :bordered="false" class="user-login-page__card">
      <a-typography-title :level="4">登录</a-typography-title>
      <a-typography-paragraph type="secondary">
        登录后即可使用平台功能，账号信息会在全局共享。
      </a-typography-paragraph>

      <a-form
        :model="formState"
        :rules="rules"
        layout="vertical"
        @finish="handleSubmit"
      >
        <a-form-item label="账号" name="userAccount">
          <a-input
            v-model:value="formState.userAccount"
            placeholder="请输入账号"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="密码" name="userPassword">
          <a-input-password
            v-model:value="formState.userPassword"
            placeholder="请输入密码"
          />
        </a-form-item>
        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            block
            :loading="loading"
          >
            登录
          </a-button>
        </a-form-item>
      </a-form>

      <div class="user-login-page__footer">
        还没有账号？
        <router-link to="/user/register">立即注册</router-link>
      </div>
    </a-card>
  </div>
</template>

<style scoped>
.user-login-page {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}

.user-login-page__card {
  width: 100%;
  max-width: 420px;
  border-radius: 12px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08);
}

.user-login-page__footer {
  margin-top: 8px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.45);
  text-align: center;
}

@media (max-width: 767px) {
  .user-login-page {
    padding: 24px 0;
  }
}
</style>
