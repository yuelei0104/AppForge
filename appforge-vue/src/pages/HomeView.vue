<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ArrowUpOutlined, LoginOutlined } from '@ant-design/icons-vue'
import AppCard from '@/components/AppCard.vue'
import { addApp, listGoodAppVoByPage, listMyAppVoByPage } from '@/api/appController'
import { API_BASE_URL } from '@/request'
import { useLoginUserStore } from '@/stores/loginUser'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const quickPrompts = ['波普风电商页面', '企业网站', '电商运营后台', '暗黑话题社区']

const prompt = ref('')
const creating = ref(false)

const myApps = ref<API.AppVO[]>([])
const myTotal = ref(0)
const myPage = ref(1)
const myPageSize = 6
const mySearch = ref('')
const myLoading = ref(false)

const goodApps = ref<API.AppVO[]>([])
const goodTotal = ref(0)
const goodPage = ref(1)
const goodPageSize = 6
const goodSearch = ref('')
const goodLoading = ref(false)

const isLoggedIn = computed(() => loginUserStore.isLoggedIn)

function useQuickPrompt(text: string) {
  prompt.value = text
}

function goLogin() {
  void router.push({ path: '/user/login', query: { redirect: '/' } })
}

async function handleCreate() {
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
    const res = await addApp({ initPrompt })
    if (res.data.code === 0 && res.data.data != null) {
      message.success('应用创建成功，正在为你生成')
      await router.push(`/app/chat/${String(res.data.data)}`)
    } else {
      message.error(res.data.message || '创建应用失败')
    }
  } catch {
    message.error('创建应用失败，请稍后重试')
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
    const res = await listMyAppVoByPage({
      pageNum: myPage.value,
      pageSize: myPageSize,
      appName: mySearch.value.trim() || undefined,
    })
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
    const res = await listGoodAppVoByPage({
      pageNum: goodPage.value,
      pageSize: goodPageSize,
      appName: goodSearch.value.trim() || undefined,
    })
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

function openMyApp(app: API.AppVO) {
  void router.push(`/app/chat/${String(app.id)}`)
}

function editMyApp(app: API.AppVO) {
  void router.push(`/app/edit/${String(app.id)}`)
}

function openGoodApp(app: API.AppVO) {
  if (
    isLoggedIn.value &&
    app.userId != null &&
    String(app.userId) === String(loginUserStore.loginUser.id)
  ) {
    void router.push(`/app/chat/${String(app.id)}`)
    return
  }
  const url = app.deployKey
    ? `${API_BASE_URL}/deploy/${app.deployKey}/`
    : `${API_BASE_URL}/static/${String(app.id)}/`
  window.open(url, '_blank', 'noopener,noreferrer')
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
        <div class="hero__eyebrow">NOCODE AI</div>
        <h1 class="hero__title">一句话，<span>生成所想</span></h1>
        <p class="hero__subtitle">与 AI 对话，轻松创建应用和网站</p>

        <div class="prompt-panel">
          <a-textarea
            v-model:value="prompt"
            class="prompt-panel__input"
            :auto-size="{ minRows: 4, maxRows: 8 }"
            :maxlength="2000"
            placeholder="使用 NoCode 创建一个高效的小工具，帮我计算……"
            @keydown="handlePromptKeydown"
          />
          <div class="prompt-panel__footer">
            <span class="prompt-panel__hint">描述越具体，生成效果越好</span>
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
            :key="item"
            type="button"
            class="quick-prompts__item"
            @click="useQuickPrompt(item)"
          >
            {{ item }}
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
          placeholder="搜索应用名称"
          allow-clear
          @search="handleMySearch"
        />
      </div>

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
          <h2 class="section__title">精选案例</h2>
          <p class="section__desc">看看别人用一句话创造了什么</p>
        </div>
        <a-input-search
          v-model:value="goodSearch"
          class="section__search"
          placeholder="搜索精选应用"
          allow-clear
          @search="handleGoodSearch"
        />
      </div>

      <a-spin :spinning="goodLoading">
        <div v-if="goodApps.length" class="app-grid">
          <AppCard
            v-for="app in goodApps"
            :key="String(app.id)"
            :app="app"
            show-author
            @open="openGoodApp"
          />
        </div>
        <a-empty v-else class="empty-state" description="暂时还没有精选应用" />
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
.home-view {
  margin: -24px;
  background: #f5f7fa;
}

.hero {
  position: relative;
  overflow: hidden;
  padding: 72px 24px 86px;
  background:
    radial-gradient(circle at 18% 12%, rgba(255, 255, 255, 0.9), transparent 34%),
    radial-gradient(circle at 82% 20%, rgba(134, 239, 226, 0.7), transparent 32%),
    linear-gradient(120deg, #f7fbf7 0%, #d9f6ef 42%, #a9e6ec 100%);
}

.hero::after {
  position: absolute;
  right: -120px;
  bottom: -180px;
  width: 480px;
  height: 480px;
  content: '';
  background: rgba(255, 255, 255, 0.28);
  border-radius: 50%;
  filter: blur(8px);
}

.hero__content {
  position: relative;
  z-index: 1;
  max-width: 900px;
  margin: 0 auto;
  text-align: center;
}

.hero__eyebrow {
  margin-bottom: 14px;
  font-size: 12px;
  font-weight: 700;
  color: #0f766e;
  letter-spacing: 3px;
}

.hero__title {
  margin: 0;
  font-size: 46px;
  font-weight: 800;
  line-height: 1.2;
  color: #102a2a;
  letter-spacing: 2px;
}

.hero__title span {
  color: #0d9488;
}

.hero__subtitle {
  margin: 16px 0 30px;
  font-size: 17px;
  color: #52706f;
}

.prompt-panel {
  padding: 16px 18px 12px;
  text-align: left;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 20px;
  box-shadow: 0 18px 48px rgba(15, 118, 110, 0.12);
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
  color: #5f7776;
}

.quick-prompts__item {
  padding: 7px 14px;
  font-size: 13px;
  color: #405b5a;
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
  padding: 46px 24px 10px;
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
  background: linear-gradient(120deg, #fff 0%, #f0fbfa 100%);
  border: 1px solid #d9f0ee;
  border-radius: 16px;
}

.login-tip__title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #102a2a;
}

.login-tip__desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: #7c8b99;
}

@media (max-width: 991px) {
  .hero {
    padding: 56px 20px 70px;
  }

  .hero__title {
    font-size: 38px;
  }

  .app-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .home-view {
    margin: -16px;
  }

  .hero {
    padding: 44px 16px 58px;
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
