import type { AppInfo } from '@/api/workspace'
export function generationLabel(status: AppInfo['generationStatus']) {
  return {
    IDLE: '待生成',
    GENERATING: '生成中',
    SUCCEEDED: '已生成',
    FAILED: '生成失败',
    STOPPED: '已停止',
  }[status || 'IDLE']
}
export function featureLabel(status: AppInfo['featureStatus']) {
  return { NONE: '未申请', PENDING: '审核中', APPROVED: '已入选', REJECTED: '未通过' }[
    status || 'NONE'
  ]
}
export function isOnline(app: AppInfo) {
  return !!app.deployKey && app.deploymentStatus !== 'OFFLINE'
}
