<template>
  <AdminModal :open="open" :title="mode === 'create' ? '新建流水线' : '编辑流水线'" description="配置节点顺序与处理逻辑" width="880px" @close="$emit('close')">
    <div class="pipeline-form">
      <label class="form-field"><span>流水线名称</span><input v-model="name" class="ui-input form-control" maxlength="60" placeholder="例如：通用文档通道" /></label>
      <label class="form-field"><span>描述</span><textarea v-model="description" class="ui-textarea textarea-control" rows="3" placeholder="说明流水线用途或流程"></textarea></label>

      <div class="mode-row">
        <strong>节点配置</strong>
        <div class="segmented-control">
          <button type="button" :class="{ active: nodeMode === 'form' }" @click="switchMode('form')">表单配置</button>
          <button type="button" :class="{ active: nodeMode === 'json' }" @click="switchMode('json')">JSON配置</button>
        </div>
      </div>

      <label v-if="nodeMode === 'json'" class="form-field">
        <span>节点配置（JSON 数组）</span>
        <textarea v-model="nodesJson" class="ui-textarea json-editor" rows="18" placeholder='[{"nodeId":"fetch","nodeType":"fetcher","settings":{},"nextNodeId":"parse"}]'></textarea>
      </label>

      <div v-else class="node-list">
        <div v-if="nodes.length === 0" class="empty-node">暂无节点，请添加节点配置</div>
        <article v-for="(node, index) in nodes" :key="node.id" class="node-card">
          <header class="node-card__header">
            <div><span class="node-type-badge">{{ node.nodeType }}</span><span>节点 {{ index + 1 }}</span></div>
            <button class="danger-link" type="button" @click="removeNode(node.id)">删除</button>
          </header>

          <div class="form-grid">
            <label class="form-field"><span>节点ID</span><input v-model="node.nodeId" class="ui-input form-control" placeholder="例如：fetch" /></label>
            <label class="form-field"><span>节点类型</span><select v-model="node.nodeType" class="ui-input form-control"><option v-for="option in nodeTypeOptions" :key="option" :value="option">{{ option }}</option></select></label>
            <label class="form-field"><span>下一节点ID</span><input v-model="node.nextNodeId" class="ui-input form-control" placeholder="例如：parse" /></label>
          </div>

          <div v-if="node.nodeType === 'fetcher'" class="muted-panel">Fetcher 无额外配置</div>

          <label v-if="node.nodeType === 'parser'" class="form-field">
            <span>解析规则（JSON）</span>
            <textarea v-model="node.parser.rulesJson" class="ui-textarea textarea-control" rows="5" placeholder='[{"mimeType":"PDF","options":{}}]'></textarea>
          </label>

          <div v-if="node.nodeType === 'chunker'" class="form-grid">
            <label class="form-field"><span>分块策略</span><select v-model="node.chunker.strategy" class="ui-input form-control"><option value="fixed_size">fixed_size</option><option value="structure_aware">structure_aware</option></select></label>
            <label class="form-field"><span>Chunk Size</span><input v-model="node.chunker.chunkSize" class="ui-input form-control" type="number" placeholder="例如：512" /></label>
            <label class="form-field"><span>Overlap Size</span><input v-model="node.chunker.overlapSize" class="ui-input form-control" type="number" placeholder="例如：128" /></label>
            <label class="form-field"><span>自定义分隔符</span><input v-model="node.chunker.separator" class="ui-input form-control" placeholder="可选" /></label>
          </div>

          <div v-if="node.nodeType === 'enhancer'" class="task-section">
            <label class="form-field"><span>模型ID</span><input v-model="node.enhancer.modelId" class="ui-input form-control" placeholder="可选" /></label>
            <TaskEditor title="增强任务" :tasks="node.enhancer.tasks" :options="enhancerTaskOptions" default-type="context_enhance" @add="node.enhancer.tasks.push(createTask('context_enhance'))" @remove="removeTask(node.enhancer.tasks, $event)" />
          </div>

          <div v-if="node.nodeType === 'enricher'" class="task-section">
            <div class="form-grid">
              <label class="form-field"><span>模型ID</span><input v-model="node.enricher.modelId" class="ui-input form-control" placeholder="可选" /></label>
              <label class="form-field"><span>附加文档元数据</span><select v-model="node.enricher.attachDocumentMetadata" class="ui-input form-control"><option :value="true">是</option><option :value="false">否</option></select></label>
            </div>
            <TaskEditor title="富集任务" :tasks="node.enricher.tasks" :options="enricherTaskOptions" default-type="keywords" @add="node.enricher.tasks.push(createTask('keywords'))" @remove="removeTask(node.enricher.tasks, $event)" />
          </div>

          <div v-if="node.nodeType === 'indexer'" class="form-grid">
            <label class="form-field"><span>Embedding 模型</span><input v-model="node.indexer.embeddingModel" class="ui-input form-control" placeholder="可选" /></label>
            <label class="form-field"><span>元数据字段</span><input v-model="node.indexer.metadataFields" class="ui-input form-control" placeholder="用逗号分隔，如 keywords,summary" /></label>
          </div>

          <label class="form-field"><span>条件（JSON / SpEL，可选）</span><textarea v-model="node.condition" class="ui-textarea textarea-control" rows="2" placeholder='{"field":"source_type","op":"eq","value":"file"} 或 #context.source.type == "file"'></textarea></label>
        </article>
        <button class="ui-button add-node-button" data-variant="outline" type="button" @click="nodes.push(createNode())"><Plus class="h-4 w-4" />添加节点</button>
      </div>
    </div>
    <template #footer>
      <button class="ui-button modal-button" data-variant="outline" type="button" :disabled="saving" @click="$emit('close')">取消</button>
      <button class="ui-button modal-button" data-variant="default" type="button" :disabled="saving" @click="handleSubmit">{{ saving ? "保存中..." : "保存" }}</button>
    </template>
  </AdminModal>
