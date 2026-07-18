<template>
  <div
    v-if="items.length >= 2"
    class="question-rail"
    @mouseenter="expanded = true"
    @mouseleave="expanded = false"
  >
    <div class="question-rail__panel" :class="{ 'is-expanded': expanded }">
      <ul class="question-rail__list sidebar-scroll">
        <li v-for="item in items" :key="item.id">
          <button type="button" :aria-label="item.text" @click="$emit('select', item.id)">
            <span v-if="expanded" :class="{ 'is-active': item.id === activeId }">{{ item.text }}</span>
            <i :class="{ 'is-active': item.id === activeId }"></i>
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";

export interface QuestionRailItem {
  id: string;
  text: string;
}

defineProps<{ items: QuestionRailItem[]; activeId: string | null }>();
defineEmits<{ (event: "select", messageId: string): void }>();

const expanded = ref(false);
</script>
