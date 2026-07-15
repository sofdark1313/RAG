<template>
  <div class="chat-shell">
    <aside class="chat-sidebar" :class="{ 'chat-sidebar--open': sidebarOpen }">
      <div class="chat-sidebar__top">
        <button class="chat-new" type="button" @click="startNewChat">
          <Plus :size="18" />
          <span>新对话</span>
        </button>
        <button class="chat-icon-button md:hidden" type="button" @click="sidebarOpen = false">
          <X :size="18" />
        </button>
      </div>

      <div class="chat-session-list">
        <button
          v-for="session in sessions"
          :key="session.id"
          class="chat-session"
          :class="{ 'chat-session--active': session.id === currentSessionId }"
          type="button"
          @click="openSession(session.id)"
        >
          <MessageSquare :size="16" />
          <span class="min-w-0 flex-1 truncate">{{ session.title || "新对话" }}</span>
          <span class="chat-session__time">{{ formatSessionTime(session.lastTime) }}</span>
        </button>
        <div v-if="sessionsLoaded && sessions.length === 0" class="chat-sidebar__empty">
          暂无历史会话
        </div>
      </div>

      <div class="chat-sidebar__footer">
        <RouterLink v-if="auth.isAdmin" class="chat-sidebar__link" to="/admin/dashboard">
          <LayoutDashboard :size="16" />
          <span>管理后台</span>
        </RouterLink>
        <button class="chat-sidebar__link" type="button" @click="handleLogout">
          <LogOut :size="16" />
          <span>退出登录</span>
        </button>
      </div>
    </aside>

    <main class="chat-main">
      <header class="chat-header">
        <button class="chat-icon-button md:hidden" type="button" @click="sidebarOpen = true">
          <PanelLeft :size="19" />
        </button>
        <div class="min-w-0">
          <p class="truncate text-sm font-semibold text-[#202123]">{{ currentTitle }}</p>
          <p class="text-xs text-[#8e8ea0]">RAG 智能助手</p>
        </div>
        <button class="chat-icon-button" type="button" @click="startNewChat">
          <Edit3 :size="18" />
        </button>
      </header>

      <section ref="messageScroller" class="chat-messages">
        <div v-if="messages.length === 0" class="chat-welcome">
          <h1>我能帮你做什么？</h1>
          <div class="chat-prompts">
            <button v-for="prompt in prompts" :key="prompt" type="button" @click="usePrompt(prompt)">
              {{ prompt }}
            </button>
          </div>
        </div>

        <div v-else class="chat-message-stack">
          <article
            v-for="message in messages"
            :key="message.id"
            class="chat-message-row"
            :class="message.role === 'user' ? 'chat-message-row--user' : 'chat-message-row--assistant'"
          >
            <div v-if="message.role === 'assistant'" class="chat-avatar">R</div>
            <div class="chat-message-body" :class="{ 'chat-message-body--user': message.role === 'user' }">
              <details v-if="message.thinking" class="chat-thinking" :open="message.isThinking">
                <summary>
                  <BrainCircuit :size="15" />
                  <span>{{ message.isThinking ? "正在思考" : `已思考 ${message.thinkingDuration || 1} 秒` }}</span>
                </summary>
                <pre>{{ message.thinking }}</pre>
              </details>

              <MarkdownContent v-if="message.content" :content="message.content" />
              <div v-else class="chat-streaming-placeholder">
                <span></span><span></span><span></span>
              </div>

              <div v-if="message.role === 'assistant' && message.status !== 'streaming'" class="chat-actions">
                <button
                  class="chat-action"
                  :class="{ 'chat-action--active': message.feedback === 'like' }"
                  type="button"
                  @click="chat.submitFeedback(message.id, message.feedback === 'like' ? null : 'like')"
                >
                  <ThumbsUp :size="15" />
                </button>
                <button
                  class="chat-action"
                  :class="{ 'chat-action--active': message.feedback === 'dislike' }"
                  type="button"
                  @click="chat.submitFeedback(message.id, message.feedback === 'dislike' ? null : 'dislike')"
                >
                  <ThumbsDown :size="15" />
                </button>
              </div>
            </div>
          </article>
        </div>
      </section>

      <footer class="chat-composer-wrap">
        <form class="chat-composer" @submit.prevent="send">
          <textarea
            ref="textareaRef"
            v-model="input"
            class="chat-textarea"
            rows="1"
            placeholder="给 RAG 智能助手发送消息"
            @keydown.enter.exact.prevent="send"
            @input="resizeTextarea"
          ></textarea>
          <div class="chat-composer__bar">
            <button
              class="chat-toggle"
              :class="{ 'chat-toggle--active': deepThinkingEnabled }"
              type="button"
              @click="chat.deepThinkingEnabled = !chat.deepThinkingEnabled"
            >
              <BrainCircuit :size="15" />
              <span>深度思考</span>
            </button>

            <button
              v-if="isStreaming"
              class="chat-send"
              type="button"
              title="停止生成"
              @click="chat.cancelGeneration"
            >
              <Square :size="16" fill="currentColor" />
            </button>
            <button v-else class="chat-send" :disabled="!input.trim()" type="submit" title="发送">
              <ArrowUp :size="18" />
            </button>
          </div>
        </form>
        <p class="chat-disclaimer">内容由模型生成，请结合业务资料核验关键信息。</p>
      </footer>
    </main>

    <button v-if="sidebarOpen" class="chat-scrim md:hidden" type="button" @click="sidebarOpen = false"></button>
  </div>
