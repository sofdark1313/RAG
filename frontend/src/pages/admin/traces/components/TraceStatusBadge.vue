<template>
  <span class="ui-badge trace-list-status-badge border" :data-variant="statusBadgeVariant(status)">
    <component
      :is="statusIcon"
      class="h-3 w-3"
      :class="{ 'animate-spin': normalizedStatus === 'running' }"
    />
    <span>{{ statusLabel(status) }}</span>
  </span>
</template>

<script setup lang="ts">
import { CheckCircle2, Loader2, XCircle } from "lucide-vue-next";
import { computed } from "vue";

import { normalizeStatus, statusBadgeVariant, statusLabel } from "@/pages/admin/traces/traceUtils";

const props = defineProps<{
  status?: string | null;
}>();

const normalizedStatus = computed(() => normalizeStatus(props.status));
const statusIcon = computed(() => {
  if (normalizedStatus.value === "failed" || normalizedStatus.value === "timeout") {
    return XCircle;
  }
  if (normalizedStatus.value === "running") return Loader2;
  return CheckCircle2;
});
</script>
