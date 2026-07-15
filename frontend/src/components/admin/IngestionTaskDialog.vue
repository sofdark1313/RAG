<template>
  <AdminModal
    :open="open"
    :title="variant === 'upload' ? '上传文件并进入通道' : '新建通道任务'"
    :description="variant === 'upload' ? '上传文件后立即触发通道任务' : '支持 Local File / URL / Feishu / S3 来源，Local File 会直接上传文件'"
    :width="variant === 'upload' ? '520px' : '720px'"
    @close="$emit('close')"
  >
    <div class="task-form">
      <label class="form-field">
        <span>流水线</span>
        <select v-model="pipelineId" class="ui-input form-control">
          <option value="" disabled>选择流水线</option>
          <option v-for="pipeline in pipelines" :key="pipeline.id" :value="pipeline.id">{{ pipeline.name }}</option>
        </select>
      </label>

      <template v-if="variant === 'task'">
        <div class="form-grid">
          <label class="form-field">
            <span>来源类型</span>
            <select v-model="sourceType" class="ui-input form-control">
              <option value="file">Local File</option>
              <option value="url">Remote URL</option>
              <option value="feishu">Feishu</option>
              <option value="s3">S3</option>
            </select>
          </label>
          <label v-if="sourceType !== 'file'" class="form-field"><span>文件名（可选）</span><input v-model="fileName" class="ui-input form-control" placeholder="例如：doc.md" /></label>
          <label v-else class="form-field"><span>本地文件</span><input class="ui-input file-input" type="file" @change="selectFile" /></label>
        </div>
        <label v-if="sourceType !== 'file'" class="form-field"><span>来源地址</span><input v-model="location" class="ui-input form-control" :placeholder="sourceMeta.placeholder" /><small>{{ sourceMeta.hint }}</small></label>
        <label v-if="showCredentials" class="form-field"><span>访问凭证（JSON，可选）</span><textarea v-model="credentialsJson" class="ui-textarea textarea-control" rows="4" :placeholder="sourceMeta.credentials || '{&quot;token&quot;:&quot;xxx&quot;}'"></textarea><small v-if="sourceMeta.credentials">示例：{{ sourceMeta.credentials }}</small></label>
        <label class="form-field"><span>任务元数据（JSON，可选）</span><textarea v-model="metadataJson" class="ui-textarea textarea-control" rows="4" placeholder='{"source":"manual"}'></textarea></label>
      </template>
      <label v-else class="form-field"><span>文件</span><input class="ui-input file-input" type="file" @change="selectFile" /></label>
    </div>
    <template #footer>
      <button class="ui-button modal-button" data-variant="outline" type="button" :disabled="saving" @click="$emit('close')">取消</button>
      <button class="ui-button modal-button" data-variant="default" type="button" :disabled="saving" @click="handleSubmit">{{ saving ? (variant === 'upload' ? '上传中...' : '创建中...') : (variant === 'upload' ? '上传' : '创建任务') }}</button>
    </template>
  </AdminModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";

import AdminModal from "@/components/admin/AdminModal.vue";
import type { IngestionPipeline, IngestionTaskCreatePayload } from "@/services/ingestionService";
import { getSystemSettings } from "@/services/settingsService";
import { toast } from "@/utils/toast";

const props = defineProps<{ open: boolean; variant: "task" | "upload"; pipelines: IngestionPipeline[] }>();
const emit = defineEmits<{ close: []; submitTask: [payload: IngestionTaskCreatePayload]; upload: [pipelineId: string, file: File] }>();
const pipelineId = ref("");
const sourceType = ref("file");
const location = ref("");
const fileName = ref("");
const credentialsJson = ref("");
const metadataJson = ref("");
const file = ref<File | null>(null);
const maxFileSize = ref(50 * 1024 * 1024);
const saving = ref(false);

const sourceMeta = computed(() => {
  if (sourceType.value === "feishu") return { placeholder: "https://open.feishu.cn/...", hint: "填写飞书文档链接", credentials: '{"tenantAccessToken":"..."} 或 {"app_id":"...","app_secret":"..."}' };
  if (sourceType.value === "s3") return { placeholder: "s3://bucket/key", hint: "填写 S3 路径，例如 s3://biz/file.md", credentials: "" };
  return { placeholder: "https://example.com/file.pdf", hint: "支持 http/https 链接", credentials: '{"token":"xxx"} 或 {"Authorization":"Bearer xxx"}' };
});
const showCredentials = computed(() => sourceType.value === "url" || sourceType.value === "feishu");

function reset() { pipelineId.value = props.pipelines[0]?.id || ""; sourceType.value = "file"; location.value = ""; fileName.value = ""; credentialsJson.value = ""; metadataJson.value = ""; file.value = null; }
function selectFile(event: Event) { file.value = (event.target as HTMLInputElement).files?.[0] || null; }
function parseJson(value: string): Record<string, unknown> | undefined | null { if (!value.trim()) return undefined; try { const parsed: unknown = JSON.parse(value); return parsed && typeof parsed === "object" ? parsed as Record<string, unknown> : {}; } catch { return null; } }

function handleSubmit() {
  if (!pipelineId.value) return toast.error("请选择流水线");
  if (props.variant === "upload" || sourceType.value === "file") {
    if (!file.value) return toast.error("请选择文件");
    if (file.value.size > maxFileSize.value) return toast.error(`上传文件大小超过限制，最大允许 ${Math.floor(maxFileSize.value / 1024 / 1024)}MB`);
    saving.value = true; emit("upload", pipelineId.value, file.value); saving.value = false; return;
  }
  if (!location.value.trim()) return toast.error("请输入来源地址");
  const credentials = parseJson(credentialsJson.value); if (credentials === null) return toast.error("凭证JSON格式错误");
  const metadata = parseJson(metadataJson.value); if (metadata === null) return toast.error("元数据JSON格式错误");
  const stringCredentials = credentials ? Object.fromEntries(Object.entries(credentials).map(([key, value]) => [key, String(value)])) : undefined;
  const payload: IngestionTaskCreatePayload = { pipelineId: pipelineId.value, source: { type: sourceType.value.toUpperCase(), location: location.value.trim(), fileName: fileName.value.trim() || undefined, credentials: stringCredentials }, metadata };
  saving.value = true; emit("submitTask", payload); saving.value = false;
}

watch(() => props.open, async (open) => { if (!open) return; reset(); try { maxFileSize.value = (await getSystemSettings()).upload.maxFileSize; } catch { /* Use fallback. */ } }, { immediate: true });
</script>

<style scoped>
.task-form{display:grid;gap:17px}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.form-field{display:grid;gap:7px;color:#334155;font-size:13px;font-weight:500}.form-field small{color:#64748b;font-weight:400;line-height:1.5}.form-control,.file-input{width:100%;height:40px;padding:0 12px;border:1px solid #e2e8f0;font-size:14px}.file-input{padding:7px 10px}.textarea-control{width:100%;resize:vertical;border:1px solid #e2e8f0;padding:10px 12px;color:#334155;font-family:inherit;font-size:13px;line-height:1.55}.modal-button{display:inline-flex;min-width:76px;height:38px;align-items:center;justify-content:center;border:1px solid #e2e8f0;padding:0 14px;font-size:13px}@media(max-width:640px){.form-grid{grid-template-columns:1fr}}
</style>
