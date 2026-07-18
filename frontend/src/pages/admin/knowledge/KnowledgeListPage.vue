<template>
  <div class="admin-page knowledge-list-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">知识库管理</h1>
        <p class="admin-page-subtitle">管理所有知识库及其文档</p>
      </div>
      <div class="admin-page-actions">
        <input
          v-model="searchName"
          class="ui-input action-input"
          placeholder="搜索知识库名称"
          @keyup.enter="handleSearch"
        />
        <button class="ui-button action-button" data-variant="outline" type="button" @click="handleSearch">
          搜索
        </button>
        <button class="ui-button action-button" data-variant="outline" type="button" @click="handleRefresh">
          <RefreshCw class="h-4 w-4" />
          刷新
        </button>
        <button class="ui-button admin-primary-gradient action-button" type="button" @click="openCreateDialog">
          <Plus class="h-4 w-4" />
          新建知识库
        </button>
      </div>
    </header>

    <section class="admin-stat-grid">
      <div v-for="item in statCards" :key="item.label" class="admin-stat-card">
        <div class="flex items-center gap-3">
          <div class="admin-stat-icon">
            <component :is="item.icon" class="h-5 w-5" />
          </div>
          <div>
            <p class="admin-stat-label">{{ item.label }}</p>
            <p class="admin-stat-value">{{ formatStatValue(item.value) }}</p>
          </div>
        </div>
        <span class="admin-stat-scope admin-stat-scope--stamp">全部</span>
      </div>
    </section>

    <section class="ui-card">
      <div class="ui-card-content px-6 pt-6">
        <div v-if="loading" class="empty-state">加载中...</div>
        <div v-else-if="knowledgeBases.length === 0" class="empty-state">暂无知识库，点击上方按钮创建</div>
        <div v-else class="ui-table-wrap">
          <table class="ui-table min-w-[980px] w-full">
            <thead class="ui-table-header">
              <tr class="ui-table-row">
                <th class="ui-table-head w-[200px] text-left">名称</th>
                <th class="ui-table-head w-[180px] text-left">Embedding模型</th>
                <th class="ui-table-head w-[220px] text-left">Collection</th>
                <th class="ui-table-head w-[90px] text-left">文档数</th>
                <th class="ui-table-head w-[120px] text-left">负责人</th>
                <th class="ui-table-head w-[160px] text-left">创建时间</th>
                <th class="ui-table-head w-[160px] text-left">修改时间</th>
                <th class="ui-table-head w-[150px] text-left">操作</th>
              </tr>
            </thead>
            <tbody class="ui-table-body">
              <tr v-for="kb in knowledgeBases" :key="kb.id" class="ui-table-row">
                <td class="ui-table-cell font-medium">
                  <button class="admin-link max-w-[200px] truncate" type="button" @click="openDocuments(kb.id)">
                    {{ kb.name }}
                  </button>
                </td>
                <td class="ui-table-cell">
                  <div v-if="embeddingParts(kb.embeddingModel).tail" class="text-xs text-slate-500">
                    <div class="font-medium text-slate-700">{{ embeddingParts(kb.embeddingModel).head }}</div>
                    <div>{{ embeddingParts(kb.embeddingModel).tail }}</div>
                  </div>
                  <span v-else class="text-sm text-slate-700">{{ kb.embeddingModel || "-" }}</span>
                </td>
                <td class="ui-table-cell">
                  <span v-if="kb.collectionName" class="ui-badge collection-badge" :class="collectionClass(kb.collectionName)">
                    {{ kb.collectionName }}
                  </span>
                  <span v-else>-</span>
                </td>
                <td class="ui-table-cell tabular-nums">{{ kb.documentCount ?? "-" }}</td>
                <td class="ui-table-cell">{{ kb.createdBy || "-" }}</td>
                <td class="ui-table-cell text-slate-500"><AdminRelativeTime :value="kb.createTime" /></td>
                <td class="ui-table-cell text-slate-500"><AdminRelativeTime :value="kb.updateTime" /></td>
                <td class="ui-table-cell">
                  <div class="flex gap-2">
                    <button class="ui-button row-button" data-variant="outline" type="button" @click="openRenameDialog(kb)">
                      <Pencil class="h-4 w-4" />
                      编辑
                    </button>
                    <button class="ui-button row-button danger-button" data-variant="ghost" type="button" @click="deleteTarget = kb">
                      <Trash2 class="h-4 w-4" />
                      删除
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <AdminPagination
          v-if="pageData"
          :current="pageData.current"
          :pages="pageData.pages"
          :total="pageData.total"
          @change="pageNo = $event"
        />
      </div>
    </section>

    <AdminModal
      :open="createDialogOpen"
      title="创建知识库"
      description="创建一个新的知识库，用于存储和检索文档"
      width="500px"
      @close="closeCreateDialog"
    >
      <form class="form-stack" @submit.prevent="handleCreate">
        <label class="form-field">
          <span>知识库名称</span>
          <input v-model="createForm.name" class="ui-input form-control" maxlength="50" placeholder="例如：产品文档库" />
          <small>为知识库起一个易于识别的名称</small>
        </label>
        <label class="form-field">
          <span>Embedding模型</span>
          <select v-model="createForm.embeddingModel" class="ui-input form-control" :disabled="modelLoading">
            <option value="" disabled>{{ modelLoading ? "加载中..." : "选择Embedding模型" }}</option>
            <option v-for="model in embeddingModels" :key="model.id" :value="model.id">
              {{ model.provider && model.model ? `${model.provider} · ${model.model}` : model.model || model.id }}
            </option>
          </select>
          <small>选择用于向量化文档的模型</small>
        </label>
        <label class="form-field">
          <span>Collection名称</span>
          <input v-model="createForm.collectionName" class="ui-input form-control" maxlength="50" placeholder="例如：productdocs" />
          <small>只能包含小写英文字母和数字</small>
        </label>
      </form>
      <template #footer>
        <button class="ui-button modal-button" data-variant="outline" type="button" :disabled="creating" @click="closeCreateDialog">
          取消
        </button>
        <button class="ui-button modal-button" data-variant="default" type="button" :disabled="creating" @click="handleCreate">
          {{ creating ? "创建中..." : "创建" }}
        </button>
      </template>
    </AdminModal>

    <AdminModal :open="Boolean(renameTarget)" title="重命名知识库" description="修改知识库名称" width="420px" @close="renameTarget = null">
      <label class="form-field">
        <span>名称</span>
        <input v-model="renameValue" class="ui-input form-control" @keyup.enter="handleRename" />
      </label>
      <template #footer>
        <button class="ui-button modal-button" data-variant="outline" type="button" @click="renameTarget = null">取消</button>
        <button class="ui-button modal-button" data-variant="default" type="button" @click="handleRename">保存</button>
      </template>
    </AdminModal>

    <AdminModal :open="Boolean(deleteTarget)" title="确认删除" width="440px" @close="deleteTarget = null">
      <p class="text-sm leading-6 text-slate-600">知识库删除后当前不提供恢复入口。确定要继续吗？</p>
      <template #footer>
        <button class="ui-button modal-button" data-variant="outline" type="button" @click="deleteTarget = null">取消</button>
        <button class="ui-button modal-button delete-confirm" type="button" @click="handleDelete">删除</button>
      </template>
    </AdminModal>
  </div>
