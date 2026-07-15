<template>
  <article class="trace-list-stat-card">
    <div class="trace-list-stat-icon" :class="`is-${tone}`">
      <component :is="icon" class="h-4 w-4" />
    </div>
    <div class="trace-list-stat-content">
      <p class="trace-list-stat-title">{{ title }}</p>
      <div class="trace-list-stat-value-row">
        <p class="trace-list-stat-value">{{ value }}</p>
        <span v-if="unit" class="trace-list-stat-unit">{{ unit }}</span>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import type { Component } from "vue";

export type StatCardTone = "cyan" | "emerald" | "indigo" | "amber" | "sky" | "rose";

defineProps<{
  title: string;
  value: string;
  unit?: string;
  icon: Component;
  tone: StatCardTone;
}>();
</script>
