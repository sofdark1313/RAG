<template>
  <div
    class="message-feedback"
    :class="{ 'is-visible': alwaysVisible || held }"
    @mouseenter="cancelHideTimer"
    @mouseleave="scheduleHide"
  >
    <button type="button" aria-label="复制内容" @click="copyContent"><Copy :size="16" /></button>
    <button
      type="button"
      aria-label="点赞"
      :class="{ 'is-liked': feedback === 'like' }"
      @click="handleFeedback('like')"
    >
      <ThumbsUp :size="16" />
    </button>
    <button
      type="button"
      aria-label="点踩"
      :class="{ 'is-disliked': feedback === 'dislike' }"
      @click="handleFeedback('dislike')"
    >
      <ThumbsDown :size="16" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { Copy, ThumbsDown, ThumbsUp } from "lucide-vue-next";
import { onBeforeUnmount, ref } from "vue";

import { useChatStore } from "@/stores/chat";
import type { FeedbackValue } from "@/types";
import { toast } from "@/utils/toast";

const props = defineProps<{
  messageId: string;
  feedback: FeedbackValue;
  content: string;
  alwaysVisible?: boolean;
}>();

const chat = useChatStore();
const held = ref(false);
let hideTimer: ReturnType<typeof setTimeout> | null = null;

function cancelHideTimer() {
  if (hideTimer) clearTimeout(hideTimer);
  hideTimer = null;
}

function markInteracted() {
  cancelHideTimer();
  held.value = true;
}

function scheduleHide() {
  if (!held.value) return;
  cancelHideTimer();
  hideTimer = setTimeout(() => {
    held.value = false;
    hideTimer = null;
  }, 3000);
}

function handleFeedback(value: Exclude<FeedbackValue, null>) {
  markInteracted();
  void chat.submitFeedback(props.messageId, props.feedback === value ? null : value);
}

async function copyContent() {
  markInteracted();
  try {
    await navigator.clipboard.writeText(props.content);
    toast.success("复制成功");
  } catch {
    toast.error("复制失败");
  }
}

onBeforeUnmount(cancelHideTimer);
</script>
