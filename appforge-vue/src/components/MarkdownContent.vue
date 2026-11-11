<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { renderMarkdown } from '@/utils/markdown'
import 'highlight.js/styles/github.css'

const props = defineProps<{ content: string }>()
const rendered = computed(() => renderMarkdown(props.content))
const container = ref<HTMLElement | null>(null)
const openCodeBlocks = new Set<number>()

/** 流式内容每次重绘都会替换 DOM，这里把用户展开过的代码块状态恢复回来。 */
function syncCodeBlockState() {
  const root = container.value
  if (!root) return
  root.querySelectorAll<HTMLDetailsElement>('details.md-code').forEach((details) => {
    const index = Number(details.dataset.codeIndex)
    if (!Number.isFinite(index)) return
    if (openCodeBlocks.has(index)) details.open = true
    details.addEventListener('toggle', () => {
      if (details.open) openCodeBlocks.add(index)
      else openCodeBlocks.delete(index)
    })
  })
}

watch(rendered, async () => {
  await nextTick()
  syncCodeBlockState()
})

onMounted(syncCodeBlockState)
</script>

<template>
  <!-- 原始 HTML 和危险链接由统一 Markdown 渲染器禁用。 -->
  <div ref="container" class="markdown-content" v-html="rendered" />
</template>

<style scoped>
.markdown-content {
  min-width: 0;
  line-height: 1.75;
  overflow-wrap: anywhere;
  white-space: normal;
}
.markdown-content :deep(> :first-child) {
  margin-top: 0;
}
.markdown-content :deep(> :last-child) {
  margin-bottom: 0;
}
.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4) {
  margin: 18px 0 10px;
  font-weight: 600;
  line-height: 1.5;
}
.markdown-content :deep(h1) {
  font-size: 21px;
}
.markdown-content :deep(h2) {
  font-size: 19px;
}
.markdown-content :deep(h3),
.markdown-content :deep(h4) {
  font-size: 16px;
}
.markdown-content :deep(p) {
  margin: 0 0 12px;
}
.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  padding-left: 22px;
  margin: 10px 0;
}
.markdown-content :deep(blockquote) {
  margin: 12px 0;
  padding: 4px 12px;
  color: #667085;
  border-left: 3px solid #c4bdf7;
}
.markdown-content :deep(code) {
  padding: 2px 5px;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  color: #5d4b9d;
  background: #efedf7;
  border-radius: 4px;
}
.markdown-content :deep(pre) {
  max-width: 100%;
  padding: 14px;
  margin: 12px 0;
  overflow-x: auto;
  color: #24292e;
  background: #f7f8fa;
  border: 1px solid #e6e8ef;
  border-radius: 8px;
}
.markdown-content :deep(pre code) {
  padding: 0;
  color: inherit;
  background: none;
  border-radius: 0;
  white-space: pre;
  overflow-wrap: normal;
  line-height: 1.65;
}
.markdown-content :deep(details.md-code) {
  margin: 12px 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e6e8ef;
  border-radius: 10px;
}
.markdown-content :deep(details.md-code > summary) {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  color: #344054;
  cursor: pointer;
  list-style: none;
  background: #fafbfc;
  user-select: none;
}
.markdown-content :deep(details.md-code > summary::-webkit-details-marker) {
  display: none;
}
.markdown-content :deep(.md-code__icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 22px;
  padding: 0 5px;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 11px;
  color: #5867db;
  background: #eef0ff;
  border-radius: 5px;
}
.markdown-content :deep(.md-code__title) {
  font-size: 13px;
  font-weight: 600;
}
.markdown-content :deep(.md-code__lines) {
  font-size: 12px;
  color: #98a2b3;
}
.markdown-content :deep(.md-code__hint) {
  margin-left: auto;
  font-size: 12px;
  color: #667085;
}
.markdown-content :deep(.md-code[open] .md-code__hint--closed),
.markdown-content :deep(.md-code:not([open]) .md-code__hint--open) {
  display: none;
}
.markdown-content :deep(.md-code__pre) {
  max-height: 420px;
  padding: 12px 14px;
  margin: 0;
  overflow: auto;
  background: #f7f8fa;
  border: 0;
  border-top: 1px solid #e6e8ef;
  border-radius: 0;
}
.markdown-content :deep(table) {
  display: block;
  max-width: 100%;
  margin: 12px 0;
  overflow-x: auto;
  border-collapse: collapse;
}
.markdown-content :deep(th),
.markdown-content :deep(td) {
  padding: 7px 10px;
  border: 1px solid #e6e8ef;
}
.markdown-content :deep(th) {
  background: #f7f8fa;
}
.markdown-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 6px;
}
.markdown-content :deep(a) {
  color: #5867db;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.markdown-content :deep(hr) {
  margin: 18px 0;
  border: 0;
  border-top: 1px solid #e6e8ef;
}
</style>
