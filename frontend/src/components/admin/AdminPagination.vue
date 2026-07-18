<template>
  <div v-if="total > 0" class="admin-pagination">
    <span>共 {{ total }} 条</span>
    <div class="flex items-center gap-2">
      <button
        class="ui-button pagination-button"
        data-variant="outline"
        type="button"
        :disabled="current <= 1"
        @click="$emit('change', current - 1)"
      >
        上一页
      </button>
      <span class="min-w-16 text-center tabular-nums">{{ current }} / {{ Math.max(pages, 1) }}</span>
      <button
        class="ui-button pagination-button"
        data-variant="outline"
        type="button"
        :disabled="current >= pages"
        @click="$emit('change', current + 1)"
      >
        下一页
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  current: number;
  pages: number;
  total: number;
}>();

defineEmits<{
  change: [page: number];
}>();
</script>

<style scoped>
.admin-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 16px;
  color: #64748b;
  font-size: 13px;
}

.pagination-button {
  min-width: 72px;
  padding: 7px 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #334155;
  font-size: 13px;
}

.pagination-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
</style>
