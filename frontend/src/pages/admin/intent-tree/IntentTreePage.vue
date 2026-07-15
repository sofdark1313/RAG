<template>
  <div class="admin-page intent-tree-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">意图树配置</h1>
        <p class="admin-page-subtitle">配置意图层级、类型和节点关系</p>
      </div>
      <div class="admin-page-actions">
        <button
          class="ui-button action-button"
          data-variant="outline"
          type="button"
          :disabled="loading"
          @click="loadTree"
        >
          <RefreshCw :class="['h-4 w-4', { 'animate-spin': loading }]" />
          刷新
        </button>
        <button
          class="ui-button action-button admin-primary-gradient"
          data-variant="default"
          type="button"
          @click="openCreateDialog(null)"
        >
          <Plus class="h-4 w-4" />
          新建根节点
        </button>
      </div>
    </header>

    <p v-if="loadError" class="error-banner" role="alert">
      <span>{{ loadError }}</span>
      <button type="button" @click="loadTree">重新加载</button>
    </p>

    <div class="intent-tree-grid">
      <section class="ui-card">
        <div class="ui-card-header">
          <h2 class="ui-card-title">意图树结构</h2>
          <p class="ui-card-description">点击节点查看详情或进行编辑</p>
        </div>
        <div class="ui-card-content tree-card-content">
          <div v-if="loading" class="empty-state">加载中...</div>
          <div v-else-if="tree.length === 0" class="empty-state">暂无节点，请先创建</div>
          <div v-else class="tree-list">
            <div
              v-for="row in visibleRows"
              :key="row.node.intentCode"
              class="tree-row"
              :class="{ 'tree-row--selected': selectedCode === row.node.intentCode }"
              :style="{ paddingLeft: `${row.depth * 16 + 12}px` }"
              @click="selectedCode = row.node.intentCode"
            >
              <div class="tree-row__main">
                <button
                  v-if="row.hasChildren"
                  class="tree-toggle"
                  type="button"
                  :aria-label="row.expanded ? '收起节点' : '展开节点'"
                  @click.stop="toggleExpanded(row.node.intentCode)"
                >
                  <ChevronDown v-if="row.expanded" class="h-4 w-4" />
                  <ChevronRight v-else class="h-4 w-4" />
                </button>
                <span v-else class="tree-toggle-placeholder"></span>
                <span class="tree-node-name">{{ row.node.name }}</span>
                <span class="ui-badge" data-variant="outline">{{
                  resolveLevelLabel(row.node.level)
                }}</span>
                <span class="ui-badge" :data-variant="resolveKindVariant(row.node.kind)">{{
                  resolveKindLabel(row.node.kind)
                }}</span>
              </div>
              <span class="tree-row__code">{{ row.node.intentCode }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="ui-card">
        <div class="ui-card-header">
          <h2 class="ui-card-title">节点详情</h2>
          <p class="ui-card-description">查看并管理当前选择的节点</p>
        </div>
        <div class="ui-card-content detail-card-content">
          <div v-if="!selectedNode" class="empty-state">请选择左侧节点</div>
          <div v-else class="node-detail">
            <div class="node-detail__header">
              <div class="min-w-0">
                <div class="node-detail__title-row">
                  <h2>{{ selectedNode.name }}</h2>
                  <span class="ui-badge" data-variant="outline">{{
                    resolveLevelLabel(selectedNode.level)
                  }}</span>
                  <span class="ui-badge" :data-variant="resolveKindVariant(selectedNode.kind)">{{
                    resolveKindLabel(selectedNode.kind)
                  }}</span>
                  <span
                    class="ui-badge"
                    :data-variant="selectedNode.enabled === 0 ? 'secondary' : 'default'"
                  >
                    {{ selectedNode.enabled === 0 ? "停用" : "启用" }}
                  </span>
                </div>
                <p class="node-detail__code">{{ selectedNode.intentCode }}</p>
              </div>
              <div class="node-detail__actions">
                <button
                  class="ui-button detail-button"
                  data-variant="default"
                  type="button"
                  @click="openCreateDialog(selectedNode)"
                >
                  <Plus class="h-4 w-4" />
                  新建子节点
                </button>
                <button
                  class="ui-button detail-button"
                  data-variant="outline"
                  type="button"
                  @click="openEditDialog(selectedNode)"
                >
                  <Pencil class="h-4 w-4" />
                  编辑节点
                </button>
                <button
                  class="ui-button detail-button detail-button--danger"
                  data-variant="ghost"
                  type="button"
                  @click="deleteTarget = selectedNode"
                >
                  <Trash2 class="h-4 w-4" />
                  删除节点
                </button>
              </div>
            </div>

            <dl class="node-meta">
              <div>
                <dt>父节点</dt>
                <dd>{{ selectedNode.parentCode || "ROOT" }}</dd>
              </div>
              <div>
                <dt>排序</dt>
                <dd>{{ selectedNode.sortOrder ?? 0 }}</dd>
              </div>
              <div>
                <dt>Collection</dt>
                <dd>{{ selectedNode.collectionName || "-" }}</dd>
              </div>
              <div>
                <dt>节点 TopK</dt>
                <dd>{{ selectedNode.topK ?? "默认（全局）" }}</dd>
              </div>
            </dl>

            <div class="detail-block">
              <h3>描述</h3>
              <p>{{ selectedNode.description || "暂无描述" }}</p>
            </div>

            <div class="detail-block">
              <h3>示例问题</h3>
              <div v-if="selectedExamples.length" class="example-list">
                <span
                  v-for="item in selectedExamples"
                  :key="item"
                  class="ui-badge"
                  data-variant="secondary"
                  >{{ item }}</span
                >
              </div>
              <p v-else>暂无示例</p>
            </div>
          </div>
        </div>
      </section>
    </div>

    <Teleport to="body">
      <div v-if="dialogOpen" class="modal-layer" role="presentation" @mousedown.self="closeDialog">
        <section
          class="modal-panel node-form-dialog"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="dialogTitleId"
          @keydown.esc="closeDialog"
        >
          <header class="modal-header">
            <div>
              <h2 :id="dialogTitleId">
                {{ dialogMode === "create" ? "新建意图节点" : "编辑意图节点" }}
              </h2>
              <p>
                {{
                  dialogMode === "create"
                    ? "配置意图节点的层级、类型与描述信息"
                    : "更新节点基础信息"
                }}
              </p>
            </div>
            <button
              class="icon-button"
              type="button"
              aria-label="关闭"
              :disabled="saving"
              @click="closeDialog"
            >
              <X class="h-5 w-5" />
            </button>
          </header>

          <form class="node-form" @submit.prevent="submitNodeForm">
            <div class="form-grid">
              <label class="form-field">
                <span>节点名称</span>
                <input
                  v-model="form.name"
                  class="ui-input"
                  maxlength="50"
                  placeholder="例如：OA系统"
                />
                <small v-if="formErrors.name">{{ formErrors.name }}</small>
              </label>
              <label class="form-field">
                <span>意图标识</span>
                <input
                  v-model="form.intentCode"
                  class="ui-input"
                  maxlength="80"
                  placeholder="例如：biz-oa"
                  :disabled="dialogMode === 'edit'"
                />
                <small v-if="formErrors.intentCode">{{ formErrors.intentCode }}</small>
              </label>
              <label class="form-field">
                <span>层级</span>
                <select v-model.number="form.level" class="ui-select-trigger">
                  <option v-for="option in LEVEL_OPTIONS" :key="option.value" :value="option.value">
                    {{ option.label }} - {{ option.description }}
                  </option>
                </select>
              </label>
              <label class="form-field">
                <span>类型</span>
                <select v-model.number="form.kind" class="ui-select-trigger">
                  <option v-for="option in KIND_OPTIONS" :key="option.value" :value="option.value">
                    {{ option.label }} - {{ option.description }}
                  </option>
                </select>
              </label>
            </div>

            <label class="form-field">
              <span>父节点</span>
              <select v-model="form.parentCode" class="ui-select-trigger">
                <option
                  v-for="option in dialogParentOptions"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </option>
              </select>
            </label>

            <label v-if="dialogMode === 'create' && form.kind === 0" class="form-field">
              <span>知识库{{ form.level === 2 ? "（必填）" : "（可选）" }}</span>
              <select v-model="form.kbId" class="ui-select-trigger">
                <option value="">请选择知识库</option>
                <option v-for="kb in knowledgeBases" :key="kb.id" :value="kb.id">
                  {{ kb.name }} ({{ kb.collectionName }})
                </option>
              </select>
              <small v-if="formErrors.kbId">{{ formErrors.kbId }}</small>
            </label>

            <label v-if="dialogMode === 'edit' && form.kind === 0" class="form-field">
              <span>Collection 名称</span>
              <input
                v-model="form.collectionName"
                class="ui-input"
                placeholder="向量数据库 Collection 名称"
              />
            </label>

            <label v-if="form.kind === 2" class="form-field">
              <span>MCP 工具ID（必填）</span>
              <input v-model="form.mcpToolId" class="ui-input" placeholder="例如：sales_query" />
              <small v-if="formErrors.mcpToolId">{{ formErrors.mcpToolId }}</small>
            </label>

            <details class="form-section" open>
              <summary>描述与示例</summary>
              <div class="form-section__body">
                <label class="form-field">
                  <span>描述</span>
                  <textarea
                    v-model="form.description"
                    class="ui-textarea"
                    rows="3"
                    placeholder="节点的语义说明与适用场景"
                  ></textarea>
                </label>
                <label class="form-field">
                  <span>示例问题</span>
                  <textarea
                    v-model="form.examplesText"
                    class="ui-textarea"
                    rows="4"
                    placeholder="每行一个示例问题"
                  ></textarea>
                </label>
              </div>
            </details>

            <details class="form-section">
              <summary>Prompt 配置</summary>
              <div class="form-section__body">
                <label class="form-field">
                  <span>短规则片段（可选）</span>
                  <textarea
                    v-model="form.promptSnippet"
                    class="ui-textarea"
                    rows="3"
                    placeholder="多意图场景下的特定规则，会添加到整体提示词中"
                  ></textarea>
                </label>
                <label class="form-field">
                  <span>Prompt 模板（可选）</span>
                  <textarea
                    v-model="form.promptTemplate"
                    class="ui-textarea"
                    rows="4"
                    placeholder="场景专属完整提示词模板"
                  ></textarea>
                </label>
                <label v-if="form.kind === 2" class="form-field">
                  <span>参数提取提示词模板（MCP专属）</span>
                  <textarea
                    v-model="form.paramPromptTemplate"
                    class="ui-textarea"
                    rows="4"
                    placeholder="用于从用户输入中提取MCP工具参数"
                  ></textarea>
                </label>
              </div>
            </details>

            <details class="form-section">
              <summary>高级设置</summary>
              <div class="form-grid form-section__body">
                <label class="form-field">
                  <span>节点 TopK（可选）</span>
                  <input
                    v-model.number="form.topK"
                    class="ui-input"
                    type="number"
                    min="1"
                    placeholder="留空则使用全局 TopK"
                  />
                  <small v-if="formErrors.topK">{{ formErrors.topK }}</small>
                </label>
                <label class="form-field">
                  <span>排序</span>
                  <input v-model.number="form.sortOrder" class="ui-input" type="number" />
                  <small v-if="formErrors.sortOrder">{{ formErrors.sortOrder }}</small>
                </label>
                <label class="checkbox-field">
                  <input v-model="form.enabled" type="checkbox" />
                  <span>启用节点</span>
                </label>
              </div>
            </details>

            <footer class="modal-footer">
              <button
                class="ui-button modal-button"
                data-variant="outline"
                type="button"
                :disabled="saving"
                @click="closeDialog"
              >
                取消
              </button>
              <button
                class="ui-button modal-button"
                data-variant="default"
                type="submit"
                :disabled="saving"
              >
                {{
                  saving
                    ? dialogMode === "create"
                      ? "创建中..."
                      : "保存中..."
                    : dialogMode === "create"
                      ? "创建"
                      : "保存"
                }}
              </button>
            </footer>
          </form>
        </section>
      </div>

      <div
        v-if="deleteTarget"
        class="modal-layer"
        role="presentation"
        @mousedown.self="deleteTarget = null"
      >
        <section
          class="modal-panel confirm-dialog"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="delete-intent-title"
          @keydown.esc="deleteTarget = null"
        >
          <h2 id="delete-intent-title">确认删除节点？</h2>
          <p>节点 [{{ deleteTarget.name }}] 将被永久删除，无法恢复。</p>
          <footer class="modal-footer">
            <button
              class="ui-button modal-button"
              data-variant="outline"
              type="button"
              :disabled="deleting"
              @click="deleteTarget = null"
            >
              取消
            </button>
            <button
              class="ui-button modal-button danger-button"
              type="button"
              :disabled="deleting"
              @click="confirmDelete"
            >
              {{ deleting ? "删除中..." : "删除" }}
            </button>
          </footer>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ChevronDown, ChevronRight, Pencil, Plus, RefreshCw, Trash2, X } from "lucide-vue-next";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";

