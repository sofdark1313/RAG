<template>
  <div class="admin-page intent-list-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">意图列表</h1>
        <p class="admin-page-subtitle">支持多维筛选、分页查看和快速定位到意图树节点</p>
      </div>
    </header>

    <section class="filter-panel">
      <div class="filter-row">
        <label class="search-field">
          <Search class="search-field__icon h-4 w-4" />
          <input
            v-model="keyword"
            class="ui-input"
            placeholder="搜索意图名称/ID..."
            aria-label="搜索意图名称或ID"
            @input="pageNo = 1"
          />
        </label>

        <div class="filter-controls">
          <select
            v-model="levelFilter"
            class="ui-select-trigger filter-select"
            aria-label="层级筛选"
            @change="pageNo = 1"
          >
            <option :value="ALL_VALUE">全部层级</option>
            <option
              v-for="option in LEVEL_OPTIONS"
              :key="option.value"
              :value="String(option.value)"
            >
              {{ option.label }}
            </option>
          </select>
          <select
            v-model="kindFilter"
            class="ui-select-trigger filter-select"
            aria-label="类型筛选"
            @change="pageNo = 1"
          >
            <option :value="ALL_VALUE">全部类型</option>
            <option
              v-for="option in KIND_OPTIONS"
              :key="option.value"
              :value="String(option.value)"
            >
              {{ option.label }}
            </option>
          </select>
          <select
            v-model="statusFilter"
            class="ui-select-trigger filter-select"
            aria-label="状态筛选"
            @change="pageNo = 1"
          >
            <option :value="ALL_VALUE">全部状态</option>
            <option value="enabled">仅启用</option>
            <option value="disabled">仅禁用</option>
          </select>
          <select
            v-model="parentFilter"
            class="ui-select-trigger parent-select"
            aria-label="父节点筛选"
            @change="pageNo = 1"
          >
            <option v-for="option in parentOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
          <button
            class="ui-button filter-button"
            data-variant="outline"
            type="button"
            :disabled="loading"
            @click="loadIntentTree"
          >
            <RefreshCw :class="['h-4 w-4', { 'animate-spin': loading }]" />
            刷新
          </button>
          <button
            class="ui-button filter-button reset-button"
            data-variant="outline"
            type="button"
            @click="resetFilters"
          >
            <X class="h-4 w-4" />
            清空筛选
          </button>
        </div>
      </div>
    </section>

    <p v-if="loadError" class="error-banner" role="alert">
      <span>{{ loadError }}</span>
      <button type="button" @click="loadIntentTree">重新加载</button>
    </p>

    <section class="ui-card table-card">
      <div class="ui-card-content table-card__content">
        <div v-if="selectedRows.length" class="batch-bar">
          <span>已选 {{ selectedRows.length }} 项</span>
          <div>
            <button
              class="ui-button batch-button"
              data-variant="outline"
              type="button"
              :disabled="batchDisabled"
              @click="runBatchUpdateEnabled(1)"
            >
              {{ batchSubmitting === "enable" ? "启用中..." : "批量启用" }}
            </button>
            <button
              class="ui-button batch-button"
              data-variant="outline"
              type="button"
              :disabled="batchDisabled"
              @click="runBatchUpdateEnabled(0)"
            >
              {{ batchSubmitting === "disable" ? "禁用中..." : "批量禁用" }}
            </button>
            <button
              class="ui-button batch-button batch-button--danger"
              data-variant="ghost"
              type="button"
              :disabled="batchDisabled"
              @click="deleteDialogOpen = true"
            >
              批量删除
            </button>
          </div>
        </div>

        <div v-if="loading" class="empty-state">加载中...</div>
        <div v-else-if="pageRows.length === 0" class="empty-state">
          {{
            rows.length === 0
              ? "暂无意图节点，请先在意图树配置中创建"
              : "没有匹配结果，请调整筛选条件"
          }}
        </div>
        <div v-else class="table-scroll">
          <table class="ui-table intent-table">
            <thead class="ui-table-header">
              <tr class="ui-table-row">
                <th class="ui-table-head select-column">
                  <input
                    class="row-checkbox"
                    type="checkbox"
                    :checked="allPageSelected"
                    :indeterminate="somePageSelected"
                    :disabled="batchSubmitting !== null || pageRows.length === 0"
                    aria-label="全选当前页"
                    @change="togglePageSelect(($event.target as HTMLInputElement).checked)"
                  />
                </th>
                <th class="ui-table-head node-column">意图节点</th>
                <th class="ui-table-head level-column">层级</th>
                <th class="ui-table-head kind-column">类型</th>
                <th class="ui-table-head path-column">路径</th>
                <th class="ui-table-head resource-column">关联资源</th>
                <th class="ui-table-head count-column">示例数</th>
                <th class="ui-table-head status-column">状态</th>
                <th class="ui-table-head action-column">操作</th>
              </tr>
            </thead>
            <tbody class="ui-table-body">
              <tr v-for="row in pageRows" :key="row.id" class="ui-table-row intent-row">
                <td class="ui-table-cell">
                  <input
                    class="row-checkbox"
                    type="checkbox"
                    :checked="selectedIdSet.has(row.id)"
                    :disabled="batchSubmitting !== null"
                    :aria-label="`选择 ${row.name}`"
                    @change="toggleRowSelect(row.id, ($event.target as HTMLInputElement).checked)"
                  />
                </td>
                <td class="ui-table-cell">
                  <div class="node-cell">
                    <span class="node-cell__name">{{ row.name }}</span>
                    <span class="node-cell__code">{{ row.intentCode }}</span>
                  </div>
                </td>
                <td class="ui-table-cell">
                  <span class="level-badge" :class="`level-badge--${row.level}`">{{
                    resolveLevelLabel(row.level)
                  }}</span>
                </td>
                <td class="ui-table-cell">
                  <span class="ui-badge" :data-variant="resolveKindVariant(row.kind)">{{
                    resolveKindLabel(row.kind)
                  }}</span>
                </td>
                <td class="ui-table-cell">
                  <div class="path-cell">
                    <template v-for="(segment, index) in row.pathNames" :key="`${row.id}-${index}`">
                      <span v-if="index > 0" class="path-divider">/</span>
                      <button
                        class="path-segment"
                        :class="{ 'path-segment--current': index === row.pathNames.length - 1 }"
                        type="button"
                        @click="locateTree(row.pathCodes[index])"
                      >
                        {{ segment }}
                      </button>
                    </template>
                  </div>
                </td>
                <td class="ui-table-cell">
                  <div class="resource-cell">
                    <span :title="resolveResourceText(row)">{{ resolveResourceText(row) }}</span>
                    <small>TopK: {{ row.topK ?? "全局默认" }}</small>
                  </div>
                </td>
                <td class="ui-table-cell">
                  <span class="numeric-cell">{{ row.exampleCount }}</span>
                </td>
                <td class="ui-table-cell">
                  <span
                    class="status-badge"
                    :class="row.enabled === 0 ? 'status-badge--disabled' : 'status-badge--enabled'"
                  >
                    {{ row.enabled === 0 ? "禁用" : "启用" }}
                  </span>
                </td>
                <td class="ui-table-cell sticky-action-cell">
                  <div class="row-actions">
                    <button
                      class="ui-button row-action"
                      data-variant="outline"
                      type="button"
                      :aria-label="`编辑 ${row.name}`"
                      @click="editNode(row)"
                    >
                      <Pencil class="h-4 w-4" />
                      编辑
                    </button>
                    <button
                      class="ui-button row-action"
                      data-variant="ghost"
                      type="button"
                      :aria-label="`定位 ${row.name} 到意图树`"
                      @click="locateTree(row.intentCode)"
                    >
                      <GitBranch class="h-4 w-4" />
                      定位树
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <footer v-if="showPagination" class="pagination">
      <span>共 {{ total }} 条，显示 {{ rangeStart }}-{{ rangeEnd }}</span>
      <div class="pagination__controls">
        <span>每页</span>
        <select
          v-model.number="pageSize"
          class="ui-select-trigger page-size-select"
          @change="pageNo = 1"
        >
          <option v-for="size in PAGE_SIZE_OPTIONS" :key="size" :value="size">{{ size }} 条</option>
        </select>
        <button
          class="ui-button page-button"
          data-variant="outline"
          type="button"
          :disabled="currentPage <= 1"
          @click="pageNo = 1"
        >
          首页
        </button>
        <button
          class="ui-button page-button"
          data-variant="outline"
          type="button"
          :disabled="currentPage <= 1"
          @click="pageNo = Math.max(1, pageNo - 1)"
        >
          上一页
        </button>
        <span>{{ currentPage }} / {{ totalPages }}</span>
        <button
          class="ui-button page-button"
          data-variant="outline"
          type="button"
          :disabled="currentPage >= totalPages"
          @click="pageNo = Math.min(totalPages, pageNo + 1)"
        >
          下一页
        </button>
        <button
          class="ui-button page-button"
          data-variant="outline"
          type="button"
          :disabled="currentPage >= totalPages"
          @click="pageNo = totalPages"
        >
          末页
        </button>
      </div>
    </footer>

    <Teleport to="body">
      <div
        v-if="deleteDialogOpen"
        class="modal-layer"
        role="presentation"
        @mousedown.self="closeDeleteDialog"
      >
        <section
          class="confirm-dialog"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="batch-delete-title"
          @keydown.esc="closeDeleteDialog"
        >
          <h2 id="batch-delete-title">确认批量删除？</h2>
          <p>将删除已选中的 {{ selectedRows.length }} 个意图节点，该操作不可恢复。</p>
          <footer>
            <button
              class="ui-button dialog-button"
              data-variant="outline"
              type="button"
              :disabled="batchSubmitting !== null"
              @click="closeDeleteDialog"
            >
              取消
            </button>
            <button
              class="dialog-button danger-button"
              type="button"
              :disabled="batchSubmitting !== null"
              @click="runBatchDelete"
            >
              {{ batchSubmitting === "delete" ? "删除中..." : "删除" }}
            </button>
          </footer>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { GitBranch, Pencil, RefreshCw, Search, X } from "lucide-vue-next";
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import {
  batchDeleteIntentNodes,
  batchDisableIntentNodes,
  batchEnableIntentNodes,
  getIntentTree,
  type IntentNodeTree
} from "@/services/intentTreeService";
import { getErrorMessage } from "@/utils/error";
import { toast } from "@/utils/toast";