</template>

<script setup lang="ts">
import { Database, FileBarChart, FolderOpen, Layers, Pencil, Plus, RefreshCw, Trash2 } from "lucide-vue-next";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import AdminModal from "@/components/admin/AdminModal.vue";
import AdminPagination from "@/components/admin/AdminPagination.vue";
import AdminRelativeTime from "@/components/admin/AdminRelativeTime.vue";
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  getKnowledgeBasesPage,
  renameKnowledgeBase,
  type KnowledgeBase,
  type PageResult
} from "@/services/knowledgeService";
import { getSystemSettings, type ModelCandidate } from "@/services/settingsService";
import { getErrorMessage } from "@/utils/error";
import { toast } from "@/utils/toast";

const PAGE_SIZE = 10;
const STATS_PAGE_SIZE = 200;
const route = useRoute();
const router = useRouter();

const queryName = computed(() => (typeof route.query.name === "string" ? route.query.name : ""));
const pageData = ref<PageResult<KnowledgeBase> | null>(null);
const loading = ref(true);
const searchName = ref(queryName.value);
const keyword = ref(queryName.value.trim());
const pageNo = ref(1);
const deleteTarget = ref<KnowledgeBase | null>(null);
const renameTarget = ref<KnowledgeBase | null>(null);
const renameValue = ref("");
const createDialogOpen = ref(false);
const creating = ref(false);
const modelLoading = ref(false);
const embeddingModels = ref<ModelCandidate[]>([]);
const statsLoading = ref(true);
const stats = reactive({ totalCount: 0, documentCount: 0, activeCount: 0, creatorCount: 0 });
const createForm = reactive({ name: "", embeddingModel: "", collectionName: "" });
let statsRequestId = 0;

