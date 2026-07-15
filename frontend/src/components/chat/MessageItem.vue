<template>
  <div v-if="isUser" class="message message--user">
    <div class="message__user-bubble">
      <p>{{ message.content }}</p>
    </div>
  </div>

  <div v-else class="message message--assistant group">
    <div class="message__avatar" aria-hidden="true">R</div>
    <div class="message__body">
      <ThinkingIndicator
        v-if="isThinking"
        :content="message.thinking"
        :duration="message.thinkingDuration"
      />

      <div v-else-if="hasThinking" class="thinking-disclosure-wrap">
        <button
          type="button"
          class="thinking-disclosure"
          :aria-expanded="thinkingExpanded"
          @click="thinkingExpanded = !thinkingExpanded"
        >
          <Brain :size="16" />
          <span>查看深度思考{{ thinkingDuration ? ` · ${thinkingDuration}` : "" }}</span>
          <ChevronDown :size="16" :class="{ 'is-expanded': thinkingExpanded }" />
        </button>
        <div v-if="thinkingExpanded" class="thinking-detail">{{ message.thinking }}</div>
      </div>

      <div v-if="isWaiting" class="ai-wait" aria-label="思考中">
        <span></span><span></span><span></span>
      </div>
      <MarkdownContent v-if="hasContent" :content="message.content" />
      <p v-if="message.status === 'error'" class="message__error">生成已中断。</p>

      <FeedbackButtons
        v-if="showFeedback"
        :message-id="message.id"
        :feedback="message.feedback || null"
        :content="message.content"
        :always-visible="isLast"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { Brain, ChevronDown } from "lucide-vue-next";
import { computed, ref } from "vue";

import FeedbackButtons from "@/components/chat/FeedbackButtons.vue";
import MarkdownContent from "@/components/chat/MarkdownContent.vue";
import ThinkingIndicator from "@/components/chat/ThinkingIndicator.vue";
import type { Message } from "@/types";

const props = defineProps<{ message: Message; isLast?: boolean }>();
const thinkingExpanded = ref(false);
const isUser = computed(() => props.message.role === "user");
const isThinking = computed(() => Boolean(props.message.isThinking));
const hasThinking = computed(() => Boolean(props.message.thinking?.trim()));
const hasContent = computed(() => props.message.content.trim().length > 0);
const isWaiting = computed(
  () => props.message.status === "streaming" && !isThinking.value && !hasContent.value
);
const thinkingDuration = computed(() =>
  props.message.thinkingDuration ? `${props.message.thinkingDuration} 秒` : ""
);
const showFeedback = computed(
  () =>
    props.message.role === "assistant" &&
    props.message.status !== "streaming" &&
    Boolean(props.message.id) &&
    !props.message.id.startsWith("assistant-")
);
</script>
