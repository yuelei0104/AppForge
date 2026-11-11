/**
 * 可视化编辑器工具类
 * 负责管理iframe内的可视化编辑功能
 */
export interface ElementInfo {
  tagName: string
  id: string
  className: string
  textContent: string
  selector: string
  pagePath: string
  rect: {
    top: number
    left: number
    width: number
    height: number
  }
}

export interface VisualEditorOptions {
  onElementSelected?: (elementInfo: ElementInfo) => void
  onElementHover?: (elementInfo: ElementInfo) => void
}

export class VisualEditor {
  private iframe: HTMLIFrameElement | null = null
  private isEditMode = false
  private options: VisualEditorOptions
  private injectionTimer: ReturnType<typeof setTimeout> | undefined

  constructor(options: VisualEditorOptions = {}) {
    this.options = options
  }

  /**
   * 初始化编辑器
   */
  init(iframe: HTMLIFrameElement) {
    this.iframe = iframe
  }

  /**
   * 开启编辑模式
   */
  enableEditMode() {
    if (!this.iframe) {
      return
    }
    this.isEditMode = true
    clearTimeout(this.injectionTimer)
    this.injectionTimer = setTimeout(() => this.injectEditScript(), 300)
  }

  /**
   * 关闭编辑模式
   */
  disableEditMode() {
    this.isEditMode = false
    clearTimeout(this.injectionTimer)
    this.sendMessageToIframe({
      type: 'TOGGLE_EDIT_MODE',
      editMode: false,
    })
    // 清除所有编辑状态
    this.sendMessageToIframe({
      type: 'CLEAR_ALL_EFFECTS',
    })
  }

  /**
   * 切换编辑模式
   */
  toggleEditMode() {
    if (this.isEditMode) {
      this.disableEditMode()
    } else {
      this.enableEditMode()
    }
    return this.isEditMode
  }

  /**
   * 强制同步状态并清理
   */
  syncState() {
    if (!this.isEditMode) {
      this.sendMessageToIframe({
        type: 'CLEAR_ALL_EFFECTS',
      })
    }
  }

  /**
   * 清除选中的元素
   */
  clearSelection() {
    this.sendMessageToIframe({
      type: 'CLEAR_SELECTION',
    })
  }

  /**
   * iframe 加载完成时调用
   */
  onIframeLoad() {
    clearTimeout(this.injectionTimer)
    this.injectEditScript()
  }

  destroy() {
    this.disableEditMode()
    this.iframe = null
  }

  /**
   * 处理来自 iframe 的消息
   */
  handleIframeMessage(event: MessageEvent) {
    if (!this.isEditMode || event.source !== this.iframe?.contentWindow) return
    if (!event.data || typeof event.data !== 'object') return
    const { type, data } = event.data
    const info = data?.elementInfo
    if (!info || typeof info !== 'object' || !info.rect || typeof info.rect !== 'object') return
    if (
      !['tagName', 'id', 'className', 'textContent', 'selector', 'pagePath'].every(
        (key) => typeof info[key] === 'string',
      )
    )
      return
    if (!['top', 'left', 'width', 'height'].every((key) => Number.isFinite(info.rect[key]))) return
    if (
      info.selector.length > 2000 ||
      info.textContent.length > 100 ||
      info.pagePath.length > 2000 ||
      info.className.length > 2000 ||
      info.id.length > 1000 ||
      !/^[a-zA-Z][a-zA-Z0-9-]*$/.test(info.tagName)
    )
      return
    const element: ElementInfo = {
      tagName: info.tagName,
      id: info.id,
      className: info.className,
      textContent: info.textContent,
      selector: info.selector,
      pagePath: info.pagePath,
      rect: {
        top: info.rect.top,
        left: info.rect.left,
        width: info.rect.width,
        height: info.rect.height,
      },
    }
    if (type === 'ELEMENT_SELECTED') this.options.onElementSelected?.(element)
    else if (type === 'ELEMENT_HOVER') this.options.onElementHover?.(element)
  }

