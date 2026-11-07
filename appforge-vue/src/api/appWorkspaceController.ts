// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /app/admin/feature/review */
export async function review(body: API.WorkspaceRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/app/admin/feature/review', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/feature/apply */
export async function apply(body: API.WorkspaceRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/app/feature/apply', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/generation/stop */
export async function stop(body: API.WorkspaceRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/app/generation/stop', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/undeploy */
export async function offline(body: API.WorkspaceRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/app/undeploy', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/version/files */
export async function files(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.filesParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseMapStringString>('/app/version/files', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/version/list */
export async function versions(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.versionsParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListAppVersionVO>('/app/version/list', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/version/rollback */
export async function rollback(body: API.WorkspaceRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseInteger>('/app/version/rollback', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
