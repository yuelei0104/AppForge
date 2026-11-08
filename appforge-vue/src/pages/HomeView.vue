<script setup lang="ts">
import { createAppWithMode, listPublicApps, type AppInfo, type AppFilters } from '@/api/workspace'
import { generationModeOptions, type GenerationMode } from '@/utils/generationMode'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ArrowUpOutlined, LoginOutlined } from '@ant-design/icons-vue'
import AppCard from '@/components/AppCard.vue'
import { listGoodAppVoByPage, listMyAppVoByPage } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const quickPrompts = [
  {
    label: '个人博客',
    prompt:
      '帮我创建个人博客网站，采用简洁的浅色设计，首页展示个人简介、头像和最新文章。提供文章分类、标签筛选和关键词搜索，文章详情支持目录与代码展示。底部放置邮箱及社交链接，适配手机和电脑，并填充几篇示例文章。',
  },
  {
    label: '企业官网',
    prompt:
      '帮我创建一家科技公司的官方网站，使用蓝紫色科技风格。包含品牌介绍、产品服务、客户案例、团队成员和联系表单。首页突出核心产品及咨询入口，导航点击后滚动到对应区块，配上真实感的示例文案，并适配移动端。',
  },
  {
    label: '活动报名',
    prompt:
      '帮我创建一场周末城市摄影活动的报名网站，使用温暖清新的配色。展示活动时间地点、行程安排、嘉宾介绍、费用和常见问题。报名表包含姓名、联系方式与人数，提交前校验必填项并显示报名成功提示，手机端也能方便填写。',
  },
  {
    label: '数据看板',
    prompt:
      '帮我创建一个电商运营数据看板，包含销售额、订单量、客单价与转化率指标，展示近七天趋势、商品排行和订单列表。支持日期筛选、订单状态筛选及关键词搜索，使用清晰的卡片布局和示例数据，适配桌面和手机浏览。',
  },
]

const prompt = ref('')
const generationMode = ref<GenerationMode>('vue_project')
const creating = ref(false)

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
      message.error(res.data.message || '获取我的应用失败')
    }
  } catch {
    message.error('获取我的应用失败，请稍后重试')
  } finally {
    myLoading.value = false
  }
}

