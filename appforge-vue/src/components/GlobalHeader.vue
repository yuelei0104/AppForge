<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Grid, message } from 'ant-design-vue'
import { LogoutOutlined, MenuOutlined, UserOutlined, SettingOutlined } from '@ant-design/icons-vue'
import UserInfo from '@/components/UserInfo.vue'
import ACCESS_ENUM from '@/access/accessEnum'
import type { AccessType } from '@/access/accessEnum'
import checkAccess from '@/access/checkAccess'
import type { MenuItemConfig } from '@/config/menu'
import { useLoginUserStore } from '@/stores/loginUser'

const props = withDefaults(
  defineProps<{
    /** 导航菜单配置，key 同时作为路由路径 */
    items: MenuItemConfig[]
    /** 网站标题 */
    title?: string
  }>(),
  {
    title: 'AppForge',
  },
)

const route = useRoute()
const router = useRouter()
const screens = Grid.useBreakpoint()
const loginUserStore = useLoginUserStore()

const drawerOpen = ref(false)

/** md 断点以下（< 768px）使用抽屉菜单，首次渲染按桌面端处理，避免闪烁 */
const isMobile = computed(() => screens.value.md === false)

/** 根据当前用户权限过滤菜单项 */
const visibleItems = computed(() =>
  props.items.filter((item) => {
    const resolved = router.resolve(item.key)
    if (resolved.meta.hideInMenu) {
      return false
    }
    const needAccess = (resolved.meta.access as AccessType | undefined) ?? ACCESS_ENUM.NOT_LOGIN
    return checkAccess(loginUserStore.loginUser, needAccess)
  }),
)

const drawerItems = computed(() => [
  ...visibleItems.value.slice(0, 1),
  { key: '/#my-apps', label: '我的作品' },
  ...visibleItems.value.slice(1),
])

/** 根据当前路由计算需要高亮的菜单项 */
const selectedKeys = computed<string[]>(() => {
  const matched = props.items.find((item) => {
    if (item.key === '/') {
      return route.path === '/'
    }
    return route.path === item.key || route.path.startsWith(`${item.key}/`)
  })
  return matched ? [matched.key] : []
})

function handleMenuClick({ key }: { key: string | number }) {
  void router.push(String(key))
}

function handleDrawerMenuClick(info: { key: string | number }) {
  drawerOpen.value = false
  handleMenuClick(info)
}

function handleLogin() {
  void router.push('/user/login')
}

async function handleLogout() {
  try {
    const success = await loginUserStore.logout()
    if (!success) {
      message.error('退出登录失败，请重试')
      return
    }
    message.success('退出登录成功')
    await router.push('/user/login')
  } catch {
    message.error('退出登录失败，请重试')
  }
}

function handleUserMenuClick({ key }: { key: string | number }) {
  if (key === 'settings') {
    void router.push('/user/settings')
  } else if (key === 'logout') {
    void handleLogout()
  }
}

// 路由变化（含浏览器前进后退）时收起移动端抽屉
watch(
  () => route.fullPath,
  () => {
    drawerOpen.value = false
  },
)
</script>

<template>
  <a-layout-header class="global-header">
    <div class="global-header__inner">
      <router-link to="/" class="global-header__brand" aria-label="返回首页">
        <span class="global-header__mark" aria-hidden="true"><span>&lt;/&gt;</span></span>
        <span class="global-header__wordmark">
          <strong>{{ title }}</strong>
          <small>AI PRODUCT STUDIO</small>
        </span>
      </router-link>

      <a-menu
        v-if="!isMobile"
        class="global-header__menu"
        mode="horizontal"
        :items="visibleItems"
        :selected-keys="selectedKeys"
        @click="handleMenuClick"
      />

      <router-link v-if="!isMobile" to="/#my-apps" class="global-header__shortcut"
        >我的作品</router-link
      >
      <div class="global-header__actions">
        <a-button
          v-if="isMobile"
          class="global-header__menu-trigger"
          type="text"
          aria-label="打开导航菜单"
          @click="drawerOpen = true"
        >
          <MenuOutlined />
        </a-button>

        <a-dropdown v-if="loginUserStore.isLoggedIn" placement="bottomRight" :trigger="['click']">
          <button type="button" class="global-header__user" aria-label="账号菜单">
            <UserInfo :user="loginUserStore.loginUser" :size="32" class="global-header__identity" />
          </button>
          <template #overlay>
            <a-menu @click="handleUserMenuClick">
              <a-menu-item key="settings">
                <template #icon><SettingOutlined /></template>
                个人设置
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item key="logout">
                <template #icon>
                  <LogoutOutlined />
                </template>
                退出登录
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <a-button v-else type="primary" @click="handleLogin">
          <template #icon>
            <UserOutlined />
          </template>
          登录
        </a-button>
      </div>
    </div>

    <a-drawer
      v-model:open="drawerOpen"
      class="global-header__drawer"
      title="导航菜单"
      placement="right"
      :width="240"
    >
      <a-menu
        mode="inline"
        :items="drawerItems"
        :selected-keys="selectedKeys"
        @click="handleDrawerMenuClick"
      />
    </a-drawer>
  </a-layout-header>
