<template>
  <div class="chat-shell">
    <Sidebar :open="sidebarOpen" @close="sidebarOpen = false" />
    <div class="chat-shell__main">
      <Header @toggle-sidebar="sidebarOpen = !sidebarOpen" />
      <main class="chat-shell__content">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";

import Header from "@/components/layout/Header.vue";
import Sidebar from "@/components/layout/Sidebar.vue";

const sidebarOpen = ref(false);
</script>