</template>

<script setup lang="ts">
import {
  ArrowUp,
  BrainCircuit,
  Edit3,
  LayoutDashboard,
  LogOut,
  MessageSquare,
  PanelLeft,
  Plus,
  Square,
  ThumbsDown,
  ThumbsUp,
  X
} from "lucide-vue-next";
import { storeToRefs } from "pinia";
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";

import MarkdownContent from "@/components/chat/MarkdownContent.vue";
import { useAuthStore } from "@/stores/auth";
import { useChatStore } from "@/stores/chat";

const prompts = [
  "帮我总结知识库里的核心政策",
  "排查一次 RAG 回答不准的原因",
  "生成一份新人培训问答",
  "解释某个业务术语的含义"
];

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const chat = useChatStore();
const { sessions, currentSessionId, messages, sessionsLoaded, isStreaming, deepThinkingEnabled } = storeToRefs(chat);
const input = ref("");
const sidebarOpen = ref(false);
const textareaRef = ref<HTMLTextAreaElement | null>(null);
const messageScroller = ref<HTMLElement | null>(null);

const currentTitle = computed(() => {
  const session = sessions.value.find((item) => item.id === currentSessionId.value);
  return session?.title || "新对话";
});

function formatSessionTime(value?: string) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const now = Date.now();
  if (now - date.getTime() < 24 * 60 * 60 * 1000) {
    return date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
  }
  return date.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" });
}

async function openSession(sessionId: string) {
  sidebarOpen.value = false;
  await router.push(`/chat/${sessionId}`);
}

async function startNewChat() {
  chat.createSession();
  input.value = "";
  sidebarOpen.value = false;
  await router.push("/chat");
  await nextTick();
  textareaRef.value?.focus();
}

function usePrompt(prompt: string) {
  input.value = prompt;
  send();
}

async function send() {
  const content = input.value.trim();
  if (!content || isStreaming.value) return;
  input.value = "";
  resizeTextarea();
  await chat.sendMessage(content);
  if (chat.currentSessionId && route.params.sessionId !== chat.currentSessionId) {
    await router.replace(`/chat/${chat.currentSessionId}`);
  }
}

async function handleLogout() {
  await auth.logout();
  await router.replace("/login");
}

function resizeTextarea() {
  nextTick(() => {
    const textarea = textareaRef.value;
    if (!textarea) return;
    textarea.style.height = "auto";
    textarea.style.height = `${Math.min(textarea.scrollHeight, 180)}px`;
  });
}

function scrollToBottom() {
  nextTick(() => {
    const target = messageScroller.value;
    if (!target) return;
    target.scrollTop = target.scrollHeight;
  });
}

watch(
  () => route.params.sessionId,
  async (sessionId) => {
    if (typeof sessionId === "string" && sessionId) {
      await chat.selectSession(sessionId);
    } else if (!chat.isStreaming) {
      chat.currentSessionId = null;
      chat.messages = [];
    }
  },
  { immediate: true }
);

watch(messages, scrollToBottom, { deep: true });

onMounted(async () => {
  if (!sessionsLoaded.value) {
    await chat.fetchSessions();
  }
  scrollToBottom();
});
</script>

<style scoped>
.chat-shell {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background: #ffffff;
  color: #202123;
}

.chat-sidebar {
  width: 260px;
  flex: 0 0 260px;
  display: flex;
  flex-direction: column;
  background: #171717;
  color: #ececf1;
}

.chat-sidebar__top {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
}

.chat-new,
.chat-sidebar__link,
.chat-session {
  display: flex;
  align-items: center;
  gap: 10px;
  border-radius: 8px;
  font-size: 14px;
  transition: background 0.16s ease, color 0.16s ease;
}

.chat-new {
  min-height: 42px;
  flex: 1;
  border: 1px solid rgba(255, 255, 255, 0.14);
  padding: 0 12px;
  color: #f4f4f4;
}

.chat-new:hover,
.chat-sidebar__link:hover,
.chat-session:hover {
  background: rgba(255, 255, 255, 0.09);
}

.chat-session-list {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px 12px;
}

.chat-session {
  width: 100%;
  min-height: 38px;
  padding: 8px 10px;
  text-align: left;
  color: #d9d9e3;
}

.chat-session--active {
  background: rgba(255, 255, 255, 0.13);
  color: #ffffff;
}

.chat-session__time {
  flex: 0 0 auto;
  color: #8e8ea0;
  font-size: 11px;
}

.chat-sidebar__empty {
  padding: 18px 12px;
  color: #8e8ea0;
  font-size: 13px;
}

.chat-sidebar__footer {
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  padding: 10px;
}

.chat-sidebar__link {
  width: 100%;
  min-height: 38px;
  padding: 8px 10px;
  color: #d9d9e3;
}

