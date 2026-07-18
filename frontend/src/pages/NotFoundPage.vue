<template>
  <main class="grid min-h-screen place-items-center bg-[#f7f7f8] px-6 text-center text-[#202123]">
    <section>
      <p class="text-sm font-medium text-[#10a37f]">404</p>
      <h1 class="mt-3 text-3xl font-semibold">页面不存在</h1>
      <RouterLink class="mt-6 inline-flex rounded-lg bg-[#202123] px-4 py-2 text-sm font-medium text-white" to="/chat">
        返回聊天
      </RouterLink>
    </section>
  </main>
</template>

<script setup lang="ts">
import { RouterLink } from "vue-router";
</script>