import type { KnowledgeBase } from "@/services/knowledgeService";
import { getKnowledgeBases } from "@/services/knowledgeService";
import type {
  IntentNodeCreatePayload,
  IntentNodeTree,
  IntentNodeUpdatePayload
} from "@/services/intentTreeService";
import {
  createIntentNode,
  deleteIntentNode,
  getIntentTree,
  updateIntentNode
} from "@/services/intentTreeService";
import { getErrorMessage } from "@/utils/error";
import { toast } from "@/utils/toast";

const ROOT_PARENT = "__ROOT__";
const LEVEL_OPTIONS = [
  { value: 0, label: "DOMAIN", description: "顶层领域" },
  { value: 1, label: "CATEGORY", description: "业务分类" },
  { value: 2, label: "TOPIC", description: "具体主题" }
];
const KIND_OPTIONS = [
  { value: 0, label: "KB", description: "知识库检索" },
  { value: 1, label: "SYSTEM", description: "系统交互" },
  { value: 2, label: "MCP", description: "工具调用" }
];

interface TreeOption {
  label: string;
  value: string;
  node: IntentNodeTree | null;
}

interface VisibleTreeRow {
  node: IntentNodeTree;
  depth: number;
  hasChildren: boolean;
  expanded: boolean;
}

interface NodeForm {
  name: string;
  intentCode: string;
  level: number;
  kind: number;
  parentCode: string;
  kbId: string;
  mcpToolId: string;
  collectionName: string;
  description: string;
  examplesText: string;
  topK: number | "";
  sortOrder: number | "";
  enabled: boolean;
  promptSnippet: string;
  promptTemplate: string;
  paramPromptTemplate: string;
}

