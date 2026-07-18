<template>
  <div class="admin-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">关键词映射管理</h1>
        <p class="admin-page-subtitle">配置查询归一化的关键词映射规则</p>
      </div>
      <div class="admin-page-actions">
        <input
          v-model="searchKeyword"
          class="ui-input h-10 w-[240px] border px-3 text-sm"
          placeholder="搜索原始词/目标词"
          @keydown.enter="handleSearch"
        />
        <button
          class="ui-button h-10 border px-4 text-sm"
          data-variant="outline"
          type="button"
          @click="handleSearch"
        >
          搜索
        </button>
        <button
          class="ui-button h-10 border px-4 text-sm"
          data-variant="outline"
          type="button"
          @click="handleRefresh"
        >
          <RefreshCw :size="16" />
          刷新
        </button>
        <button
          class="ui-button admin-primary-gradient h-10 px-4 text-sm"
          type="button"
          @click="openCreateDialog"
        >
          <Plus :size="16" />
          新增映射
        </button>
      </div>
    </header>

    <section class="ui-card">
      <div class="ui-card-content px-6 pt-6">
        <div v-if="loading" class="py-8 text-center text-muted-foreground">加载中...</div>
        <div v-else-if="records.length === 0" class="py-8 text-center text-muted-foreground">
          暂无映射规则，点击上方按钮新增
        </div>
        <div v-else class="ui-table-wrap">
          <table class="ui-table min-w-[1180px] w-full">
            <thead class="ui-table-header">
              <tr class="ui-table-row">
                <th class="ui-table-head w-[170px] text-left">原始词</th>
                <th class="ui-table-head w-[170px] text-left">目标词</th>
                <th class="ui-table-head w-[100px] text-left">匹配类型</th>
                <th class="ui-table-head w-[80px] text-left">优先级</th>
                <th class="ui-table-head w-[80px] text-left">状态</th>
                <th class="ui-table-head w-[180px] text-left">备注</th>
                <th class="ui-table-head w-[160px] text-left">创建时间</th>
                <th class="ui-table-head w-[160px] text-left">更新时间</th>
                <th class="ui-table-head w-[150px] text-left">操作</th>
              </tr>
            </thead>
            <tbody class="ui-table-body">
              <tr v-for="item in records" :key="item.id" class="ui-table-row">
                <td
                  class="ui-table-cell max-w-[160px] truncate font-medium text-slate-900"
                  :title="item.sourceTerm"
                >
                  {{ item.sourceTerm }}
                </td>
                <td class="ui-table-cell max-w-[160px] truncate" :title="item.targetTerm">
                  {{ item.targetTerm }}
                </td>
                <td class="ui-table-cell">
                  <span class="ui-badge inline-flex border px-2 py-1" data-variant="secondary">
                    {{ matchTypeLabel(item.matchType) }}
                  </span>
                </td>
                <td class="ui-table-cell">{{ item.priority }}</td>
                <td class="ui-table-cell">
                  <span
                    class="ui-badge inline-flex border px-2 py-1"
                    :data-variant="item.enabled ? 'default' : 'outline'"
                  >
                    {{ item.enabled ? "启用" : "禁用" }}
                  </span>
                </td>
                <td
                  class="ui-table-cell max-w-[160px] truncate text-muted-foreground"
                  :title="item.remark || ''"
                >
                  {{ item.remark || "-" }}
                </td>
                <td
                  class="ui-table-cell cursor-default truncate text-sm tabular-nums"
                  :title="formatTimeTooltip(item.createTime)"
                >
                  {{ formatTime(item.createTime) }}
                </td>
                <td
                  class="ui-table-cell cursor-default truncate text-sm tabular-nums"
                  :title="formatTimeTooltip(item.updateTime)"
                >
                  {{ formatTime(item.updateTime) }}
                </td>
                <td class="ui-table-cell">
                  <div class="flex justify-center gap-2">
                    <button
                      class="ui-button h-8 border px-3 text-xs"
                      data-variant="outline"
                      type="button"
                      @click="openEditDialog(item)"
                    >
                      <Pencil :size="16" />
                      编辑
                    </button>
                    <button
                      class="ui-button h-8 px-3 text-xs text-red-600 hover:text-red-600"
                      data-variant="ghost"
                      type="button"
                      @click="deleteTarget = item"
                    >
                      <Trash2 :size="16" />
                      删除
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <div
      v-if="pageData"
      class="flex flex-wrap items-center justify-between gap-2 text-sm text-slate-500"
    >
      <span>共 {{ pageData.total }} 条</span>
      <div class="flex items-center gap-2">
        <button
          class="ui-button h-8 border px-3 text-xs"
          data-variant="outline"
          type="button"
          :disabled="pageData.current <= 1"
          @click="pageNo = Math.max(1, pageNo - 1)"
        >
          上一页
        </button>
        <span>{{ pageData.current }} / {{ pageData.pages }}</span>
        <button
          class="ui-button h-8 border px-3 text-xs"
          data-variant="outline"
          type="button"
          :disabled="pageData.current >= pageData.pages"
          @click="pageNo = Math.min(pageData.pages || 1, pageNo + 1)"
        >
          下一页
        </button>
      </div>
    </div>

    <div
      v-if="deleteTarget"
      class="admin-modal-backdrop"
      role="presentation"
      @mousedown.self="deleteTarget = null"
    >
      <section
        aria-labelledby="mapping-delete-title"
        aria-modal="true"
        class="admin-modal max-w-[440px]"
        role="alertdialog"
      >
        <div class="admin-modal-header">
          <h2 id="mapping-delete-title" class="text-lg font-semibold text-slate-900">确认删除</h2>
          <p class="mt-2 text-sm text-slate-500">删除后该映射规则将不再生效，是否继续？</p>
        </div>
        <div class="admin-modal-footer">
          <button
            class="ui-button h-10 border px-4 text-sm"
            data-variant="outline"
            type="button"
            @click="deleteTarget = null"
          >
            取消
          </button>
          <button
            class="ui-button h-10 bg-red-600 px-4 text-sm text-white hover:bg-red-700"
            type="button"
            @click="handleDelete"
          >
            删除
          </button>
        </div>
      </section>
    </div>

    <div
      v-if="dialogState.open"
      class="admin-modal-backdrop"
      role="presentation"
      @mousedown.self="closeDialog"
    >
      <section
        :aria-labelledby="dialogTitleId"
        aria-modal="true"
        class="admin-modal max-w-[520px]"
        role="dialog"
      >
        <div class="admin-modal-header">
          <h2 :id="dialogTitleId" class="text-lg font-semibold text-slate-900">
            {{ dialogState.mode === "create" ? "新增映射规则" : "编辑映射规则" }}
          </h2>
          <p class="mt-1 text-sm text-slate-500">配置查询归一化的关键词映射</p>
          <button class="admin-modal-close" title="关闭" type="button" @click="closeDialog">
            <X :size="18" />
          </button>
        </div>
        <div class="space-y-4 px-6 py-5">
          <label class="admin-form-field">
            <span>原始词 *</span>
            <input
              v-model="form.sourceTerm"
              autofocus
              class="ui-input h-10 border px-3 text-sm"
              placeholder="用户输入的原始关键词"
            />
          </label>
          <label class="admin-form-field">
            <span>目标词 *</span>
            <input
              v-model="form.targetTerm"
              class="ui-input h-10 border px-3 text-sm"
              placeholder="归一化后的目标关键词"
            />
          </label>
          <div class="grid grid-cols-2 gap-4">
            <label class="admin-form-field">
              <span>匹配类型</span>
              <select v-model.number="form.matchType" class="ui-input h-10 border px-3 text-sm">
                <option
                  v-for="option in MATCH_TYPE_OPTIONS"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </option>
              </select>
            </label>
            <label class="admin-form-field">
              <span>优先级</span>
              <input
                v-model.number="form.priority"
                class="ui-input h-10 border px-3 text-sm"
                placeholder="数值越小优先级越高"
                type="number"
              />
            </label>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <label class="admin-form-field">
              <span>启用状态</span>
              <select v-model="form.enabled" class="ui-input h-10 border px-3 text-sm">
                <option :value="true">启用</option>
                <option :value="false">禁用</option>
              </select>
            </label>
          </div>
          <label class="admin-form-field">
            <span>备注</span>
            <input
              v-model="form.remark"
              class="ui-input h-10 border px-3 text-sm"
              placeholder="可选备注信息"
            />
          </label>
        </div>
        <div class="admin-modal-footer">
          <button
            class="ui-button h-10 border px-4 text-sm"
            data-variant="outline"
            type="button"
            @click="closeDialog"
          >
            取消
          </button>
          <button
            class="ui-button h-10 px-4 text-sm"
            data-variant="default"
            type="button"
            @click="handleSubmit"
          >
            保存
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Pencil, Plus, RefreshCw, Trash2, X } from "lucide-vue-next";
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";

