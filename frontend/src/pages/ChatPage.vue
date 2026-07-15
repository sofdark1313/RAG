<template>
  <MainLayout>
    <div class="chat-page">
      <div class="chat-page__messages">
        <MessageList
          :messages="messages"
          :is-loading="isLoading"
          :is-streaming="isStreaming"
          :session-key="currentSessionId"
        />
      </div>
      <div v-if="!showWelcome" class="chat-page__composer">
        <ChatInput />
      </div>
    </div>
  </MainLayout>
</template>

<script setup lang="ts">
import { storeToRefs } from "pinia";
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import ChatInput from "@/components/chat/ChatInput.vue";
import MessageList from "@/components/chat/MessageList.vue";
import MainLayout from "@/components/layout/MainLayout.vue";
import { useChatStore } from "@/stores/chat";

const route = useRoute();
const router = useRouter();
const chat = useChatStore();
const { messages, isLoading, isStreaming, currentSessionId, sessions, isCreatingNew } = storeToRefs(chat);
const sessionsReady = ref(false);

const showWelcome = computed(() => messages.value.length === 0 && !isLoading.value);

async function syncRouteSession(sessionId: unknown) {
  if (!sessionsReady.value) return;

  if (typeof sessionId === "string" && sessionId) {
    if (!sessions.value.some((session) => session.id === sessionId)) {
      chat.createSession();
      await router.replace("/chat");
      return;
    }
    await chat.selectSession(sessionId);
    return;
  }

  if (!isCreatingNew.value && !currentSessionId.value) {
    chat.createSession();
  }
}

watch(
  () => route.params.sessionId,
  (sessionId) => {
    void syncRouteSession(sessionId);
  }
);

watch(currentSessionId, (sessionId) => {
  if (sessionId && route.params.sessionId !== sessionId) {
    void router.replace(`/chat/${sessionId}`);
  }
});

onMounted(async () => {
  if (!chat.sessionsLoaded) {
    await chat.fetchSessions();
  }
  sessionsReady.value = true;
  await syncRouteSession(route.params.sessionId);
});
</script>