  /**
   * 向 iframe 发送消息
   */
  private sendMessageToIframe(message: Record<string, unknown>) {
    if (this.iframe?.contentWindow) {
      this.iframe.contentWindow.postMessage(message, '*')
    }
  }

  /**
   * 注入编辑脚本到 iframe
   */
  private injectEditScript() {
    if (!this.iframe) return

    // Protected previews keep an opaque sandbox origin. Install through the preview-only
    // bridge instead of granting generated pages access to the parent application.
    this.sendMessageToIframe({
      type: 'INSTALL_VISUAL_EDITOR',
      script: this.generateEditScript(),
      editMode: this.isEditMode,
    })
  }

  /**
   * 生成编辑脚本内容
   */
  private generateEditScript() {
    return `
      (function() {
        let isEditMode = ${this.isEditMode};
        let currentHoverElement = null;
        let currentSelectedElement = null;

        function injectStyles() {
          if (document.getElementById('edit-mode-styles')) return;
          const style = document.createElement('style');
          style.id = 'edit-mode-styles';
          style.textContent = \`
            .edit-hover {
              outline: 2px dashed #1890ff !important;
              outline-offset: 2px !important;
              cursor: crosshair !important;
              transition: outline 0.2s ease !important;
              position: relative !important;
            }
            .edit-hover::before {
              content: '' !important;
              position: absolute !important;
              top: -4px !important;
              left: -4px !important;
              right: -4px !important;
              bottom: -4px !important;
              background: rgba(24, 144, 255, 0.02) !important;
              pointer-events: none !important;
              z-index: -1 !important;
            }
            .edit-selected {
              outline: 3px solid #52c41a !important;
              outline-offset: 2px !important;
              cursor: default !important;
              position: relative !important;
            }
            .edit-selected::before {
              content: '' !important;
              position: absolute !important;
              top: -4px !important;
              left: -4px !important;
              right: -4px !important;
              bottom: -4px !important;
              background: rgba(82, 196, 26, 0.03) !important;
              pointer-events: none !important;
              z-index: -1 !important;
            }
          \`;
          document.head.appendChild(style);
        }

        // 生成元素选择器
        function generateSelector(element) {
          const path = [];
          let current = element;
          while (current && current !== document.body) {
            let selector = current.tagName.toLowerCase();
            if (current.id) {
              selector += '#' + CSS.escape(current.id);
              path.unshift(selector);
              break;
            }
            if (current.getAttribute('class')) {
              const classes = current.getAttribute('class').split(/\\s+/).filter(c => c && !c.startsWith('edit-')).map(c => CSS.escape(c));
              if (classes.length > 0) {
                selector += '.' + classes.join('.');
              }
            }
            const siblings = Array.from(current.parentElement?.children || []);
            const index = siblings.indexOf(current) + 1;
            selector += ':nth-child(' + index + ')';
            path.unshift(selector);
            current = current.parentElement;
          }
          return path.join(' > ');
        }

        // 获取元素信息
        function getElementInfo(element) {
          const rect = element.getBoundingClientRect();
          // 获取 HTML 文件名后面的部分（查询参数和锚点）
          const query = new URLSearchParams(window.location.search);
          query.delete('visualEditor');
          query.delete('t');
          let pagePath = (query.size ? '?' + query.toString() : '') + window.location.hash;
          // 如果没有查询参数和锚点，则显示为空
          if (!pagePath) {
            pagePath = '';
          }

          return {
            tagName: element.tagName,
            id: element.id,
            className: (element.getAttribute('class') || '').split(/\\s+/).filter(c => c && !c.startsWith('edit-')).join(' '),
            textContent: element.textContent?.trim().substring(0, 100) || '',
            selector: generateSelector(element),
            pagePath: pagePath,
            rect: {
              top: rect.top,
              left: rect.left,
              width: rect.width,
              height: rect.height
            }
          };
        }

        // 清除悬浮效果
        function clearHoverEffect() {
          if (currentHoverElement) {
            currentHoverElement.classList.remove('edit-hover');
            currentHoverElement = null;
          }
        }

        // 清除选中效果
        function clearSelectedEffect() {
          const selected = document.querySelectorAll('.edit-selected');
          selected.forEach(el => el.classList.remove('edit-selected'));
          currentSelectedElement = null;
        }

        let eventListenersAdded = false;

        function addEventListeners() {
           if (eventListenersAdded) return;

           const mouseoverHandler = (event) => {
             if (!isEditMode) return;

             const target = event.target;
             if (target === currentHoverElement || target === currentSelectedElement) return;
             if (target === document.body || target === document.documentElement) return;
             if (!(target instanceof Element) || target.tagName === 'SCRIPT' || target.tagName === 'STYLE' || target.closest('#edit-tip')) return;

             clearHoverEffect();
             target.classList.add('edit-hover');
             currentHoverElement = target;
           };

           const mouseoutHandler = (event) => {
             if (!isEditMode) return;

             const target = event.target;
             if (!event.relatedTarget || !target.contains(event.relatedTarget)) {
               clearHoverEffect();
             }
           };

           const clickHandler = (event) => {
             if (!isEditMode) return;

             event.preventDefault();
             event.stopPropagation();

             const target = event.target;
             if (target === document.body || target === document.documentElement) return;
             if (!(target instanceof Element) || target.tagName === 'SCRIPT' || target.tagName === 'STYLE' || target.closest('#edit-tip')) return;

             clearSelectedEffect();
             clearHoverEffect();

             target.classList.add('edit-selected');
             currentSelectedElement = target;

             const elementInfo = getElementInfo(target);
             try {
               window.parent.postMessage({
                 type: 'ELEMENT_SELECTED',
                 data: { elementInfo }
               }, '*');
             } catch {
               // 静默处理发送失败
             }
           };

           document.body.addEventListener('mouseover', mouseoverHandler, true);
           document.body.addEventListener('mouseout', mouseoutHandler, true);
           document.body.addEventListener('click', clickHandler, true);
           eventListenersAdded = true;
         }

         function setupEventListeners() {
           addEventListeners();
         }

        // 监听父窗口消息
        window.addEventListener('message', (event) => {
           if (event.source !== window.parent || !event.data || typeof event.data !== 'object') return;
           const { type, editMode } = event.data;
           switch (type) {
             case 'TOGGLE_EDIT_MODE':
               isEditMode = editMode === true;
               if (isEditMode) {
                 injectStyles();
                 setupEventListeners();
                 showEditTip();
               } else {
                 clearHoverEffect();
                 clearSelectedEffect();
               }
               break;
             case 'CLEAR_SELECTION':
               clearSelectedEffect();
               break;
             case 'CLEAR_ALL_EFFECTS':
               isEditMode = false;
               clearHoverEffect();
               clearSelectedEffect();
               const tip = document.getElementById('edit-tip');
               if (tip) tip.remove();
               break;
           }
         });

         function showEditTip() {
           if (document.getElementById('edit-tip')) return;
           const tip = document.createElement('div');
           tip.id = 'edit-tip';
           tip.innerHTML = '🎯 编辑模式已开启<br/>悬浮查看元素，点击选中元素';
           tip.style.cssText = \`
             position: fixed;
             top: 20px;
             right: 20px;
             background: #1890ff;
             color: white;
             padding: 12px 16px;
             border-radius: 6px;
             font-size: 14px;
             z-index: 9999;
             box-shadow: 0 4px 12px rgba(0,0,0,0.15);
             animation: fadeIn 0.3s ease;
           \`;
           const style = document.getElementById('edit-tip-animation') || document.createElement('style');
           style.id = 'edit-tip-animation';
           style.textContent = '@keyframes fadeIn { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }';
           document.head.appendChild(style);
           document.body.appendChild(tip);
           setTimeout(() => {
             if (tip.parentNode) {
               tip.style.animation = 'fadeIn 0.3s ease reverse';
               setTimeout(() => tip.remove(), 300);
             }
           }, 3000);
         }
         injectStyles();
         setupEventListeners();
         if (isEditMode) showEditTip();
      })();
    `
  }
}
