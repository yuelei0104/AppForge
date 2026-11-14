<script setup lang="ts">
import { ThunderboltOutlined, EditOutlined, RocketOutlined } from '@ant-design/icons-vue'
defineProps<{ title: string; description: string }>()
</script>

<template>
  <main class="auth-shell">
    <section class="auth-story" aria-labelledby="auth-story-title">
      <span class="auth-eyebrow">YOUR NEXT IDEA STARTS HERE</span>
      <h2 id="auth-story-title">让好想法，<br />接着<span>发生。</span></h2>
      <p>从一句描述开始，把灵感变成可以预览、修改和分享的作品。</p>
      <ul>
        <li>
          <span><ThunderboltOutlined aria-hidden="true" /></span>
          <div>
            <strong>用自然语言开始</strong><small>说清楚想法，AI 帮你搭建第一个版本。</small>
          </div>
        </li>
        <li>
          <span><EditOutlined aria-hidden="true" /></span>
          <div><strong>边预览，边完善</strong><small>通过对话调整细节，继续打磨作品。</small></div>
        </li>
        <li>
          <span><RocketOutlined aria-hidden="true" /></span>
          <div>
            <strong>把作品带到下一步</strong><small>下载源码继续开发，或发布访问链接。</small>
          </div>
        </li>
      </ul>
      <router-link to="/about">第一次使用？查看使用指南 →</router-link>
    </section>
    <section class="auth-card" aria-labelledby="auth-title">
      <div class="auth-card__heading">
        <span class="auth-brand">APPFORGE</span>
        <h1 id="auth-title">{{ title }}</h1>
        <p>{{ description }}</p>
      </div>
      <slot />
    </section>
  </main>
</template>

<style scoped>
.auth-shell {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 80px;
  align-items: center;
  max-width: 1040px;
  margin: 30px auto;
  padding: 24px 0 36px;
}
.auth-story {
  padding: 12px 0;
}
.auth-eyebrow,
.auth-brand {
  color: var(--forge-accent);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 1.8px;
}
.auth-story h2 {
  margin: 24px 0 18px;
  color: var(--forge-text);
  font-size: 44px;
  line-height: 1.35;
  font-weight: 700;
  letter-spacing: -1px;
}
.auth-story h2 span {
  color: var(--forge-accent);
}
.auth-story > p {
  max-width: 350px;
  color: var(--forge-muted);
  line-height: 1.9;
  font-size: 14px;
}
.auth-story ul {
  list-style: none;
  padding: 0;
  margin: 32px 0;
  display: grid;
  gap: 24px;
}
.auth-story li {
  display: flex;
  gap: 14px;
  align-items: center;
}
.auth-story li > span {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 12px;
  color: var(--forge-accent);
  background: var(--forge-accent-soft);
  font-size: 18px;
}
.auth-story strong {
  display: block;
  font-size: 14px;
  font-weight: 500;
}
.auth-story small {
  display: block;
  color: var(--forge-muted);
  font-size: 12px;
  margin-top: 5px;
  line-height: 1.7;
}
.auth-story > a {
  font-size: 12px;
  color: var(--forge-accent);
}
.auth-card {
  min-width: 0;
  padding: 36px;
  background: #fff;
  border: 1px solid var(--forge-border);
  border-radius: 22px;
  box-shadow: 0 12px 48px #41377607;
}
.auth-card__heading {
  margin-bottom: 28px;
}
.auth-card h1 {
  margin: 14px 0 10px;
  font-size: 27px;
  font-weight: 650;
  color: var(--forge-text);
  line-height: 1.4;
}
.auth-card__heading > p {
  color: var(--forge-muted);
  font-size: 13px;
  line-height: 1.9;
  margin: 0;
}
.auth-card :deep(.ant-input-affix-wrapper),
.auth-card :deep(input.ant-input) {
  border-radius: 10px;
}
.auth-card :deep(.ant-input-affix-wrapper) {
  min-height: 42px;
}
.auth-card :deep(input.ant-input:not(.ant-input-affix-wrapper input)) {
  min-height: 42px;
}
.auth-card :deep(.ant-form-item) {
  margin-bottom: 20px;
}
.auth-card :deep(.ant-alert) {
  margin-bottom: 20px;
}
.auth-card :deep(.auth-submit) {
  height: 44px;
  width: 100%;
  border-radius: 10px;
  font-size: 14px;
}
.auth-card :deep(.auth-links) {
  display: flex;
  gap: 12px;
  justify-content: space-between;
  align-items: center;
  margin: 0 0 24px;
  font-size: 13px;
  line-height: 1.8;
}
.auth-card :deep(.auth-links a),
.auth-card :deep(.auth-switch a) {
  color: var(--forge-accent);
}
.auth-card :deep(.auth-switch) {
  margin: 24px 0 0;
  text-align: center;
  color: var(--forge-muted);
  font-size: 13px;
}
@media (max-width: 1000px) {
  .auth-shell {
    gap: 40px;
  }
  .auth-card {
    padding: 30px;
  }
  .auth-story h2 {
    font-size: 38px;
  }
}
@media (max-width: 767px) {
  .auth-shell {
    display: block;
    margin: 12px auto 24px;
    padding: 0;
    max-width: 460px;
  }
  .auth-story {
    display: none;
  }
  .auth-card {
    padding: 28px 24px;
    border-radius: 18px;
  }
  .auth-card h1 {
    font-size: 25px;
  }
}
</style>