.chat-main {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #ffffff;
}

.chat-header {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #ececf1;
  padding: 0 18px;
}

.chat-icon-button {
  display: inline-grid;
  place-items: center;
  height: 36px;
  width: 36px;
  border-radius: 8px;
  color: inherit;
}

.chat-icon-button:hover {
  background: rgba(0, 0, 0, 0.06);
}

.chat-messages {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 24px 16px 170px;
}

.chat-welcome {
  min-height: 100%;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 28px;
  padding-bottom: 10vh;
}

.chat-welcome h1 {
  font-size: clamp(28px, 4vw, 34px);
  font-weight: 600;
  letter-spacing: 0;
}

.chat-prompts {
  display: grid;
  width: min(720px, 100%);
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.chat-prompts button {
  min-height: 58px;
  border: 1px solid #e5e5e5;
  border-radius: 12px;
  padding: 12px 14px;
  text-align: left;
  color: #4b5563;
  background: #ffffff;
}

.chat-prompts button:hover {
  background: #f7f7f8;
}

.chat-message-stack {
  width: min(820px, 100%);
  margin: 0 auto;
}

.chat-message-row {
  display: flex;
  gap: 16px;
  padding: 18px 0;
}

.chat-message-row--user {
  justify-content: flex-end;
}

.chat-avatar {
  display: grid;
  place-items: center;
  height: 30px;
  width: 30px;
  flex: 0 0 auto;
  border-radius: 8px;
  background: #10a37f;
  color: white;
  font-weight: 700;
  font-size: 13px;
}

.chat-message-body {
  min-width: 0;
  max-width: 100%;
  color: #202123;
  font-size: 15px;
  line-height: 1.7;
}

.chat-message-body--user {
  max-width: min(72%, 680px);
  border-radius: 18px;
  background: #f4f4f4;
  padding: 10px 16px;
  line-height: 1.55;
}

.chat-thinking {
  margin-bottom: 10px;
  border-radius: 10px;
  background: #f7f7f8;
  color: #6e6e80;
}

.chat-thinking summary {
  display: flex;
  cursor: pointer;
  align-items: center;
  gap: 7px;
  padding: 8px 10px;
  font-size: 13px;
  font-weight: 500;
}

.chat-thinking pre {
  max-height: 220px;
  overflow: auto;
  border-top: 1px solid #ececf1;
  padding: 10px;
  white-space: pre-wrap;
  font-size: 12px;
}

.chat-actions {
  display: flex;
  gap: 4px;
  margin-top: 10px;
  color: #8e8ea0;
}

.chat-action {
  display: grid;
  place-items: center;
  height: 30px;
  width: 30px;
  border-radius: 7px;
}

.chat-action:hover,
.chat-action--active {
  background: #f1f1f1;
  color: #202123;
}

.chat-streaming-placeholder {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  min-height: 24px;
}

.chat-streaming-placeholder span {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: #8e8ea0;
  animation: chat-dot 1.2s infinite ease-in-out;
}

.chat-streaming-placeholder span:nth-child(2) {
  animation-delay: 0.15s;
}

.chat-streaming-placeholder span:nth-child(3) {
  animation-delay: 0.3s;
}

.chat-composer-wrap {
  position: fixed;
  left: 260px;
  right: 0;
  bottom: 0;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0), #ffffff 22%);
  padding: 36px 16px 14px;
}

.chat-composer {
  width: min(780px, 100%);
  margin: 0 auto;
  border: 1px solid #d9d9e3;
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.08);
  padding: 10px;
}

.chat-textarea {
  width: 100%;
  resize: none;
  border: 0;
  outline: none;
  max-height: 180px;
  padding: 6px 8px;
  color: #202123;
  font-size: 15px;
  line-height: 1.5;
}

.chat-composer__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 4px;
}

.chat-toggle {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 32px;
  border-radius: 999px;
  border: 1px solid #e5e5e5;
  padding: 0 12px;
  color: #6e6e80;
  font-size: 13px;
}

.chat-toggle--active {
  border-color: #10a37f;
  color: #0f7f64;
  background: #ecfdf7;
}

.chat-send {
  display: grid;
  place-items: center;
  height: 34px;
  width: 34px;
  border-radius: 999px;
  background: #202123;
  color: white;
}

.chat-send:disabled {
  background: #d9d9e3;
  color: #8e8ea0;
}

.chat-disclaimer {
  margin-top: 8px;
  text-align: center;
  font-size: 12px;
  color: #8e8ea0;
}

.chat-scrim {
  position: fixed;
  inset: 0;
  z-index: 20;
  background: rgba(0, 0, 0, 0.4);
}

@keyframes chat-dot {
  0%,
  80%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-4px);
  }
}

@media (max-width: 767px) {
  .chat-sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 30;
    transform: translateX(-100%);
    transition: transform 0.2s ease;
  }

  .chat-sidebar--open {
    transform: translateX(0);
  }

  .chat-composer-wrap {
    left: 0;
  }

  .chat-prompts {
    grid-template-columns: minmax(0, 1fr);
  }

  .chat-message-body--user {
    max-width: 86%;
  }
}
</style>
