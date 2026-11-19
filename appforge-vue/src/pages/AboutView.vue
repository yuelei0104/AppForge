<script setup lang="ts">
import {
  ArrowRightOutlined,
  CheckOutlined,
  CodeOutlined,
  FileTextOutlined,
  LayoutOutlined,
  RocketOutlined,
} from '@ant-design/icons-vue'
import { generationModeLabel } from '@/utils/generationMode'
import { CodeGenTypeEnum } from '@/config/codeGenType'

const steps = [
  {
    number: '01',
    title: '说清楚你的想法',
    description: '在首页描述用途、页面内容、视觉风格和需要的交互，也可以从场景示例开始。',
    hint: '先写清楚「为谁做、做什么」',
  },
  {
    number: '02',
    title: '选择合适的模式',
    description: '简单展示选单页网站，拆分页面与样式选轻量网站，需要组件化组织时选完整网站工程。',
    hint: '按作品复杂度选择',
  },
  {
    number: '03',
    title: '预览，再继续打磨',
    description: '进入创作工作区后等待生成，在电脑或手机预览中检查效果，通过对话提出具体修改。',
    hint: '一次聚焦一个修改目标',
  },
  {
    number: '04',
    title: '发布或下载作品',
    description: '生成完成后发布应用，获取访问链接；也可以下载源码或工程，继续开发和使用。',
    hint: '分享前确认作品的可见范围',
  },
]

const modes = [
  {
    value: CodeGenTypeEnum.HTML,
    icon: FileTextOutlined,
    type: '轻巧起步',
    description: '把内容、样式和交互放在一个网页中，适合先验证一个简单想法。',
    examples: '活动介绍 · 个人名片 · 产品落地页',
    features: ['单个 HTML 文件', '集中展示一个主题'],
  },
  {
    value: CodeGenTypeEnum.MULTI_FILE,
    icon: LayoutOutlined,
    type: '灵活展示',
    description: '将页面、样式和脚本分开组织，适合内容更丰富的展示型网站。',
    examples: '企业官网 · 作品集 · 小型展示网站',
    features: ['HTML、CSS、JavaScript 分文件', '便于分别调整结构与视觉'],
  },
  {
    value: CodeGenTypeEnum.VUE_PROJECT,
    icon: CodeOutlined,
    type: '完整工程',
    description: '生成 Vue 项目，通过组件组织更复杂的界面与交互，构建完成后预览。',
    examples: '管理界面 · 数据看板 · 多页面应用界面',
    features: ['独立项目与工程配置', '可下载工程继续开发'],
  },
]

const faqs = [
  {
    question: '第一次创建，需要先登录吗？',
    answer:
      '需要。你可以先在首页准备需求，点击「开始创建」时登录。登录后创建的作品会保存在「我的作品」中，之后可以继续编辑。',
    link: { to: '/#my-apps', label: '前往我的作品' },
  },
  {
    question: '生成失败，或者暂时看不到预览怎么办？',
    answer:
      '先查看工作区里的生成进度和错误提示。完整网站工程还需要完成构建才能预览；已有内容可以尝试点击预览区的刷新。如果生成失败，可编辑上次需求后重新发送，或从「我的作品」重新进入。',
  },
  {
    question: '发布后，其他人就能看到我的作品了吗？',
    answer:
      '发布用于生成访问链接，「公开 / 私有」决定谁能访问。新作品默认为私有，私有作品的预览和发布链接仅本人或管理员可访问。需要分享时，在作品的「编辑应用信息」中把可见范围设为「公开」。',
    link: { to: '/#my-apps', label: '查看我的作品' },
  },
  {
    question: '为什么「全部公开应用」没有我的作品？',
    answer:
      '请先确认作品已保存为「公开」，再检查搜索词和标签筛选。发布成功不代表自动公开；私有作品只会出现在自己的作品列表中。',
  },
  {
    question: '精选案例是实时排行榜吗？怎么申请？',
    answer:
      '精选案例由管理员审核推荐，不是实时热度排行榜。作品设为公开且已有生成版本后，可以在工作区的「应用详情」中申请精选，审核通过后进入精选列表。',
  },
  {
    question: '怎么修改昵称和密码？',
    answer:
      '点击右上角头像或昵称，进入「个人设置」。昵称保存后立即更新；修改密码需要原密码和两次一致的新密码，成功后使用新密码重新登录。忘记原密码时，从登录页进入「忘记密码」，使用管理员核验身份后提供的一次性重置码设置新密码。',
    link: { to: '/user/settings', label: '打开个人设置' },
  },
  {
    question: '生成管理界面后，就能处理真实业务了吗？',
    answer:
      '生成的界面可能使用示例数据。真实登录、数据库、支付等业务需要对应的服务接入；使用或分享前，请检查实际功能、数据和访问权限。',
  },
]
</script>

