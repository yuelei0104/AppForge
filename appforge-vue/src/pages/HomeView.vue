<script setup lang="ts">
import { createAppWithMode, listPublicApps, type AppInfo, type AppFilters } from '@/api/workspace'
import { CodeGenTypeEnum } from '@/config/codeGenType'
import type { GenerationMode } from '@/utils/generationMode'
import { coverPending } from '@/utils/cover'
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowUpOutlined,
  LoginOutlined,
  ArrowRightOutlined,
  AppstoreOutlined,
  UserOutlined,
  GlobalOutlined,
  BarChartOutlined,
  ControlOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue'
import AppCard from '@/components/AppCard.vue'
import HeroArtifact from '@/components/HeroArtifact.vue'
import FeaturedWorkStrip from '@/components/FeaturedWorkStrip.vue'
import { listGoodAppVoByPage, listMyAppVoByPage } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const quickPrompts = [
  {
    label: '个人作品集',
    description: '展示作品，讲述你的故事',
    icon: UserOutlined,
    tone: 'violet',
    prompt:
      '帮我创建一个个人作品集网站，使用简洁、留白充足的设计。包含个人简介、技能介绍、项目作品与联系方式。项目卡片支持分类筛选，点击后展示项目详情，填充示例内容，适配手机和电脑。',
  },
  {
    label: '企业官网',
    description: '为品牌建立专属空间',
    icon: GlobalOutlined,
    tone: 'blue',
    prompt:
      '帮我创建一家科技公司的官方网站，使用蓝紫色科技风格。包含品牌介绍、产品服务、客户案例、团队成员和联系表单。首页突出核心产品及咨询入口，导航点击后滚动到对应区块，配上真实感的示例文案，并适配移动端。',
  },
  {
    label: '管理后台',
    description: '让日常管理井井有条',
    icon: ControlOutlined,
    tone: 'amber',
    prompt:
      '帮我创建一个办公用品管理后台，包含侧边导航、库存概览、物品列表和领用记录。支持按名称搜索、分类筛选，使用弹窗新增和编辑物品。先使用示例数据演示完整交互，并适配手机和电脑。',
  },
  {
    label: '数据看板',
    description: '让数据变得一目了然',
    icon: BarChartOutlined,
    tone: 'mint',
    prompt:
      '帮我创建一个电商运营数据看板，包含销售额、订单量、客单价与转化率指标，展示近七天趋势、商品排行和订单列表。支持日期筛选、订单状态筛选及关键词搜索，使用清晰的卡片布局和示例数据，适配桌面和手机浏览。',
  },
]

const prompt = ref('')
const generationMode = ref<GenerationMode>(CodeGenTypeEnum.VUE_PROJECT)
const modeChips: { value: GenerationMode; label: string; hint: string }[] = [
  { value: CodeGenTypeEnum.VUE_PROJECT, label: '多页应用', hint: '完整工程' },
  { value: CodeGenTypeEnum.MULTI_FILE, label: '轻量网站', hint: '多文件' },
  { value: CodeGenTypeEnum.HTML, label: '单页站点', hint: '快速验证' },
]
const creating = ref(false)
const promptInput = ref<{ focus: () => void }>()
const myError = ref('')
const goodError = ref('')
const selectedScenario = computed(() => quickPrompts.find((item) => item.prompt === prompt.value))

function focusPrompt() {
  document.getElementById('create-app')?.scrollIntoView({ block: 'center' })
  promptInput.value?.focus()
}

const myApps = ref<AppInfo[]>([])
const myTotal = ref(0)
const myPage = ref(1)
const myPageSize = 6
const mySearch = ref('')
const myLoading = ref(false)

const goodApps = ref<AppInfo[]>([])
const goodTotal = ref(0)
const goodPage = ref(1)
const goodPageSize = 6
const goodSearch = ref('')
const goodTag = ref('')
const myTag = ref('')
const discovery = ref<'featured' | 'public'>('featured')
const goodLoading = ref(false)