</template>

<script lang="ts">
import { defineComponent, type PropType } from "vue";
import { Plus, Trash2 } from "lucide-vue-next";

interface EditorTask {
  id: string;
  type: string;
  systemPrompt: string;
  userPromptTemplate: string;
}

export const TaskEditor = defineComponent({
  name: "TaskEditor",
  components: { Plus, Trash2 },
  props: {
    title: { type: String, required: true },
    tasks: { type: Array as PropType<EditorTask[]>, required: true },
    options: { type: Array as PropType<string[]>, required: true },
    defaultType: { type: String, required: true }
  },
  emits: ["add", "remove"],
  template: `<div class="task-editor"><div class="task-editor__header"><strong>{{ title }}</strong><button class="ui-button task-add" data-variant="outline" type="button" @click="$emit('add')"><Plus class="h-4 w-4" />添加任务</button></div><div v-if="tasks.length === 0" class="empty-task">暂无任务</div><article v-for="(task, index) in tasks" :key="task.id" class="task-card"><header><span>任务 {{ index + 1 }}</span><button type="button" title="删除任务" @click="$emit('remove', task.id)"><Trash2 class="h-4 w-4" /></button></header><label class="form-field"><span>任务类型</span><select v-model="task.type" class="ui-input form-control"><option v-for="option in options" :key="option" :value="option">{{ option }}</option></select></label><label class="form-field"><span>System Prompt</span><textarea v-model="task.systemPrompt" class="ui-textarea textarea-control" rows="2" placeholder="可选"></textarea></label><label class="form-field"><span>User Prompt 模板</span><textarea v-model="task.userPromptTemplate" class="ui-textarea textarea-control" rows="2" placeholder="可选"></textarea></label></article></div>`
});
</script>

<script setup lang="ts">
import { Plus } from "lucide-vue-next";
import { ref, watch } from "vue";

import AdminModal from "@/components/admin/AdminModal.vue";
import type { IngestionPipeline, IngestionPipelineNode, IngestionPipelinePayload } from "@/services/ingestionService";
import { toast } from "@/utils/toast";

