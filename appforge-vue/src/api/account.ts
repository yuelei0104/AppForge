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
