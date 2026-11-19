import { updateMyPassword, updateMyProfile, type PasswordUpdate } from '@/api/account'
import { useLoginUserStore } from '@/stores/loginUser'
import { validateNickname, validatePasswordChange } from '@/utils/accountValidation'

export function useAccountSettings() {
  const store = useLoginUserStore()

  async function saveNickname(value: string) {
    const response = await updateMyProfile(validateNickname(value))
    if (response.data.code !== 0 || !response.data.data?.id) {
      throw new Error(response.data.message || '昵称保存失败，请重试')
    }
    // 以服务端返回的身份更新导航栏；不在客户端修改账号或权限。
    store.setLoginUser(response.data.data)
    return response.data.data.userName || ''
  }

  async function changePassword(values: PasswordUpdate) {
    validatePasswordChange(values.oldPassword, values.newPassword, values.checkPassword)
    const response = await updateMyPassword({ ...values })
    if (response.data.code !== 0 || response.data.data !== true) {
      throw new Error(response.data.message || '密码修改失败，请重试')
    }
    store.setLoginUser(null)
  }

  return { saveNickname, changePassword }
}