const isLoggedIn = computed(() => loginUserStore.isLoggedIn)
const featuredPreview = computed(() => goodApps.value.slice(0, 3))

function useQuickPrompt(text: string) {
  prompt.value = text
  promptInput.value?.focus()
}

function goLogin() {
  void router.push({ path: '/user/login', query: { redirect: '/' } })
}

async function handleCreate() {
  if (creating.value) return
  const initPrompt = prompt.value.trim()
  if (!initPrompt) {
    message.warning('请先描述你想创建的应用')
    return
  }
  if (!isLoggedIn.value) {
    goLogin()
    return
  }
  creating.value = true
  try {
    const id = await createAppWithMode(initPrompt, generationMode.value)
    message.success('应用创建成功，正在为你生成')
    await router.push(`/app/chat/${id}`)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '创建应用失败，请稍后重试')
  } finally {
    creating.value = false
  }
}

function handlePromptKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
    event.preventDefault()
    void handleCreate()
  }
}

async function fetchMyApps() {
  if (!isLoggedIn.value) {
    myApps.value = []
    myTotal.value = 0
    return
  }
  myLoading.value = true
  myError.value = ''
  try {
    const filters: AppFilters = {
      pageNum: myPage.value,
      pageSize: myPageSize,
      searchText: mySearch.value.trim() || undefined,
      tag: myTag.value.trim() || undefined,
    }
    const res = await listMyAppVoByPage(filters)
    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records ?? []
      myTotal.value = Number(res.data.data.totalRow ?? 0)
    } else {
      myError.value = res.data.message || '获取我的作品失败'
    }
  } catch {
    myError.value = '暂时无法加载我的作品，请检查连接后重试'
  } finally {
    myLoading.value = false
  }
}

async function fetchGoodApps() {
  goodLoading.value = true
  goodError.value = ''
  try {
    const filters: AppFilters = {
      pageNum: goodPage.value,
      pageSize: goodPageSize,
      searchText: goodSearch.value.trim() || undefined,
      tag: goodTag.value.trim() || undefined,
      sortField: 'createTime',
      sortOrder: 'desc',
    }
    const res =
      discovery.value === 'featured'
        ? await listGoodAppVoByPage(filters)
        : await listPublicApps(filters)
    if (res.data.code === 0 && res.data.data) {
      goodApps.value = res.data.data.records ?? []
      goodTotal.value = Number(res.data.data.totalRow ?? 0)
    } else {
      goodError.value = res.data.message || '获取案例失败'
    }
  } catch {
    goodError.value = '暂时无法加载案例，请检查连接后重试'
  } finally {
    goodLoading.value = false
  }
}

function handleMySearch() {
  myPage.value = 1
  void fetchMyApps()
}

function handleGoodSearch() {
  goodPage.value = 1
  void fetchGoodApps()
}

function handleMyPageChange(page: number) {
  myPage.value = page
  void fetchMyApps()
}

function handleGoodPageChange(page: number) {
  goodPage.value = page
  void fetchGoodApps()
}

function openMyApp(app: AppInfo) {
  void router.push({ path: `/app/chat/${String(app.id)}` })
}

function editMyApp(app: AppInfo) {
  void router.push(`/app/edit/${String(app.id)}`)
}

function openGoodApp(app: AppInfo) {
  openMyApp(app)
}

let coverTimer: ReturnType<typeof setInterval> | undefined
let coverDeadline = 0
const coversPending = computed(() =>
  [...myApps.value, ...goodApps.value].some(
    (app) => app.coverEnabled && coverPending(app.coverStatus),
  ),
)
watch(coversPending, (pending) => {
  clearInterval(coverTimer)
  coverTimer = undefined
  if (!pending) return
  coverDeadline = Date.now() + 120000
  coverTimer = setInterval(() => {
    if (Date.now() >= coverDeadline) {
      clearInterval(coverTimer)
      coverTimer = undefined
      return
    }
    if (document.hidden) return
    if (!myLoading.value && myApps.value.some((app) => coverPending(app.coverStatus)))
      void fetchMyApps()
    if (!goodLoading.value && goodApps.value.some((app) => coverPending(app.coverStatus)))
      void fetchGoodApps()
  }, 3000)
})
onBeforeUnmount(() => clearInterval(coverTimer))

