import request from '@/request'

export type PasswordUpdate = { oldPassword: string; newPassword: string; checkPassword: string }

export function updateMyProfile(userName: string) {
  return request<API.BaseResponseLoginUserVO>('/user/profile/update', {
    method: 'POST',
    data: { userName },
  })
}

export function updateMyPassword(data: PasswordUpdate) {
  return request<API.BaseResponseBoolean>('/user/password/update', { method: 'POST', data })
}

export type PasswordReset = {
  userAccount: string
  resetCode: string
  newPassword: string
  checkPassword: string
}
export type PasswordResetCode = { resetCode: string; expiresInSeconds: number }

export function resetPassword(data: PasswordReset) {
  return request<API.BaseResponseBoolean>('/user/password/reset', { method: 'POST', data })
}
export function issuePasswordResetCode(userId: number | string, identityVerified: boolean) {
  return request<{ code: number; data?: PasswordResetCode; message?: string }>(
    '/user/password/reset-code',
    {
      method: 'POST',
      data: { userId, identityVerified },
    },
  )
}
