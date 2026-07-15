<template>
  <div class="admin-page knowledge-chunks-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">分块管理</h1>
        <p class="admin-page-subtitle">{{ doc?.docName || docId }} {{ kbName ? `（知识库: ${kbName}）` : "" }}</p>
      </div>
      <div class="admin-page-actions">
        <button class="ui-button action-button" data-variant="outline" type="button" @click="router.back()">返回文档</button>
        <button class="ui-button admin-primary-gradient action-button" type="button" @click="openCreateDialog">
          <Plus class="h-4 w-4" />
          新建分块
        </button>
      </div>
    </header>

    <section class="ui-card">
      <div class="ui-card-header chunk-card-header">
        <div>
          <h2 class="ui-card-title">Chunk 列表</h2>
          <p class="ui-card-description">支持编辑、启停、批量操作</p>
        </div>
        <div class="chunk-toolbar">
          <select v-model="enabledFilter" class="ui-input toolbar-select" @change="handleFilterChange">
            <option value="">全部状态</option>
            <option value="1">启用</option>
            <option value="0">禁用</option>
          </select>
          <button class="ui-button toolbar-button" data-variant="outline" type="button" @click="handleRefresh">
            <RefreshCw class="h-4 w-4" />刷新
          </button>
          <button class="ui-button toolbar-button" data-variant="outline" type="button" :disabled="selectedIds.size === 0" @click="handleBatchToggle(true)">
            <ShieldCheck class="h-4 w-4" />批量启用
          </button>
          <button class="ui-button toolbar-button" data-variant="outline" type="button" :disabled="selectedIds.size === 0" @click="handleBatchToggle(false)">
            <ShieldX class="h-4 w-4" />批量禁用
          </button>
        </div>
      </div>
      <div class="ui-card-content px-6 pt-5">
        <div v-if="loading" class="empty-state">加载中...</div>
        <div v-else-if="chunks.length === 0" class="empty-state">暂无分块</div>
        <div v-else class="ui-table-wrap">
          <table class="ui-table chunk-table w-full">
            <thead class="ui-table-header">
              <tr class="ui-table-row">
                <th class="ui-table-head w-[48px] text-left"><input type="checkbox" :checked="allSelected" aria-label="全选" @change="toggleSelectAll" /></th>
                <th class="ui-table-head w-[70px] text-left">序号</th>
                <th class="ui-table-head text-left">内容</th>
                <th class="ui-table-head w-[90px] text-left">状态</th>
                <th class="ui-table-head w-[90px] text-left">字符数</th>
                <th class="ui-table-head w-[90px] text-left"><span class="token-title">Token<CircleHelp class="h-3.5 w-3.5" title="预估Token数，仅提供参考" /></span></th>
                <th class="ui-table-head w-[160px] text-left">更新时间</th>
                <th class="ui-table-head w-[250px] text-left">操作</th>
              </tr>
            </thead>
            <tbody class="ui-table-body">
              <tr v-for="chunk in chunks" :key="chunk.id" class="ui-table-row">
                <td class="ui-table-cell"><input type="checkbox" :checked="selectedIds.has(String(chunk.id))" :aria-label="`选择分块 ${chunk.chunkIndex ?? chunk.id}`" @change="toggleSelect(String(chunk.id))" /></td>
                <td class="ui-table-cell tabular-nums">{{ chunk.chunkIndex ?? "-" }}</td>
                <td class="ui-table-cell"><p class="chunk-content" :title="chunk.content || ''">{{ truncateText(chunk.content) }}</p></td>
                <td class="ui-table-cell"><span class="ui-badge state-badge" :class="chunk.enabled === 1 ? 'enabled' : 'disabled'">{{ enabledLabel(chunk.enabled) }}</span></td>
                <td class="ui-table-cell tabular-nums">{{ chunk.charCount ?? "-" }}</td>
                <td class="ui-table-cell tabular-nums">{{ chunk.tokenCount ?? "-" }}</td>
                <td class="ui-table-cell text-slate-500"><AdminRelativeTime :value="chunk.updateTime" /></td>
                <td class="ui-table-cell">
                  <div class="row-actions">
                    <button class="ui-button row-button" data-variant="outline" type="button" @click="openEditDialog(chunk)"><PenSquare class="h-4 w-4" />编辑</button>
                    <button class="ui-button row-button" data-variant="outline" type="button" @click="handleToggleEnabled(chunk)">{{ chunk.enabled === 1 ? "禁用" : "启用" }}</button>
                    <button class="ui-button row-button danger-button" data-variant="ghost" type="button" @click="deleteTarget = chunk"><Trash2 class="h-4 w-4" />删除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <AdminPagination v-if="pageData" :current="pageData.current" :pages="pageData.pages" :total="pageData.total" @change="pageNo = $event" />
      </div>
    </section>

    <AdminModal :open="dialogOpen" :title="dialogMode === 'create' ? '新建分块' : '编辑分块'" description="手动维护分块内容" width="760px" @close="dialogOpen = false">
      <div class="chunk-form">
        <label v-if="dialogMode === 'create'" class="index-field">
          <span>序号</span>
          <input v-model="chunkIndex" class="ui-input index-input" type="number" min="0" step="1" placeholder="0、1..." />
          <small>留空则自动追加到末尾</small>
        </label>
        <label class="content-field">
          <span>内容</span>
          <textarea v-model="chunkContent" class="ui-textarea content-input" rows="14"></textarea>
        </label>
      </div>
      <template #footer>
        <button class="ui-button modal-button" data-variant="outline" type="button" :disabled="saving" @click="dialogOpen = false">取消</button>
        <button class="ui-button modal-button" data-variant="default" type="button" :disabled="saving" @click="handleDialogSubmit">{{ saving ? "保存中..." : "保存" }}</button>
      </template>
    </AdminModal>

    <AdminModal :open="Boolean(deleteTarget)" title="确认删除分块？" width="440px" @close="deleteTarget = null">
      <p class="text-sm leading-6 text-slate-600">该分块将被删除且向量会清理。</p>
      <template #footer>
        <button class="ui-button modal-button" data-variant="outline" type="button" @click="deleteTarget = null">取消</button>
        <button class="ui-button modal-button destructive-button" type="button" @click="handleDelete">删除</button>
      </template>
    </AdminModal>
  </div>