const knowledgeBases = computed(() => pageData.value?.records || []);
const statCards = computed(() => [
  { label: "知识库", value: stats.totalCount, icon: Database },
  { label: "文档数", value: stats.documentCount, icon: FileBarChart },
  { label: "含文档知识库", value: stats.activeCount, icon: FolderOpen },
  { label: "创建用户数", value: stats.creatorCount, icon: Layers }
]);

async function loadKnowledgeBases(current = pageNo.value, name = keyword.value) {
  loading.value = true;
  try {
    pageData.value = await getKnowledgeBasesPage(current, PAGE_SIZE, name || undefined);
  } catch (error) {
    toast.error(getErrorMessage(error, "加载知识库列表失败"));
  } finally {
    loading.value = false;
  }
}

async function loadStats(name = keyword.value) {
  const requestId = ++statsRequestId;
  statsLoading.value = true;
  try {
    const firstPage = await getKnowledgeBasesPage(1, STATS_PAGE_SIZE, name.trim() || undefined);
    if (requestId !== statsRequestId) return;
    let documentCount = 0;
    let activeCount = 0;
    const creators = new Set<string>();
    const collect = (records: KnowledgeBase[]) => {
      for (const kb of records) {
        const count = kb.documentCount ?? 0;
        documentCount += count;
        if (count > 0) activeCount += 1;
        if (kb.createdBy) creators.add(kb.createdBy);
      }
    };
    collect(firstPage.records || []);
    const total = firstPage.total ?? firstPage.records.length;
    const pages = firstPage.pages || Math.max(1, Math.ceil(total / STATS_PAGE_SIZE));
    for (let current = 2; current <= pages; current += 1) {
      const nextPage = await getKnowledgeBasesPage(current, STATS_PAGE_SIZE, name.trim() || undefined);
      if (requestId !== statsRequestId) return;
      collect(nextPage.records || []);
    }
    Object.assign(stats, { totalCount: total, documentCount, activeCount, creatorCount: creators.size });
  } catch {
    if (requestId === statsRequestId) {
      Object.assign(stats, { totalCount: 0, documentCount: 0, activeCount: 0, creatorCount: 0 });
    }
  } finally {
    if (requestId === statsRequestId) statsLoading.value = false;
  }
}

function handleSearch() {
  pageNo.value = 1;
  keyword.value = searchName.value.trim();
}

function handleRefresh() {
  pageNo.value = 1;
  void loadKnowledgeBases(1, keyword.value);
  void loadStats(keyword.value);
}

function openDocuments(id: string) {
  void router.push(`/admin/knowledge/${id}`);
}

function openRenameDialog(kb: KnowledgeBase) {
  renameTarget.value = kb;
  renameValue.value = kb.name;
}

async function handleRename() {
  if (!renameTarget.value) return;
  const name = renameValue.value.trim();
  if (!name) {
    toast.error("请输入知识库名称");
    return;
  }
  if (name === renameTarget.value.name) {
    renameTarget.value = null;
    return;
  }
  try {
    await renameKnowledgeBase(renameTarget.value.id, name);
    toast.success("重命名成功");
    renameTarget.value = null;
    await loadKnowledgeBases();
  } catch (error) {
    toast.error(getErrorMessage(error, "重命名失败"));
  }
}

async function handleDelete() {
  if (!deleteTarget.value) return;
  try {
    await deleteKnowledgeBase(deleteTarget.value.id);
    toast.success("删除成功");
    deleteTarget.value = null;
    pageNo.value = 1;
    await Promise.all([loadKnowledgeBases(1), loadStats()]);
  } catch (error) {
    toast.error(getErrorMessage(error, "删除失败"));
  }
}