type PipelineNodeType = "fetcher" | "parser" | "enhancer" | "chunker" | "enricher" | "indexer";
interface EnhancerTaskForm { id: string; type: string; systemPrompt: string; userPromptTemplate: string; }
interface PipelineNodeForm { id: string; nodeId: string; nodeType: PipelineNodeType; nextNodeId: string; condition: string; chunker: { strategy: string; chunkSize: string; overlapSize: string; separator: string }; enhancer: { modelId: string; tasks: EnhancerTaskForm[] }; enricher: { modelId: string; attachDocumentMetadata: boolean; tasks: EnhancerTaskForm[] }; parser: { rulesJson: string }; indexer: { embeddingModel: string; metadataFields: string }; }

const props = defineProps<{ open: boolean; mode: "create" | "edit"; pipeline: IngestionPipeline | null }>();
const emit = defineEmits<{ close: []; submit: [payload: IngestionPipelinePayload] }>();
const nodeTypeOptions: PipelineNodeType[] = ["fetcher", "parser", "enhancer", "chunker", "enricher", "indexer"];
const enhancerTaskOptions = ["context_enhance", "keywords", "questions", "metadata"];
const enricherTaskOptions = ["keywords", "summary", "metadata"];
const name = ref("");
const description = ref("");
const nodeMode = ref<"form" | "json">("form");
const nodesJson = ref("");
const nodes = ref<PipelineNodeForm[]>([]);
const saving = ref(false);

