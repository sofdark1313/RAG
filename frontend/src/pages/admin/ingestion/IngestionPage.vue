<template>
  <div class="admin-page ingestion-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">数据通道</h1>
        <p class="admin-page-subtitle">管理通道流水线与任务执行情况</p>
      </div>
      <div class="admin-page-actions tab-actions">
        <button class="ui-button tab-button" :class="{ active: activeTab === 'pipelines' }" type="button" @click="changeTab('pipelines')"><FolderKanban class="h-4 w-4" />流水线</button>
        <button class="ui-button tab-button" :class="{ active: activeTab === 'tasks' }" type="button" @click="changeTab('tasks')"><ClipboardList class="h-4 w-4" />任务</button>
      </div>
    </header>

    <section v-if="activeTab === 'pipelines'" class="ui-card">
      <div class="ui-card-header section-header">
        <div><h2 class="ui-card-title">通道流水线</h2><p class="ui-card-description">配置节点顺序与处理逻辑</p></div>
        <div class="section-toolbar">
          <input v-model="pipelineSearch" class="ui-input toolbar-input" placeholder="搜索流水线名称" @keyup.enter="handlePipelineSearch" />
          <button class="ui-button toolbar-button" data-variant="outline" type="button" @click="handlePipelineSearch">搜索</button>
          <button class="ui-button toolbar-button" data-variant="outline" type="button" @click="handlePipelineRefresh"><RefreshCw class="h-4 w-4" />刷新</button>
          <button class="ui-button admin-primary-gradient toolbar-button" type="button" @click="openCreatePipeline"><Plus class="h-4 w-4" />新建流水线</button>
        </div>
      </div>
      <div class="ui-card-content px-6 pt-5">
        <div v-if="pipelineLoading" class="empty-state">加载中...</div>
        <div v-else-if="pipelines.length === 0" class="empty-state">暂无流水线</div>
        <div v-else class="ui-table-wrap">
          <table class="ui-table pipeline-table w-full">
            <thead class="ui-table-header"><tr class="ui-table-row"><th class="ui-table-head w-[250px] text-left">名称</th><th class="ui-table-head text-left">描述</th><th class="ui-table-head w-[90px] text-left">节点数</th><th class="ui-table-head w-[120px] text-left">负责人</th><th class="ui-table-head w-[160px] text-left">更新时间</th><th class="ui-table-head w-[270px] text-left">操作</th></tr></thead>
            <tbody class="ui-table-body">
              <tr v-for="pipeline in pipelines" :key="pipeline.id" class="ui-table-row">
                <td class="ui-table-cell font-medium text-slate-900">{{ pipeline.name }}</td>
                <td class="ui-table-cell text-slate-500">{{ pipeline.description || "-" }}</td>
                <td class="ui-table-cell tabular-nums">{{ pipeline.nodes?.length ?? 0 }}</td>
                <td class="ui-table-cell">{{ pipeline.createdBy || "-" }}</td>
                <td class="ui-table-cell text-slate-500"><AdminRelativeTime :value="pipeline.updateTime" /></td>
                <td class="ui-table-cell"><div class="row-actions"><button class="ui-button row-button" data-variant="outline" type="button" @click="openPipelineNodes(pipeline)">查看节点</button><button class="ui-button row-button" data-variant="outline" type="button" @click="openEditPipeline(pipeline)"><Pencil class="h-4 w-4" />编辑</button><button class="ui-button row-button danger-button" data-variant="ghost" type="button" @click="pipelineDeleteTarget = pipeline"><Trash2 class="h-4 w-4" />删除</button></div></td>
              </tr>
            </tbody>
          </table>
        </div>
        <AdminPagination v-if="pipelinePage" :current="pipelinePage.current" :pages="pipelinePage.pages" :total="pipelinePage.total" @change="pipelinePageNo = $event" />
      </div>
    </section>

    <section v-else class="ui-card">
      <div class="ui-card-header section-header">
        <div><h2 class="ui-card-title">通道任务</h2><p class="ui-card-description">监控执行状态与节点日志</p></div>
        <div class="section-toolbar">
          <select v-model="taskStatus" class="ui-input toolbar-select" @change="handleTaskFilter"><option value="">全部状态</option><option v-for="status in statusOptions" :key="status" :value="status">{{ status }}</option></select>
          <button class="ui-button toolbar-button" data-variant="outline" type="button" @click="handleTaskRefresh"><RefreshCw class="h-4 w-4" />刷新</button>
          <button class="ui-button toolbar-button" data-variant="outline" type="button" @click="uploadDialogOpen = true"><FileUp class="h-4 w-4" />上传文件</button>
          <button class="ui-button admin-primary-gradient toolbar-button" type="button" @click="taskDialogOpen = true"><Plus class="h-4 w-4" />新建任务</button>
        </div>
      </div>
      <div class="ui-card-content px-6 pt-5">
        <div v-if="taskLoading" class="empty-state">加载中...</div>
        <div v-else-if="tasks.length === 0" class="empty-state">暂无任务</div>
        <div v-else class="ui-table-wrap">
          <table class="ui-table task-table w-full">
            <thead class="ui-table-header"><tr class="ui-table-row"><th class="ui-table-head w-[220px] text-left">任务ID</th><th class="ui-table-head text-left">来源</th><th class="ui-table-head w-[110px] text-left">状态</th><th class="ui-table-head w-[120px] text-left">负责人</th><th class="ui-table-head w-[90px] text-left">分片数</th><th class="ui-table-head w-[160px] text-left">创建时间</th><th class="ui-table-head w-[130px] text-left">操作</th></tr></thead>
            <tbody class="ui-table-body">
              <tr v-for="task in tasks" :key="task.id" class="ui-table-row">
                <td class="ui-table-cell task-id" :title="task.id">{{ task.id }}</td>
                <td class="ui-table-cell"><strong>{{ task.sourceType || "-" }}</strong><span class="ml-2 text-slate-500">{{ task.sourceFileName || task.sourceLocation || "" }}</span></td>
                <td class="ui-table-cell"><span class="ui-badge status-badge" :class="statusClass(task.status)">{{ task.status?.toLowerCase() || "unknown" }}</span></td>
                <td class="ui-table-cell">{{ task.createdBy || "-" }}</td>
                <td class="ui-table-cell tabular-nums">{{ task.chunkCount ?? "-" }}</td>
                <td class="ui-table-cell text-slate-500"><AdminRelativeTime :value="task.createTime" /></td>
                <td class="ui-table-cell"><button class="ui-button row-button" data-variant="outline" type="button" @click="openTaskDetail(task.id)">查看详情</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <AdminPagination v-if="taskPage" :current="taskPage.current" :pages="taskPage.pages" :total="taskPage.total" @change="taskPageNo = $event" />
      </div>
    </section>

    <IngestionPipelineDialog :open="pipelineDialog.open" :mode="pipelineDialog.mode" :pipeline="pipelineDialog.pipeline" @close="closePipelineDialog" @submit="handlePipelineSubmit" />
    <IngestionTaskDialog :open="taskDialogOpen" variant="task" :pipelines="pipelineOptions" @close="taskDialogOpen = false" @submit-task="handleTaskSubmit" @upload="handleTaskUpload" />
    <IngestionTaskDialog :open="uploadDialogOpen" variant="upload" :pipelines="pipelineOptions" @close="uploadDialogOpen = false" @upload="handleStandaloneUpload" @submit-task="handleTaskSubmit" />

    <AdminModal :open="pipelineNodesDialog.open" title="流水线节点" :description="pipelineNodesDialog.pipeline?.name || ''" width="720px" @close="pipelineNodesDialog = { open: false, pipeline: null }">
      <div v-if="pipelineNodesDialog.pipeline?.nodes?.length" class="ui-table-wrap">
        <table class="ui-table nodes-table w-full"><thead class="ui-table-header"><tr class="ui-table-row"><th class="ui-table-head w-[55px] text-left">#</th><th class="ui-table-head w-[150px] text-left">节点ID</th><th class="ui-table-head w-[105px] text-left">类型</th><th class="ui-table-head w-[130px] text-left">下一节点</th><th class="ui-table-head text-left">配置</th></tr></thead><tbody class="ui-table-body"><tr v-for="(node, index) in pipelineNodesDialog.pipeline.nodes" :key="node.id || `${node.nodeId}-${index}`" class="ui-table-row"><td class="ui-table-cell">{{ index + 1 }}</td><td class="ui-table-cell task-id">{{ node.nodeId }}</td><td class="ui-table-cell"><span class="ui-badge node-badge">{{ node.nodeType }}</span></td><td class="ui-table-cell">{{ node.nextNodeId || "-" }}</td><td class="ui-table-cell"><pre class="node-config">{{ truncateJson(node.settings || node.condition) }}</pre></td></tr></tbody></table>
      </div>
      <div v-else class="empty-state">暂无节点</div>
    </AdminModal>

    <AdminModal :open="Boolean(pipelineDeleteTarget)" title="确认删除流水线？" width="460px" @close="pipelineDeleteTarget = null"><p class="text-sm leading-6 text-slate-600">流水线 [{{ pipelineDeleteTarget?.name }}] 将被永久删除。</p><template #footer><button class="ui-button modal-button" data-variant="outline" type="button" @click="pipelineDeleteTarget = null">取消</button><button class="ui-button modal-button destructive-button" type="button" @click="handlePipelineDelete">删除</button></template></AdminModal>

    <AdminModal :open="taskDetail.open" title="任务详情" :description="taskDetail.taskId || ''" width="820px" @close="closeTaskDetail">
      <div v-if="taskDetailLoading || !taskDetailData" class="empty-state">加载中...</div>
      <div v-else class="task-detail">
        <div class="task-overview">
          <div><div class="flex items-center gap-2"><span class="ui-badge status-badge" :class="statusClass(taskDetailData.status)">{{ taskDetailData.status || "-" }}</span><span v-if="taskDetailData.errorMessage" class="ui-badge status-badge failed">error</span></div><p>Pipeline: {{ taskDetailData.pipelineId }}</p><p>Source: {{ taskDetailData.sourceType || "-" }} {{ taskDetailData.sourceFileName || taskDetailData.sourceLocation || "" }}</p><p>Chunks: {{ taskDetailData.chunkCount ?? "-" }}</p></div>
          <div><p>Created: {{ formatDate(taskDetailData.createTime) }}</p><p>Started: {{ formatDate(taskDetailData.startedAt) }}</p><p>Completed: {{ formatDate(taskDetailData.completedAt) }}</p></div>
        </div>
        <div v-if="taskDetailData.errorMessage" class="error-box">{{ taskDetailData.errorMessage }}</div>
        <section><h3>任务元数据</h3><pre class="metadata-box">{{ stringifyJson(taskDetailData.metadata) }}</pre></section>
        <section><h3>节点执行日志</h3><div v-if="taskDetailNodes.length === 0" class="mt-2 text-sm text-slate-500">暂无节点日志</div><div v-else class="ui-table-wrap mt-2"><table class="ui-table detail-node-table w-full"><thead class="ui-table-header"><tr class="ui-table-row"><th class="ui-table-head w-[170px] text-left">节点</th><th class="ui-table-head w-[110px] text-left">类型</th><th class="ui-table-head w-[100px] text-left">状态</th><th class="ui-table-head w-[100px] text-left">耗时</th><th class="ui-table-head text-left">消息</th></tr></thead><tbody class="ui-table-body"><tr v-for="node in taskDetailNodes" :key="node.id" class="ui-table-row"><td class="ui-table-cell task-id">{{ node.nodeId }}</td><td class="ui-table-cell">{{ node.nodeType }}</td><td class="ui-table-cell"><span class="ui-badge status-badge" :class="nodeStatusClass(node.status)">{{ node.status || "-" }}</span></td><td class="ui-table-cell">{{ node.durationMs ?? "-" }} ms</td><td class="ui-table-cell text-xs text-slate-500">{{ node.message || node.errorMessage || "-" }}</td></tr></tbody></table></div></section>
      </div>
    </AdminModal>
  </div>
