/** 复制原始文字；非安全 HTTP 或权限受限时，回退到浏览器选择复制。 */
export async function copyText(text: string): Promise<void> {
  if (!text.trim()) throw new Error('没有可复制的文字')
  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return
    } catch {
      // 部分浏览器会暴露 Clipboard API，但拒绝写入权限，继续尝试兼容方式。
    }
  }
  if (typeof document === 'undefined' || typeof document.execCommand !== 'function') {
    throw new Error('无法复制，请选中文字后手动复制')
  }
  const focused = document.activeElement as HTMLElement | null
  const selection = document.getSelection()
  const ranges = selection
    ? Array.from({ length: selection.rangeCount }, (_, index) =>
        selection.getRangeAt(index).cloneRange(),
      )
    : []
  const field = document.createElement('textarea')
  field.value = text
  field.readOnly = true
  field.tabIndex = -1
  field.setAttribute('aria-hidden', 'true')
  Object.assign(field.style, { position: 'fixed', left: '-9999px', top: '0', fontSize: '16px' })
  document.body.appendChild(field)
  try {
    field.select()
    field.setSelectionRange(0, text.length)
    if (!document.execCommand('copy')) throw new Error('复制被浏览器拒绝')
  } catch {
    throw new Error('无法复制，请选中文字后手动复制')
  } finally {
    field.remove()
    // 焦点/选区恢复失败也不能把一次成功复制误报为失败。
    try {
      if (typeof focused?.focus === 'function') focused.focus({ preventScroll: true })
      if (selection) {
        selection.removeAllRanges()
        for (const range of ranges) selection.addRange(range)
      }
    } catch {
      // 复制期间原元素可能已经被页面移除。
    }
  }
}
