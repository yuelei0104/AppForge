import { createRouter, createWebHistory } from 'vue-router'
import ACCESS_ENUM from '@/access/accessEnum'
import AboutView from '@/pages/AboutView.vue'
import AppChatPage from '@/pages/app/AppChatPage.vue'
import AppEditPage from '@/pages/app/AppEditPage.vue'
import HomeView from '@/pages/HomeView.vue'
import NoAuthPage from '@/pages/NoAuthPage.vue'
import AppManagePage from '@/pages/admin/AppManagePage.vue'
import ChatManagePage from '@/pages/admin/ChatManagePage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, top: 88 }
    return { top: 0 }
  },
  routes: [
    {
      path: '/admin/chatManage',
      name: 'chatManage',
      component: ChatManagePage,
      meta: { access: ACCESS_ENUM.ADMIN },
    },
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/about',
      name: 'about',
      component: AboutView,
    },
    {
      path: '/user/login',
      name: 'userLogin',
      component: UserLoginPage,
    },
    {
      path: '/user/settings',
      name: 'userSettings',
      component: () => import('@/pages/user/UserSettingsPage.vue'),
      meta: { access: ACCESS_ENUM.USER },
    },
    {
      path: '/user/register',
      name: 'userRegister',
      component: UserRegisterPage,
    },
    {
      path: '/app/chat/:appId',
      name: 'appChat',
      component: AppChatPage,
      meta: {
        access: ACCESS_ENUM.USER,
      },
    },
    {
      path: '/app/edit/:appId',
      name: 'appEdit',
      component: AppEditPage,
      meta: {
        access: ACCESS_ENUM.USER,
      },
    },
    {
      path: '/admin/appManage',
      name: 'appManage',
      component: AppManagePage,
      meta: {
        access: ACCESS_ENUM.ADMIN,
      },
    },
    {
      path: '/admin/userManage',
      name: 'userManage',
      component: UserManagePage,
      meta: {
        access: ACCESS_ENUM.ADMIN,
      },
    },
    {
      path: '/noAuth',
      name: 'noAuth',
      component: NoAuthPage,
    },
  ],
})

export default router