type FormErrors = Partial<Record<keyof NodeForm, string>>;

const route = useRoute();
const tree = ref<IntentNodeTree[]>([]);
const loading = ref(true);
const loadError = ref("");
const selectedCode = ref<string | null>(null);
const expandedMap = reactive<Record<string, boolean>>({});
const knowledgeBases = ref<KnowledgeBase[]>([]);
const dialogOpen = ref(false);
const dialogMode = ref<"create" | "edit">("create");
const dialogParent = ref<IntentNodeTree | null>(null);
const editingNode = ref<IntentNodeTree | null>(null);
const saving = ref(false);
const deleteTarget = ref<IntentNodeTree | null>(null);
const deleting = ref(false);
const formErrors = reactive<FormErrors>({});
const dialogTitleId = "intent-node-dialog-title";

const form = reactive<NodeForm>({
  name: "",
  intentCode: "",
  level: 0,
  kind: 0,
  parentCode: ROOT_PARENT,
  kbId: "",
  mcpToolId: "",
  collectionName: "",
  description: "",
  examplesText: "",
  topK: "",
  sortOrder: 0,
  enabled: true,
  promptSnippet: "",
  promptTemplate: "",
  paramPromptTemplate: ""
});

const focusIntentCode = computed(() => {
  const value = route.query.intentCode;
  return typeof value === "string" && value.trim() ? value.trim() : null;
});