const ALL_VALUE = "__ALL__";
const ROOT_VALUE = "__ROOT__";
const PAGE_SIZE_OPTIONS = [10, 20, 50];
const LEVEL_OPTIONS = [
  { value: 0, label: "DOMAIN" },
  { value: 1, label: "CATEGORY" },
  { value: 2, label: "TOPIC" }
];
const KIND_OPTIONS = [
  { value: 0, label: "KB" },
  { value: 1, label: "SYSTEM" },
  { value: 2, label: "MCP" }
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
  depth: number;
  pathText: string;
  pathNames: string[];
  pathCodes: string[];
  childCount: number;
  exampleCount: number;
}

type BatchAction = "enable" | "disable" | "delete";

const route = useRoute();
const router = useRouter();
const tree = ref<IntentNodeTree[]>([]);
const loading = ref(true);
const loadError = ref("");
const levelFilter = ref(ALL_VALUE);
const kindFilter = ref(ALL_VALUE);
const statusFilter = ref(ALL_VALUE);
const parentFilter = ref(ALL_VALUE);
const keyword = ref("");
const pageNo = ref(1);
const pageSize = ref(PAGE_SIZE_OPTIONS[0]);
const selectedIds = ref<number[]>([]);
const batchSubmitting = ref<BatchAction | null>(null);
const deleteDialogOpen = ref(false);