function localId() { return `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`; }
function createTask(type: string): EnhancerTaskForm { return { id: localId(), type, systemPrompt: "", userPromptTemplate: "" }; }
function createNode(nodeType: PipelineNodeType = "fetcher"): PipelineNodeForm { return { id: localId(), nodeId: "", nodeType, nextNodeId: "", condition: "", chunker: { strategy: "structure_aware", chunkSize: "", overlapSize: "", separator: "" }, enhancer: { modelId: "", tasks: [] }, enricher: { modelId: "", attachDocumentMetadata: true, tasks: [] }, parser: { rulesJson: "" }, indexer: { embeddingModel: "", metadataFields: "" } }; }
function mapTasks(value: unknown): EnhancerTaskForm[] { if (!Array.isArray(value)) return []; return value.map((task) => ({ id: localId(), type: String((task as { type?: string }).type || ""), systemPrompt: String((task as { systemPrompt?: string }).systemPrompt || ""), userPromptTemplate: String((task as { userPromptTemplate?: string }).userPromptTemplate || "") })); }
function buildNodeForm(node: IngestionPipelineNode): PipelineNodeForm { const settings = node.settings || {}; const type = (node.nodeType as PipelineNodeType) || "fetcher"; const tasks = mapTasks(settings.tasks); return { ...createNode(type), nodeId: node.nodeId || "", nextNodeId: node.nextNodeId || "", condition: node.condition ? (typeof node.condition === "string" ? node.condition : JSON.stringify(node.condition, null, 2)) : "", chunker: { strategy: String(settings.strategy || "structure_aware"), chunkSize: settings.chunkSize == null ? "" : String(settings.chunkSize), overlapSize: settings.overlapSize == null ? "" : String(settings.overlapSize), separator: String(settings.separator || "") }, enhancer: { modelId: String(settings.modelId || ""), tasks: type === "enhancer" ? tasks : [] }, enricher: { modelId: String(settings.modelId || ""), attachDocumentMetadata: typeof settings.attachDocumentMetadata === "boolean" ? settings.attachDocumentMetadata : true, tasks: type === "enricher" ? tasks : [] }, parser: { rulesJson: Array.isArray(settings.rules) ? JSON.stringify(settings.rules, null, 2) : "" }, indexer: { embeddingModel: String(settings.embeddingModel || ""), metadataFields: Array.isArray(settings.metadataFields) ? settings.metadataFields.join(", ") : "" } }; }
function removeNode(id: string) { nodes.value = nodes.value.filter((node) => node.id !== id); }
function removeTask(tasks: EnhancerTaskForm[], id: string) { const index = tasks.findIndex((task) => task.id === id); if (index >= 0) tasks.splice(index, 1); }
function parseCondition(raw: string): unknown { const value = raw.trim(); if (!value) return null; return value.startsWith("{") || value.startsWith("[") ? JSON.parse(value) : value; }
function buildSettings(node: PipelineNodeForm): Record<string, unknown> | undefined {
  if (node.nodeType === "chunker") { if (!node.chunker.strategy) throw new Error("分块节点需要选择 strategy"); const chunkSize = node.chunker.chunkSize.trim() ? Number(node.chunker.chunkSize) : undefined; const overlapSize = node.chunker.overlapSize.trim() ? Number(node.chunker.overlapSize) : undefined; if (chunkSize !== undefined && Number.isNaN(chunkSize)) throw new Error("chunkSize 必须是数字"); if (overlapSize !== undefined && Number.isNaN(overlapSize)) throw new Error("overlapSize 必须是数字"); return { strategy: node.chunker.strategy, chunkSize, overlapSize, separator: node.chunker.separator.trim() || undefined }; }
  if (node.nodeType === "enhancer" || node.nodeType === "enricher") { const group = node.nodeType === "enhancer" ? node.enhancer : node.enricher; const result: Record<string, unknown> = {}; if (group.modelId.trim()) result.modelId = group.modelId.trim(); const tasks = group.tasks.filter((task) => task.type).map((task) => ({ type: task.type, systemPrompt: task.systemPrompt.trim() || undefined, userPromptTemplate: task.userPromptTemplate.trim() || undefined })); if (tasks.length) result.tasks = tasks; if (node.nodeType === "enricher") result.attachDocumentMetadata = node.enricher.attachDocumentMetadata; return Object.keys(result).length ? result : undefined; }
  if (node.nodeType === "parser") { if (!node.parser.rulesJson.trim()) return undefined; const parsed: unknown = JSON.parse(node.parser.rulesJson); if (Array.isArray(parsed)) return { rules: parsed }; if (parsed && typeof parsed === "object" && Array.isArray((parsed as { rules?: unknown }).rules)) return { rules: (parsed as { rules: unknown[] }).rules }; throw new Error("解析规则必须是数组或包含 rules 字段的对象"); }
  if (node.nodeType === "indexer") { const result: Record<string, unknown> = {}; if (node.indexer.embeddingModel.trim()) result.embeddingModel = node.indexer.embeddingModel.trim(); const fields = node.indexer.metadataFields.split(",").map((field) => field.trim()).filter(Boolean); if (fields.length) result.metadataFields = fields; return Object.keys(result).length ? result : undefined; }
  return undefined;
}
function buildNodesPayload(source: PipelineNodeForm[]): IngestionPipelinePayload["nodes"] { return source.map((node) => { if (!node.nodeId.trim()) throw new Error("节点ID不能为空"); const condition = parseCondition(node.condition); return { nodeId: node.nodeId.trim(), nodeType: node.nodeType, settings: buildSettings(node) || null, condition: condition as Record<string, unknown> | null, nextNodeId: node.nextNodeId.trim() || null }; }); }
function switchMode(next: "form" | "json") { if (next === nodeMode.value) return; try { if (next === "json") nodesJson.value = JSON.stringify(buildNodesPayload(nodes.value), null, 2); else { const parsed: unknown = nodesJson.value.trim() ? JSON.parse(nodesJson.value) : []; if (!Array.isArray(parsed)) throw new Error("节点配置必须是JSON数组"); nodes.value = parsed.map((item) => buildNodeForm(item as IngestionPipelineNode)); } nodeMode.value = next; } catch (error) { toast.error(error instanceof Error ? error.message : "节点配置错误"); } }
async function handleSubmit() { if (!name.value.trim()) return toast.error("请输入流水线名称"); saving.value = true; try { let payloadNodes: IngestionPipelinePayload["nodes"] = []; if (nodeMode.value === "json") { const parsed: unknown = nodesJson.value.trim() ? JSON.parse(nodesJson.value) : []; if (!Array.isArray(parsed)) throw new Error("节点配置必须是JSON数组"); payloadNodes = parsed.map((item) => { const node = item as Record<string, unknown>; if (!String(node.nodeId || "").trim() || !String(node.nodeType || "").trim()) throw new Error("每个节点必须包含 nodeId 与 nodeType"); return { nodeId: String(node.nodeId).trim(), nodeType: String(node.nodeType).trim(), settings: (node.settings as Record<string, unknown>) || null, condition: (node.condition as Record<string, unknown>) || null, nextNodeId: node.nextNodeId ? String(node.nextNodeId) : null }; }); } else payloadNodes = buildNodesPayload(nodes.value); emit("submit", { name: name.value.trim(), description: description.value.trim() || undefined, nodes: payloadNodes }); } catch (error) { toast.error(error instanceof Error ? error.message : "节点配置错误"); } finally { saving.value = false; } }