const selectedNode = computed(() => findNodeByCode(tree.value, selectedCode.value));
const selectedExamples = computed(() => parseExamples(selectedNode.value?.examples));
const treeOptions = computed(() => buildTreeOptions(tree.value));
const dialogParentOptions = computed(() => {
  const options =
    dialogMode.value === "edit" && editingNode.value
      ? treeOptions.value.filter((option) => option.value !== editingNode.value?.intentCode)
      : treeOptions.value;
  return [{ label: "ROOT", value: ROOT_PARENT, node: null }, ...options];
});

const visibleRows = computed<VisibleTreeRow[]>(() => {
  const rows: VisibleTreeRow[] = [];
  const walk = (nodes: IntentNodeTree[], depth: number) => {
    for (const node of nodes) {
      const hasChildren = Boolean(node.children?.length);
      const expanded = expandedMap[node.intentCode] !== false;
      rows.push({ node, depth, hasChildren, expanded });
      if (hasChildren && expanded) walk(node.children || [], depth + 1);
    }
  };
  walk(tree.value, 0);
  return rows;
});

function parseExamples(value?: string | null) {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    if (Array.isArray(parsed)) return parsed.map(String).filter(Boolean);
  } catch {
    // Plain text examples remain supported for older records.
  }
  return value
    .split("\n")
    .map((item) => item.trim())
    .filter(Boolean);
}

