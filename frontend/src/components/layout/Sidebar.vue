<template>
  <button
    type="button"
    aria-label="关闭会话列表"
    class="chat-sidebar__overlay"
    :class="{ 'is-visible': open }"
    @click="$emit('close')"
  ></button>

  <aside class="chat-sidebar" :class="{ 'chat-sidebar--open': open }" aria-label="会话列表">
    <div class="chat-sidebar__brand">
      <span class="chat-sidebar__logo"><Bot :size="16" /></span>
      <span>Ragent</span>
    </div>

    <button type="button" class="chat-sidebar__new" @click="handleCreateSession">
      <Plus :size="16" />
      新对话
    </button>

    <label class="chat-sidebar__search">
      <Search :size="16" aria-hidden="true" />
      <span class="sr-only">搜索对话</span>
      <input v-model="query" placeholder="搜索对话" />
    </label>

    <SessionList
      :sessions="sessions"
      :current-session-id="currentSessionId"
      :is-loading="isLoading"
      :sessions-loaded="sessionsLoaded"
      :query="query"
      @select="selectConversation"
      @rename="handleRename"
      @delete="deleteTarget = $event"
    />

    <div ref="accountRef" class="chat-sidebar__account-wrap">
      <button
        type="button"
        class="chat-sidebar__account"
        :data-state="accountMenuOpen ? 'open' : 'closed'"
        aria-label="用户菜单"
        @click="accountMenuOpen = !accountMenuOpen"
      >
        <span class="chat-sidebar__avatar">
          <img v-if="showAvatar" :src="avatarUrl" alt="" @error="avatarFailed = true" />
          <template v-else>{{ avatarFallback }}</template>
        </span>
        <span class="chat-sidebar__username">{{ displayName }}</span>
        <MoreHorizontal :size="16" />
      </button>

      <div v-if="accountMenuOpen" class="account-menu" role="menu">
        <button v-if="auth.isAdmin" type="button" role="menuitem" @click="openAdmin">
          <Settings :size="16" />管理后台
        </button>
        <button type="button" role="menuitem" class="is-destructive" @click="handleLogout">
          <LogOut :size="16" />退出登录
        </button>
      </div>
    </div>
  </aside>

  <div v-if="deleteTarget" class="dialog-backdrop" role="presentation" @click.self="deleteTarget = null">
    <section class="confirm-dialog" role="alertdialog" aria-modal="true" aria-labelledby="delete-title">
      <h2 id="delete-title">删除该会话？</h2>
      <p>“{{ deleteTarget.title || "该会话" }}”将被永久删除，无法恢复。</p>
      <div class="confirm-dialog__actions">
        <button type="button" class="dialog-button" @click="deleteTarget = null">取消</button>
        <button type="button" class="dialog-button dialog-button--danger" @click="confirmDelete">删除</button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { Bot, LogOut, MoreHorizontal, Plus, Search, Settings } from "lucide-vue-next";
import { storeToRefs } from "pinia";
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";

import SessionList from "@/components/session/SessionList.vue";
import { useAuthStore } from "@/stores/auth";
import { useChatStore } from "@/stores/chat";
import type { Session } from "@/types";

defineProps<{ open: boolean }>();
const emit = defineEmits<{ (event: "close"): void }>();

const router = useRouter();
const auth = useAuthStore();
const chat = useChatStore();
const { sessions, currentSessionId, isLoading, sessionsLoaded } = storeToRefs(chat);
const query = ref("");
const deleteTarget = ref<Session | null>(null);
const accountMenuOpen = ref(false);
const accountRef = ref<HTMLElement | null>(null);
const avatarFailed = ref(false);

const avatarUrl = computed(() => auth.user?.avatar?.trim() || "");
const showAvatar = computed(() => Boolean(avatarUrl.value) && !avatarFailed.value);
const avatarFallback = computed(() => (auth.user?.username || auth.user?.userId || "用户").slice(0, 1).toUpperCase());
const displayName = computed(() => {
  const name = auth.user?.username || auth.user?.userId || "用户";
  return /^\d+$/.test(name) ? "用户" : name;
});

watch(avatarUrl, () => {
  avatarFailed.value = false;
});

function handleDocumentPointerDown(event: PointerEvent) {
  if (accountRef.value && !accountRef.value.contains(event.target as Node)) accountMenuOpen.value = false;
}

function handleCreateSession() {
  chat.createSession();
  void router.push("/chat");
  emit("close");
}

function selectConversation(sessionId: string) {
  void router.push(`/chat/${sessionId}`);
  emit("close");
}

function handleRename(payload: { id: string; title: string }) {
  void chat.renameSession(payload.id, payload.title);
}

async function confirmDelete() {
  if (!deleteTarget.value) return;
  const target = deleteTarget.value;
  const wasCurrent = currentSessionId.value === target.id;
  deleteTarget.value = null;
  await chat.deleteSession(target.id);
  if (wasCurrent) await router.push("/chat");
}

function openAdmin() {
  accountMenuOpen.value = false;
  window.open("/admin", "_blank", "noopener,noreferrer");
}

async function handleLogout() {
  accountMenuOpen.value = false;
  chat.cancelGeneration();
  await auth.logout();
  chat.$reset();
  await router.replace("/login");
}

onMounted(() => {
  document.addEventListener("pointerdown", handleDocumentPointerDown);
});

onBeforeUnmount(() => {
  document.removeEventListener("pointerdown", handleDocumentPointerDown);
});
</script>
