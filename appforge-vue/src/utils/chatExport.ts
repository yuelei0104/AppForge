/** 业务错误也可能以 Blob 返回，先验证类型，避免把登录失败保存成 Markdown。 */
export async function validateMarkdownExport(blob: Blob, contentType: string) {
  if (!contentType.toLowerCase().includes('text/markdown')) {
    let errorText = '导出失败，请稍后重试'
    try {
      const result = JSON.parse(await blob.text()) as { message?: string }
      if (result.message) errorText = result.message
    } catch {
      /* 非 JSON 响应保留默认提示。 */
    }
    throw new Error(errorText)
  }
  return blob
}