function buildTreeOptions(nodes: IntentNodeTree[], prefix = "", result: TreeOption[] = []) {
  for (const node of nodes) {
    const label = prefix ? `${prefix} > ${node.name}` : node.name;
    result.push({ label, value: node.intentCode, node });
    if (node.children?.length) buildTreeOptions(node.children, label, result);
  }
  return result;
}

function findNodeByCode(nodes: IntentNodeTree[], code: string | null): IntentNodeTree | null {
  if (!code) return null;
  for (const node of nodes) {
    if (node.intentCode === code) return node;
    const found = findNodeByCode(node.children || [], code);
    if (found) return found;
  }
  return null;
}

function resolveLevelLabel(value?: number | null) {
  return LEVEL_OPTIONS.find((option) => option.value === (value ?? 0))?.label ?? "UNKNOWN";
}

function resolveKindLabel(value?: number | null) {
  return KIND_OPTIONS.find((option) => option.value === (value ?? 0))?.label ?? "UNKNOWN";
}

function resolveKindVariant(value?: number | null) {
  const label = resolveKindLabel(value);
  return label === "MCP" ? "default" : label === "SYSTEM" ? "secondary" : "outline";
}

function toggleExpanded(code: string) {
  expandedMap[code] = !(expandedMap[code] !== false);
}

async function loadTree() {
  loading.value = true;
  loadError.value = "";
  try {
    const data = await getIntentTree();
    tree.value = data || [];
    if (focusIntentCode.value && findNodeByCode(tree.value, focusIntentCode.value)) {
      selectedCode.value = focusIntentCode.value;
    } else if (!findNodeByCode(tree.value, selectedCode.value)) {
      selectedCode.value = tree.value[0]?.intentCode ?? null;
    }
  } catch (error) {
    loadError.value = getErrorMessage(error, "加载意图树失败");
    toast.error(loadError.value);
    console.error(error);
  } finally {
    loading.value = false;
  }
}

async function loadKnowledgeBases() {
  try {
    knowledgeBases.value = await getKnowledgeBases();
  } catch (error) {
    console.error(error);
  }
}

function clearFormErrors() {
  for (const key of Object.keys(formErrors) as Array<keyof NodeForm>) delete formErrors[key];
}

function assignForm(values: NodeForm) {
  Object.assign(form, values);
  clearFormErrors();
}