const rows = computed(() => flattenIntentTree(tree.value));
const parentOptions = computed(() => [
  { value: ALL_VALUE, label: "全部父节点" },
  { value: ROOT_VALUE, label: "ROOT（根节点）" },
  ...rows.value.map((row) => ({ value: row.intentCode, label: row.pathText }))
]);

const filteredRows = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase();
  return rows.value.filter((row) => {
    if (normalizedKeyword) {
      const searchable = [row.name, row.intentCode, String(row.id), row.pathText]
        .join(" ")
        .toLowerCase();
      if (!searchable.includes(normalizedKeyword)) return false;
    }
    if (levelFilter.value !== ALL_VALUE && row.level !== Number(levelFilter.value)) return false;
    if (kindFilter.value !== ALL_VALUE && row.kind !== Number(kindFilter.value)) return false;
    if (statusFilter.value === "enabled" && row.enabled === 0) return false;
    if (statusFilter.value === "disabled" && row.enabled !== 0) return false;
    if (parentFilter.value === ROOT_VALUE && row.parentCode) return false;
    if (
      ![ALL_VALUE, ROOT_VALUE].includes(parentFilter.value) &&
      row.parentCode !== parentFilter.value
    )
      return false;
    return true;
  });
});

const total = computed(() => filteredRows.value.length);
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
const currentPage = computed(() => Math.min(pageNo.value, totalPages.value));
const startIndex = computed(() => (currentPage.value - 1) * pageSize.value);
const pageRows = computed(() =>
  filteredRows.value.slice(startIndex.value, startIndex.value + pageSize.value)
);
const selectedIdSet = computed(() => new Set(selectedIds.value));
const selectedRows = computed(() => rows.value.filter((row) => selectedIdSet.value.has(row.id)));
const pageRowIds = computed(() => pageRows.value.map((row) => row.id));
const allPageSelected = computed(
  () => pageRowIds.value.length > 0 && pageRowIds.value.every((id) => selectedIdSet.value.has(id))
);
const somePageSelected = computed(
  () => !allPageSelected.value && pageRowIds.value.some((id) => selectedIdSet.value.has(id))
);
const rangeStart = computed(() => (total.value === 0 ? 0 : startIndex.value + 1));
const rangeEnd = computed(() =>
  total.value === 0 ? 0 : Math.min(startIndex.value + pageRows.value.length, total.value)
);
const showPagination = computed(() => !loading.value && total.value > 0);
const batchDisabled = computed(() => batchSubmitting.value !== null);

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

