import { message } from 'ant-design-vue'
import router from '@/router'
import { useLoginUserStore } from '@/stores/loginUser'
import ACCESS_ENUM from './accessEnum'
import type { AccessType } from './accessEnum'
import checkAccess from './checkAccess'

// 是否已经尝试获取过登录用户，避免每次路由切换都重复请求
let firstFetchLoginUser = true

/**
 * 全局路由权限校验
 */
router.beforeEach(async (to) => {
  const loginUserStore = useLoginUserStore()

  // 首次进入页面时，先等待登录用户信息返回，刷新后也能正确判断权限
  if (firstFetchLoginUser) {
    await loginUserStore.fetchLoginUser()
    firstFetchLoginUser = false
  }

  const needAccess = (to.meta.access as AccessType | undefined) ?? ACCESS_ENUM.NOT_LOGIN

  // 不需要登录的页面直接放行
  if (needAccess === ACCESS_ENUM.NOT_LOGIN) {
    return true
  }

  // 需要登录但当前未登录，跳转登录页并记录来源地址
  if (!loginUserStore.isLoggedIn) {
    message.warning('请先登录')
    return {
      path: '/user/login',
      query: { redirect: to.fullPath },
    }
  }

  // 已登录但权限不足，跳转无权限页面
  if (!checkAccess(loginUserStore.loginUser, needAccess)) {
    message.error('没有权限')
    return { path: '/noAuth' }
  }

  return true
})
