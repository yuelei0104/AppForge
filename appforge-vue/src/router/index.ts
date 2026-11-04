import { createRouter, createWebHistory } from 'vue-router'
import ACCESS_ENUM from '@/access/accessEnum'
import AboutView from '@/pages/AboutView.vue'
import HomeView from '@/pages/HomeView.vue'
import NoAuthPage from '@/pages/NoAuthPage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
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
      path: '/user/register',
      name: 'userRegister',
      component: UserRegisterPage,
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