function flattenIntentTree(
  nodes: IntentNodeTree[],
  parentNames: string[] = [],
  parentCodes: string[] = []
): FlatIntentNode[] {
  const result: FlatIntentNode[] = [];
  for (const node of nodes) {
    const currentNames = [...parentNames, node.name];
    const currentCodes = [...parentCodes, node.intentCode];
    const children = node.children || [];
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
      depth: Math.max(currentNames.length - 1, 0),
      pathText: currentNames.join(" > "),
      pathNames: currentNames,
      pathCodes: currentCodes,
      childCount: children.length,
      exampleCount: parseExamples(node.examples).length
    });
    result.push(...flattenIntentTree(children, currentNames, currentCodes));
  }
  return result;
}

function resolveLevelLabel(value: number) {
  return LEVEL_OPTIONS.find((option) => option.value === value)?.label ?? "UNKNOWN";
}

function resolveKindLabel(value: number) {
  return KIND_OPTIONS.find((option) => option.value === value)?.label ?? "UNKNOWN";
}

function resolveKindVariant(value: number) {
  const label = resolveKindLabel(value);
  return label === "MCP" ? "default" : label === "SYSTEM" ? "secondary" : "outline";
}

function resolveResourceText(row: FlatIntentNode) {
  if (row.kind === 0) return row.collectionName || "-";
  if (row.kind === 2) return row.mcpToolId || "-";
  return "系统策略";
}

async function loadIntentTree() {
  loading.value = true;
  loadError.value = "";
  try {
    tree.value = (await getIntentTree()) || [];
  } catch (error) {
    loadError.value = getErrorMessage(error, "加载意图列表失败");
    toast.error(loadError.value);
    console.error(error);
  } finally {
    loading.value = false;
  }
}

function resetFilters() {
  keyword.value = "";
  levelFilter.value = ALL_VALUE;
  kindFilter.value = ALL_VALUE;
  statusFilter.value = ALL_VALUE;
  parentFilter.value = ALL_VALUE;
  pageNo.value = 1;
}

function toggleRowSelect(id: number, checked: boolean) {
  if (checked) {
    if (!selectedIds.value.includes(id)) selectedIds.value = [...selectedIds.value, id];
  } else {
    selectedIds.value = selectedIds.value.filter((item) => item !== id);
  }
}

function togglePageSelect(checked: boolean) {
  if (checked) {
    selectedIds.value = Array.from(new Set([...selectedIds.value, ...pageRowIds.value]));
  } else {
    const pageIds = new Set(pageRowIds.value);
    selectedIds.value = selectedIds.value.filter((id) => !pageIds.has(id));
  }
}

