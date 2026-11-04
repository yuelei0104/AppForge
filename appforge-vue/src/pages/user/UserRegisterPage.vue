<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { userRegister } from '@/api/userController'

const router = useRouter()

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const loading = ref(false)

/**
 * 验证确认密码
 * @param _rule 表单校验规则
 * @param value 确认密码
 * @param callback 校验回调
 */
function validateCheckPassword(
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void,
) {
  if (value && value !== formState.userPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

async function handleSubmit(values: API.UserRegisterRequest) {
  loading.value = true
  try {
    const res = await userRegister(values)
    if (res.data.code === 0) {
      message.success('注册成功')
      await router.push({ path: '/user/login', replace: true })
    } else {
      message.error('注册失败，' + res.data.message)
    }
  } catch {
    message.error('注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div id="userRegisterPage">
    <h2 class="title">岳蕾 AI 应用生成 - 用户注册</h2>
    <div class="desc">不写一行代码，生成完整应用</div>

    <a-form
      :model="formState"
      name="basic"
      autocomplete="off"
      @finish="handleSubmit"
    >
      <a-form-item
        name="userAccount"
        :rules="[{ required: true, message: '请输入账号' }]"
      >
        <a-input
          v-model:value="formState.userAccount"
          placeholder="请输入账号"
        />
      </a-form-item>

      <a-form-item
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码不能小于 8 位' },
        ]"
      >
        <a-input-password
          v-model:value="formState.userPassword"
          placeholder="请输入密码"
        />
      </a-form-item>

      <a-form-item
        name="checkPassword"
        :rules="[
          { required: true, message: '请确认密码' },
          { min: 8, message: '密码不能小于 8 位' },
          { validator: validateCheckPassword },
        ]"
      >
        <a-input-password
          v-model:value="formState.checkPassword"
          placeholder="请确认密码"
        />
      </a-form-item>

      <div class="tips">
        已有账号？
        <RouterLink to="/user/login">去登录</RouterLink>
      </div>

      <a-form-item>
        <a-button
          type="primary"
          html-type="submit"
          style="width: 100%"
          :loading="loading"
        >
          注册
        </a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<style scoped>
#userRegisterPage {
  max-width: 360px;
  margin: 0 auto;
}

.title {
  margin-bottom: 16px;
  text-align: center;
}

.desc {
  margin-bottom: 16px;
  color: #bbb;
  text-align: center;
}

.tips {
  margin-bottom: 16px;
  font-size: 13px;
  color: #bbb;
  text-align: right;
}
</style>