</template>

<script setup lang="ts">
import { ClipboardList, FileUp, FolderKanban, Pencil, Plus, RefreshCw, Trash2 } from "lucide-vue-next";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import AdminModal from "@/components/admin/AdminModal.vue";
import AdminPagination from "@/components/admin/AdminPagination.vue";
import AdminRelativeTime from "@/components/admin/AdminRelativeTime.vue";
import IngestionPipelineDialog from "@/components/admin/IngestionPipelineDialog.vue";
import IngestionTaskDialog from "@/components/admin/IngestionTaskDialog.vue";
import {
  createIngestionPipeline,
  createIngestionTask,
  deleteIngestionPipeline,
  getIngestionPipeline,
  getIngestionPipelines,
  getIngestionTask,
  getIngestionTaskNodes,
  getIngestionTasks,
  updateIngestionPipeline,
  uploadIngestionTask,
  type IngestionPipeline,
  type IngestionPipelinePayload,
  type IngestionTask,
  type IngestionTaskCreatePayload,
  type IngestionTaskNode,
  type PageResult
} from "@/services/ingestionService";
import { getErrorMessage } from "@/utils/error";
import { toast } from "@/utils/toast";

const PIPELINE_PAGE_SIZE = 10;
const TASK_PAGE_SIZE = 10;
const statusOptions = ["pending", "running", "completed", "failed"];
const route = useRoute();
const router = useRouter();
const activeTab = ref<"pipelines" | "tasks">(route.query.tab === "tasks" ? "tasks" : "pipelines");
const pipelinePage = ref<PageResult<IngestionPipeline> | null>(null);
const pipelineKeyword = ref("");
const pipelineSearch = ref("");
const pipelinePageNo = ref(1);
const pipelineLoading = ref(false);
const pipelineOptions = ref<IngestionPipeline[]>([]);
const pipelineDeleteTarget = ref<IngestionPipeline | null>(null);
const pipelineDialog = reactive<{ open: boolean; mode: "create" | "edit"; pipeline: IngestionPipeline | null }>({ open: false, mode: "create", pipeline: null });
let pipelineNodesDialog = reactive<{ open: boolean; pipeline: IngestionPipeline | null }>({ open: false, pipeline: null });
const taskPage = ref<PageResult<IngestionTask> | null>(null);
const taskStatus = ref("");
const taskPageNo = ref(1);
const taskLoading = ref(false);
const taskDialogOpen = ref(false);
const uploadDialogOpen = ref(false);
const taskDetail = reactive<{ open: boolean; taskId: string | null }>({ open: false, taskId: null });
const taskDetailData = ref<IngestionTask | null>(null);
const taskDetailNodes = ref<IngestionTaskNode[]>([]);
const taskDetailLoading = ref(false);
const pipelines = computed(() => pipelinePage.value?.records || []);
const tasks = computed(() => taskPage.value?.records || []);

