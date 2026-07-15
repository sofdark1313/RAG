<template>
  <div class="admin-page intent-edit-page">
    <section v-if="loading" class="ui-card state-card">
      <div class="ui-card-content">加载中...</div>
    </section>

    <section v-else-if="loadError" class="ui-card state-card state-card--error">
      <div class="ui-card-content">
        <p>{{ loadError }}</p>
        <button
          class="ui-button state-button"
          data-variant="outline"
          type="button"
          @click="loadTree"
        >
          重新加载
        </button>
      </div>
    </section>

    <section v-else-if="!currentNode" class="ui-card state-card">
      <div class="ui-card-content">
        <p>未找到对应意图节点</p>
        <button class="ui-button state-button" data-variant="outline" type="button" @click="goBack">
          返回意图列表
        </button>
      </div>
    </section>

    <template v-else>
      <header class="admin-page-header">
        <div>
          <h1 class="admin-page-title">编辑意图节点</h1>
          <p class="admin-page-subtitle">{{ currentNode.name }}（{{ currentNode.intentCode }}）</p>
        </div>
        <div class="admin-page-actions">
          <button
            class="ui-button back-button"
            data-variant="outline"
            type="button"
            @click="goBack"
          >
            返回意图列表
          </button>
        </div>
      </header>

      <section class="ui-card">
        <div class="ui-card-header">
          <h2 class="ui-card-title">节点配置</h2>
          <p class="ui-card-description">修改节点基础信息、Prompt与高级参数</p>
        </div>
        <div class="ui-card-content form-card-content">
          <form class="edit-form" @submit.prevent="submitForm">
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
                <input v-model="form.intentCode" class="ui-input" disabled />
                <small v-if="formErrors.intentCode">{{ formErrors.intentCode }}</small>
              </label>
            </div>

            <div class="form-grid">
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
                <option v-for="option in parentOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </label>

            <label v-if="form.kind === 0" class="form-field">
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
                    placeholder="节点语义说明与适用场景"
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
                    placeholder="多意图场景下的规则补充"
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
                    placeholder="用于提取MCP工具参数"
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
                  <span>节点启用</span>
                </label>
              </div>
            </details>

            <footer class="form-actions">
              <button
                class="ui-button form-button"
                data-variant="outline"
                type="button"
                :disabled="saving"
                @click="goBack"
              >
                取消
              </button>
              <button
                class="ui-button form-button admin-primary-gradient"
                data-variant="default"
                type="submit"
                :disabled="saving"
              >
                {{ saving ? "保存中..." : "保存修改" }}
              </button>
            </footer>
          </form>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import {
  getIntentTree,
  updateIntentNode,
  type IntentNodeTree,
  type IntentNodeUpdatePayload
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

interface FlatIntentNode {
  id: number;
  intentCode: string;
  name: string;
  level: number;
  kind: number;
  parentCode?: string | null;
  description?: string | null;
  examples?: string | null;
  collectionName?: string | null;
  mcpToolId?: string | null;
  topK?: number | null;
  enabled: number;
  sortOrder: number;
  promptSnippet?: string | null;
  promptTemplate?: string | null;
  paramPromptTemplate?: string | null;
  pathText: string;
}

interface EditForm {
  name: string;
  intentCode: string;
  level: number;
  kind: number;
  parentCode: string;
  collectionName: string;
  mcpToolId: string;
  description: string;
  examplesText: string;
  topK: number | "";
  sortOrder: number | "";
  enabled: boolean;
  promptSnippet: string;
  promptTemplate: string;
  paramPromptTemplate: string;
}

type FormErrors = Partial<Record<keyof EditForm, string>>;

const route = useRoute();
const router = useRouter();
const tree = ref<IntentNodeTree[]>([]);
const loading = ref(true);
const saving = ref(false);
const loadError = ref("");
const formErrors = reactive<FormErrors>({});
const form = reactive<EditForm>(emptyForm());

const routeId = computed(() => (typeof route.params.id === "string" ? route.params.id : ""));
const returnTo = computed(() => {
  const from = route.query.from;
  return typeof from === "string" && from.startsWith("/admin/") ? from : "/admin/intent-list";
});
const rows = computed(() => flattenIntentTree(tree.value));
const currentNode = computed(
  () => rows.value.find((row) => String(row.id) === routeId.value) || null
);

const excludedCodes = computed(() => {
  if (!currentNode.value) return new Set<string>();
  const childrenMap = new Map<string, string[]>();
  for (const row of rows.value) {
    if (!row.parentCode) continue;
    const children = childrenMap.get(row.parentCode) || [];
    children.push(row.intentCode);
    childrenMap.set(row.parentCode, children);
  }
  const excluded = new Set<string>([currentNode.value.intentCode]);
  const stack = [currentNode.value.intentCode];
  while (stack.length) {
    const code = stack.pop();
    if (!code) continue;
    for (const childCode of childrenMap.get(code) || []) {
      if (excluded.has(childCode)) continue;
      excluded.add(childCode);
      stack.push(childCode);
    }
  }
  return excluded;
});

const parentOptions = computed(() => [
  { value: ROOT_PARENT, label: "ROOT" },
  ...rows.value
    .filter((row) => !excludedCodes.value.has(row.intentCode))
    .map((row) => ({ value: row.intentCode, label: row.pathText }))
]);

function emptyForm(): EditForm {
  return {
    name: "",
    intentCode: "",
    level: 0,
    kind: 0,
    parentCode: ROOT_PARENT,
    collectionName: "",
    mcpToolId: "",
    description: "",
    examplesText: "",
    topK: "",
    sortOrder: 0,
    enabled: true,
    promptSnippet: "",
    promptTemplate: "",
    paramPromptTemplate: ""
  };
}

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

function flattenIntentTree(nodes: IntentNodeTree[], parentPath: string[] = []): FlatIntentNode[] {
  const result: FlatIntentNode[] = [];
  for (const node of nodes) {
    const currentPath = [...parentPath, node.name];
    result.push({
      id: node.id,
      intentCode: node.intentCode,
      name: node.name,
      level: node.level ?? 0,
      kind: node.kind ?? 0,
      parentCode: node.parentCode,
      description: node.description,
      examples: node.examples,
      collectionName: node.collectionName,
      mcpToolId: node.mcpToolId,
      topK: node.topK,
      enabled: node.enabled === 0 ? 0 : 1,
      sortOrder: node.sortOrder ?? 0,
      promptSnippet: node.promptSnippet,
      promptTemplate: node.promptTemplate,
      paramPromptTemplate: node.paramPromptTemplate,
      pathText: currentPath.join(" > ")
    });
    result.push(...flattenIntentTree(node.children || [], currentPath));
  }
  return result;
}

function clearFormErrors() {
  for (const key of Object.keys(formErrors) as Array<keyof EditForm>) delete formErrors[key];
}

function resetForm(node: FlatIntentNode | null) {
  clearFormErrors();
  if (!node) {
    Object.assign(form, emptyForm());
    return;
  }
  Object.assign(form, {
    name: node.name || "",
    intentCode: node.intentCode || "",
    level: node.level ?? 0,
    kind: node.kind ?? 0,
    parentCode: node.parentCode || ROOT_PARENT,
    collectionName: node.collectionName || "",
    mcpToolId: node.mcpToolId || "",
    description: node.description || "",
    examplesText: parseExamples(node.examples).join("\n"),
    topK: node.topK ?? "",
    sortOrder: node.sortOrder ?? 0,
    enabled: node.enabled !== 0,
    promptSnippet: node.promptSnippet || "",
    promptTemplate: node.promptTemplate || "",
    paramPromptTemplate: node.paramPromptTemplate || ""
  } satisfies EditForm);
}

async function loadTree() {
  loading.value = true;
  loadError.value = "";
  try {
    tree.value = (await getIntentTree()) || [];
  } catch (error) {
    loadError.value = getErrorMessage(error, "加载意图节点失败");
    toast.error(loadError.value);
    console.error(error);
  } finally {
    loading.value = false;
  }
}

function validateForm() {
  clearFormErrors();
  if (!form.name.trim()) formErrors.name = "请输入节点名称";
  else if (form.name.trim().length > 50) formErrors.name = "名称不能超过50个字符";
  if (!form.intentCode.trim()) formErrors.intentCode = "意图标识不能为空";
  if (form.kind === 2 && !form.mcpToolId.trim()) formErrors.mcpToolId = "MCP节点必须填写工具ID";
  if (form.topK !== "" && (!Number.isInteger(form.topK) || form.topK <= 0))
    formErrors.topK = "TopK 必须为大于 0 的整数";
  if (form.sortOrder !== "" && !Number.isInteger(form.sortOrder))
    formErrors.sortOrder = "排序必须为整数";
  return Object.keys(formErrors).length === 0;
}

async function submitForm() {
  if (!currentNode.value || !validateForm()) return;
  const examples = form.examplesText
    .split("\n")
    .map((item) => item.trim())
    .filter(Boolean);
  const payload: IntentNodeUpdatePayload = {
    name: form.name.trim(),
    level: form.level,
    parentCode: form.parentCode === ROOT_PARENT ? null : form.parentCode || null,
    description: form.description.trim(),
    examples,
    collectionName: form.kind === 0 ? form.collectionName.trim() : "",
    mcpToolId: form.kind === 2 ? form.mcpToolId.trim() : "",
    kind: form.kind,
    topK: form.topK === "" ? undefined : form.topK,
    sortOrder: form.sortOrder === "" ? 0 : form.sortOrder,
    enabled: form.enabled ? 1 : 0,
    promptSnippet: form.promptSnippet.trim(),
    promptTemplate: form.promptTemplate.trim(),
    paramPromptTemplate: form.kind === 2 ? form.paramPromptTemplate.trim() : ""
  };

  saving.value = true;
  try {
    await updateIntentNode(currentNode.value.id, payload);
    toast.success("更新成功");
    await router.push(returnTo.value);
  } catch (error) {
    toast.error(getErrorMessage(error, "更新失败"));
    console.error(error);
  } finally {
    saving.value = false;
  }
}

function goBack() {
  void router.push(returnTo.value);
}

watch(currentNode, (node) => resetForm(node), { immediate: true });
watch(routeId, () => void loadTree(), { immediate: true });
</script>

<style scoped>
.state-card .ui-card-content {
  display: grid;
  justify-items: center;
  gap: 12px;
  padding: 48px 24px;
  text-align: center;
  color: #64748b;
  font-size: 14px;
}
.state-card--error .ui-card-content {
  color: #b91c1c;
}
.state-button,
.back-button,
.form-button {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 14px;
  font-weight: 500;
}
.form-card-content {
  padding: 20px 24px 24px;
}
.edit-form {
  display: grid;
  gap: 16px;
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
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 4px;
}
button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
