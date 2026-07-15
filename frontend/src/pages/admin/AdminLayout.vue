<template>
  <div class="admin-layout flex h-screen overflow-hidden">
    <aside class="admin-sidebar">
      <div class="admin-sidebar__brand">
        <div class="flex items-center gap-3">
          <div class="admin-sidebar__logo">R</div>
          <div>
            <p class="admin-sidebar__title">RAG Admin</p>
            <p class="admin-sidebar__subtitle">Knowledge Ops</p>
          </div>
        </div>
      </div>

      <div class="admin-sidebar__user">
        <div class="admin-sidebar__avatar">{{ userInitial }}</div>
        <div class="min-w-0">
          <p class="truncate text-sm font-medium text-white">{{ auth.user?.username || "管理员" }}</p>
          <p class="admin-sidebar__role">admin</p>
        </div>
      </div>

      <nav class="min-h-0 flex-1 overflow-y-auto px-3 pb-4">
        <div v-for="group in navGroups" :key="group.title" class="mb-5">
          <p class="admin-sidebar__group-title">{{ group.title }}</p>
          <RouterLink
            v-for="item in group.items"
            :key="item.to"
            class="admin-sidebar__item"
            :class="{ 'admin-sidebar__item--active': isActive(item.to) }"
            :to="item.to"
          >
            <span
              class="admin-sidebar__item-indicator"
              :class="{ 'is-active': isActive(item.to) }"
            ></span>
            <component :is="item.icon" class="admin-sidebar__item-icon" />
            <span>{{ item.label }}</span>
          </RouterLink>
        </div>
      </nav>

      <div class="admin-sidebar__footer">
        <RouterLink class="admin-sidebar__action ui-button" to="/chat">
          <MessageCircle :size="16" />
          <span>返回聊天</span>
        </RouterLink>
        <button class="admin-sidebar__logout" type="button" @click="handleLogout">
          <LogOut :size="16" />
          <span>退出登录</span>
        </button>
      </div>
    </aside>

    <main class="admin-main">
      <header class="admin-topbar">
        <div class="admin-topbar-inner">
          <div class="admin-topbar-search">
            <Search class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              class="ui-input h-10 w-full rounded-lg border border-slate-200 bg-white pl-9 pr-16 text-sm"
              placeholder="搜索知识库、意图、Trace"
            />
            <span class="admin-topbar-kbd">⌘ K</span>
          </div>
          <div class="flex items-center gap-3">
            <span class="admin-code">Vue3</span>
            <span class="admin-code">{{ auth.user?.username || "admin" }}</span>
          </div>
        </div>
      </header>

      <section class="admin-content">
        <div class="admin-breadcrumbs">
          <RouterLink to="/admin/dashboard">管理后台</RouterLink>
          <span>/</span>
          <span>{{ route.meta.title }}</span>
        </div>
        <RouterView />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import {
  BarChart3,
  BookOpen,
  BrainCircuit,
  DatabaseZap,
  GitBranch,
  LogOut,
  MessageCircle,
  Search,
  Settings,
  Sparkles,
  Users,
  Workflow
} from "lucide-vue-next";
import { computed } from "vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";

import { useAuthStore } from "@/stores/auth";

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();

const navGroups = [
  {
    title: "总览",
    items: [{ label: "数据概览", to: "/admin/dashboard", icon: BarChart3 }]
  },
  {
    title: "知识运营",
    items: [
      { label: "知识库管理", to: "/admin/knowledge", icon: BookOpen },
      { label: "数据接入", to: "/admin/ingestion", icon: DatabaseZap },
      { label: "样例问题", to: "/admin/sample-questions", icon: Sparkles }
    ]
  },
  {
    title: "问答治理",
    items: [
      { label: "意图树", to: "/admin/intent-tree", icon: GitBranch },
      { label: "意图列表", to: "/admin/intent-list", icon: BrainCircuit },
      { label: "术语映射", to: "/admin/mappings", icon: Workflow },
      { label: "RAG Trace", to: "/admin/traces", icon: BarChart3 }
    ]
  },
  {
    title: "系统",
    items: [
      { label: "系统设置", to: "/admin/settings", icon: Settings },
      { label: "用户管理", to: "/admin/users", icon: Users }
    ]
  }
];

const userInitial = computed(() => (auth.user?.username || "A").slice(0, 1).toUpperCase());

function isActive(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`);
}

async function handleLogout() {
  await auth.logout();
  await router.replace("/login");
}
</script>
