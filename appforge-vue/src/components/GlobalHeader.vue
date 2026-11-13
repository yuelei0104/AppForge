<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Grid, message } from 'ant-design-vue'
import { LogoutOutlined, MenuOutlined, UserOutlined } from '@ant-design/icons-vue'
import UserInfo from '@/components/UserInfo.vue'
import logoUrl from '@/assets/logo.png'
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
  if (key === 'logout') {
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
        <img :src="logoUrl" alt="网站 logo" class="global-header__logo" />
        <span class="global-header__title">{{ title }}</span>
        <span class="global-header__badge">AI 创作空间</span>
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

        <a-dropdown v-if="loginUserStore.isLoggedIn" placement="bottomRight">
          <div class="global-header__user" role="button" tabindex="0">
            <UserInfo :user="loginUserStore.loginUser" :size="32" class="global-header__identity" />
          </div>
          <template #overlay>
            <a-menu @click="handleUserMenuClick">
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
  height: 64px;
  padding: 0 24px;
  line-height: normal;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid #f0f0f0;
}

.global-header__inner {
  display: flex;
  align-items: center;
  gap: 24px;
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

.global-header__logo {
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: 8px;
}

.global-header__title {
  min-width: 0;
  overflow: hidden;
  font-size: 22px;
  font-weight: 600;
  line-height: 24px;
  color: rgba(0, 0, 0, 0.88);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.global-header__menu {
  flex: 1;
  min-width: 0;
  line-height: 62px;
  background: transparent;
  border-bottom: none;
}

.global-header__actions {
  display: flex;
  flex: none;
  gap: 12px;
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
  cursor: pointer;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.global-header__user:hover {
  background: rgba(0, 0, 0, 0.04);
}

.global-header__identity {
  max-width: 160px;
  overflow: hidden;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.global-header__badge {
  font-size: 11px;
  color: var(--forge-accent);
  background: var(--forge-accent-soft);
  padding: 4px 8px;
  border-radius: 6px;
  white-space: nowrap;
}
.global-header__shortcut {
  color: var(--forge-muted);
  white-space: nowrap;
  font-size: 14px;
}
.global-header__shortcut:hover {
  color: var(--forge-accent);
}

@media (max-width: 767px) {
  .global-header__badge {
    display: none;
  }
  .global-header {
    padding: 0 16px;
  }

  .global-header__inner {
    gap: 12px;
  }

  .global-header__logo {
    width: 32px;
    height: 32px;
  }

  .global-header__title {
    font-size: 15px;
  }

  .global-header__identity {
    max-width: 116px;
  }
}

@media (max-width: 375px) {
  .global-header__title {
    max-width: 130px;
  }
}
</style>