async function loadPipelines(current = pipelinePageNo.value) { pipelineLoading.value = true; try { pipelinePage.value = await getIngestionPipelines(current, PIPELINE_PAGE_SIZE, pipelineKeyword.value || undefined); } catch (error) { toast.error(getErrorMessage(error, "加载流水线失败")); } finally { pipelineLoading.value = false; } }
async function loadPipelineOptions() { try { pipelineOptions.value = (await getIngestionPipelines(1, 200)).records || []; } catch { pipelineOptions.value = []; } }
async function loadTasks(current = taskPageNo.value) { taskLoading.value = true; try { taskPage.value = await getIngestionTasks(current, TASK_PAGE_SIZE, taskStatus.value || undefined); } catch (error) { toast.error(getErrorMessage(error, "加载任务失败")); } finally { taskLoading.value = false; } }

function changeTab(tab: "pipelines" | "tasks") { activeTab.value = tab; void router.replace({ query: { ...route.query, tab } }); }
function handlePipelineSearch() { pipelinePageNo.value = 1; pipelineKeyword.value = pipelineSearch.value.trim(); }
function handlePipelineRefresh() { pipelinePageNo.value = 1; void loadPipelines(1); void loadPipelineOptions(); }
function handleTaskFilter() { taskPageNo.value = 1; void loadTasks(1); }
function handleTaskRefresh() { taskPageNo.value = 1; void loadTasks(1); void loadPipelineOptions(); }
function openCreatePipeline() { Object.assign(pipelineDialog, { open: true, mode: "create", pipeline: null }); }
function closePipelineDialog() { Object.assign(pipelineDialog, { open: false, mode: "create", pipeline: null }); }

