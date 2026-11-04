<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { Rule, RuleObject } from 'ant-design-vue/es/form'
import { userRegister } from '@/api/userController'

const router = useRouter()

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

function validateCheckPassword(_rule: RuleObject, value: string) {
  if (!value) {
    return Promise.reject('请再次输入密码')
  }
  if (value !== formState.userPassword) {
    return Promise.reject('两次输入的密码不一致')
  }
  return Promise.resolve()
}

const rules: Record<string, Rule[]> = {
  userAccount: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 4, message: '账号至少 4 位', trigger: 'blur' },
  ],
  userPassword: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' },
  ],
  checkPassword: [
    { required: true, validator: validateCheckPassword, trigger: 'blur' },
  ],
}

const loading = ref(false)

async function handleSubmit() {
  loading.value = true
  try {
    const res = await userRegister({ ...formState })
    if (res.data.code === 0) {
      message.success('注册成功，请登录')
      await router.push('/user/login')
    } else {
      message.error(res.data.message || '注册失败')
    }
  } catch {
    message.error('注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="user-register-page">
    <a-card :bordered="false" class="user-register-page__card">
      <a-typography-title :level="4">注册</a-typography-title>
      <a-typography-paragraph type="secondary">
        注册一个账号，开始使用 AI 零代码应用生成平台。
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
            placeholder="请输入账号（至少 4 位）"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="密码" name="userPassword">
          <a-input-password
            v-model:value="formState.userPassword"
            placeholder="请输入密码（至少 8 位）"
          />
        </a-form-item>
        <a-form-item label="确认密码" name="checkPassword">
          <a-input-password
            v-model:value="formState.checkPassword"
            placeholder="请再次输入密码"
          />
        </a-form-item>
        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            block
            :loading="loading"
          >
            注册
          </a-button>
        </a-form-item>
      </a-form>

      <div class="user-register-page__footer">
        已有账号？
        <router-link to="/user/login">返回登录</router-link>
      </div>
    </a-card>
  </div>
</template>

<style scoped>
.user-register-page {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}

.user-register-page__card {
  width: 100%;
  max-width: 420px;
  border-radius: 12px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08);
}

.user-register-page__footer {
  margin-top: 8px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.45);
  text-align: center;
}

@media (max-width: 767px) {
  .user-register-page {
    padding: 24px 0;
  }
}
</style>
