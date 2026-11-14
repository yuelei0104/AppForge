<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import GlobalFooter from '@/components/GlobalFooter.vue'
import GlobalHeader from '@/components/GlobalHeader.vue'
import { menuItems } from '@/config/menu'

const route = useRoute()
const isWorkspace = computed(() => route.name === 'appChat')
</script>

<template>
  <a-layout class="basic-layout" :class="{ 'basic-layout--workspace': isWorkspace }">
    <GlobalHeader v-if="!isWorkspace" :items="menuItems" />

    <a-layout-content class="basic-layout__content">
      <div
        class="basic-layout__container"
        :class="{
          'basic-layout__container--home': route.path === '/',
          'basic-layout__container--workspace': isWorkspace,
        }"
      >
        <RouterView v-slot="{ Component, route }">
          <transition name="basic-layout-fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </RouterView>
      </div>
    </a-layout-content>

    <GlobalFooter v-if="!isWorkspace" />
  </a-layout>
</template>

<style scoped>
.basic-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--forge-background);
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

.basic-layout__container--workspace {
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

  .basic-layout__container--workspace,
  .basic-layout__container--home {
    padding: 0;
  }
}
</style>