async function openEditPipeline(pipeline: IngestionPipeline) { try { Object.assign(pipelineDialog, { open: true, mode: "edit", pipeline: await getIngestionPipeline(pipeline.id) }); } catch (error) { toast.error(getErrorMessage(error, "获取流水线详情失败")); } }
async function openPipelineNodes(pipeline: IngestionPipeline) { try { pipelineNodesDialog.open = true; pipelineNodesDialog.pipeline = await getIngestionPipeline(pipeline.id); } catch (error) { pipelineNodesDialog.open = false; toast.error(getErrorMessage(error, "获取流水线详情失败")); } }
async function handlePipelineSubmit(payload: IngestionPipelinePayload) { try { if (pipelineDialog.mode === "create") { await createIngestionPipeline(payload); toast.success("创建成功"); } else if (pipelineDialog.pipeline) { await updateIngestionPipeline(pipelineDialog.pipeline.id, payload); toast.success("更新成功"); } closePipelineDialog(); pipelinePageNo.value = 1; await Promise.all([loadPipelines(1), loadPipelineOptions()]); } catch (error) { toast.error(getErrorMessage(error, pipelineDialog.mode === "create" ? "创建失败" : "更新失败")); } }
async function handlePipelineDelete() { if (!pipelineDeleteTarget.value) return; try { await deleteIngestionPipeline(pipelineDeleteTarget.value.id); toast.success("删除成功"); pipelineDeleteTarget.value = null; pipelinePageNo.value = 1; await Promise.all([loadPipelines(1), loadPipelineOptions()]); } catch (error) { toast.error(getErrorMessage(error, "删除失败")); } }

