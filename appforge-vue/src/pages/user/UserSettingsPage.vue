<script setup lang="ts">
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { useAccountSettings } from '@/composables/useAccountSettings'

const router = useRouter()
const store = useLoginUserStore()
const { saveNickname, changePassword } = useAccountSettings()
const nickname = ref('')
const savingProfile = ref(false)
const savingPassword = ref(false)
const profileError = ref('')
const passwordError = ref('')
const passwords = reactive({ oldPassword: '', newPassword: '', checkPassword: '' })
watch(
  () => store.loginUser.userName,
  (value) => {
    nickname.value = value || ''
  },
  { immediate: true },
)
function clearPasswords() {
  passwords.oldPassword = ''
  passwords.newPassword = ''
  passwords.checkPassword = ''
}
onBeforeUnmount(clearPasswords)
async function submitProfile() {
  if (savingProfile.value) return
  savingProfile.value = true
  profileError.value = ''
  try {
    nickname.value = await saveNickname(nickname.value)
    message.success('昵称已更新')
  } catch (error) {
    profileError.value = error instanceof Error ? error.message : '昵称保存失败，请稍后重试'
  } finally {
    savingProfile.value = false
  }
}
async function submitPassword() {
  if (savingPassword.value) return
  savingPassword.value = true
  passwordError.value = ''
  try {
    await changePassword(passwords)
    clearPasswords()
    message.success('密码已修改，请使用新密码重新登录')
    await router.replace('/user/login')
  } catch (error) {
    passwordError.value = error instanceof Error ? error.message : '密码修改失败，请稍后重试'
  } finally {
    savingPassword.value = false
  }
}
</script>

<template>
  <main class="account-settings">
    <router-link to="/" class="back-link">← 返回创作空间</router-link>
    <header class="settings-heading">
      <span class="eyebrow">YOUR ACCOUNT</span>
      <h1>个人设置</h1>
      <p>用昵称介绍自己，让每一次创作都有你的名字。</p>
    </header>
    <div class="settings-grid">
      <section class="settings-card" aria-labelledby="profile-title">
        <div class="card-heading">
          <UserOutlined />
          <h2 id="profile-title">个人资料</h2>
        </div>
        <p class="card-description">昵称会显示在导航栏和你的公开作品中。</p>
        <a-form :model="{ userName: nickname }" layout="vertical" @finish="submitProfile">
          <a-form-item label="登录账号">
            <a-input :value="store.loginUser.userAccount" disabled aria-label="登录账号" />
          </a-form-item>
          <a-form-item
            name="userName"
            label="昵称"
            required
            extra="2～24 个字符，可使用中文、字母或表情。"
          >
            <a-input
              v-model:value="nickname"
              autocomplete="nickname"
              placeholder="给自己起一个昵称"
              aria-label="昵称"
            />
          </a-form-item>
          <a-alert
            v-if="profileError"
            :message="profileError"
            type="error"
            show-icon
            role="alert"
          />
          <a-button
            type="primary"
            html-type="submit"
            :loading="savingProfile"
            :disabled="savingPassword"
            >保存昵称</a-button
          >
        </a-form>
      </section>
      <section class="settings-card" aria-labelledby="password-title">
        <div class="card-heading">
          <LockOutlined />
          <h2 id="password-title">修改密码</h2>
        </div>
        <p class="card-description">修改成功后需要重新登录，其他设备的旧登录状态也将失效。</p>
        <a-form :model="passwords" layout="vertical" @finish="submitPassword">
          <a-form-item name="oldPassword" label="原密码" required>
            <a-input-password
              v-model:value="passwords.oldPassword"
              autocomplete="current-password"
              placeholder="请输入原密码"
              aria-label="原密码"
            />
          </a-form-item>
          <a-form-item name="newPassword" label="新密码" required extra="8～64 位，与原密码不同。">
            <a-input-password
              v-model:value="passwords.newPassword"
              autocomplete="new-password"
              placeholder="请输入新密码"
              aria-label="新密码"
              :maxlength="64"
            />
          </a-form-item>
          <a-form-item name="checkPassword" label="确认新密码" required>
            <a-input-password
              v-model:value="passwords.checkPassword"
              autocomplete="new-password"
              placeholder="请再次输入新密码"
              aria-label="确认新密码"
              :maxlength="64"
            />
          </a-form-item>
          <a-alert
            v-if="passwordError"
            :message="passwordError"
            type="error"
            show-icon
            role="alert"
          />
          <a-button
            type="primary"
            html-type="submit"
            :loading="savingPassword"
            :disabled="savingProfile"
            >修改密码并重新登录</a-button
          >
          <p class="password-hint">忘记原密码？请联系站点管理员重置。</p>
        </a-form>
      </section>
    </div>
  </main>
</template>

<style scoped>
.account-settings {
  max-width: 1000px;
  margin: 0 auto;
  padding: 36px 24px 72px;
}
.back-link {
  color: var(--forge-muted, #737386);
  font-size: 13px;
}
.settings-heading {
  margin: 32px 0;
}
.eyebrow {
  color: var(--forge-accent, #6654d9);
  font-size: 11px;
  letter-spacing: 2px;
}
h1 {
  margin: 10px 0;
  font-size: 32px;
  font-weight: 650;
}
.settings-heading p,
.card-description,
.password-hint {
  color: var(--forge-muted, #737386);
  line-height: 1.8;
}
.settings-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  align-items: start;
}
.settings-card {
  min-width: 0;
  padding: 28px;
  border: 1px solid #eae8f2;
  border-radius: 20px;
  background: white;
  box-shadow: 0 8px 32px #35266c05;
}
.card-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--forge-accent, #6654d9);
}
h2 {
  margin: 0;
  font-size: 20px;
  color: #252436;
}
.card-description {
  font-size: 13px;
  min-height: 46px;
  margin: 14px 0 22px;
}
.settings-card :deep(.ant-input),
.settings-card :deep(.ant-input-affix-wrapper) {
  border-radius: 10px;
}
.settings-card :deep(.ant-btn) {
  height: 40px;
  margin-top: 8px;
  border-radius: 10px;
}
.settings-card :deep(.ant-alert) {
  margin-bottom: 16px;
}
.password-hint {
  margin: 14px 0 0;
  font-size: 12px;
}
@media (max-width: 700px) {
  .account-settings {
    padding: 24px 16px 48px;
  }
  .settings-grid {
    grid-template-columns: 1fr;
    gap: 18px;
  }
  .settings-card {
    padding: 22px;
  }
  h1 {
    font-size: 28px;
  }
  .card-description {
    min-height: 0;
  }
}
</style>
