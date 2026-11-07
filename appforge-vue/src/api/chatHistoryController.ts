import request from '@/request'

/** 最近消息按时间降序返回；时间与 ID 共同作为向前翻页的游标。 */
export function listAppChatHistory(params: API.listAppChatHistoryParams, signal?: AbortSignal) {
  const { appId, ...query } = params
  return request<API.BaseResponsePageChatHistory>(
    `/chatHistory/app/${encodeURIComponent(String(appId))}`,
    { params: query, signal },
  )
}

export function listAllChatHistoryByPageForAdmin(body: API.ChatHistoryQueryRequest) {
  return request<API.BaseResponsePageChatHistory>('/chatHistory/admin/list/page/vo', {
    method: 'POST',
    data: body,
  })
}

export function removeChatHistory(id: string) {
  return request<boolean>(`/chatHistory/remove/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
