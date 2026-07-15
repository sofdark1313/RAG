<template>
  <div class="chat-composer-wrap">
    <div class="chat-composer" :class="{ 'chat-composer--focused': focused }">
      <textarea
        ref="textareaRef"
        v-model="value"
        class="chat-composer__input"
        rows="1"
        :placeholder="deepThinkingEnabled ? '输入需要深入分析的问题' : '给 Ragent 发送消息'"
        aria-label="聊天输入框"
        @focus="focused = true"
        @blur="focused = false"
        @compositionstart="composing = true"
        @compositionend="composing = false"
        @keydown="handleKeydown"
        @input="adjustHeight"
      ></textarea>
      <div class="chat-composer__toolbar">
        <button
          type="button"
          class="chat-composer__mode"
          :class="{ 'is-active': deepThinkingEnabled }"
          :disabled="isStreaming"
          :aria-pressed="deepThinkingEnabled"
          @click="chat.deepThinkingEnabled = !chat.deepThinkingEnabled"
        >
          <Brain :size="16" />
          深度思考
        </button>
        <button
          type="button"
          class="chat-composer__send"
          :class="{ 'is-stopping': isStreaming }"
          :disabled="!hasContent && !isStreaming"
          :aria-label="isStreaming ? '停止生成' : '发送消息'"
          @click="handleSubmit"
        >
          <Square v-if="isStreaming" :size="16" fill="currentColor" />
          <Send v-else :size="16" />
        </button>
      </div>
    </div>
    <p class="chat-composer__hint">
      {{ isStreaming ? "正在生成回答…" : "Ragent 可能会出错，请核对重要信息。" }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { Brain, Send, Square } from "lucide-vue-next";
import { storeToRefs } from "pinia";
import { computed, nextTick, ref } from "vue";

import { useChatStore } from "@/stores/chat";

const chat = useChatStore();
const { isStreaming, deepThinkingEnabled } = storeToRefs(chat);
const value = ref("");
const focused = ref(false);
const composing = ref(false);
const textareaRef = ref<HTMLTextAreaElement | null>(null);
const hasContent = computed(() => value.value.trim().length > 0);

function focusInput() {
  void nextTick(() => textareaRef.value?.focus({ preventScroll: true }));
}

function adjustHeight() {
  void nextTick(() => {
    const textarea = textareaRef.value;
    if (!textarea) return;
    textarea.style.height = "auto";
    textarea.style.height = `${Math.min(textarea.scrollHeight, 160)}px`;
  });
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key !== "Enter" || event.shiftKey) return;
  if (event.isComposing || composing.value || event.keyCode === 229) return;
  event.preventDefault();
  handleSubmit();
}

function handleSubmit() {
  if (isStreaming.value) {
    chat.cancelGeneration();
    focusInput();
    return;
  }

  const next = value.value.trim();
  if (!next) return;
  value.value = "";
  adjustHeight();
  focusInput();
  void chat.sendMessage(next);
}
</script>