function openCreateDialog(parent: IntentNodeTree | null) {
  dialogMode.value = "create";
  dialogParent.value = parent;
  editingNode.value = null;
  assignForm({
    name: "",
    intentCode: "",
    level: parent ? Math.min((parent.level ?? 0) + 1, 2) : 0,
    kind: parent?.kind ?? 0,
    parentCode: parent?.intentCode || ROOT_PARENT,
    kbId: "",
    mcpToolId: "",
    collectionName: "",
    description: "",
    examplesText: "",
    topK: "",
    sortOrder: 0,
    enabled: true,
    promptSnippet: "",
    promptTemplate: "",
    paramPromptTemplate: ""
  });
  dialogOpen.value = true;
}

function openEditDialog(node: IntentNodeTree) {
  dialogMode.value = "edit";
  editingNode.value = node;
  dialogParent.value = null;
  assignForm({
    name: node.name || "",
    intentCode: node.intentCode || "",
    level: node.level ?? 0,
    kind: node.kind ?? 0,
    parentCode: node.parentCode || ROOT_PARENT,
    kbId: "",
    mcpToolId: node.mcpToolId || "",
    collectionName: node.collectionName || "",
    description: node.description || "",
    examplesText: parseExamples(node.examples).join("\n"),
    topK: node.topK ?? "",
    sortOrder: node.sortOrder ?? 0,
    enabled: node.enabled !== 0,
    promptSnippet: node.promptSnippet || "",
    promptTemplate: node.promptTemplate || "",
    paramPromptTemplate: node.paramPromptTemplate || ""
  });
  dialogOpen.value = true;
}

function closeDialog() {
  if (!saving.value) dialogOpen.value = false;
}

function validateForm() {
  clearFormErrors();
  if (!form.name.trim()) formErrors.name = "请输入节点名称";
  else if (form.name.trim().length > 50) formErrors.name = "名称不能超过50个字符";
  if (!form.intentCode.trim()) formErrors.intentCode = "请输入意图标识";
  else if (!/^[a-zA-Z0-9_-]+$/.test(form.intentCode.trim()))
    formErrors.intentCode = "仅支持字母、数字、-和_";
  if (form.topK !== "" && (!Number.isInteger(form.topK) || form.topK <= 0))
    formErrors.topK = "TopK 必须为大于 0 的整数";
  if (form.sortOrder !== "" && !Number.isInteger(form.sortOrder))
    formErrors.sortOrder = "排序必须为整数";
  if (dialogMode.value === "create" && form.kind === 0 && form.level === 2 && !form.kbId)
    formErrors.kbId = "TOPIC 节点请选择知识库";
  if (form.kind === 2 && !form.mcpToolId.trim())
    formErrors.mcpToolId =
      dialogMode.value === "create" ? "请输入MCP工具ID" : "MCP节点必须填写工具ID";
  return Object.keys(formErrors).length === 0;
}

function splitExamples(value: string) {
  return value
    .split("\n")
    .map((item) => item.trim())
    .filter(Boolean);
}