<template>
  <main class="guide-page">
    <section class="guide-hero" aria-labelledby="guide-title">
      <div class="guide-hero__copy">
        <span class="eyebrow">GET STARTED WITH APPFORGE</span>
        <h1 id="guide-title">AppForge 使用指南</h1>
        <p class="guide-lead">从一个想法，到可以访问的网站。</p>
        <p class="guide-intro">
          描述需求，让 AI 帮你构建。预览、对话修改、下载与发布，在一个创作空间里完成。
        </p>
        <router-link to="/#create-app" class="guide-button"
          >开始创建应用 <ArrowRightOutlined aria-hidden="true"
        /></router-link>
      </div>
      <aside class="prompt-example" aria-label="需求描述示例">
        <span class="prompt-example__label"
          ><FileTextOutlined aria-hidden="true" /> 一份清晰的需求，可以这样写</span
        >
        <p>
          创建一个个人摄影作品集，使用简洁的黑白风格。包含个人介绍、作品分类、图片展示和联系方式，作品支持分类筛选，并适配手机浏览。
        </p>
        <div class="prompt-tags">
          <span>用途</span><span>内容</span><span>风格</span><span>交互</span>
        </div>
        <small>先完成核心功能，再通过对话逐步完善。</small>
      </aside>
    </section>

    <nav class="guide-nav" aria-label="使用指南目录">
      <router-link to="/about#creation-steps">01 创建流程</router-link>
      <router-link to="/about#generation-modes">02 选择模式</router-link>
      <router-link to="/about#common-questions">03 常见问题</router-link>
    </nav>

    <section id="creation-steps" class="guide-section" aria-labelledby="steps-title">
      <div class="section-heading">
        <span class="eyebrow">YOUR FIRST CREATION</span>
        <h2 id="steps-title">四步，开始你的创作</h2>
        <p>不必一次写完所有细节，先让第一个版本运行起来。</p>
      </div>
      <ol class="steps-grid">
        <li v-for="step in steps" :key="step.number" class="step-card">
          <span class="step-number">{{ step.number }}</span>
          <h3>{{ step.title }}</h3>
          <p>{{ step.description }}</p>
          <small>{{ step.hint }}</small>
        </li>
      </ol>
    </section>

    <section id="generation-modes" class="guide-section" aria-labelledby="modes-title">
      <div class="section-heading">
        <span class="eyebrow">CHOOSE YOUR STARTING POINT</span>
        <h2 id="modes-title">哪一种模式适合你？</h2>
        <p>三种模式对应不同的代码组织方式，按当前需要选择即可。</p>
      </div>
      <div class="modes-grid">
        <article v-for="mode in modes" :key="mode.value" class="mode-card">
          <div class="mode-card__top">
            <span class="mode-icon"><component :is="mode.icon" aria-hidden="true" /></span
            ><span class="mode-type">{{ mode.type }}</span>
          </div>
          <h3>{{ generationModeLabel(mode.value) }}</h3>
          <p class="mode-description">{{ mode.description }}</p>
          <ul>
            <li v-for="feature in mode.features" :key="feature">
              <CheckOutlined aria-hidden="true" />{{ feature }}
            </li>
          </ul>
          <div class="mode-examples">
            <span>适合用来做</span>
            <p>{{ mode.examples }}</p>
          </div>
        </article>
      </div>
      <p class="mode-note">
        选择完整网站工程时，预览需要等待项目构建完成。各模式生成的实际内容，请以预览和下载结果为准。
      </p>
    </section>

    <section id="common-questions" class="guide-section faq-section" aria-labelledby="faq-title">
      <div class="section-heading">
        <span class="eyebrow">A LITTLE HELP ALONG THE WAY</span>
        <h2 id="faq-title">你可能还想知道</h2>
        <p>关于生成、分享和账号，常用答案都在这里。</p>
      </div>
      <div class="faq-list">
        <details v-for="faq in faqs" :key="faq.question" class="faq-item">
          <summary>{{ faq.question }}<span class="faq-plus" aria-hidden="true">+</span></summary>
          <div class="faq-answer">
            <p>{{ faq.answer }}</p>
            <router-link v-if="faq.link" :to="faq.link.to"
              >{{ faq.link.label }} <ArrowRightOutlined aria-hidden="true"
            /></router-link>
          </div>
        </details>
      </div>
    </section>

    <section class="guide-start" aria-labelledby="start-title">
      <div>
        <span class="start-icon"><RocketOutlined aria-hidden="true" /></span>
        <h2 id="start-title">现在，把你的想法写下来。</h2>
        <p>从一个小而明确的需求开始，边预览，边完善。</p>
      </div>
      <router-link to="/#create-app" class="guide-button"
        >开始创建应用 <ArrowRightOutlined aria-hidden="true"
      /></router-link>
    </section>
  </main>
