<template>
  <section class="welcome-screen">
    <div class="welcome-screen__mark">R</div>
    <h1 class="welcome-screen__title">有什么可以帮你？</h1>
    <p class="welcome-screen__description">我会从已授权的知识库中查找答案</p>

    <div class="welcome-screen__composer">
      <div class="chat-composer" :class="{ 'chat-composer--focused': focused }">
        <textarea
          ref="textareaRef"
          v-model="value"
          class="chat-composer__input"
          rows="1"
          :placeholder="deepThinkingEnabled ? '输入需要深入分析的问题' : '给 Ragent 发送消息'"
          aria-label="发送消息"
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
            <Brain :size="16" />深度思考
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
    </div>

    <div class="welcome-screen__prompts">
      <button
        v-for="preset in promptPresets"
        :key="preset.id || preset.title"
        type="button"
        class="welcome-prompt"
        :disabled="isStreaming"
        @click="usePreset(preset.prompt)"
      >
        <component :is="preset.icon" :size="16" />
        <span>
          <strong>{{ preset.title }}</strong>
          <small>{{ preset.description }}</small>
        </span>
        <ArrowUpRight :size="16" />
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ArrowUpRight, BookOpen, Brain, Check, Lightbulb, Send, Square } from "lucide-vue-next";
import { storeToRefs } from "pinia";
import { computed, nextTick, onMounted, ref } from "vue";
import type { Component } from "vue";

import { listSampleQuestions } from "@/services/sampleQuestionService";
import { useChatStore } from "@/stores/chat";

interface PromptPreset {
  id?: string;
  title: string;
  description: string;
  prompt: string;
  icon: Component;
}

const presetIcons = [BookOpen, Check, Lightbulb];
const defaultPresets: PromptPreset[] = [
  { title: "总结内容", description: "提炼重点与行动项", prompt: "请总结相关资料并列出关键要点。", icon: BookOpen },
  { title: "拆解任务", description: "整理步骤与优先级", prompt: "请把这个目标拆成可执行步骤，并给出优先级。", icon: Check },
  { title: "比较方案", description: "分析选项与取舍", prompt: "请给出可选方案，并比较各自的优缺点。", icon: Lightbulb }
];

const chat = useChatStore();
const { isStreaming, deepThinkingEnabled } = storeToRefs(chat);
const value = ref("");
const focused = ref(false);
const composing = ref(false);
const textareaRef = ref<HTMLTextAreaElement | null>(null);
const promptPresets = ref<PromptPreset[]>(defaultPresets);
const hasContent = computed(() => value.value.trim().length > 0);

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
    return;
  }
  const next = value.value.trim();
  if (!next) return;
  value.value = "";
  adjustHeight();
  void chat.sendMessage(next);
}

function usePreset(prompt: string) {
  value.value = prompt;
  adjustHeight();
  void nextTick(() => textareaRef.value?.focus());
}

onMounted(async () => {
  try {
    const data = await listSampleQuestions();
    const mapped = data
      .filter((item) => item.question?.trim())
      .slice(0, 4)
      .map((item, index): PromptPreset => {
        const prompt = item.question.trim();
        return {
          id: item.id,
          title: item.title?.trim() || (prompt.length > 12 ? `${prompt.slice(0, 12)}…` : prompt),
          description: item.description?.trim() || "从知识库中查找答案",
          prompt,
          icon: presetIcons[index % presetIcons.length]
        };
      });
    if (mapped.length) promptPresets.value = mapped;
  } catch {
    // Keep local presets when the recommendation service is unavailable.
  }
});
</script>
