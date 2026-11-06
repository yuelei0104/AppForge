<script setup lang="ts">
import { RouterView, useRoute } from 'vue-router'
import GlobalFooter from '@/components/GlobalFooter.vue'
import GlobalHeader from '@/components/GlobalHeader.vue'
import { menuItems } from '@/config/menu'

const route = useRoute()
</script>

<template>
  <a-layout class="basic-layout">
    <GlobalHeader :items="menuItems" />

    <a-layout-content class="basic-layout__content">
      <div
        class="basic-layout__container"
        :class="{ 'basic-layout__container--home': route.path === '/' }"
      >
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

.basic-layout__container--home {
  max-width: none;
  padding: 0;
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

  .basic-layout__container--home {
    padding: 0;
  }
}
</style>