</template>

<style scoped>
.global-header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: 72px;
  padding: 0 28px;
  line-height: normal;
  background: rgb(243 238 229 / 88%);
  backdrop-filter: blur(18px);
  border-bottom: 1px solid rgb(23 21 26 / 9%);
}

.global-header__inner {
  display: flex;
  align-items: center;
  gap: 28px;
  width: 100%;
  max-width: 1360px;
  min-width: 0;
  height: 100%;
  margin: 0 auto;
}

.global-header__brand {
  display: inline-flex;
  flex: 0 1 auto;
  gap: 10px;
  align-items: center;
  min-width: 0;
  color: inherit;
  text-decoration: none;
}

.global-header__mark {
  display: grid;
  flex: none;
  place-items: center;
  width: 36px;
  height: 36px;
  color: var(--forge-ink);
  background: var(--forge-orange);
  border-radius: 9px;
  box-shadow: inset 0 -2px 0 rgb(23 21 26 / 12%);
}

.global-header__mark span {
  font-family: var(--forge-font-mono);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: -1px;
  transform: translateY(-1px);
}

.global-header__wordmark {
  display: grid;
  gap: 1px;
  min-width: 0;
}

.global-header__wordmark strong {
  overflow: hidden;
  color: var(--forge-ink);
  font-family: var(--forge-font-display);
  font-size: 21px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0.02em;
  text-overflow: ellipsis;
  text-transform: uppercase;
  white-space: nowrap;
}

.global-header__wordmark small {
  color: var(--forge-muted-2);
  font-family: var(--forge-font-mono);
  font-size: 7px;
  letter-spacing: 0.22em;
  white-space: nowrap;
}

.global-header__menu {
  flex: 1;
  min-width: 0;
  margin-left: 20px;
  line-height: 70px;
  background: transparent;
  border-bottom: none;
}

.global-header__menu :deep(.ant-menu-item) {
  color: var(--forge-muted);
  font-size: 13px;
}

.global-header__menu :deep(.ant-menu-item-selected) {
  color: var(--forge-ink);
  font-weight: 600;
}

.global-header__menu :deep(.ant-menu-item-selected::after) {
  border-bottom-color: var(--forge-orange);
  border-bottom-width: 2px;
}

.global-header__actions {
  display: flex;
  flex: none;
  gap: 10px;
  align-items: center;
  margin-left: auto;
}

.global-header__menu-trigger {
  padding: 0 8px;
  font-size: 18px;
}

.global-header__user {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 4px 8px;
  font: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 9px;
  transition: background-color 0.2s;
}

.global-header__user:hover {
  background: rgb(23 21 26 / 6%);
}

.global-header__identity {
  max-width: 160px;
  overflow: hidden;
  font-size: 13px;
  color: var(--forge-ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.global-header__shortcut {
  color: var(--forge-muted);
  font-size: 13px;
  white-space: nowrap;
}

.global-header__shortcut:hover {
  color: var(--forge-orange-deep);
}

.global-header__actions :deep(.ant-btn-primary) {
  color: var(--forge-ink);
  background: var(--forge-orange);
  border-color: var(--forge-orange);
  box-shadow: inset 0 -2px 0 rgb(23 21 26 / 16%);
}

.global-header__actions :deep(.ant-btn-primary:hover) {
  color: var(--forge-ink) !important;
  background: #ff6d48 !important;
  border-color: #ff6d48 !important;
}

@media (max-width: 767px) {
  .global-header {
    height: 64px;
    padding: 0 16px;
  }

  .global-header__inner {
    gap: 12px;
  }

  .global-header__mark {
    width: 32px;
    height: 32px;
  }

  .global-header__wordmark strong {
    font-size: 17px;
  }

  .global-header__wordmark small {
    display: none;
  }

  .global-header__identity {
    max-width: 116px;
  }
}

@media (max-width: 375px) {
  .global-header__wordmark strong {
    max-width: 130px;
  }
}
</style>