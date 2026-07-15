<template>
  <div class="prose prose-slate max-w-none" v-html="html"></div>
</template>

<script setup lang="ts">
import { computed } from "vue";

import { markdown } from "@/utils/markdown";

const props = defineProps<{
  content: string;
}>();

const html = computed(() => markdown.render(props.content || ""));
</script>
