<template>
  <main class="login-page">
    <div class="login-page__theme">
      <ThemeToggle />
    </div>

    <section class="login-card">
      <div class="login-card__brand" aria-hidden="true">R</div>
      <h1>登录 Ragent</h1>
      <p>使用你的账号继续访问知识问答。</p>

      <form class="login-form" @submit.prevent="handleLogin">
        <div class="login-field">
          <label for="username">用户名</label>
          <input
            id="username"
            v-model="username"
            class="ui-input"
            autocomplete="username"
            autofocus
            placeholder="请输入用户名"
          />
        </div>

        <div class="login-field">
          <label for="password">密码</label>
          <div class="login-password">
            <input
              id="password"
              v-model="password"
              class="ui-input"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="请输入密码"
            />
            <button
              type="button"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              @click="showPassword = !showPassword"
            >
              <EyeOff v-if="showPassword" :size="16" />
              <Eye v-else :size="16" />
            </button>
          </div>
        </div>

        <p v-if="errorMessage" class="login-error" role="alert">{{ errorMessage }}</p>
        <button class="login-submit" type="submit" :disabled="loading">
          {{ loading ? "正在登录…" : "登录" }}
        </button>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { Eye, EyeOff } from "lucide-vue-next";
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";

import ThemeToggle from "@/components/layout/ThemeToggle.vue";
import { useAuthStore } from "@/stores/auth";

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const username = ref("");
const password = ref("");
const showPassword = ref(false);
const loading = ref(false);
const errorMessage = ref<string | null>(null);

async function handleLogin() {
  errorMessage.value = null;
  if (!username.value.trim() || !password.value.trim()) {
    errorMessage.value = "请输入用户名和密码。";
    return;
  }

  loading.value = true;
  try {
    await auth.login(username.value.trim(), password.value.trim());
    await router.replace(String(route.query.redirect || "/chat"));
  } catch (error) {
    errorMessage.value = (error as Error).message || "登录失败，请稍后重试。";
  } finally {
    loading.value = false;
  }
}
</script>
