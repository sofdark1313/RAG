<template>
  <div class="thinking-status" role="status">
    <LoaderCircle :size="16" />
    <span>{{ content?.trim() || "正在分析知识库…" }}</span>
    <span v-if="duration" class="thinking-status__duration">{{ duration }} 秒</span>
  </div>
</template>

<script setup lang="ts">
import { LoaderCircle } from "lucide-vue-next";

defineProps<{ content?: string; duration?: number }>();
</script>
