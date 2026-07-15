<template>
  <time :datetime="value || undefined" :title="fullTime">{{ relativeTime }}</time>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  value?: string | null;
}>();

const date = computed(() => {
  if (!props.value) return null;
  const parsed = new Date(props.value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
});

const fullTime = computed(() => date.value?.toLocaleString("zh-CN") || props.value || "");

const relativeTime = computed(() => {
  if (!props.value) return "-";
  if (!date.value) return props.value;
  const diff = Date.now() - date.value.getTime();
  const minutes = Math.floor(diff / 60_000);
  const hours = Math.floor(diff / 3_600_000);
  const days = Math.floor(diff / 86_400_000);
  if (minutes < 1) return "刚刚";
  if (minutes < 60) return `${minutes} 分钟前`;
  if (hours < 24) return `${hours} 小时前`;
  if (days < 7) return `${days} 天前`;
  return date.value.toLocaleDateString("zh-CN");
});
</script>