</template>

<style scoped>
.guide-page {
  max-width: 1120px;
  margin: 12px auto 36px;
  color: var(--forge-text);
}
.guide-hero {
  display: grid;
  grid-template-columns: 1.12fr 1fr;
  gap: 52px;
  align-items: center;
  padding: 48px 0 40px;
}
.eyebrow {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 1.8px;
  color: var(--forge-accent);
}
h1 {
  margin: 16px 0 12px;
  font-size: clamp(30px, 3.5vw, 42px);
  font-weight: 700;
  letter-spacing: -1px;
  line-height: 1.3;
}
.guide-lead {
  font-size: 22px;
  margin: 0 0 14px;
  font-weight: 500;
}
.guide-intro {
  max-width: 460px;
  line-height: 1.9;
  color: var(--forge-muted);
  margin: 0 0 26px;
}
.guide-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 12px 20px;
  color: #fff;
  background: var(--forge-accent);
  border: 1px solid var(--forge-accent);
  border-radius: 11px;
  font-size: 14px;
  font-weight: 500;
  transition:
    background 0.2s,
    transform 0.2s;
}
.guide-button:hover {
  color: #fff;
  background: #5544c4;
  transform: translateY(-1px);
}
.prompt-example {
  min-width: 0;
  padding: 28px;
  border: 1px solid #e2ddfb;
  border-radius: 20px;
  background: linear-gradient(145deg, #f1edff, #fff 85%);
  box-shadow: 0 14px 40px #6654d909;
}
.prompt-example__label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--forge-accent);
}
.prompt-example > p {
  margin: 20px 0;
  font-size: 15px;
  line-height: 2;
}
.prompt-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}
.prompt-tags span {
  padding: 3px 10px;
  color: #675c97;
  background: #fff;
  border: 1px solid #e8e2fb;
  border-radius: 6px;
  font-size: 11px;
}
.prompt-example small {
  color: var(--forge-muted);
  font-size: 12px;
}
.guide-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 24px;
  padding: 20px 0;
  border-top: 1px solid var(--forge-border);
  border-bottom: 1px solid var(--forge-border);
}
.guide-nav a {
  color: var(--forge-muted);
  font-size: 13px;
  padding: 5px 0;
}
.guide-nav a:hover {
  color: var(--forge-accent);
}
.guide-section {
  margin-top: 64px;
  scroll-margin-top: 12px;
}
.section-heading {
  margin-bottom: 24px;
}
h2 {
  margin: 10px 0;
  font-size: 26px;
  font-weight: 650;
  line-height: 1.4;
}
.section-heading > p {
  color: var(--forge-muted);
  margin: 0;
  line-height: 1.8;
  font-size: 14px;
}
.steps-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.step-card {
  padding: 24px 20px;
  background: #fff;
  border: 1px solid var(--forge-border);
  border-radius: 16px;
}
.step-number {
  display: inline-flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--forge-accent-soft);
  color: var(--forge-accent);
  font-size: 12px;
  font-weight: 600;
}
h3 {
  font-size: 17px;
  font-weight: 600;
  margin: 18px 0 12px;
}
.step-card > p {
  font-size: 13px;
  color: var(--forge-muted);
  line-height: 1.9;
  margin: 0 0 18px;
}
.step-card > small {
  font-size: 11px;
  color: #8b84a6;
}
.modes-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}
.mode-card {
  padding: 26px;
  min-width: 0;
  background: #fff;
  border: 1px solid var(--forge-border);
  border-radius: 18px;
}
.mode-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.mode-icon {
  display: inline-flex;
  padding: 11px;
  border-radius: 12px;
  color: var(--forge-accent);
  background: var(--forge-accent-soft);
  font-size: 20px;
}
.mode-type {
  font-size: 11px;
  color: var(--forge-muted);
}
.mode-card h3 {
  font-size: 20px;
}
.mode-description {
  min-height: 76px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--forge-muted);
}
.mode-card ul {
  list-style: none;
  margin: 20px 0;
  padding: 0;
  font-size: 12px;
}
.mode-card li {
  display: flex;
  gap: 8px;
  align-items: baseline;
  margin-top: 10px;
}
.mode-card li :deep(.anticon) {
  color: var(--forge-accent);
}
.mode-examples {
  border-top: 1px solid var(--forge-border);
  padding-top: 18px;
}
.mode-examples > span {
  font-size: 11px;
  color: var(--forge-muted);
}
.mode-examples > p {
  font-size: 12px;
  line-height: 1.8;
  margin: 8px 0 0;
}
.mode-note {
  font-size: 12px;
  line-height: 1.8;
  margin: 16px 0 0;
  color: var(--forge-muted);
}
.faq-section {
  display: grid;
  grid-template-columns: 0.8fr 1.8fr;
  gap: 48px;
}
.faq-list {
  border-top: 1px solid var(--forge-border);
}
.faq-item {
  border-bottom: 1px solid var(--forge-border);
}
.faq-item summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 22px 0;
  cursor: pointer;
  list-style: none;
  font-size: 14px;
  line-height: 1.8;
  font-weight: 500;
}
.faq-item summary::-webkit-details-marker {
  display: none;
}
.faq-plus {
  flex-shrink: 0;
  color: var(--forge-accent);
  font-size: 22px;
  line-height: 1;
  transition: transform 0.2s;
}
.faq-item[open] .faq-plus {
  transform: rotate(45deg);
}
.faq-answer {
  padding: 0 26px 22px 0;
  font-size: 13px;
  line-height: 1.95;
  color: var(--forge-muted);
}
.faq-answer > p {
  margin: 0;
}
.faq-answer > a {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  color: var(--forge-accent);
  margin-top: 14px;
}
.guide-start {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  margin-top: 64px;
  padding: 32px;
  border-radius: 20px;
  background: #efecff;
}
.start-icon {
  color: var(--forge-accent);
  font-size: 22px;
}
.guide-start h2 {
  font-size: 23px;
}
.guide-start p {
  color: var(--forge-muted);
  font-size: 13px;
  margin: 0;
  line-height: 1.9;
}
.guide-start .guide-button {
  flex-shrink: 0;
}
@media (max-width: 1000px) {
  .guide-hero {
    gap: 28px;
  }
  .steps-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .mode-card {
    padding: 20px;
  }
  .faq-section {
    gap: 28px;
  }
}
@media (max-width: 767px) {
  .guide-page {
    margin-top: 0;
  }
  .guide-hero {
    grid-template-columns: 1fr;
    padding-top: 24px;
    gap: 28px;
  }
  .guide-lead {
    font-size: 19px;
  }
  .prompt-example {
    padding: 24px;
  }
  .guide-section {
    margin-top: 44px;
  }
  .modes-grid,
  .faq-section {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .mode-description {
    min-height: 0;
  }
  .faq-section .section-heading {
    margin-bottom: 4px;
  }
  h2 {
    font-size: 23px;
  }
  .guide-start {
    align-items: flex-start;
    flex-direction: column;
    padding: 26px;
    margin-top: 44px;
    gap: 20px;
  }
}
@media (max-width: 480px) {
  .steps-grid {
    grid-template-columns: 1fr;
  }
  .step-card {
    padding: 22px;
  }
  .guide-nav {
    gap: 8px 18px;
  }
  .guide-nav a {
    font-size: 12px;
  }
}
</style>
