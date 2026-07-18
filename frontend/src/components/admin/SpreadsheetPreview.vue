<template>
  <div class="spreadsheet-preview">
    <div v-if="loading" class="spreadsheet-preview__state">加载中...</div>
    <div v-else-if="failed" class="spreadsheet-preview__state">表格预览失败</div>
    <div ref="containerRef" class="h-full w-full"></div>
  </div>
</template>

<script setup lang="ts">
import jsPreviewExcel, { type JsExcelPreview } from "@js-preview/excel";
import "@js-preview/excel/lib/index.css";
import { onBeforeUnmount, ref, watch } from "vue";

import { fetchDocumentFile } from "@/services/knowledgeService";

const props = defineProps<{
  docId: string;
}>();

const containerRef = ref<HTMLDivElement | null>(null);
const loading = ref(true);
const failed = ref(false);
let previewer: JsExcelPreview | null = null;
let requestId = 0;

async function renderPreview() {
  const currentRequest = ++requestId;
  loading.value = true;
  failed.value = false;
  previewer?.destroy();
  previewer = null;

  try {
    const buffer = await fetchDocumentFile(props.docId);
    if (currentRequest !== requestId || !containerRef.value) return;
    previewer = jsPreviewExcel.init(containerRef.value);
    await previewer.preview(buffer);
  } catch {
    if (currentRequest === requestId) {
      failed.value = true;
    }
  } finally {
    if (currentRequest === requestId) {
      loading.value = false;
    }
  }
}

watch(
  [() => props.docId, containerRef],
  ([docId, container]) => {
    if (docId && container) void renderPreview();
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  requestId += 1;
  previewer?.destroy();
});
</script>

<style scoped>
.spreadsheet-preview {
  position: relative;
  min-height: 560px;
  height: 100%;
  overflow: hidden;
}

.spreadsheet-preview__state {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: grid;
  place-items: center;
  background: rgb(255 255 255 / 0.86);
  color: #64748b;
  font-size: 14px;
}
</style>
