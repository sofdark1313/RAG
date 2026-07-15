<template>
  <main class="grid min-h-screen place-items-center bg-[#f7f7f8] px-4 py-10 text-[#202123]">
    <section class="w-full max-w-[420px]">
      <div class="mb-8 text-center">
        <div class="mx-auto mb-5 grid h-12 w-12 place-items-center rounded-xl bg-[#202123] text-lg font-semibold text-white">
          R
        </div>
        <h1 class="text-2xl font-semibold">登录 RAG 智能助手</h1>
        <p class="mt-2 text-sm text-[#6e6e80]">进入聊天工作台和管理后台</p>
      </div>

      <form class="rounded-2xl border border-[#dedede] bg-white p-6 shadow-sm" @submit.prevent="handleLogin">
        <label class="block text-sm font-medium text-[#353740]" for="username">账号</label>
        <input
          id="username"
          v-model="username"
          class="mt-2 h-11 w-full rounded-lg border border-[#d9d9e3] px-3 text-sm outline-none transition focus:border-[#10a37f] focus:ring-2 focus:ring-[#10a37f]/20"
          autocomplete="username"
          placeholder="请输入账号"
        />

        <label class="mt-5 block text-sm font-medium text-[#353740]" for="password">密码</label>
        <input
          id="password"
          v-model="password"
          class="mt-2 h-11 w-full rounded-lg border border-[#d9d9e3] px-3 text-sm outline-none transition focus:border-[#10a37f] focus:ring-2 focus:ring-[#10a37f]/20"
          type="password"
          autocomplete="current-password"
          placeholder="请输入密码"
        />

        <button
          class="mt-6 h-11 w-full rounded-lg bg-[#10a37f] text-sm font-semibold text-white transition hover:bg-[#0e906f] disabled:cursor-not-allowed disabled:opacity-60"
          :disabled="loading"
          type="submit"
        >
          {{ loading ? "登录中..." : "登录" }}
        </button>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";

import { useAuthStore } from "@/stores/auth";
import { toast } from "@/utils/toast";

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const username = ref("");
const password = ref("");
const loading = ref(false);

async function handleLogin() {
  if (!username.value.trim() || !password.value) {
    toast.error("请输入账号和密码");
    return;
  }

  loading.value = true;
  try {
    await auth.login(username.value.trim(), password.value);
    await router.replace(String(route.query.redirect || "/chat"));
  } catch (error) {
    toast.error((error as Error).message || "登录失败");
  } finally {
    loading.value = false;
  }
}
</script>
