import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getLoginUser, userLogout } from '@/api/userController'

const EMPTY_USER: API.LoginUserVO = {
  userName: '未登录',
}

export const useLoginUserStore = defineStore('loginUser', () => {
  // 全局共享的当前登录用户信息
  const loginUser = ref<API.LoginUserVO>({ ...EMPTY_USER })

  // 是否已登录：以用户 id 是否存在为准
  const isLoggedIn = computed(() => Boolean(loginUser.value.id))

  // 获取当前登录用户信息，供应用启动时调用
  async function fetchLoginUser() {
    try {
      const res = await getLoginUser()
      if (res.data.code === 0 && res.data.data) {
        loginUser.value = res.data.data
        return loginUser.value
      }
      loginUser.value = { ...EMPTY_USER }
    } catch {
      // 未登录属于正常情况，不打断页面渲染
      loginUser.value = { ...EMPTY_USER }
    }
    return loginUser.value
  }

  // 登录成功后，直接用返回的用户信息更新全局状态
  function setLoginUser(newLoginUser: API.LoginUserVO | null) {
    loginUser.value = newLoginUser ?? { ...EMPTY_USER }
  }

  // 退出登录：无论接口是否成功，都清空本地登录态
  async function logout() {
    try {
      await userLogout()
    } finally {
      setLoginUser(null)
    }
  }

  return {
    loginUser,
    isLoggedIn,
    fetchLoginUser,
    setLoginUser,
    logout,
  }
})