async function runBatchUpdateEnabled(enabled: 0 | 1) {
  if (!selectedRows.value.length || batchSubmitting.value) return;
  batchSubmitting.value = enabled === 1 ? "enable" : "disable";
  try {
    const targetIds = selectedRows.value.map((row) => row.id);
    if (enabled === 1) await batchEnableIntentNodes(targetIds);
    else await batchDisableIntentNodes(targetIds);
    toast.success(`已${enabled === 1 ? "启用" : "禁用"} ${targetIds.length} 项`);
    await loadIntentTree();
    selectedIds.value = [];
  } catch (error) {
    toast.error(getErrorMessage(error, "批量更新失败"));
    console.error(error);
  } finally {
    batchSubmitting.value = null;
  }
}

async function runBatchDelete() {
  if (!selectedRows.value.length || batchSubmitting.value) return;
  batchSubmitting.value = "delete";
  try {
    const targetIds = selectedRows.value.map((row) => row.id);
    await batchDeleteIntentNodes(targetIds);
    toast.success(`已删除 ${targetIds.length} 项`);
    deleteDialogOpen.value = false;
    await loadIntentTree();
    selectedIds.value = [];
  } catch (error) {
    toast.error(getErrorMessage(error, "批量删除失败"));
    console.error(error);
  } finally {
    batchSubmitting.value = null;
  }
}

function closeDeleteDialog() {
  if (!batchSubmitting.value) deleteDialogOpen.value = false;
}

function locateTree(intentCode: string | undefined) {
  if (!intentCode) return;
  void router.push({ path: "/admin/intent-tree", query: { intentCode } });
}

function editNode(row: FlatIntentNode) {
  void router.push({
    path: `/admin/intent-list/${row.id}/edit`,
    query: { from: route.fullPath }
  });
}

watch(currentPage, (value) => {
  if (pageNo.value !== value) pageNo.value = value;
});

watch(rows, (value) => {
  const validIds = new Set(value.map((row) => row.id));
  selectedIds.value = selectedIds.value.filter((id) => validIds.has(id));
});

onMounted(() => void loadIntentTree());
</script>