async function handleTaskSubmit(payload: IngestionTaskCreatePayload) { try { const result = await createIngestionTask(payload); toast.success(`任务已创建：${result.taskId}`); taskDialogOpen.value = false; taskPageNo.value = 1; await loadTasks(1); } catch (error) { toast.error(getErrorMessage(error, "创建失败")); } }
async function handleTaskUpload(pipelineId: string, file: File) { try { const result = await uploadIngestionTask(pipelineId, file); toast.success(`上传成功：${result.taskId}`); taskDialogOpen.value = false; taskPageNo.value = 1; await loadTasks(1); } catch (error) { toast.error(getErrorMessage(error, "上传失败")); } }
async function handleStandaloneUpload(pipelineId: string, file: File) { try { const result = await uploadIngestionTask(pipelineId, file); toast.success(`上传成功：${result.taskId}`); uploadDialogOpen.value = false; taskPageNo.value = 1; await loadTasks(1); } catch (error) { toast.error(getErrorMessage(error, "上传失败")); } }

async function openTaskDetail(taskId: string) { taskDetail.open = true; taskDetail.taskId = taskId; taskDetailLoading.value = true; try { const [detail, nodes] = await Promise.all([getIngestionTask(taskId), getIngestionTaskNodes(taskId)]); taskDetailData.value = detail; taskDetailNodes.value = nodes || []; } catch (error) { toast.error(getErrorMessage(error, "加载任务详情失败")); } finally { taskDetailLoading.value = false; } }
function closeTaskDetail() { taskDetail.open = false; taskDetail.taskId = null; taskDetailData.value = null; taskDetailNodes.value = []; }

