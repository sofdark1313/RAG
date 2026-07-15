<template>
  <div class="admin-layout h-screen overflow-hidden">
    <aside class="admin-sidebar" :class="{ 'admin-sidebar--collapsed': collapsed }">
      <div class="admin-sidebar__brand">
        <div class="flex items-center gap-3" :class="{ 'justify-center': collapsed }">
          <div class="admin-sidebar__logo">R</div>
          <div v-if="!collapsed" class="min-w-0">
            <h1 class="admin-sidebar__title">Ragent AI 管理后台</h1>
            <p class="admin-sidebar__subtitle">内部管理</p>
          </div>
        </div>
      </div>

      <nav class="flex-1 space-y-4 px-2 pb-4">
        <div v-for="group in menuGroups" :key="group.title" class="space-y-2">
          <p v-if="!collapsed" class="admin-sidebar__group-title">{{ group.title }}</p>
          <div class="space-y-1">
            <template v-for="item in group.items" :key="item.id || item.path">
              <RouterLink
                v-if="!item.children"
                :to="routeTarget(item)"
                class="admin-sidebar__item"
                :class="{
                  'admin-sidebar__item--active': isLeafActive(item),
                  'justify-center': collapsed
                }"
                :title="collapsed ? item.label : undefined"
              >
                <component :is="item.icon" class="admin-sidebar__item-icon" />
                <span :class="{ 'sr-only': collapsed }">{{ item.label }}</span>
              </RouterLink>

              <template v-else-if="collapsed">
                <RouterLink
                  v-for="child in item.children"
                  :key="`${child.path}-${child.search || ''}`"
                  :to="routeTarget(child)"
                  class="admin-sidebar__item justify-center"
                  :class="{ 'admin-sidebar__item--active': isLeafActive(child) }"
                  :title="child.label"
                >
                  <component :is="child.icon" class="admin-sidebar__item-icon" />
                  <span class="sr-only">{{ child.label }}</span>
                </RouterLink>
              </template>

              <div v-else class="space-y-1">
                <button
                  type="button"
                  class="admin-sidebar__item admin-sidebar__item--group w-full"
                  :class="{ 'admin-sidebar__item--group-active': isGroupActive(item) }"
                  @click="toggleGroup(item.id || item.path)"
                >
                  <component :is="item.icon" class="admin-sidebar__item-icon" />
                  <span class="flex-1 text-left">{{ item.label }}</span>
                  <ChevronDown v-if="openGroups[item.id || item.path]" :size="16" />
                  <ChevronRight v-else :size="16" />
                </button>
                <div v-if="openGroups[item.id || item.path]" class="ml-6 space-y-1">
                  <RouterLink
                    v-for="child in item.children"
                    :key="`${child.path}-${child.search || ''}`"
                    :to="routeTarget(child)"
                    class="admin-sidebar__item text-[13px]"
                    :class="{ 'admin-sidebar__item--active': isLeafActive(child) }"
                  >
                    <component :is="child.icon" class="admin-sidebar__item-icon" />
                    <span>{{ child.label }}</span>
                  </RouterLink>
                </div>
              </div>
            </template>
          </div>
        </div>
      </nav>

      <div class="admin-sidebar__footer">
        <button type="button" class="admin-sidebar__collapse" @click="collapsed = !collapsed">
          <ChevronsRight v-if="collapsed" :size="16" />
          <ChevronsLeft v-else :size="16" />
          <span v-if="!collapsed">收起侧边栏</span>
        </button>
      </div>
    </aside>

    <main class="admin-main">
      <header class="admin-topbar">
        <div class="admin-topbar-inner">
          <div class="flex items-center gap-3">
            <button class="ui-button lg:hidden" type="button" aria-label="切换侧边栏" @click="collapsed = !collapsed">
              <Menu :size="18" />
            </button>
            <div class="admin-topbar-search">
              <Search class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" :size="16" />
              <input
                ref="searchInput"
                v-model="searchQuery"
                class="ui-input w-full pl-10 pr-16"
                name="kb-search"
                autocomplete="off"
                placeholder="筛选知识库..."
                @focus="searchFocused = true"
                @blur="handleSearchBlur"
                @keydown.enter.prevent="openFirstSearchResult"
                @keydown.esc="closeSearch"
              />
              <span class="admin-topbar-kbd">Ctrl K</span>
              <div v-if="showSuggestions" class="admin-topbar-suggest" @mousedown.prevent>
                <div v-if="searchLoading && !knowledgeOptions.length && !documentOptions.length" class="admin-topbar-suggest-item">搜索中...</div>
                <div v-if="knowledgeOptions.length" class="admin-topbar-suggest-section">
                  <div class="admin-topbar-suggest-group">知识库</div>
                  <button v-for="kb in knowledgeOptions" :key="kb.id" class="admin-topbar-suggest-item" type="button" @click="openKnowledge(kb)">
                    <span class="font-medium text-slate-900">{{ kb.name }}</span>
                    <span class="truncate text-xs text-slate-400">{{ kb.collectionName || '未设置 Collection' }}</span>
                  </button>
                </div>
                <div v-if="documentOptions.length" class="admin-topbar-suggest-section">
                  <div class="admin-topbar-suggest-group">文档</div>
                  <button v-for="doc in documentOptions" :key="doc.id" class="admin-topbar-suggest-item" type="button" @click="openDocument(doc)">
                    <span class="truncate font-medium text-slate-900">{{ doc.docName }}</span>
                    <span class="truncate text-xs text-slate-400">{{ doc.kbName || `知识库 ${doc.kbId}` }}</span>
                  </button>
                </div>
                <div v-if="!searchLoading && !knowledgeOptions.length && !documentOptions.length" class="admin-topbar-suggest-item">暂无匹配结果</div>
              </div>
            </div>
          </div>

          <div class="admin-topbar__actions">
            <RouterLink class="ui-button hidden items-center gap-2 border border-slate-200 px-3 sm:inline-flex" to="/chat" target="_blank">
              <MessageSquare :size="16" />
              返回聊天
            </RouterLink>
            <ThemeToggle />
            <div class="relative">
              <button class="admin-user-menu" :class="{ 'is-open': userMenuOpen }" type="button" aria-label="用户菜单" @click="userMenuOpen = !userMenuOpen">
                <span class="admin-user-menu__avatar h-7 w-7 rounded-full text-xs font-semibold">
                  <img v-if="auth.user?.avatar" :src="auth.user.avatar" alt="" class="h-full w-full object-cover" />
                  <span v-else>{{ userInitial }}</span>
                </span>
                <span class="hidden sm:inline">{{ auth.user?.username || '管理员' }}</span>
                <ChevronDown :size="15" />
              </button>
              <div v-if="userMenuOpen" class="admin-user-dropdown">
                <div class="admin-user-dropdown__meta">{{ auth.user?.username || '管理员' }} · {{ roleLabel }}</div>
                <button class="admin-user-dropdown__item" type="button" @click="openPasswordDialog">
                  <KeyRound :size="16" />
                  修改密码
                </button>
                <button class="admin-user-dropdown__item text-rose-600" type="button" @click="handleLogout">
                  <LogOut :size="16" />
                  退出登录
                </button>
              </div>
            </div>
          </div>
        </div>
      </header>

      <section class="admin-content">
        <nav class="admin-breadcrumbs" aria-label="面包屑">
          <template v-for="(item, index) in breadcrumbs" :key="`${item.label}-${index}`">
            <RouterLink v-if="item.to && index < breadcrumbs.length - 1" :to="item.to">{{ item.label }}</RouterLink>
            <span v-else>{{ item.label }}</span>
            <span v-if="index < breadcrumbs.length - 1">/</span>
          </template>
        </nav>
        <RouterView />
      </section>
    </main>

    <div v-if="passwordOpen" class="admin-modal-backdrop" @mousedown.self="closePasswordDialog">
      <form class="admin-modal" @submit.prevent="submitPassword">
        <h2 class="text-lg font-semibold text-slate-900">修改密码</h2>
        <p class="mt-1 text-sm text-slate-500">请输入当前密码与新密码</p>
        <div class="mt-5 space-y-4">
          <label class="block space-y-2 text-sm font-medium text-slate-700">
            <span>当前密码</span>
            <input v-model="passwordForm.currentPassword" class="ui-input h-10 w-full px-3" type="password" autocomplete="current-password" />
          </label>
          <label class="block space-y-2 text-sm font-medium text-slate-700">
            <span>新密码</span>
            <input v-model="passwordForm.newPassword" class="ui-input h-10 w-full px-3" type="password" autocomplete="new-password" />
          </label>
          <label class="block space-y-2 text-sm font-medium text-slate-700">
            <span>确认新密码</span>
            <input v-model="passwordForm.confirmPassword" class="ui-input h-10 w-full px-3" type="password" autocomplete="new-password" />
          </label>
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <button class="ui-button border border-slate-200 px-4 py-2" type="button" @click="closePasswordDialog">取消</button>
          <button class="ui-button bg-slate-900 px-4 py-2 text-white" type="submit" :disabled="passwordSubmitting">
            {{ passwordSubmitting ? '保存中...' : '保存' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  ChevronDown,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  ClipboardList,
  Database,
  FolderKanban,
  GitBranch,
  KeyRound,
  Layers,
  LayoutDashboard,
  Lightbulb,
  LogOut,
  Menu,
  MessageSquare,
  Search,
  Settings,
  Upload,
  Users,
  Workflow,
  type LucideIcon
} from "lucide-vue-next";
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { RouterLink, RouterView, useRoute, useRouter, type RouteLocationRaw } from "vue-router";

import { getKnowledgeBases, searchKnowledgeDocuments, type KnowledgeBase, type KnowledgeDocumentSearchItem } from "@/services/knowledgeService";
import { changePassword } from "@/services/userService";
import { useAuthStore } from "@/stores/auth";
import { toast } from "@/utils/toast";
import ThemeToggle from "@/components/layout/ThemeToggle.vue";

type MenuEntry = {
  id?: string;
  path: string;
  label: string;
  icon: LucideIcon;
  search?: string;
  children?: MenuEntry[];
};

const menuGroups: Array<{ title: string; items: MenuEntry[] }> = [
  {
    title: "导航",
    items: [
      { path: "/admin/dashboard", label: "Dashboard", icon: LayoutDashboard },
      { path: "/admin/knowledge", label: "知识库管理", icon: Database },
      {
        id: "intent",
        path: "/admin/intent-tree",
        label: "意图管理",
        icon: Layers,
        children: [
          { path: "/admin/intent-tree", label: "意图树配置", icon: GitBranch },
          { path: "/admin/intent-list", label: "意图列表", icon: ClipboardList }
        ]
      },
      {
        id: "ingestion",
        path: "/admin/ingestion",
        label: "数据通道",
        icon: Upload,
        children: [
          { path: "/admin/ingestion", label: "流水线管理", icon: FolderKanban, search: "?tab=pipelines" },
          { path: "/admin/ingestion", label: "流水线任务", icon: ClipboardList, search: "?tab=tasks" }
        ]
      },
      { path: "/admin/mappings", label: "关键词映射", icon: KeyRound },
      { path: "/admin/traces", label: "链路追踪", icon: Workflow }
    ]
  },
  {
    title: "设置",
    items: [
      { path: "/admin/users", label: "用户管理", icon: Users },
      { path: "/admin/sample-questions", label: "示例问题", icon: Lightbulb },
      { path: "/admin/settings", label: "系统设置", icon: Settings }
    ]
  }
];

const breadcrumbMap: Record<string, string> = {
  dashboard: "Dashboard",
  knowledge: "知识库管理",
  "intent-tree": "意图树配置",
  "intent-list": "意图列表",
  ingestion: "数据通道",
  traces: "链路追踪",
  "sample-questions": "示例问题",
  mappings: "关键词映射",
  settings: "系统设置",
  users: "用户管理"
};

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const collapsed = ref(false);
const openGroups = reactive<Record<string, boolean>>({ ingestion: true, intent: true });
const searchInput = ref<HTMLInputElement | null>(null);
const searchQuery = ref("");
const searchFocused = ref(false);
const searchLoading = ref(false);
const knowledgeOptions = ref<KnowledgeBase[]>([]);
const documentOptions = ref<KnowledgeDocumentSearchItem[]>([]);
const userMenuOpen = ref(false);
const passwordOpen = ref(false);
const passwordSubmitting = ref(false);
const passwordForm = reactive({ currentPassword: "", newPassword: "", confirmPassword: "" });
let searchTimer: number | undefined;
let searchRequest = 0;

const userInitial = computed(() => (auth.user?.username || "管").slice(0, 1).toUpperCase());
const roleLabel = computed(() => (auth.user?.role === "admin" ? "管理员" : "成员"));
const showSuggestions = computed(() => searchFocused.value && searchQuery.value.trim().length > 0);
const breadcrumbs = computed(() => {
  const segments = route.path.split("/").filter(Boolean);
  const items: Array<{ label: string; to?: string }> = [{ label: "首页", to: "/admin/dashboard" }];
  const section = segments[1];
  if (!section) return items;

  if (section === "intent-tree" || section === "intent-list") {
    items.push({ label: "意图管理", to: "/admin/intent-tree" });
    if (section === "intent-list" && segments.includes("edit")) {
      items.push({ label: "意图列表", to: "/admin/intent-list" }, { label: "编辑节点" });
    } else {
      items.push({ label: breadcrumbMap[section] || section });
    }
  } else {
    items.push({ label: breadcrumbMap[section] || section, to: `/admin/${section}` });
  }

  if (section === "ingestion") {
    items.push({ label: route.query.tab === "tasks" ? "流水线任务" : "流水线管理" });
  }
  if (section === "knowledge" && segments.length > 2) items.push({ label: "文档管理" });
  if (section === "knowledge" && segments.includes("docs")) items.push({ label: "分块管理" });
  if (section === "traces" && segments.length > 2) items.push({ label: "链路详情" });
  return items;
});

watch(searchQuery, () => {
  window.clearTimeout(searchTimer);
  const keyword = searchQuery.value.trim();
  if (!keyword) {
    knowledgeOptions.value = [];
    documentOptions.value = [];
    searchLoading.value = false;
    return;
  }
  searchTimer = window.setTimeout(async () => {
    const requestId = ++searchRequest;
    searchLoading.value = true;
    try {
      const [knowledge, documents] = await Promise.all([getKnowledgeBases(1, 6, keyword), searchKnowledgeDocuments(keyword, 6)]);
      if (requestId !== searchRequest) return;
      knowledgeOptions.value = knowledge;
      documentOptions.value = documents;
    } catch {
      if (requestId !== searchRequest) return;
      knowledgeOptions.value = [];
      documentOptions.value = [];
    } finally {
      if (requestId === searchRequest) searchLoading.value = false;
    }
  }, 200);
});

watch(
  () => route.path,
  (path) => {
    if (path.startsWith("/admin/ingestion")) openGroups.ingestion = true;
    if (path.startsWith("/admin/intent")) openGroups.intent = true;
    userMenuOpen.value = false;
  }
);

function routeTarget(item: MenuEntry): RouteLocationRaw {
  if (!item.search) return item.path;
  return `${item.path}${item.search}`;
}

function isLeafActive(item: MenuEntry) {
  if (route.path !== item.path && !route.path.startsWith(`${item.path}/`)) return false;
  if (item.search) return route.fullPath.endsWith(item.search);
  return true;
}

function isGroupActive(item: MenuEntry) {
  return item.children?.some(isLeafActive) || false;
}

function toggleGroup(group: string) {
  openGroups[group] = !openGroups[group];
}

function clearSearch() {
  searchQuery.value = "";
  knowledgeOptions.value = [];
  documentOptions.value = [];
  searchFocused.value = false;
}

async function openKnowledge(kb: KnowledgeBase) {
  clearSearch();
  await router.push(`/admin/knowledge/${kb.id}`);
}

async function openDocument(doc: KnowledgeDocumentSearchItem) {
  clearSearch();
  await router.push(`/admin/knowledge/${doc.kbId}/docs/${doc.id}`);
}

async function openFirstSearchResult() {
  if (knowledgeOptions.value[0]) return openKnowledge(knowledgeOptions.value[0]);
  if (documentOptions.value[0]) return openDocument(documentOptions.value[0]);
  const keyword = searchQuery.value.trim();
  if (keyword) {
    clearSearch();
    await router.push({ path: "/admin/knowledge", query: { name: keyword } });
  }
}

function handleSearchBlur() {
  window.setTimeout(() => (searchFocused.value = false), 150);
}

function closeSearch() {
  searchInput.value?.blur();
  searchFocused.value = false;
}

function openPasswordDialog() {
  userMenuOpen.value = false;
  passwordOpen.value = true;
}

function closePasswordDialog() {
  passwordOpen.value = false;
  passwordForm.currentPassword = "";
  passwordForm.newPassword = "";
  passwordForm.confirmPassword = "";
}

async function submitPassword() {
  if (!passwordForm.currentPassword || !passwordForm.newPassword) {
    toast.error("请输入当前密码和新密码");
    return;
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    toast.error("两次输入的新密码不一致");
    return;
  }
  passwordSubmitting.value = true;
  try {
    await changePassword({ currentPassword: passwordForm.currentPassword, newPassword: passwordForm.newPassword });
    toast.success("密码已更新");
    closePasswordDialog();
  } catch (error) {
    toast.error((error as Error).message || "修改密码失败");
  } finally {
    passwordSubmitting.value = false;
  }
}

async function handleLogout() {
  await auth.logout();
  await router.replace("/login");
}

function handleGlobalKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
    event.preventDefault();
    searchInput.value?.focus();
  }
  if (event.key === "Escape") userMenuOpen.value = false;
}

onMounted(() => {
  window.addEventListener("keydown", handleGlobalKeydown);
});

onBeforeUnmount(() => {
  window.clearTimeout(searchTimer);
  window.removeEventListener("keydown", handleGlobalKeydown);
});
</script>

<style src="../../styles/admin.css"></style>