</template>

<script setup lang="ts">
import { CircleHelp, PenSquare, Plus, RefreshCw, ShieldCheck, ShieldX, Trash2 } from "lucide-vue-next";
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import AdminModal from "@/components/admin/AdminModal.vue";
import AdminPagination from "@/components/admin/AdminPagination.vue";
import AdminRelativeTime from "@/components/admin/AdminRelativeTime.vue";
import {
  batchToggleChunks,
  createChunk,
  deleteChunk,
  getChunksPage,
  getDocument,
  getKnowledgeBase,
  toggleChunk,
  updateChunk,
  type KnowledgeChunk,
  type KnowledgeDocument,
  type PageResult
} from "@/services/knowledgeService";
import { getErrorMessage } from "@/utils/error";
import { toast } from "@/utils/toast";

const PAGE_SIZE = 10;
const route = useRoute();
const router = useRouter();
const kbId = computed(() => String(route.params.kbId || ""));
const docId = computed(() => String(route.params.docId || ""));

const doc = ref<KnowledgeDocument | null>(null);
const kbName = ref("");
const pageData = ref<PageResult<KnowledgeChunk> | null>(null);
const pageNo = ref(1);
const loading = ref(false);
const enabledFilter = ref("");
const selectedIds = ref(new Set<string>());
const deleteTarget = ref<KnowledgeChunk | null>(null);
const dialogOpen = ref(false);
const dialogMode = ref<"create" | "edit">("create");
const editTarget = ref<KnowledgeChunk | null>(null);
const chunkContent = ref("");
const chunkIndex = ref("");
const saving = ref(false);

const chunks = computed(() => pageData.value?.records || []);
const allSelected = computed(() => chunks.value.length > 0 && chunks.value.every((chunk) => selectedIds.value.has(String(chunk.id))));

async function loadDocument() {
  if (!docId.value) return;
  try { doc.value = await getDocument(docId.value); } catch (error) { toast.error(getErrorMessage(error, "加载文档失败")); }
}

async function loadKnowledgeBase() {
  if (!kbId.value) return;
  try { kbName.value = (await getKnowledgeBase(kbId.value)).name; } catch { kbName.value = ""; }
}

async function loadChunks(current = pageNo.value) {
  if (!docId.value) return;
  loading.value = true;
  try { pageData.value = await getChunksPage(docId.value, { current, size: PAGE_SIZE, enabled: enabledFilter.value === "" ? undefined : Number(enabledFilter.value) }); }
  catch (error) { toast.error(getErrorMessage(error, "加载分块失败")); }
  finally { loading.value = false; }
}

function handleFilterChange() { pageNo.value = 1; selectedIds.value = new Set(); void loadChunks(1); }
function handleRefresh() { pageNo.value = 1; void loadChunks(1); }
function toggleSelectAll() { selectedIds.value = allSelected.value ? new Set() : new Set(chunks.value.map((chunk) => String(chunk.id))); }
function toggleSelect(id: string) { const next = new Set(selectedIds.value); next.has(id) ? next.delete(id) : next.add(id); selectedIds.value = next; }

async function handleBatchToggle(enabled: boolean) {
  const ids = [...selectedIds.value];
  if (!ids.length) return toast.error("请选择需要操作的分块");
  const value = enabled ? 1 : 0;
  if (!chunks.value.filter((chunk) => ids.includes(String(chunk.id))).some((chunk) => chunk.enabled !== value)) return toast.info(enabled ? "所选分块已全部启用" : "所选分块已全部禁用");
  try { await batchToggleChunks(docId.value, enabled, ids); toast.success(enabled ? "批量启用成功" : "批量禁用成功"); selectedIds.value = new Set(); await loadChunks(); }
  catch (error) { toast.error(getErrorMessage(error, enabled ? "批量启用失败" : "批量禁用失败")); }
}