async function fetchGoodApps() {
  goodLoading.value = true
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
      message.error(res.data.message || '获取精选应用失败')
    }
  } catch {
    message.error('获取精选应用失败，请稍后重试')
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

onMounted(() => {
  void fetchMyApps()
  void fetchGoodApps()
})
</script>

<template>
  <div class="home-view">
    <section class="hero">
      <div class="hero__content">
        <div class="hero__eyebrow">APPFORGE · CREATE WITH AI</div>
        <h1 class="hero__title">AI 应用生成平台</h1>
        <p class="hero__subtitle">一句话轻松创建网站应用</p>

        <div class="prompt-panel">
          <a-textarea
            v-model:value="prompt"
            class="prompt-panel__input"
            :auto-size="{ minRows: 4, maxRows: 8 }"
            :maxlength="2000"
            placeholder="帮我创建个人博客网站"
            @keydown="handlePromptKeydown"
          />
          <div class="prompt-panel__footer">
            <div class="prompt-panel__mode">
              <a-select
                v-model:value="generationMode"
                aria-label="生成模式"
                :disabled="creating"
                :options="generationModeOptions"
              />
              <span class="prompt-panel__hint">描述越具体，生成效果越好</span>
            </div>
            <a-button
              type="primary"
              shape="circle"
              size="large"
              :loading="creating"
              aria-label="开始创建应用"
              @click="handleCreate"
            >
              <template #icon>
                <ArrowUpOutlined />
              </template>
            </a-button>
          </div>
        </div>

        <div class="quick-prompts">
          <span class="quick-prompts__label">试试：</span>
          <button
            v-for="item in quickPrompts"
            :key="item.label"
            type="button"
            class="quick-prompts__item"
            @click="useQuickPrompt(item.prompt)"
          >
            {{ item.label }}
          </button>
        </div>
      </div>
    </section>

    <section class="section">
      <div class="section__head">
        <div>
          <h2 class="section__title">我的作品</h2>
          <p class="section__desc">你创建的每一个应用，都在这里</p>
        </div>
        <a-input-search
          v-if="isLoggedIn"
          v-model:value="mySearch"
          class="section__search"
          placeholder="搜索名称、描述或标签"
          allow-clear
          @search="handleMySearch"
        />
      </div>

      <a-input-search
        v-if="isLoggedIn"
        v-model:value="myTag"
        class="tag-filter"
        placeholder="按标签精确筛选"
        allow-clear
        @search="handleMySearch"
      />
      <div v-if="!isLoggedIn" class="login-tip">
        <div>
          <h3 class="login-tip__title">登录后查看你的作品</h3>
          <p class="login-tip__desc">用一句话创建属于自己的应用，并随时继续修改。</p>
        </div>
        <a-button type="primary" @click="goLogin">
          <template #icon>
            <LoginOutlined />
          </template>
          去登录
        </a-button>
      </div>

      <a-spin v-else :spinning="myLoading">
        <div v-if="myApps.length" class="app-grid">
          <AppCard
            v-for="app in myApps"
            :key="String(app.id)"
            :app="app"
            editable
            @open="openMyApp"
            @edit="editMyApp"
          />
        </div>
        <a-empty v-else class="empty-state" description="还没有应用，先写一句提示词吧" />
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

    <section class="section section--featured">
      <div class="section__head">
        <div>
          <h2 class="section__title">{{ discovery === 'featured' ? '精选案例' : '公开应用' }}</h2>
          <p class="section__desc">看看别人用一句话创造了什么</p>
        </div>
        <a-input-search
          v-model:value="goodSearch"
          class="section__search"
          placeholder="搜索名称、描述或标签"
          allow-clear
          @search="handleGoodSearch"
        />
      </div>

      <div class="discovery-filters">
        <a-radio-group v-model:value="discovery" @change="handleGoodSearch">
          <a-radio-button value="featured">精选案例</a-radio-button>
          <a-radio-button value="public">全部公开应用</a-radio-button>
        </a-radio-group>
        <a-input-search
          v-model:value="goodTag"
          class="tag-filter"
          placeholder="按标签精确筛选"
          allow-clear
          @search="handleGoodSearch"
        />
      </div>
      <a-spin :spinning="goodLoading">
        <div v-if="goodApps.length" class="app-grid">
          <AppCard v-for="app in goodApps" :key="String(app.id)" :app="app" @open="openGoodApp" />
        </div>
        <a-empty
          v-else
          class="empty-state"
          :description="discovery === 'featured' ? '暂时还没有精选应用' : '没有符合条件的公开应用'"
        />
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
  </div>
</template>

<style scoped>
.prompt-panel__mode {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}
.prompt-panel__mode .ant-select {
  min-width: 146px;
}
.home-view {
  min-height: calc(100vh - 64px);
  padding-bottom: 32px;
  background:
    radial-gradient(ellipse at 10% 0%, rgba(170, 192, 255, 0.36), transparent 42%),
    radial-gradient(ellipse at 92% 18%, rgba(176, 160, 248, 0.28), transparent 42%),
    linear-gradient(155deg, #f0f5ff 0%, #f5f3ff 42%, #f8fbff 100%);
}

.hero {
  padding: 68px 24px 36px;
}

.hero__content {
  position: relative;
  z-index: 1;
  max-width: 820px;
  margin: 0 auto;
  text-align: center;
}

.hero__eyebrow {
  margin-bottom: 14px;
  font-size: 12px;
  font-weight: 700;
  color: #7571ab;
  letter-spacing: 3px;
}

.hero__title {
  margin: 0;
  font-size: 46px;
  font-weight: 800;
  line-height: 1.2;
  color: #242943;
  letter-spacing: 1px;
}

.hero__subtitle {
  margin: 16px 0 30px;
  font-size: 17px;
  color: #727790;
}

.prompt-panel {
  padding: 16px 18px 12px;
  text-align: left;
  background: rgba(255, 255, 255, 0.92);
  border: none;
  border-radius: 20px;
  box-shadow:
    0 16px 48px rgba(85, 78, 163, 0.1),
    0 0 0 1px rgba(145, 135, 200, 0.08);
  backdrop-filter: blur(12px);
}

.prompt-panel__input {
  padding: 0;
  font-size: 15px;
  background: transparent;
  border: none;
  box-shadow: none;
  resize: none;
}

.prompt-panel__input:hover,
.prompt-panel__input:focus {
  border: none;
  box-shadow: none;
}

.prompt-panel__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.prompt-panel__hint {
  font-size: 12px;
  color: #98a2b3;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: center;
  margin-top: 22px;
}

.quick-prompts__label {
  font-size: 13px;
  color: #85849d;
}

.quick-prompts__item {
  padding: 7px 14px;
  font-size: 13px;
  color: #5c5a78;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 999px;
  transition:
    background 0.2s,
    transform 0.2s;
}

.quick-prompts__item:hover {
  background: #fff;
  transform: translateY(-1px);
}

.section {
  max-width: 1240px;
  padding: 36px 24px 10px;
  margin: 0 auto;
}

.section--featured {
  padding-bottom: 56px;
}

.section__head {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 22px;
}

.section__title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: #101828;
}

.section__desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: #98a2b3;
}

.section__search {
  width: 250px;
}

.tag-filter {
  max-width: 260px;
  margin-bottom: 16px;
}
.discovery-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 6px;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}

.empty-state {
  padding: 48px 0;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}

.login-tip {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 26px 28px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid #e6e4f5;
  border-radius: 16px;
}

.login-tip__title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #242943;
}

.login-tip__desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: #7c8b99;
}

@media (max-width: 991px) {
  .hero {
    padding: 56px 20px 30px;
  }

  .hero__title {
    font-size: 38px;
  }

  .app-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .hero {
    padding: 44px 16px 24px;
  }

  .hero__title {
    font-size: 30px;
  }

  .hero__subtitle {
    font-size: 15px;
  }

  .prompt-panel {
    padding: 14px 14px 10px;
    border-radius: 16px;
  }

  .section {
    padding: 34px 16px 8px;
  }

  .section__title {
    font-size: 22px;
  }

  .section__search {
    width: 100%;
  }

  .app-grid {
    grid-template-columns: 1fr;
  }
}
</style>