onMounted(() => {
  void fetchMyApps()
  void fetchGoodApps()
})
</script>

<template>
  <main class="home-view">
    <section id="create-app" class="hero" aria-labelledby="creation-title">
      <div class="hero__inner">
        <div class="hero__copy">
          <div class="hero__eyebrow"><span />从提示词到可用的产品</div>
          <h1 id="creation-title" class="hero__title">把想法，<br /><span>锻造成产品。</span></h1>
          <p class="hero__subtitle">
            描述你要做的产品，AppForge 完成页面生成、预览、修改与发布。从第一版到上线，都在同一个工作台。
          </p>

          <div class="prompt-panel" :class="{ 'prompt-panel--busy': creating }">
            <div class="prompt-panel__top">
              <span>DESCRIBE YOUR PRODUCT</span>
              <span>{{ prompt.length }} / 2000</span>
            </div>
            <a-textarea
              ref="promptInput"
              v-model:value="prompt"
              class="prompt-panel__input"
              aria-label="描述你想创建的应用"
              :auto-size="{ minRows: 3, maxRows: 7 }"
              :maxlength="2000"
              :disabled="creating"
              placeholder="帮我做一个科技公司官网，深色背景、橙色强调色，包含产品、客户案例和联系表单。"
              @keydown="handlePromptKeydown"
            />
            <div class="prompt-panel__modes" role="group" aria-label="生成模式">
              <button
                v-for="mode in modeChips"
                :key="mode.value"
                type="button"
                class="mode-chip"
                :class="{ 'mode-chip--active': generationMode === mode.value }"
                :aria-pressed="generationMode === mode.value"
                :disabled="creating"
                @click="generationMode = mode.value"
              >
                <strong>{{ mode.label }}</strong><small>{{ mode.hint }}</small>
              </button>
            </div>
            <div class="prompt-panel__footer">
              <span class="prompt-panel__hint">⌘ / Ctrl + Enter</span>
              <a-button
                class="forge-cta"
                size="large"
                :loading="creating"
                :disabled="!prompt.trim()"
                aria-label="开始创建应用"
                @click="handleCreate"
              >
                {{ creating ? '正在创建' : '开始锻造' }}<ArrowUpOutlined v-if="!creating" />
              </a-button>
            </div>
          </div>

          <div class="scenario-grid" aria-label="创作场景">
            <button
              v-for="item in quickPrompts"
              :key="item.label"
              type="button"
              class="scenario-card"
              :class="{ 'scenario-card--selected': selectedScenario?.label === item.label }"
              :aria-pressed="selectedScenario?.label === item.label"
              :disabled="creating"
              @click="useQuickPrompt(item.prompt)"
            >
              <span class="scenario-card__icon"><component :is="item.icon" /></span>
              <strong>{{ item.label }}</strong>
              <ArrowRightOutlined class="scenario-card__arrow" />
            </button>
          </div>

          <FeaturedWorkStrip
            v-if="featuredPreview.length"
            :apps="featuredPreview"
            @open="openGoodApp"
          />
        </div>

        <div class="hero__visual">
          <HeroArtifact />
          <div class="hero__proof">
            <span><b>01</b> 描述需求</span>
            <i />
            <span><b>02</b> 生成第一版</span>
            <i />
            <span><b>03</b> 发布上线</span>
          </div>
        </div>
      </div>
    </section>

    <section id="my-apps" class="section section--mine" aria-labelledby="my-apps-title">
      <div class="section__head">
        <div>
          <div class="section__eyebrow">YOUR WORKSPACE</div>
          <h2 id="my-apps-title" class="section__title">
            继续你的创作<span v-if="isLoggedIn" class="section__count">{{ myTotal }}</span>
          </h2>
          <p class="section__desc">从上次停下的地方，继续实现你的想法。</p>
        </div>
        <a-button @click="focusPrompt"><PlusOutlined />创建新应用</a-button>
      </div>
      <div v-if="isLoggedIn" class="section__filters">
        <a-input-search
          v-model:value="mySearch"
          class="section__search"
          placeholder="搜索我的作品"
          aria-label="搜索我的作品"
          allow-clear
          @search="handleMySearch"
        />
        <a-input-search
          v-model:value="myTag"
          class="tag-filter"
          placeholder="按标签精确筛选"
          aria-label="筛选我的作品标签"
          allow-clear
          @search="handleMySearch"
        />
      </div>
      <div v-if="!isLoggedIn" class="login-tip">
        <span class="login-tip__icon"><AppstoreOutlined /></span>
        <div class="login-tip__copy">
          <h3>保存你的第一版</h3>
          <p>登录后保留对话、版本与发布记录，随时回来继续。</p>
        </div>
        <button type="button" class="text-cta" @click="goLogin">
          <LoginOutlined />登录并继续<span>→</span>
        </button>
      </div>
      <a-spin v-else :spinning="myLoading">
        <a-alert v-if="myError" :message="myError" type="warning" show-icon
          ><template #action
            ><a-button size="small" @click="fetchMyApps">重新加载</a-button></template
          ></a-alert
        >
        <div v-else-if="myApps.length" class="app-grid">
          <AppCard
            v-for="app in myApps"
            :key="String(app.id)"
            :app="app"
            editable
            @open="openMyApp"
            @edit="editMyApp"
          />
        </div>
        <a-empty
          v-else-if="!myLoading"
          class="empty-state"
          :description="
            mySearch || myTag ? '没有找到符合条件的作品' : '还没有作品，先从一个想法开始吧'
          "
          ><a-button v-if="!mySearch && !myTag" @click="focusPrompt"
            >创建第一个应用</a-button
          ></a-empty
        >
        <div v-else class="loading-space" />
      </a-spin>
      <a-pagination
        v-if="myTotal > myPageSize"
        class="pagination"
        :current="myPage"
        :page-size="myPageSize"
        :total="myTotal"
        :show-size-changer="false"
        @change="handleMyPageChange"
      />
    </section>

    <section id="discover" class="section section--featured" aria-labelledby="discover-title">
      <div class="section__head">
        <div>
          <div class="section__eyebrow">MADE WITH APPFORGE</div>
          <h2 id="discover-title" class="section__title">发现更多可能</h2>
          <p class="section__desc">看看其他创作者，把怎样的想法变成了现实。</p>
        </div>
        <a-input-search
          v-model:value="goodSearch"
          class="section__search"
          placeholder="搜索名称、描述或标签"
          aria-label="搜索案例"
          allow-clear
          @search="handleGoodSearch"
        />
      </div>
      <div class="discovery-filters">
        <a-radio-group v-model:value="discovery" @change="handleGoodSearch"
          ><a-radio-button value="featured">精选案例</a-radio-button
          ><a-radio-button value="public">全部公开应用</a-radio-button></a-radio-group
        >
        <a-input-search
          v-model:value="goodTag"
          class="tag-filter"
          placeholder="按标签精确筛选"
          aria-label="筛选案例标签"
          allow-clear
          @search="handleGoodSearch"
        />
      </div>
      <a-spin :spinning="goodLoading">
        <a-alert v-if="goodError" :message="goodError" type="warning" show-icon
          ><template #action
            ><a-button size="small" @click="fetchGoodApps">重新加载</a-button></template
          ></a-alert
        >
        <div v-else-if="goodApps.length" class="app-grid">
          <AppCard v-for="app in goodApps" :key="String(app.id)" :app="app" @open="openGoodApp" />
        </div>
        <a-empty
          v-else-if="!goodLoading"
          class="empty-state"
          :description="
            discovery === 'featured' ? '暂时还没有符合条件的精选案例' : '没有符合条件的公开应用'
          "
        />
        <div v-else class="loading-space" />
      </a-spin>
      <a-pagination
        v-if="goodTotal > goodPageSize"
        class="pagination"
        :current="goodPage"
        :page-size="goodPageSize"
        :total="goodTotal"
        :show-size-changer="false"
        @change="handleGoodPageChange"
      />
    </section>
  </main>