async function handleToggleEnabled(chunk: KnowledgeChunk) {
  const enabled = chunk.enabled !== 1;
  try { await toggleChunk(docId.value, String(chunk.id), enabled); toast.success(enabled ? "已启用" : "已禁用"); await loadChunks(); }
  catch (error) { toast.error(getErrorMessage(error, "操作失败")); }
}

async function handleDelete() {
  if (!deleteTarget.value) return;
  try { await deleteChunk(docId.value, String(deleteTarget.value.id)); toast.success("删除成功"); deleteTarget.value = null; await loadChunks(); }
  catch (error) { toast.error(getErrorMessage(error, "删除失败")); }
}

function openCreateDialog() { dialogMode.value = "create"; editTarget.value = null; chunkContent.value = ""; chunkIndex.value = ""; dialogOpen.value = true; }
function openEditDialog(chunk: KnowledgeChunk) { dialogMode.value = "edit"; editTarget.value = chunk; chunkContent.value = chunk.content || ""; chunkIndex.value = ""; dialogOpen.value = true; }

async function handleDialogSubmit() {
  const content = chunkContent.value.trim();
  if (!content) return toast.error("请输入内容");
  const rawIndex = chunkIndex.value.trim();
  const index = rawIndex === "" ? null : Number(rawIndex);
  if (rawIndex !== "" && (!Number.isInteger(index) || Number(index) < 0)) return toast.error("序号必须为非负整数");
  saving.value = true;
  try {
    if (dialogMode.value === "create") { await createChunk(docId.value, { content, index }); toast.success("创建成功"); }
    else if (editTarget.value) { await updateChunk(docId.value, String(editTarget.value.id), { content }); toast.success("更新成功"); }
    dialogOpen.value = false; await loadChunks();
  } catch (error) { toast.error(getErrorMessage(error, dialogMode.value === "create" ? "创建失败" : "更新失败")); }
  finally { saving.value = false; }
}

function truncateText(value?: string | null, max = 120) { if (!value) return "-"; return value.length <= max ? value : `${value.slice(0, max)}...`; }
function enabledLabel(enabled?: number | null) { return enabled === 1 ? "启用" : "禁用"; }

watch(pageNo, (value) => void loadChunks(value));
watch(docId, () => { pageNo.value = 1; selectedIds.value = new Set(); void loadDocument(); void loadChunks(1); });
onMounted(() => { void loadDocument(); void loadKnowledgeBase(); void loadChunks(); });
</script>

<style scoped>
.action-button,.toolbar-button,.row-button,.modal-button{display:inline-flex;align-items:center;justify-content:center;gap:7px;border:1px solid #e2e8f0}.action-button{height:40px;padding:0 14px;font-size:14px}.chunk-card-header{display:flex;align-items:center;justify-content:space-between;gap:18px}.chunk-toolbar{display:flex;flex:1;flex-wrap:wrap;align-items:center;justify-content:flex-end;gap:8px}.toolbar-select{width:150px;height:40px;padding:0 12px;border:1px solid #e2e8f0;font-size:13px}.toolbar-button{height:40px;padding:0 12px;font-size:13px}.toolbar-button:disabled{cursor:not-allowed;opacity:.45}.empty-state{padding:32px 12px;color:#64748b;text-align:center}.chunk-table{min-width:1040px}.token-title{display:inline-flex;align-items:center;gap:5px}.chunk-content{max-width:420px;overflow:hidden;color:#64748b;font-size:13px;text-overflow:ellipsis;white-space:nowrap}.state-badge{display:inline-flex;border:1px solid;padding:4px 9px}.state-badge.enabled{border-color:#c7d2fe;background:#eef2ff;color:#4338ca}.state-badge.disabled{border-color:#e2e8f0;background:#fff;color:#64748b}.row-actions{display:flex;gap:6px}.row-button{height:32px;padding:0 9px;font-size:12px}.danger-button{color:#dc2626}.chunk-form{display:grid;gap:16px}.index-field{display:flex;align-items:center;gap:12px;color:#334155;font-size:13px;font-weight:500}.index-input{width:100px;height:36px;padding:0 10px;border:1px solid #e2e8f0}.index-field small{color:#64748b;font-weight:400}.content-field{display:grid;gap:8px;color:#334155;font-size:13px;font-weight:500}.content-input{min-height:280px;resize:vertical;border:1px solid #e2e8f0;padding:12px;color:#334155;font-family:inherit;font-size:14px;line-height:1.65}.modal-button{min-width:76px;height:38px;padding:0 14px;font-size:13px}.destructive-button{border-color:#dc2626;background:#dc2626;color:#fff}@media(max-width:900px){.chunk-card-header{align-items:flex-start;flex-direction:column}.chunk-toolbar{width:100%;justify-content:flex-start}}@media(max-width:640px){.toolbar-select{width:100%}.toolbar-button{flex:1}.index-field{align-items:flex-start;flex-wrap:wrap}}
</style>
