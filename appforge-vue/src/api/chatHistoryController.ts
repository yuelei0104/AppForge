/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /chatHistory/admin/list/page/vo */
export async function listAllChatHistoryByPageForAdmin(
  body: API.ChatHistoryQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageChatHistory>('/chatHistory/admin/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /chatHistory/app/${param0} */
export async function listAppChatHistory(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listAppChatHistoryParams,
  options?: { [key: string]: any }
) {
  const { appId: param0, ...queryParams } = params
  return request<API.BaseResponsePageChatHistory>(`/chatHistory/app/${param0}`, {
    method: 'GET',
    params: {
      // pageSize has a default value: 10
      pageSize: '10',

      ...queryParams,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /chatHistory/app/${param0}/export */
export async function exportUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.exportUsingGETParams,
  options?: { [key: string]: any }
) {
  const { appId: param0, ...queryParams } = params
  return request<API.StreamingResponseBody>(`/chatHistory/app/${param0}/export`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /chatHistory/app/${param0}/memory */
export async function memory(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.memoryParams,
  options?: { [key: string]: any }
) {
  const { appId: param0, ...queryParams } = params
  return request<API.BaseResponseChatMemoryVO>(`/chatHistory/app/${param0}/memory`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /chatHistory/app/${param0}/memory/compress */
export async function compress(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.compressParams,
  options?: { [key: string]: any }
) {
  const { appId: param0, ...queryParams } = params
  return request<API.BaseResponseChatMemoryVO>(`/chatHistory/app/${param0}/memory/compress`, {
    method: 'POST',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /chatHistory/app/${param0}/stats */
export async function statistics(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.statisticsParams,
  options?: { [key: string]: any }
) {
  const { appId: param0, ...queryParams } = params
  return request<API.BaseResponseChatStatisticsVO>(`/chatHistory/app/${param0}/stats`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /chatHistory/getInfo/${param0} */
export async function getInfo(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getInfoParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params
  return request<API.ChatHistory>(`/chatHistory/getInfo/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /chatHistory/list */
export async function list(options?: { [key: string]: any }) {
  return request<API.ChatHistory[]>('/chatHistory/list', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /chatHistory/page */
export async function page(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.pageParams,
  options?: { [key: string]: any }
) {
  return request<API.PageChatHistory>('/chatHistory/page', {
    method: 'GET',
    params: {
      ...params,
      page: undefined,
      ...params['page'],
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 DELETE /chatHistory/remove/${param0} */
export async function remove(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.removeParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params
  return request<boolean>(`/chatHistory/remove/${param0}`, {
    method: 'DELETE',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /chatHistory/save */
export async function save(body: API.ChatHistory, options?: { [key: string]: any }) {
  return request<boolean>('/chatHistory/save', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 PUT /chatHistory/update */
export async function update(body: API.ChatHistory, options?: { [key: string]: any }) {
  return request<boolean>('/chatHistory/update', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 获取应用对话记忆。 */
export async function getChatMemory(appId: string, signal?: AbortSignal) {
  return request<API.BaseResponseChatMemoryVO>(`/chatHistory/app/${encodeURIComponent(appId)}/memory`, {
    method: 'GET',
    signal,
  })
}

/** 整理应用对话记忆。 */
export async function compressChatMemory(appId: string, signal?: AbortSignal) {
  return request<API.BaseResponseChatMemoryVO>(
    `/chatHistory/app/${encodeURIComponent(appId)}/memory/compress`,
    { method: 'POST', signal },
  )
}

/** 获取对话统计。 */
export async function getChatStatistics(appId: string, signal?: AbortSignal) {
  return request<API.BaseResponseChatStatisticsVO>(
    `/chatHistory/app/${encodeURIComponent(appId)}/stats`,
    { method: 'GET', signal },
  )
}

/** 导出 Markdown。 */
export async function exportChatMarkdown(appId: string, signal?: AbortSignal) {
  return request<Blob>(`/chatHistory/app/${encodeURIComponent(appId)}/export`, {
    method: 'GET',
    responseType: 'blob',
    signal,
  })
}

/** 删除单条对话记录。 */
export async function removeChatHistory(id: string, signal?: AbortSignal) {
  return request<boolean>(`/chatHistory/remove/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    signal,
  })
}
