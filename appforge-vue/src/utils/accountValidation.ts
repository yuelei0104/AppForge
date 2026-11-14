export function validateNickname(value: string): string {
  const nickname = value.trim()
  if (
    Array.from(nickname).length < 2 ||
    Array.from(nickname).length > 24 ||
    /[\u0000-\u001f\u007f-\u009f]/.test(nickname)
  ) {
    throw new Error('昵称需为 2～24 个字符，不能包含换行等控制字符')
  }
  return nickname
}
export function validatePasswordChange(
  oldPassword: string,
  newPassword: string,
  checkPassword: string,
) {
  if (!oldPassword.trim() || !newPassword.trim() || !checkPassword.trim())
    throw new Error('请完整填写密码')
  if (newPassword.length < 8 || newPassword.length > 64) throw new Error('新密码需为 8～64 位')
  if (newPassword !== checkPassword) throw new Error('两次输入的新密码不一致')
  if (oldPassword === newPassword) throw new Error('新密码不能与原密码相同')
}