import {
  createQueryTermMapping,
  deleteQueryTermMapping,
  getQueryTermMappingsPage,
  updateQueryTermMapping,
  type PageResult,
  type QueryTermMapping
} from "@/services/queryTermMappingService";
import { getErrorMessage } from "@/utils/error";
import { formatRelativeTime, formatTooltipTime } from "@/utils/time";
import { toast } from "@/utils/toast";

const PAGE_SIZE = 10;

const MATCH_TYPE_OPTIONS = [
  { value: 1, label: "精确匹配" },
  { value: 2, label: "前缀匹配" },
  { value: 3, label: "正则匹配" },
  { value: 4, label: "整词匹配" }
];

const pageData = ref<PageResult<QueryTermMapping> | null>(null);
const loading = ref(true);
const deleteTarget = ref<QueryTermMapping | null>(null);
const pageNo = ref(1);
const searchKeyword = ref("");
const keyword = ref("");
const dialogState = reactive<{
  open: boolean;
  mode: "create" | "edit";
  item: QueryTermMapping | null;
}>({ open: false, mode: "create", item: null });
const form = reactive({
  sourceTerm: "",
  targetTerm: "",
  matchType: 1,
  priority: 0,
  enabled: true,
  remark: ""
});

const records = computed(() => pageData.value?.records || []);
const dialogTitleId = computed(() => `query-term-mapping-${dialogState.mode}-title`);

