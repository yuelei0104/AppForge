import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'

hljs.registerLanguage('xml', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('bash', bash)

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  highlight(code, language) {
    if (language && hljs.getLanguage(language)) {
      try {
        return hljs.highlight(code, { language, ignoreIllegals: true }).value
      } catch {
        // 未完成的流式代码和无法高亮的内容继续按纯文本展示。
      }
    }
    return '' // markdown-it 会对未知语言执行 HTML 转义。
  },
})

interface RenderEnv {
  codeIndex?: number
}

const LANGUAGE_LABELS: Record<string, string> = {
  html: 'HTML',
  xml: 'HTML',
  css: 'CSS',
  javascript: 'JavaScript',
  js: 'JavaScript',
  typescript: 'TypeScript',
  ts: 'TypeScript',
  json: 'JSON',
  bash: 'Shell',
  shell: 'Shell',
  sh: 'Shell',
  python: 'Python',
  java: 'Java',
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function codeLanguage(info: string) {
  const language = info.trim().split(/\s+/)[0]?.toLowerCase() ?? ''
  return language && hljs.getLanguage(language) ? language : ''
}

function languageLabel(language: string) {
  return LANGUAGE_LABELS[language] ?? (language ? language.toUpperCase() : '代码')
}

markdown.renderer.rules.fence = (tokens, index, _options, env) => {
  const token = tokens[index]!
  const language = codeLanguage(token.info)
  const label = languageLabel(language)
  const code = token.content
  const lineCount = code.endsWith('\n')
    ? code.split('\n').length - 1
    : code.split('\n').length
  let highlighted = escapeHtml(code)
  if (language) {
    try {
      highlighted = hljs.highlight(code, { language, ignoreIllegals: true }).value
    } catch {
      // 未完成的流式代码继续按纯文本展示。
    }
  }
  const languageClass = language ? ` language-${language}` : ''

  // 短代码保留原样，避免把一行命令也做成了折叠卡片。
  if (lineCount < 8 && code.length < 500) {
    return `<pre class="md-code-block"><code class="hljs${languageClass}">${highlighted}</code></pre>\n`
  }

  const renderEnv = (env ?? {}) as unknown as RenderEnv
  const codeIndex = renderEnv.codeIndex ?? 0
  renderEnv.codeIndex = codeIndex + 1
  return `<details class="md-code" data-code-index="${codeIndex}">
<summary class="md-code__summary">
<span class="md-code__icon">&lt;/&gt;</span>
<span class="md-code__title">${escapeHtml(label)} 代码</span>
<span class="md-code__lines">${lineCount} 行</span>
<span class="md-code__hint md-code__hint--closed">展开代码</span>
<span class="md-code__hint md-code__hint--open">收起代码</span>
</summary>
<pre class="md-code__pre"><code class="hljs${languageClass}">${highlighted}</code></pre>
</details>\n`
}

const renderLink = markdown.renderer.rules.link_open
markdown.renderer.rules.link_open = (tokens, index, options, env, renderer) => {
  tokens[index]!.attrSet('target', '_blank')
  tokens[index]!.attrSet('rel', 'noopener noreferrer')
  return renderLink
    ? renderLink(tokens, index, options, env, renderer)
    : renderer.renderToken(tokens, index, options)
}

/** 禁用原始 HTML，模型返回的 HTML 只能作为文本或代码展示。 */
export function renderMarkdown(content: string) {
  return markdown.render(content, { codeIndex: 0 })
}