async function openCreateDialog() {
  createDialogOpen.value = true;
  modelLoading.value = true;
  try {
    const settings = await getSystemSettings();
    const unique = new Map<string, ModelCandidate>();
    for (const item of settings.ai?.embedding?.candidates || []) {
      if (item.id && item.enabled !== false) unique.set(item.id, item);
    }
    embeddingModels.value = [...unique.values()];
  } catch {
    embeddingModels.value = [];
  } finally {
    modelLoading.value = false;
  }
}

function closeCreateDialog() {
  createDialogOpen.value = false;
  Object.assign(createForm, { name: "", embeddingModel: "", collectionName: "" });
}

async function handleCreate() {
  const name = createForm.name.trim();
  const collectionName = createForm.collectionName.trim();
  if (!name) return toast.error("请输入知识库名称");
  if (!createForm.embeddingModel) return toast.error("请选择Embedding模型");
  if (!collectionName) return toast.error("请输入Collection名称");
  if (!/^[a-z0-9]+$/.test(collectionName)) return toast.error("Collection名称只能包含小写英文字母和数字");
  creating.value = true;
  try {
    await createKnowledgeBase({ name, embeddingModel: createForm.embeddingModel, collectionName });
    toast.success("创建成功");
    closeCreateDialog();
    pageNo.value = 1;
    await Promise.all([loadKnowledgeBases(1), loadStats()]);
  } catch (error) {
    toast.error(getErrorMessage(error, "创建失败"));
  } finally {
    creating.value = false;
  }
}

function formatStatValue(value: number) {
  return statsLoading.value ? "--" : value.toLocaleString("zh-CN");
}

function embeddingParts(model?: string) {
  if (!model) return { head: "", tail: "" };
  const parts = model.split("-");
  if (parts.length < 2) return { head: model, tail: "" };
  return { head: parts.slice(0, -1).join("-"), tail: parts.at(-1) || "" };
}

function collectionClass(name: string) {
  const value = name.toLowerCase();
  if (value.includes("biz")) return "border-blue-200 bg-blue-50 text-blue-700";
  if (value.includes("group")) return "border-purple-200 bg-purple-50 text-purple-700";
  return "border-slate-200 bg-slate-100 text-slate-600";
}

watch([pageNo, keyword], () => void loadKnowledgeBases());
watch(keyword, () => void loadStats());
watch(queryName, (value) => {
  const next = value.trim();
  if (next !== keyword.value) {
    searchName.value = next;
    keyword.value = next;
    pageNo.value = 1;
  }
});

onMounted(() => {
  void loadKnowledgeBases();
  void loadStats();
});
</script>

<style scoped>
.action-input {
  width: 220px;
  height: 40px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  font-size: 14px;
}

.action-button,
.modal-button,
.row-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 1px solid #e2e8f0;
}

.action-button {
  min-height: 40px;
  padding: 0 14px;
  font-size: 14px;
}

.row-button {
  min-height: 32px;
  padding: 0 10px;
  font-size: 12px;
}

.danger-button,
.delete-confirm {
  color: #dc2626;
}

.delete-confirm {
  border-color: #dc2626;
  background: #dc2626;
  color: #fff;
}

.collection-badge {
  display: inline-flex;
  max-width: 205px;
  overflow: hidden;
  padding: 5px 12px;
  border-width: 1px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-state {
  padding: 32px 12px;
  color: #64748b;
  text-align: center;
}

.form-stack {
  display: grid;
  gap: 18px;
}

.form-field {
  display: grid;
  gap: 7px;
  color: #334155;
  font-size: 13px;
  font-weight: 500;
}

.form-field small {
  color: #64748b;
  font-weight: 400;
}

.form-control {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  font-size: 14px;
}

.modal-button {
  min-width: 76px;
  min-height: 38px;
  padding: 0 14px;
  font-size: 13px;
}

@media (max-width: 720px) {
  .admin-page-actions,
  .action-input {
    width: 100%;
  }

  .action-button {
    flex: 1;
  }
}
</style>