function matchTypeLabel(type: number) {
  return MATCH_TYPE_OPTIONS.find((option) => option.value === type)?.label || `类型${type}`;
}

function resetForm() {
  form.sourceTerm = "";
  form.targetTerm = "";
  form.matchType = 1;
  form.priority = 0;
  form.enabled = true;
  form.remark = "";
}

async function loadData(current = pageNo.value, keywordValue = keyword.value) {
  try {
    loading.value = true;
    pageData.value = await getQueryTermMappingsPage(current, PAGE_SIZE, keywordValue || undefined);
  } catch (error) {
    toast.error(getErrorMessage(error, "加载映射规则失败"));
    console.error(error);
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pageNo.value = 1;
  keyword.value = searchKeyword.value.trim();
}

async function handleRefresh() {
  pageNo.value = 1;
  await loadData(1, keyword.value);
}

function openCreateDialog() {
  resetForm();
  dialogState.open = true;
  dialogState.mode = "create";
  dialogState.item = null;
}

function openEditDialog(item: QueryTermMapping) {
  form.sourceTerm = item.sourceTerm || "";
  form.targetTerm = item.targetTerm || "";
  form.matchType = item.matchType ?? 1;
  form.priority = item.priority ?? 0;
  form.enabled = item.enabled ?? true;
  form.remark = item.remark || "";
  dialogState.open = true;
  dialogState.mode = "edit";
  dialogState.item = item;
}

function closeDialog() {
  dialogState.open = false;
  dialogState.mode = "create";
  dialogState.item = null;
  resetForm();
}

async function handleSubmit() {
  const payload = {
    sourceTerm: form.sourceTerm.trim(),
    targetTerm: form.targetTerm.trim(),
    matchType: form.matchType,
    priority: form.priority,
    enabled: form.enabled,
    remark: form.remark.trim() || null
  };

  if (!payload.sourceTerm) {
    toast.error("请输入原始词");
    return;
  }
  if (!payload.targetTerm) {
    toast.error("请输入目标词");
    return;
  }

  try {
    if (dialogState.mode === "create") {
      await createQueryTermMapping(payload);
      toast.success("创建成功");
      pageNo.value = 1;
      await loadData(1, keyword.value);
    } else if (dialogState.item) {
      await updateQueryTermMapping(dialogState.item.id, payload);
      toast.success("更新成功");
      await loadData(pageNo.value, keyword.value);
    }
    closeDialog();
  } catch (error) {
    toast.error(getErrorMessage(error, "保存失败"));
    console.error(error);
  }
}

async function handleDelete() {
  if (!deleteTarget.value) return;

  try {
    await deleteQueryTermMapping(deleteTarget.value.id);
    toast.success("删除成功");
    pageNo.value = 1;
    await loadData(1, keyword.value);
  } catch (error) {
    toast.error(getErrorMessage(error, "删除失败"));
    console.error(error);
  } finally {
    deleteTarget.value = null;
  }
}

function formatTime(value?: string | null) {
  return formatRelativeTime(value);
}

function formatTimeTooltip(value?: string | null) {
  return value ? formatTooltipTime(value) : "";
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key !== "Escape") return;
  if (deleteTarget.value) {
    deleteTarget.value = null;
  } else if (dialogState.open) {
    closeDialog();
  }
}

watch([pageNo, keyword], () => loadData(), { immediate: true });
onMounted(() => window.addEventListener("keydown", handleKeydown));
onBeforeUnmount(() => window.removeEventListener("keydown", handleKeydown));
</script>

<style scoped>
.admin-modal-backdrop {
  @apply fixed inset-0 z-[80] flex items-center justify-center bg-slate-900/45 p-4;
}

.admin-modal {
  @apply max-h-[calc(100vh-2rem)] w-full overflow-y-auto rounded-lg border border-slate-200 bg-white p-0 shadow-xl;
}

.admin-modal-header {
  @apply relative border-b border-slate-100 px-6 py-5 pr-14;
}

.admin-modal-footer {
  @apply flex justify-end gap-2 border-t border-slate-100 px-6 py-4;
}

.admin-form-field {
  @apply grid gap-2 text-sm font-medium text-slate-700;
}

.admin-modal-close {
  @apply absolute right-4 top-4 inline-flex h-8 w-8 items-center justify-center rounded-md text-slate-400 transition hover:bg-slate-100 hover:text-slate-700;
}

.ui-button {
  @apply inline-flex items-center justify-center gap-2;
}

button:disabled {
  @apply cursor-not-allowed opacity-50;
}
</style>