</template>

<style scoped>
.home-view {
  padding-bottom: 84px;
  overflow: hidden;
  background:
    linear-gradient(180deg, #f7f1e8 0, #f3eee5 520px, #f3eee5 100%);
}

.hero {
  position: relative;
  padding: 70px 32px 48px;
  overflow: hidden;
}

.hero::before {
  position: absolute;
  inset: 0;
  z-index: 0;
  content: '';
  background:
    radial-gradient(ellipse at 86% 16%, rgb(242 91 53 / 13%), transparent 38%),
    radial-gradient(ellipse at 4% 82%, rgb(216 239 122 / 14%), transparent 34%),
    linear-gradient(rgb(23 21 26 / 3%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(23 21 26 / 3%) 1px, transparent 1px);
  background-size:
    auto,
    auto,
    44px 44px,
    44px 44px;
  mask-image: linear-gradient(to bottom, #000 0%, #000 62%, transparent 100%);
}

.hero__inner {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(440px, 0.98fr);
  gap: 62px;
  align-items: center;
  width: 100%;
  max-width: 1360px;
  margin: 0 auto;
}

.hero__copy {
  min-width: 0;
  padding-top: 6px;
}

.hero__eyebrow {
  display: inline-flex;
  gap: 9px;
  align-items: center;
  color: var(--forge-muted);
  font-family: var(--forge-font-mono);
  font-size: 9px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.hero__eyebrow span {
  display: inline-block;
  width: 30px;
  height: 1px;
  background: var(--forge-orange);
}

.hero__title {
  max-width: 720px;
  margin: 18px 0 18px;
  color: var(--forge-ink);
  font-family: var(--forge-font-display);
  font-size: clamp(44px, 5.8vw, 76px);
  font-weight: 700;
  line-height: 1.02;
  letter-spacing: -0.035em;
}

.hero__title span {
  position: relative;
  display: inline-block;
  color: var(--forge-ink);
}

.hero__title span::after {
  position: absolute;
  right: -2px;
  bottom: 0.04em;
  left: -2px;
  z-index: -1;
  height: 0.3em;
  content: '';
  background: var(--forge-orange);
  opacity: 0.92;
  transform: skewX(-4deg);
}

.hero__subtitle {
  max-width: 585px;
  margin: 0 0 28px;
  color: var(--forge-muted);
  font-size: 14.5px;
  line-height: 1.85;
}

.prompt-panel {
  max-width: 720px;
  padding: 15px 16px 13px;
  background: var(--forge-card);
  border: 1px solid #bdb1a1;
  border-radius: 10px;
  box-shadow:
    0 1px 0 rgb(255 255 255 / 80%) inset,
    0 18px 50px rgb(80 59 35 / 9%);
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.prompt-panel:focus-within {
  border-color: var(--forge-ink);
  box-shadow:
    0 0 0 3px rgb(242 91 53 / 12%),
    0 18px 50px rgb(80 59 35 / 10%);
}

.prompt-panel__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2px 8px;
  color: var(--forge-muted-2);
  font-family: var(--forge-font-mono);
  font-size: 8px;
  letter-spacing: 0.1em;
}

.prompt-panel__input {
  padding: 2px 2px 8px;
  color: var(--forge-ink);
  font-size: 16px;
  line-height: 1.8;
  resize: none;
  background: transparent;
  border: 0;
  box-shadow: none;
}

.prompt-panel__input:hover,
.prompt-panel__input:focus {
  border: 0;
  box-shadow: none;
}

.prompt-panel__input::placeholder {
  color: #9b9286;
}

.prompt-panel__modes {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 10px 0 9px;
  border-top: 1px dashed #d9cebe;
}

.mode-chip {
  display: inline-flex;
  gap: 6px;
  align-items: baseline;
  padding: 6px 9px;
  color: var(--forge-muted);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 4px;
  transition:
    color 0.18s,
    background-color 0.18s,
    border-color 0.18s;
}

.mode-chip strong {
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.mode-chip small {
  color: var(--forge-muted-2);
  font-size: 9px;
}

.mode-chip:hover {
  color: var(--forge-ink);
  background: #f5efe6;
}

.mode-chip--active {
  color: var(--forge-ink);
  background: #f4e9db;
  border-color: #d8c8b2;
}

.mode-chip--active small {
  color: var(--forge-orange-deep);
}

.prompt-panel__footer {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.prompt-panel__hint {
  color: var(--forge-muted-2);
  font-family: var(--forge-font-mono);
  font-size: 8px;
}

.prompt-panel__footer :deep(.forge-cta) {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  height: 40px;
  padding: 0 17px;
  color: var(--forge-ink);
  font-size: 12px;
  font-weight: 700;
  background: var(--forge-orange);
  border: 1px solid #d94a27;
  border-radius: 5px;
  box-shadow: inset 0 -2px 0 rgb(23 21 26 / 15%);
}

.prompt-panel__footer :deep(.forge-cta:hover) {
  color: var(--forge-ink) !important;
  background: #ff6d48 !important;
  border-color: #ff6d48 !important;
}

.prompt-panel__footer :deep(.forge-cta:disabled) {
  color: #8f877b;
  background: #e7ded1;
  border-color: #d8ccbb;
  box-shadow: none;
}

.scenario-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-width: 720px;
  margin-top: 14px;
}

.scenario-card {
  display: inline-flex;
  gap: 7px;
  align-items: center;
  padding: 7px 10px;
  color: var(--forge-muted);
  cursor: pointer;
  background: rgb(255 253 249 / 54%);
  border: 1px solid #d9cebe;
  border-radius: 4px;
  transition:
    color 0.18s,
    background-color 0.18s,
    border-color 0.18s;
}

.scenario-card:hover,
.scenario-card--selected {
  color: var(--forge-ink);
  background: var(--forge-card);
  border-color: var(--forge-ink);
}

.scenario-card__icon {
  display: inline-flex;
  color: var(--forge-orange-deep);
  font-size: 12px;
}

.scenario-card strong {
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
}

.scenario-card__arrow {
  color: var(--forge-muted-2);
  font-size: 8px;
}

.hero__visual {
  min-width: 0;
}

.hero__proof {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: center;
  margin-top: 4px;
  color: var(--forge-muted-2);
  font-family: var(--forge-font-mono);
  font-size: 7px;
  letter-spacing: 0.06em;
}

.hero__proof b {
  margin-right: 4px;
  color: var(--forge-orange-deep);
  font-weight: 600;
}

.hero__proof i {
  width: 18px;
  height: 1px;
  background: #cfc2b1;
}

.section {
  position: relative;
  max-width: 1360px;
  margin: 0 auto;
  padding: 44px 32px 14px;
  scroll-margin-top: 90px;
}

.section--mine {
  padding-top: 24px;
}

.section--featured {
  margin-top: 34px;
  padding-top: 44px;
  border-top: 1px solid #d8ccbb;
}

.section__head {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 22px;
}

.section__eyebrow {
  margin-bottom: 8px;
  color: var(--forge-orange-deep);
  font-family: var(--forge-font-mono);
  font-size: 8px;
  font-weight: 600;
  letter-spacing: 0.13em;
}

.section__title {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 0;
  color: var(--forge-ink);
  font-family: var(--forge-font-display);
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.025em;
}

.section__count {
  padding: 2px 7px;
  color: var(--forge-muted);
  font-family: var(--forge-font-mono);
  font-size: 9px;
  font-weight: 400;
  background: #e8ddcd;
  border-radius: 3px;
}

.section__desc {
  margin: 7px 0 0;
  color: var(--forge-muted);
  font-size: 12px;
}

.section__filters,
.discovery-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 20px;
}

.section__search {
  width: 260px;
}

.tag-filter {
  width: 220px;
}

.discovery-filters {
  justify-content: space-between;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}


.empty-state {
  padding: 44px 20px;
  background: var(--forge-card);
  border: 1px dashed #cfc2b1;
  border-radius: 8px;
}

.loading-space {
  min-height: 180px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 30px;
}

.login-tip {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  padding: 15px 18px;
  background: rgb(255 253 249 / 62%);
  border: 1px solid #d8ccbb;
  border-radius: 8px;
}

.login-tip__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 38px;
  height: 38px;
  color: var(--forge-ink);
  font-size: 17px;
  background: var(--forge-lime);
  border-radius: 8px;
}

.login-tip__copy h3 {
  margin: 0;
  color: var(--forge-ink);
  font-size: 13px;
  font-weight: 600;
}

.login-tip__copy p {
  margin: 3px 0 0;
  color: var(--forge-muted);
  font-size: 11px;
}

.text-cta {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 8px 0;
  margin-left: auto;
  color: var(--forge-orange-deep);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-bottom: 1px solid currentcolor;
}

.text-cta span {
  font-size: 12px;
}

.text-cta:hover {
  color: var(--forge-ink);
}

@media (max-width: 1120px) {
  .hero__inner {
    grid-template-columns: minmax(0, 1fr) minmax(360px, 0.84fr);
    gap: 34px;
  }

  .hero {
    padding-right: 24px;
    padding-left: 24px;
  }
}

@media (max-width: 900px) {
  .hero__inner {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .hero {
    padding-top: 48px;
  }

  .hero__visual {
    max-width: 660px;
    margin: 0 auto;
  }

  .hero__proof {
    margin-bottom: 6px;
  }

  .app-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .hero {
    padding: 38px 16px 30px;
  }

  .hero__title {
    margin-top: 15px;
    font-size: clamp(38px, 12vw, 50px);
    letter-spacing: -0.04em;
  }

  .hero__subtitle {
    margin-bottom: 20px;
    font-size: 13px;
  }

  .prompt-panel {
    padding: 12px;
  }

  .prompt-panel__input {
    font-size: 14px;
  }

  .mode-chip {
    flex: 1;
    justify-content: center;
    padding: 6px 5px;
  }

  .mode-chip small {
    display: none;
  }

  .prompt-panel__hint {
    display: none;
  }

  .prompt-panel__footer {
    justify-content: flex-end;
  }

  .prompt-panel__footer :deep(.forge-cta) {
    width: 100%;
    justify-content: center;
  }

  .scenario-grid {
    gap: 4px;
  }

  .scenario-card {
    flex: 1;
    justify-content: center;
    padding: 7px 5px;
  }

  .scenario-card__arrow {
    display: none;
  }

  .hero__proof {
    gap: 6px;
    justify-content: flex-start;
    font-size: 6px;
  }

  .hero__proof i {
    width: 10px;
  }

  .section {
    padding: 32px 16px 10px;
  }

  .section--mine {
    padding-top: 12px;
  }

  .section__title {
    font-size: 25px;
  }

  .section__search,
  .section__filters .tag-filter,
  .discovery-filters .tag-filter {
    width: 100%;
  }

  .app-grid {
    grid-template-columns: 1fr;
    gap: 14px;
  }


  .login-tip {
    padding: 13px;
  }

  .text-cta {
    margin-left: 52px;
  }
}
</style>