<template>
  <div v-if="messages.length === 0" class="message-list-empty">
    <div v-if="isLoading" class="message-list-loading"></div>
    <WelcomeScreen v-else />
  </div>

  <div v-else class="message-list-shell">
    <div ref="scrollerRef" class="message-list" @scroll="updateActiveQuestion">
      <div class="message-list__inner">
        <div
          v-for="(message, index) in messages"
          :key="message.id"
          :data-message-id="message.id"
          @mousedown="handleTripleClick"
        >
          <MessageItem :message="message" :is-last="index === messages.length - 1" />
        </div>
        <div class="message-list__footer" aria-hidden="true"></div>
      </div>
    </div>
    <QuestionRail :items="userQuestions" :active-id="activeQuestionId" @select="scrollToQuestion" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";

import MessageItem from "@/components/chat/MessageItem.vue";
import QuestionRail, { type QuestionRailItem } from "@/components/chat/QuestionRail.vue";
import WelcomeScreen from "@/components/chat/WelcomeScreen.vue";
import type { Message } from "@/types";

const props = defineProps<{
  messages: Message[];
  isLoading: boolean;
  isStreaming: boolean;
  sessionKey?: string | null;
}>();

const scrollerRef = ref<HTMLElement | null>(null);
const activeQuestionId = ref<string | null>(null);
let settleTimer: ReturnType<typeof setTimeout> | null = null;

const userQuestions = computed<QuestionRailItem[]>(() =>
  props.messages
    .filter((message) => message.role === "user" && message.content.trim())
    .map((message) => ({ id: message.id, text: message.content.replace(/\s+/g, " ").trim() }))
);

function scrollToBottom() {
  void nextTick(() => {
    const scroller = scrollerRef.value;
    if (scroller) scroller.scrollTop = scroller.scrollHeight;
  });
}

function settleAtBottom() {
  scrollToBottom();
  if (settleTimer) clearTimeout(settleTimer);
  settleTimer = setTimeout(scrollToBottom, 220);
}

function scrollToQuestion(messageId: string) {
  const target = scrollerRef.value?.querySelector<HTMLElement>(`[data-message-id="${CSS.escape(messageId)}"]`);
  target?.scrollIntoView({ block: "start", behavior: "smooth" });
}

function updateActiveQuestion() {
  const scroller = scrollerRef.value;
  if (!scroller || userQuestions.value.length === 0) {
    activeQuestionId.value = null;
    return;
  }
  const top = scroller.getBoundingClientRect().top + 48;
  let active = userQuestions.value[0].id;
  for (const question of userQuestions.value) {
    const node = scroller.querySelector<HTMLElement>(`[data-message-id="${CSS.escape(question.id)}"]`);
    if (node && node.getBoundingClientRect().top <= top) active = question.id;
  }
  activeQuestionId.value = active;
}

function handleTripleClick(event: MouseEvent) {
  if (event.detail < 3) return;
  event.preventDefault();
  const target = event.target as HTMLElement;
  const message = event.currentTarget as HTMLElement;
  const block = target.closest("p, li, h1, h2, h3, h4, h5, h6, pre, blockquote, td, th");
  const container = block && message.contains(block) ? block : message;
  const selection = window.getSelection();
  if (!selection) return;
  const range = document.createRange();
  range.selectNodeContents(container);
  selection.removeAllRanges();
  selection.addRange(range);
}

watch(
  () => props.sessionKey,
  () => settleAtBottom(),
  { immediate: true }
);

watch(
  () => [props.messages.length, props.messages.at(-1)?.content, props.messages.at(-1)?.thinking, props.isStreaming],
  () => {
    if (props.isStreaming) scrollToBottom();
    else settleAtBottom();
    void nextTick(updateActiveQuestion);
  }
);

onBeforeUnmount(() => {
  if (settleTimer) clearTimeout(settleTimer);
});
</script>
