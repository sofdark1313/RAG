<template>
  <div class="admin-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">示例问题管理</h1>
        <p class="admin-page-subtitle">配置欢迎页的示例问题与推荐问法</p>
      </div>
      <div class="admin-page-actions">
        <input
          v-model="searchKeyword"
          class="ui-input h-10 w-[240px] border px-3 text-sm"
          placeholder="搜索标题/描述/问题"
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
          新增示例
        </button>
      </div>
    </header>

    <section class="ui-card">
      <div class="ui-card-content px-6 pt-6">
        <div v-if="loading" class="py-8 text-center text-muted-foreground">加载中...</div>
        <div v-else-if="records.length === 0" class="py-8 text-center text-muted-foreground">
          暂无示例问题，点击上方按钮新增
        </div>
        <div v-else class="ui-table-wrap">
          <table class="ui-table min-w-[860px] w-full">
            <thead class="ui-table-header">
              <tr class="ui-table-row">
                <th class="ui-table-head w-[180px] text-left">标题</th>
                <th class="ui-table-head w-[220px] text-left">描述</th>
                <th class="ui-table-head text-left">示例问题</th>
                <th class="ui-table-head w-[170px] text-left">更新时间</th>
                <th class="ui-table-head w-[140px] text-left">操作</th>
              </tr>
            </thead>
            <tbody class="ui-table-body">
              <tr v-for="item in records" :key="item.id" class="ui-table-row">
                <td
                  class="ui-table-cell max-w-[160px] truncate font-medium text-slate-900"
                  :title="item.title || ''"
                >
                  {{ item.title || "-" }}
                </td>
                <td class="ui-table-cell max-w-[200px] truncate" :title="item.description || ''">
                  {{ item.description || "-" }}
                </td>
                <td class="ui-table-cell max-w-[360px] truncate" :title="item.question">
                  {{ item.question }}
                </td>
                <td
                  class="ui-table-cell cursor-default truncate text-sm tabular-nums text-muted-foreground"
                  :title="formatTimeTooltip(item.updateTime || item.createTime)"
                >
                  {{ formatTime(item.updateTime || item.createTime) }}
                </td>
                <td class="ui-table-cell">
                  <div class="flex justify-end gap-2">
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
        aria-labelledby="sample-delete-title"
        aria-modal="true"
        class="admin-modal max-w-[440px]"
        role="alertdialog"
      >
        <div class="admin-modal-header">
          <h2 id="sample-delete-title" class="text-lg font-semibold text-slate-900">确认删除</h2>
          <p class="mt-2 text-sm text-slate-500">删除后该示例问题将不会出现在欢迎页，是否继续？</p>
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
            {{ dialogState.mode === "create" ? "新增示例问题" : "编辑示例问题" }}
          </h2>
          <p class="mt-1 text-sm text-slate-500">配置欢迎页的示例问题和推荐问法</p>
          <button class="admin-modal-close" title="关闭" type="button" @click="closeDialog">
            <X :size="18" />
          </button>
        </div>
        <div class="space-y-4 px-6 py-5">
          <label class="admin-form-field">
            <span>标题</span>
            <input
              v-model="form.title"
              autofocus
              class="ui-input h-10 border px-3 text-sm"
              placeholder="例如：任务拆解"
            />
          </label>
          <label class="admin-form-field">
            <span>描述</span>
            <input
              v-model="form.description"
              class="ui-input h-10 border px-3 text-sm"
              placeholder="例如：把目标拆成可执行步骤与优先级"
            />
          </label>
          <label class="admin-form-field">
            <span>示例问题</span>
            <textarea
              v-model="form.question"
              class="ui-textarea min-h-[120px] resize-y border px-3 py-2 text-sm"
              placeholder="请输入示例问题内容"
            ></textarea>
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
  createSampleQuestion,
  deleteSampleQuestion,
  getSampleQuestionsPage,
  updateSampleQuestion,
  type PageResult,
  type SampleQuestion
} from "@/services/sampleQuestionService";
import { getErrorMessage } from "@/utils/error";
import { formatRelativeTime, formatTooltipTime } from "@/utils/time";
import { toast } from "@/utils/toast";

const PAGE_SIZE = 10;

const pageData = ref<PageResult<SampleQuestion> | null>(null);
const loading = ref(true);
const deleteTarget = ref<SampleQuestion | null>(null);
const pageNo = ref(1);
const searchKeyword = ref("");
const keyword = ref("");
const dialogState = reactive<{
  open: boolean;
  mode: "create" | "edit";
  item: SampleQuestion | null;
}>({ open: false, mode: "create", item: null });
const form = reactive({ title: "", description: "", question: "" });

const records = computed(() => pageData.value?.records || []);
const dialogTitleId = computed(() => `sample-question-${dialogState.mode}-title`);

function resetForm() {
  form.title = "";
  form.description = "";
  form.question = "";
}

async function loadQuestions(current = pageNo.value, keywordValue = keyword.value) {
  try {
    loading.value = true;
    pageData.value = await getSampleQuestionsPage(current, PAGE_SIZE, keywordValue || undefined);
  } catch (error) {
    toast.error(getErrorMessage(error, "加载示例问题失败"));
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
  await loadQuestions(1, keyword.value);
}

function openCreateDialog() {
  resetForm();
  dialogState.open = true;
  dialogState.mode = "create";
  dialogState.item = null;
}

function openEditDialog(item: SampleQuestion) {
  form.title = item.title || "";
  form.description = item.description || "";
  form.question = item.question || "";
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
    title: form.title.trim() || null,
    description: form.description.trim() || null,
    question: form.question.trim()
  };

  if (!payload.question) {
    toast.error("请输入示例问题内容");
    return;
  }

  try {
    if (dialogState.mode === "create") {
      await createSampleQuestion(payload);
      toast.success("创建成功");
      pageNo.value = 1;
      await loadQuestions(1, keyword.value);
    } else if (dialogState.item) {
      await updateSampleQuestion(dialogState.item.id, payload);
      toast.success("更新成功");
      await loadQuestions(pageNo.value, keyword.value);
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
    await deleteSampleQuestion(deleteTarget.value.id);
    toast.success("删除成功");
    pageNo.value = 1;
    await loadQuestions(1, keyword.value);
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

watch([pageNo, keyword], () => loadQuestions(), { immediate: true });
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