<style scoped>
.filter-panel {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  padding: 12px;
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.search-field {
  position: relative;
  min-width: 280px;
  max-width: 420px;
  flex: 1;
}
.search-field__icon {
  position: absolute;
  left: 12px;
  top: 50%;
  z-index: 1;
  transform: translateY(-50%);
  color: #94a3b8;
  pointer-events: none;
}
.search-field input {
  width: 100%;
  height: 40px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 12px 8px 38px;
  color: #0f172a;
  font-size: 14px;
  outline: none;
}
.search-field input:focus,
.filter-controls select:focus,
.page-size-select:focus {
  border-color: #818cf8;
  box-shadow: 0 0 0 3px rgba(129, 140, 248, 0.14);
}
.filter-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.filter-controls select,
.page-size-select {
  height: 40px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  padding: 0 32px 0 11px;
  color: #334155;
  font-size: 14px;
  outline: none;
}
.filter-select {
  width: 136px;
}
.parent-select {
  width: 220px;
}
.filter-button,
.batch-button,
.row-action,
.page-button,
.dialog-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: 8px;
  font-weight: 500;
}
.filter-button {
  height: 40px;
  padding: 0 12px;
  font-size: 14px;
}
.reset-button {
  border-color: #fecdd3 !important;
  background: #fff1f2 !important;
  color: #be123c !important;
}
.reset-button:hover {
  background: #ffe4e6 !important;
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
.table-card {
  overflow: hidden;
}
.table-card__content {
  padding: 16px 24px 24px;
}
.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: -1px -24px 12px;
  border-top: 1px solid #e2e8f0;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
  padding: 8px 24px;
  color: #334155;
  font-size: 14px;
  font-weight: 500;
}
.batch-bar > div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.batch-button {
  min-height: 32px;
  padding: 5px 12px;
  font-size: 12px;
}
.batch-button--danger {
  color: #dc2626 !important;
}
.empty-state {
  padding: 40px 16px;
  text-align: center;
  color: #64748b;
  font-size: 14px;
}
.table-scroll {
  margin: 0 -24px -24px;
  overflow-x: auto;
}
.intent-table {
  min-width: 1280px;
  width: 100%;
}
.intent-table th {
  height: 40px;
  padding-top: 8px;
  padding-bottom: 8px;
  text-align: left;
}
.intent-table td {
  padding-top: 8px;
  padding-bottom: 8px;
}
.select-column {
  width: 48px;
}
.node-column {
  width: 300px;
}
.level-column,
.kind-column {
  width: 120px;
}
.path-column {
  width: 320px;
}
.resource-column {
  width: 220px;
}
.count-column,
.status-column {
  width: 90px;
}
.action-column {
  position: sticky;
  right: 0;
  z-index: 20;
  width: 180px;
  background: #f9fafb;
  box-shadow: -1px 0 0 #e2e8f0;
}
.intent-row {
  font-size: 13px;
}
.intent-row:hover {
  background: #f8fafc !important;
}
.row-checkbox {
  width: 16px;
  height: 16px;
  accent-color: #4f46e5;
}
.node-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.node-cell__name {
  color: #0f172a;
  font-weight: 600;
}
.node-cell__code {
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #f8fafc;
  padding: 2px 8px;
  color: #475569;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
.level-badge,
.status-badge,
.ui-badge {
  display: inline-flex;
  align-items: center;
  border: 1px solid;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 600;
}
.level-badge--0 {
  border-color: #91d5ff;
  background: #e6f7ff;
  color: #1890ff;
}
.level-badge--1 {
  border-color: #b7eb8f;
  background: #f6ffed;
  color: #52c41a;
}
.level-badge--2 {
  border-color: #ffd591;
  background: #fff7e6;
  color: #fa8c16;
}
.path-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}
.path-divider {
  color: #cbd5e1;
}
.path-segment {
  border-radius: 4px;
  padding: 2px 6px;
  color: #94a3b8;
  font-size: 12px;
  transition:
    background-color 150ms ease,
    color 150ms ease;
}
.path-segment:hover {
  background: #f1f5f9;
  color: #475569;
}
.path-segment--current {
  background: #f1f5f9;
  color: #475569;
  font-weight: 500;
}
.resource-cell {
  display: grid;
  gap: 2px;
}
.resource-cell > span {
  max-width: 190px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #334155;
  font-size: 14px;
}
.resource-cell small {
  color: #94a3b8;
  font-size: 12px;
}
.numeric-cell {
  color: #334155;
  font-weight: 500;
}
.status-badge--enabled {
  border-color: #b7eb8f;
  background: #f6ffed;
  color: #52c41a;
}
.status-badge--disabled {
  border-color: #d9d9d9;
  background: #fafafa;
  color: #8c8c8c;
}
.sticky-action-cell {
  position: sticky;
  right: 0;
  z-index: 10;
  background: #fff;
  box-shadow: -1px 0 0 #e2e8f0;
}
.intent-row:hover .sticky-action-cell {
  background: #f8fafc;
}
.row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.row-action {
  min-height: 32px;
  padding: 5px 10px;
  font-size: 12px;
}
.pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #64748b;
  font-size: 14px;
}
.pagination__controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.page-size-select {
  width: 92px;
  height: 32px;
}
.page-button {
  min-height: 32px;
  padding: 5px 10px;
  font-size: 13px;
}
.modal-layer {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.45);
  padding: 24px;
}
.confirm-dialog {
  width: min(100%, 480px);
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  padding: 24px;
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.18);
}
.confirm-dialog h2 {
  color: #0f172a;
  font-size: 18px;
  font-weight: 600;
}
.confirm-dialog p {
  margin-top: 6px;
  color: #64748b;
  font-size: 14px;
}
.confirm-dialog footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 22px;
}
.dialog-button {
  min-height: 38px;
  padding: 7px 14px;
  font-size: 14px;
}
.dialog-button[data-variant="outline"] {
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #334155;
}
.dialog-button[data-variant="outline"]:hover {
  background: #f8fafc;
}
.danger-button {
  border-radius: 8px;
  background: #dc2626;
  padding: 7px 14px;
  color: #fff;
  font-size: 14px;
  font-weight: 500;
}
.danger-button:hover {
  background: #b91c1c;
}
button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

@media (max-width: 1200px) {
  .filter-row {
    align-items: stretch;
    flex-direction: column;
  }
  .search-field {
    max-width: none;
  }
}

@media (max-width: 640px) {
  .search-field {
    min-width: 0;
  }
  .filter-select,
  .parent-select {
    width: 100%;
  }
  .filter-controls > * {
    flex: 1 1 140px;
  }
  .batch-bar,
  .pagination {
    align-items: flex-start;
    flex-direction: column;
  }
  .modal-layer {
    padding: 12px;
  }
}
</style>
