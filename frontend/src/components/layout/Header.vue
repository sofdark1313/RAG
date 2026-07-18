<template>
  <header class="chat-header">
    <div class="chat-header__leading">
      <button
        type="button"
        class="chat-header__menu"
        aria-label="打开会话列表"
        @click="$emit('toggle-sidebar')"
      >
        <Menu :size="20" />
      </button>
      <p class="chat-header__title">{{ currentSession?.title || "新对话" }}</p>
    </div>
    <ThemeToggle />
  </header>
</template>

<script setup lang="ts">
import { Menu } from "lucide-vue-next";
import { storeToRefs } from "pinia";
import { computed } from "vue";

import ThemeToggle from "@/components/layout/ThemeToggle.vue";
import { useChatStore } from "@/stores/chat";

defineEmits<{ (event: "toggle-sidebar"): void }>();

const chat = useChatStore();
const { sessions, currentSessionId } = storeToRefs(chat);
const currentSession = computed(() =>
  sessions.value.find((session) => session.id === currentSessionId.value)
);
</script>