async function submitNodeForm() {
  if (!validateForm()) return;
  const parentCode = form.parentCode === ROOT_PARENT ? null : form.parentCode || null;
  const examples = splitExamples(form.examplesText);
  saving.value = true;
  try {
    if (dialogMode.value === "create") {
      const payload: IntentNodeCreatePayload = {
        kbId: form.kind === 0 ? form.kbId || undefined : undefined,
        intentCode: form.intentCode.trim(),
        name: form.name.trim(),
        level: form.level,
        parentCode,
        description: form.description.trim() || undefined,
        examples: examples.length ? examples : undefined,
        kind: form.kind,
        topK: form.topK === "" ? undefined : form.topK,
        sortOrder: form.sortOrder === "" ? 0 : form.sortOrder,
        enabled: form.enabled ? 1 : 0,
        mcpToolId: form.kind === 2 ? form.mcpToolId.trim() || undefined : undefined,
        promptSnippet: form.promptSnippet.trim() || undefined,
        promptTemplate: form.promptTemplate.trim() || undefined,
        paramPromptTemplate:
          form.kind === 2 ? form.paramPromptTemplate.trim() || undefined : undefined
      };
      await createIntentNode(payload);
      toast.success("创建成功");
    } else if (editingNode.value) {
      const payload: IntentNodeUpdatePayload = {
        name: form.name.trim(),
        level: form.level,
        parentCode,
        description: form.description.trim() || undefined,
        examples: examples.length ? examples : undefined,
        collectionName: form.kind === 0 ? form.collectionName.trim() || undefined : undefined,
        mcpToolId: form.kind === 2 ? form.mcpToolId.trim() || undefined : undefined,
        kind: form.kind,
        topK: form.topK === "" ? undefined : form.topK,
        sortOrder: form.sortOrder === "" ? 0 : form.sortOrder,
        enabled: form.enabled ? 1 : 0,
        promptSnippet: form.promptSnippet.trim() || undefined,
        promptTemplate: form.promptTemplate.trim() || undefined,
        paramPromptTemplate:
          form.kind === 2 ? form.paramPromptTemplate.trim() || undefined : undefined
      };
      await updateIntentNode(editingNode.value.id, payload);
      toast.success("更新成功");
    }
    dialogOpen.value = false;
    await loadTree();
  } catch (error) {
    toast.error(getErrorMessage(error, dialogMode.value === "create" ? "创建失败" : "更新失败"));
    console.error(error);
  } finally {
    saving.value = false;
  }
}

async function confirmDelete() {
  if (!deleteTarget.value || deleting.value) return;
  deleting.value = true;
  try {
    await deleteIntentNode(deleteTarget.value.id);
    toast.success("删除成功");
    deleteTarget.value = null;
    await loadTree();
  } catch (error) {
    toast.error(getErrorMessage(error, "删除失败"));
    console.error(error);
  } finally {
    deleting.value = false;
  }
}

watch(focusIntentCode, (code) => {
  if (code && findNodeByCode(tree.value, code)) selectedCode.value = code;
});

onMounted(() => {
  void loadTree();
  void loadKnowledgeBases();
});
</script>

