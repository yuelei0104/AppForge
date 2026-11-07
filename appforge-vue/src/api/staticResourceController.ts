// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /deploy/${param0} */
export async function getDeployIndex1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getDeployIndex1Params,
  options?: { [key: string]: any }
) {
  const { deployKey: param0, ...queryParams } = params
  return request<string>(`/deploy/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /deploy/${param0}/ */
export async function getDeployIndex(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getDeployIndexParams,
  options?: { [key: string]: any }
) {
  const { deployKey: param0, ...queryParams } = params
  return request<string>(`/deploy/${param0}/`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /deploy/${param0}/${param1} */
export async function getDeployResource(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getDeployResourceParams,
  options?: { [key: string]: any }
) {
  const { deployKey: param0, fileName: param1, ...queryParams } = params
  return request<string>(`/deploy/${param0}/${param1}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /static/${param0} */
export async function getPreviewIndex(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getPreviewIndexParams,
  options?: { [key: string]: any }
) {
  const { appId: param0, ...queryParams } = params
  return request<string>(`/static/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /static/${param0}/ */
export async function getPreviewIndex1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getPreviewIndex1Params,
  options?: { [key: string]: any }
) {
  const { appId: param0, ...queryParams } = params
  return request<string>(`/static/${param0}/`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /static/${param0}/${param1} */
export async function getPreviewResource(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getPreviewResourceParams,
  options?: { [key: string]: any }
) {
  const { appId: param0, fileName: param1, ...queryParams } = params
  return request<string>(`/static/${param0}/${param1}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}
