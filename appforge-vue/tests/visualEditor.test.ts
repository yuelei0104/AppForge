import { afterEach, describe, expect, it, vi } from 'vitest'
import { VisualEditor } from '../src/utils/visualEditor'
const info = {
  tagName: 'H1',
  id: 'hero',
  className: 'title',
  textContent: '原始标题',
  selector: 'h1#hero',
  pagePath: '#/resume',
  rect: { top: 10, left: 20, width: 200, height: 50 },
}
function fixture() {
  const postMessage = vi.fn(),
    selected = vi.fn(),
    source = { postMessage }
  const editor = new VisualEditor({ onElementSelected: selected })
  editor.init({ contentWindow: source } as unknown as HTMLIFrameElement)
  return { editor, selected, source, postMessage }
}
afterEach(() => vi.useRealTimers())
describe('document visual editor', () => {
  it('installs runtime-only code and produces valid JavaScript before and after enabling', () => {
    vi.useFakeTimers()
    const { editor, postMessage } = fixture()
    editor.onIframeLoad()
    let payload = postMessage.mock.calls.at(-1)![0]
    expect(payload.type).toBe('INSTALL_VISUAL_EDITOR')
    expect(payload.editMode).toBe(false)
    expect(() => new Function(payload.script)).not.toThrow()
    editor.enableEditMode()
    vi.advanceTimersByTime(300)
    payload = postMessage.mock.calls.at(-1)![0]
    expect(payload.editMode).toBe(true)
    expect(() => new Function(payload.script)).not.toThrow()
    expect(payload.script).toContain('ELEMENT_SELECTED')
    editor.destroy()
  })
  it('accepts well-formed selections only from the active preview window', () => {
    vi.useFakeTimers()
    const { editor, source, selected } = fixture()
    editor.enableEditMode()
    const message = { type: 'ELEMENT_SELECTED', data: { elementInfo: info } }
    editor.handleIframeMessage({ source: {}, data: message } as MessageEvent)
    editor.handleIframeMessage({ source, data: null } as unknown as MessageEvent)
    editor.handleIframeMessage({
      source,
      data: { ...message, data: { elementInfo: { ...info, selector: 5 } } },
    } as unknown as MessageEvent)
    expect(selected).not.toHaveBeenCalled()
    editor.handleIframeMessage({ source, data: message } as unknown as MessageEvent)
    expect(selected).toHaveBeenCalledExactlyOnceWith(info)
    editor.destroy()
  })
  it('clears highlights and pending injection when disabled or destroyed', () => {
    vi.useFakeTimers()
    const { editor, source, postMessage, selected } = fixture()
    editor.enableEditMode()
    editor.disableEditMode()
    const count = postMessage.mock.calls.length
    vi.advanceTimersByTime(1000)
    expect(postMessage).toHaveBeenCalledTimes(count)
    expect(postMessage.mock.calls.at(-1)![0]).toEqual({ type: 'CLEAR_ALL_EFFECTS' })
    editor.handleIframeMessage({
      source,
      data: { type: 'ELEMENT_SELECTED', data: { elementInfo: info } },
    } as unknown as MessageEvent)
    expect(selected).not.toHaveBeenCalled()
    editor.destroy()
    editor.onIframeLoad()
    expect(postMessage.mock.calls.at(-1)![0]).toEqual({ type: 'CLEAR_ALL_EFFECTS' })
  })
})