watch(() => props.open, (open) => { if (!open) return; name.value = props.pipeline?.name || ""; description.value = props.pipeline?.description || ""; nodes.value = (props.pipeline?.nodes || []).map(buildNodeForm); nodesJson.value = props.pipeline?.nodes?.length ? JSON.stringify(props.pipeline.nodes, null, 2) : ""; nodeMode.value = "form"; }, { immediate: true });
</script>

<style scoped>
.pipeline-form,.node-list,.task-section,.task-editor{display:grid;gap:16px}.form-field{display:grid;gap:7px;color:#334155;font-size:13px;font-weight:500}.form-control{width:100%;height:40px;padding:0 12px;border:1px solid #e2e8f0;font-size:14px}.textarea-control,.json-editor{width:100%;resize:vertical;border:1px solid #e2e8f0;padding:10px 12px;color:#334155;font-family:inherit;font-size:13px;line-height:1.55}.json-editor{font-family:ui-monospace,SFMono-Regular,Menlo,monospace}.mode-row,.node-card__header,.task-editor__header,.task-card header{display:flex;align-items:center;justify-content:space-between;gap:12px}.mode-row{font-size:13px}.segmented-control{display:flex;border:1px solid #e2e8f0;border-radius:8px;padding:3px;background:#f8fafc}.segmented-control button{border-radius:6px;padding:6px 10px;color:#64748b;font-size:12px}.segmented-control button.active{background:#4f46e5;color:#fff}.empty-node,.empty-task{border:1px dashed #cbd5e1;border-radius:8px;padding:20px;color:#64748b;font-size:13px;text-align:center}.node-card{display:grid;gap:16px;border:1px solid #e2e8f0;border-radius:8px;padding:16px}.node-card__header{color:#64748b;font-size:12px}.node-card__header>div{display:flex;align-items:center;gap:8px}.node-type-badge{border:1px solid #e2e8f0;border-radius:999px;padding:3px 8px;color:#475569}.danger-link{color:#dc2626}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.muted-panel{border-radius:8px;background:#f8fafc;padding:12px;color:#64748b;font-size:13px}.add-node-button,.modal-button,.task-add{display:inline-flex;align-items:center;justify-content:center;gap:7px;border:1px solid #e2e8f0}.add-node-button{justify-self:start;height:38px;padding:0 13px;font-size:13px}.modal-button{min-width:76px;height:38px;padding:0 14px;font-size:13px}.task-add{height:34px;padding:0 10px;font-size:12px}.task-card{display:grid;gap:12px;border:1px solid #e2e8f0;border-radius:8px;padding:13px}.task-card header{color:#64748b;font-size:11px}.task-card header button{color:#dc2626}@media(max-width:640px){.form-grid{grid-template-columns:1fr}.mode-row{align-items:flex-start;flex-direction:column}.segmented-control{width:100%}.segmented-control button{flex:1}}
</style>
