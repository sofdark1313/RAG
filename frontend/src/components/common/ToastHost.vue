<template>
  <div class="pointer-events-none fixed right-4 top-4 z-[100] flex w-[min(360px,calc(100vw-32px))] flex-col gap-2">
    <div
      v-for="item in items"
      :key="item.id"
      class="pointer-events-auto rounded-lg border bg-white px-4 py-3 text-sm shadow-lg"
      :class="{
        'border-emerald-200 text-emerald-800': item.type === 'success',
        'border-red-200 text-red-700': item.type === 'error',
        'border-slate-200 text-slate-700': item.type === 'info'
      }"
    >
      {{ item.message }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from "vue";

import { subscribeToast, type ToastMessage } from "@/utils/toast";

const items = ref<ToastMessage[]>([]);

const unsubscribe = subscribeToast((message) => {
  items.value = [message, ...items.value].slice(0, 4);
  window.setTimeout(() => {
    items.value = items.value.filter((item) => item.id !== message.id);
  }, 3200);
});

onBeforeUnmount(unsubscribe);
</script>
