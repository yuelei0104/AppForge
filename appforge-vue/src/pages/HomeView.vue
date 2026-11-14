<script setup lang="ts">
import { createAppWithMode, listPublicApps, type AppInfo, type AppFilters } from '@/api/workspace'
import { CodeGenTypeEnum } from '@/config/codeGenType'
import { generationModeOptions, type GenerationMode } from '@/utils/generationMode'
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
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import AppCard from '@/components/AppCard.vue'
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
      <div class="hero__content">
        <div class="hero__eyebrow"><ThunderboltOutlined /> 从一个想法开始</div>
        <h1 id="creation-title" class="hero__title">把想法，变成<span>可用的应用。</span></h1>
        <p class="hero__subtitle">描述你的需求，让 AI 帮你构建。预览、修改、发布，都在这里。</p>
        <div class="prompt-panel" :class="{ 'prompt-panel--busy': creating }">
          <a-textarea
            ref="promptInput"
            v-model:value="prompt"
            class="prompt-panel__input"
            aria-label="描述你想创建的应用"
            :auto-size="{ minRows: 3, maxRows: 7 }"
            :maxlength="2000"
            :disabled="creating"
            placeholder="我想创建一个个人摄影网站，支持作品分类、图片展示和在线联系……"
            @keydown="handlePromptKeydown"
          />
          <div class="prompt-panel__footer">
            <div class="prompt-panel__mode">
              <span v-if="selectedScenario" class="scenario-label"
                ><component :is="selectedScenario.icon" />{{ selectedScenario.label }}</span
              >
              <a-select
                v-model:value="generationMode"
                aria-label="生成模式"
                :disabled="creating"
                :options="generationModeOptions"
              />
            </div>
            <div class="prompt-panel__submit">
              <span class="prompt-panel__hint">{{
                prompt.length ? `${prompt.length} / 2000` : 'Ctrl / ⌘ + Enter'
              }}</span>
              <a-button
                type="primary"
                size="large"
                :loading="creating"
                :disabled="!prompt.trim()"
                aria-label="开始创建应用"
                @click="handleCreate"
              >
                {{ creating ? '正在创建' : '开始创建' }}<ArrowUpOutlined v-if="!creating" />
              </a-button>
            </div>
          </div>
        </div>
        <p class="hero__note">
          {{
            selectedScenario
              ? '已填入示例需求，你可以修改后再开始创建'
              : '还没有具体想法？从下面的场景获取灵感'
          }}
        </p>
        <div class="scenario-grid" aria-label="创作场景">
          <button
            v-for="item in quickPrompts"
            :key="item.label"
            type="button"
            class="scenario-card"
            :class="[
              `scenario-card--${item.tone}`,
              { 'scenario-card--selected': selectedScenario?.label === item.label },
            ]"
            :aria-pressed="selectedScenario?.label === item.label"
            :disabled="creating"
            @click="useQuickPrompt(item.prompt)"
          >
            <span class="scenario-card__icon"><component :is="item.icon" /></span>
            <span class="scenario-card__copy"
              ><strong>{{ item.label }}</strong
              ><small>{{ item.description }}</small></span
            >
            <ArrowRightOutlined class="scenario-card__arrow" />
          </button>
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
        <div>
          <h3>你的下一个作品，从这里诞生</h3>
          <p>登录后保存创作，随时回来继续编辑。</p>
        </div>
        <a-button type="primary" @click="goLogin"><LoginOutlined />登录开启创作</a-button>
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
  padding-bottom: 64px;
  background: #fafbfd;
}
.hero {
  position: relative;
  padding: 64px 24px 46px;
  overflow: hidden;
  background:
    radial-gradient(ellipse at 20% 20%, #eeedff 0, transparent 50%),
    radial-gradient(ellipse at 85% 70%, #e9f6f8 0, transparent 45%), linear-gradient(#fff, #fafbfd);
}
.hero__content {
  position: relative;
  max-width: 880px;
  margin: 0 auto;
  text-align: center;
}
.hero__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #7161ae;
  font-size: 12px;
  padding: 6px 12px;
  background: #ffffffb3;
  border: 1px solid #e5e0fa;
  border-radius: 30px;
}
.hero__title {
  margin: 22px 0 14px;
  color: #242336;
  font-size: clamp(30px, 3.4vw, 48px);
  font-weight: 750;
  line-height: 1.4;
  letter-spacing: -1.5px;
}
.hero__title span {
  color: var(--forge-accent);
}
.hero__subtitle {
  margin: 0 0 32px;
  color: var(--forge-muted);
  font-size: 15px;
  line-height: 1.8;
}
.prompt-panel {
  padding: 22px 22px 16px;
  text-align: left;
  background: #fff;
  border: 1px solid #e2dff1;
  border-radius: 20px;
  box-shadow: 0 12px 40px #4d418912;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}
.prompt-panel:focus-within {
  border-color: #a398e2;
  box-shadow:
    0 12px 40px #4d418918,
    0 0 0 3px #6654d90b;
}
.prompt-panel__input {
  padding: 0;
  font-size: 16px;
  line-height: 1.8;
  background: transparent;
  border: 0;
  box-shadow: none;
  resize: none;
}
.prompt-panel__input:hover,
.prompt-panel__input:focus {
  border: 0;
  box-shadow: none;
}
.prompt-panel__input::placeholder {
  color: #9a99a9;
}
.prompt-panel__footer,
.prompt-panel__mode,
.prompt-panel__submit {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.prompt-panel__footer {
  justify-content: space-between;
  gap: 12px;
  margin-top: 20px;
}
.prompt-panel__mode .ant-select {
  min-width: 150px;
}
.prompt-panel__hint {
  color: #9291a3;
  font-size: 11px;
}
.prompt-panel__submit .ant-btn {
  font-size: 13px;
  height: 38px;
  border-radius: 10px;
}
.scenario-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 8px;
  background: var(--forge-accent-soft);
  color: var(--forge-accent);
  font-size: 12px;
  border-radius: 7px;
}
.hero__note {
  margin: 16px 0 22px;
  color: #737083;
  font-size: 12px;
}
.scenario-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.scenario-card {
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
  padding: 16px 12px;
  background: #ffffffb3;
  border: 1px solid #e9e8f0;
  border-radius: 12px;
  cursor: pointer;
  transition:
    transform 0.2s,
    border-color 0.2s;
}
.scenario-card:hover {
  transform: translateY(-3px);
  border-color: #c5bce8;
}
.scenario-card--selected {
  border-color: var(--forge-accent);
  background: #f5f2ff;
}
.scenario-card__icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  color: #8064cc;
  background: #f0eaff;
  font-size: 18px;
}
.scenario-card--blue .scenario-card__icon {
  color: #4e83c3;
  background: #eaf2fd;
}
.scenario-card--amber .scenario-card__icon {
  color: #ba8a4f;
  background: #fbf1df;
}
.scenario-card--mint .scenario-card__icon {
  color: #41978f;
  background: #e6f5ef;
}
.scenario-card__copy {
  min-width: 0;
}
.scenario-card__copy strong {
  display: block;
  color: #3c394f;
  font-size: 13px;
  font-weight: 600;
}
.scenario-card__copy small {
  display: block;
  margin-top: 5px;
  color: #787284;
  font-size: 10px;
  white-space: nowrap;
}
.scenario-card__arrow {
  margin-left: auto;
  color: #b4afc4;
  font-size: 10px;
}
.section {
  max-width: 1280px;
  margin: 0 auto;
  padding: 32px 32px 12px;
  scroll-margin-top: 80px;
}
.section--featured {
  margin-top: 30px;
  padding-top: 36px;
  border-top: 1px solid var(--forge-border);
}
.section__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}
.section__eyebrow {
  color: #81768f;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 1.7px;
  margin-bottom: 8px;
}
.section__title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  color: var(--forge-text);
  font-size: 23px;
  font-weight: 650;
}
.section__count {
  font-size: 11px;
  padding: 2px 7px;
  background: #eceaf4;
  color: #7a718f;
  border-radius: 6px;
}
.section__desc {
  margin: 7px 0 0;
  color: #787284;
  font-size: 12px;
}
.section__filters,
.discovery-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}
.section__search {
  width: 256px;
}
.tag-filter {
  width: 214px;
}
.discovery-filters {
  justify-content: space-between;
}
.app-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 22px;
}
.empty-state {
  padding: 40px 20px;
  border: 1px dashed #e3dfed;
  border-radius: 14px;
  background: #fff;
}
.loading-space {
  min-height: 180px;
}
.pagination {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}
.login-tip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 18px;
  padding: 28px;
  background: #fff;
  border: 1px solid var(--forge-border);
  border-radius: 14px;
}
.login-tip__icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  color: var(--forge-accent);
  background: var(--forge-accent-soft);
  border-radius: 14px;
  font-size: 23px;
}
.login-tip h3 {
  margin: 0;
  color: #454054;
  font-size: 15px;
  font-weight: 600;
}
.login-tip p {
  margin: 6px 0 0;
  color: #9690a3;
  font-size: 12px;
}
.login-tip .ant-btn {
  margin-left: auto;
}
@media (max-width: 1000px) {
  .scenario-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .app-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .hero {
    padding: 38px 16px 26px;
  }
  .hero__title {
    font-size: 30px;
    letter-spacing: -1px;
  }
  .hero__title span {
    display: block;
  }
  .hero__subtitle {
    font-size: 13px;
    margin-bottom: 22px;
  }
  .prompt-panel {
    padding: 16px;
    border-radius: 16px;
  }
  .prompt-panel__input {
    font-size: 14px;
  }
  .prompt-panel__hint {
    display: none;
  }
  .prompt-panel__mode {
    gap: 6px;
  }
  .prompt-panel__mode .ant-select {
    min-width: 132px;
  }
  .scenario-label {
    display: none;
  }
  .scenario-card {
    padding: 12px 10px;
    gap: 8px;
  }
  .scenario-card__arrow {
    display: none;
  }
  .scenario-card__copy small {
    font-size: 9px;
  }
  .scenario-card__icon {
    width: 30px;
    height: 30px;
    font-size: 15px;
  }
  .section {
    padding: 28px 16px 8px;
  }
  .section__title {
    font-size: 21px;
  }
  .section__search {
    width: 100%;
  }
  .section__filters .tag-filter,
  .discovery-filters .tag-filter {
    width: 100%;
  }
  .app-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .login-tip {
    padding: 20px;
    gap: 12px;
  }
  .login-tip .ant-btn {
    margin-left: 60px;
  }
}
</style>