function stringifyJson(value: unknown) { if (!value) return "-"; try { return JSON.stringify(value, null, 2); } catch { return String(value); } }
function truncateJson(value: unknown, max = 120) { const raw = stringifyJson(value); return raw.length <= max ? raw : `${raw.slice(0, max)}...`; }
function statusClass(status?: string | null) { const value = status?.toLowerCase(); return value === "completed" || value === "success" ? "completed" : value === "failed" ? "failed" : value === "running" ? "running" : "pending"; }
function nodeStatusClass(status?: string | null) { return statusClass(status); }
function formatDate(value?: string | null) { if (!value) return "-"; const date = new Date(value); return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN"); }

watch(pipelinePageNo, (value) => void loadPipelines(value));
watch(pipelineKeyword, () => void loadPipelines(1));
watch(taskPageNo, (value) => void loadTasks(value));
watch(() => route.query.tab, (tab) => { activeTab.value = tab === "tasks" ? "tasks" : "pipelines"; });
onMounted(() => { if (route.query.tab !== "tasks" && route.query.tab !== "pipelines") void router.replace({ query: { ...route.query, tab: "pipelines" } }); void loadPipelines(); void loadTasks(); void loadPipelineOptions(); });
</script>

<style scoped>
.tab-actions{flex:0 0 auto}.tab-button,.toolbar-button,.row-button,.modal-button{display:inline-flex;align-items:center;justify-content:center;gap:7px;border:1px solid #e2e8f0}.tab-button{height:36px;padding:0 12px;background:#fff;color:#475569;font-size:13px}.tab-button.active{border-color:#4f46e5;background:#4f46e5;color:#fff}.section-header{display:flex;align-items:center;justify-content:space-between;gap:18px}.section-toolbar{display:flex;flex:1;flex-wrap:wrap;align-items:center;justify-content:flex-end;gap:8px}.toolbar-input,.toolbar-select{height:40px;padding:0 12px;border:1px solid #e2e8f0;font-size:13px}.toolbar-input{width:250px}.toolbar-select{width:170px}.toolbar-button{height:40px;padding:0 13px;font-size:13px}.empty-state{padding:36px 12px;color:#64748b;text-align:center}.pipeline-table{min-width:1020px}.task-table{min-width:1050px}.nodes-table{min-width:660px}.detail-node-table{min-width:720px}.row-actions{display:flex;align-items:center;gap:7px}.row-button{height:32px;padding:0 9px;font-size:12px}.danger-button{color:#dc2626}.task-id{max-width:220px;overflow:hidden;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:11px;text-overflow:ellipsis;white-space:nowrap}.status-badge,.node-badge{display:inline-flex;border:1px solid;padding:4px 9px}.status-badge.completed{border-color:#a7f3d0;background:#ecfdf5;color:#047857}.status-badge.failed{border-color:#fecaca;background:#fef2f2;color:#dc2626}.status-badge.running{border-color:#fde68a;background:#fffbeb;color:#b45309}.status-badge.pending,.node-badge{border-color:#e2e8f0;background:#f8fafc;color:#64748b}.node-config{max-width:280px;overflow:hidden;color:#64748b;font-size:11px;line-height:1.5;text-overflow:ellipsis;white-space:pre-wrap}.modal-button{min-width:76px;height:38px;padding:0 14px;font-size:13px}.destructive-button{border-color:#dc2626;background:#dc2626;color:#fff}.task-detail{display:grid;gap:22px}.task-overview{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px;color:#64748b;font-size:13px;line-height:1.8}.error-box{border:1px solid #fecaca;border-radius:8px;background:#fef2f2;padding:12px;color:#dc2626;font-size:13px}.task-detail h3{color:#334155;font-size:13px;font-weight:600}.metadata-box{margin-top:8px;overflow:auto;border-radius:8px;background:#f8fafc;padding:12px;color:#64748b;font-size:11px;line-height:1.55}@media(max-width:900px){.section-header{align-items:flex-start;flex-direction:column}.section-toolbar{width:100%;justify-content:flex-start}}@media(max-width:640px){.toolbar-input,.toolbar-select{width:100%}.toolbar-button{flex:1}.task-overview{grid-template-columns:1fr}.tab-actions{width:100%}.tab-button{flex:1}}
</style>
