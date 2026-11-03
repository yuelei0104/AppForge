<script setup lang="ts">
import { message } from 'ant-design-vue'
import { RouterView } from 'vue-router'
import GlobalFooter from '@/components/GlobalFooter.vue'
import GlobalHeader from '@/components/GlobalHeader.vue'
import { menuItems } from '@/config/menu'

function handleLogin() {
  message.info('登录功能开发中，敬请期待')
}
</script>

<template>
  <a-layout class="basic-layout">
    <GlobalHeader :items="menuItems" @login="handleLogin" />

    <a-layout-content class="basic-layout__content">
      <div class="basic-layout__container">
        <RouterView v-slot="{ Component, route }">
          <transition name="basic-layout-fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </RouterView>
      </div>
    </a-layout-content>

    <GlobalFooter />
  </a-layout>
</template>

<style scoped>
.basic-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f5f5f5;
}

.basic-layout__content {
  flex: 1;
  width: 100%;
}

.basic-layout__container {
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px;
}

.basic-layout-fade-enter-active,
.basic-layout-fade-leave-active {
  transition: opacity 0.2s ease;
}

.basic-layout-fade-enter-from,
.basic-layout-fade-leave-to {
  opacity: 0;
}

@media (max-width: 767px) {
  .basic-layout__container {
    padding: 16px;
  }
}
</style>