<style scoped>
.intent-tree-grid {
  display: grid;
  gap: 24px;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
}
.action-button,
.detail-button,
.modal-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  padding: 8px 14px;
  font-size: 14px;
}
.tree-card-content,
.detail-card-content {
  padding: 16px 24px 24px;
}
.empty-state {
  padding: 40px 16px;
  text-align: center;
  color: #64748b;
  font-size: 14px;
}
.tree-list {
  display: grid;
  gap: 4px;
}
.tree-row {
  display: flex;
  min-height: 40px;
  cursor: pointer;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-radius: 8px;
  padding-top: 8px;
  padding-right: 12px;
  padding-bottom: 8px;
  transition: background-color 150ms ease;
}
.tree-row:hover {
  background: #f8fafc;
}
.tree-row--selected {
  background: #f1f5f9;
  color: #0f172a;
}
.tree-row__main {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}
.tree-toggle {
  display: inline-flex;
  width: 20px;
  height: 20px;
  flex: 0 0 20px;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  color: #64748b;
}
.tree-toggle:hover {
  background: #e2e8f0;
  color: #0f172a;
}
.tree-toggle-placeholder {
  width: 20px;
  height: 20px;
  flex: 0 0 20px;
}
.tree-node-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #0f172a;
  font-size: 14px;
  font-weight: 500;
}
.tree-row__code {
  display: none;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #64748b;
  font-size: 12px;
}
.tree-row:hover .tree-row__code {
  display: block;
}
.ui-badge {
  display: inline-flex;
  align-items: center;
  border: 1px solid;
  padding: 2px 8px;
  line-height: 1.4;
}
.node-detail {
  display: grid;
  gap: 18px;
}
.node-detail__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.node-detail__title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.node-detail__title-row h2 {
  color: #0f172a;
  font-size: 18px;
  font-weight: 600;
}
.node-detail__code {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}
.node-detail__actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.detail-button {
  min-height: 34px;
  padding: 6px 10px;
  font-size: 13px;
}
.detail-button--danger {
  color: #dc2626 !important;
}
.node-meta {
  display: grid;
  gap: 10px;
  font-size: 14px;
}
.node-meta div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.node-meta dt {
  color: #64748b;
}
.node-meta dd {
  max-width: 65%;
  overflow-wrap: anywhere;
  text-align: right;
  color: #0f172a;
}
.detail-block h3 {
  color: #0f172a;
  font-size: 14px;
  font-weight: 500;
}
.detail-block p {
  margin-top: 4px;
  color: #64748b;
  font-size: 14px;
  white-space: pre-wrap;
}
.example-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.error-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fef2f2;
  padding: 10px 14px;
  color: #b91c1c;
  font-size: 14px;
}
.error-banner button {
  font-weight: 600;
  text-decoration: underline;
}
.modal-layer {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow-y: auto;
  background: rgba(15, 23, 42, 0.45);
  padding: 24px;
}
.modal-panel {
  width: min(100%, 640px);
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.18);
}
.node-form-dialog {
  max-height: 85vh;
  overflow-y: auto;
}
.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid #f1f5f9;
  padding: 20px 24px 16px;
}
.modal-header h2,
.confirm-dialog h2 {
  color: #0f172a;
  font-size: 18px;
  font-weight: 600;
}
.modal-header p,
.confirm-dialog p {
  margin-top: 4px;
  color: #64748b;
  font-size: 14px;
}
.icon-button {
  display: inline-flex;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: #64748b;
}
.icon-button:hover {
  background: #f1f5f9;
  color: #0f172a;
}
.node-form {
  display: grid;
  gap: 16px;
  padding: 20px 24px 24px;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.form-field {
  display: grid;
  gap: 7px;
  color: #334155;
  font-size: 14px;
  font-weight: 500;
}
.form-field input,
.form-field select,
.form-field textarea {
  width: 100%;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  padding: 9px 11px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 400;
  outline: none;
}
.form-field textarea {
  resize: vertical;
}
.form-field input:focus,
.form-field select:focus,
.form-field textarea:focus {
  border-color: #818cf8;
  box-shadow: 0 0 0 3px rgba(129, 140, 248, 0.15);
}
.form-field input:disabled {
  background: #f8fafc;
  color: #64748b;
}
.form-field small {
  color: #dc2626;
  font-size: 12px;
  font-weight: 400;
}
.form-section {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 16px;
}
.form-section summary {
  cursor: pointer;
  color: #0f172a;
  font-size: 14px;
  font-weight: 500;
}
.form-section__body {
  margin-top: 14px;
}
.form-section > .form-section__body:not(.form-grid) {
  display: grid;
  gap: 16px;
}
.checkbox-field {
  display: flex;
  min-height: 64px;
  align-items: flex-end;
  gap: 8px;
  color: #334155;
  font-size: 14px;
  font-weight: 500;
}
.checkbox-field input {
  width: 16px;
  height: 16px;
  accent-color: #4f46e5;
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 4px;
}
.modal-button[data-variant="outline"] {
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #334155;
}
.modal-button[data-variant="outline"]:hover {
  background: #f8fafc;
}
.modal-button[data-variant="default"] {
  background: #4f46e5;
  color: #fff;
}
.modal-button[data-variant="default"]:hover {
  background: #4338ca;
}
.danger-button {
  border-radius: 8px;
  background: #dc2626;
  padding: 8px 14px;
  color: #fff;
  font-size: 14px;
  font-weight: 500;
}
.danger-button:hover {
  background: #b91c1c;
}
.confirm-dialog {
  padding: 24px;
}
.confirm-dialog .modal-footer {
  margin-top: 20px;
}
button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

@media (max-width: 1024px) {
  .intent-tree-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .modal-layer {
    align-items: flex-start;
    padding: 12px;
  }
  .form-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .node-detail__header {
    flex-direction: column;
  }
  .node-detail__actions {
    width: 100%;
    flex-direction: row;
    flex-wrap: wrap;
  }
  .tree-row__code {
    display: none !important;
  }
}
</style>
