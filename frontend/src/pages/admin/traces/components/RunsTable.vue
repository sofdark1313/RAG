<template>
  <section class="ui-card trace-list-table-card">
    <div class="ui-card-content trace-list-table-content">
      <div v-if="loading" class="trace-list-table-empty">加载中...</div>
      <div v-else-if="runs.length === 0" class="trace-list-table-empty">暂无链路数据</div>
      <div v-else class="trace-list-table-wrap">
        <div class="ui-table-wrap">
          <table class="ui-table trace-list-table">
            <thead class="ui-table-header">
              <tr class="ui-table-row">
                <th class="ui-table-head trace-col-question">用户问题</th>
                <th class="ui-table-head trace-col-trace-id">Trace Id</th>
                <th class="ui-table-head trace-col-user">用户名</th>
                <th class="ui-table-head trace-col-duration">耗时</th>
                <th class="ui-table-head trace-col-duration">首字耗时</th>
                <th class="ui-table-head trace-col-status">状态</th>
                <th class="ui-table-head trace-col-time">执行时间</th>
                <th class="ui-table-head trace-col-action">操作</th>
              </tr>
            </thead>
            <tbody class="ui-table-body">
              <tr v-for="run in runs" :key="run.traceId" class="ui-table-row trace-list-table-row">
                <td class="ui-table-cell trace-col-question">
                  <span
                    v-if="displayText(run.question)"
                    class="trace-list-run-meta-line line-clamp-1"
                    :title="displayText(run.question)"
                  >
                    {{ displayText(run.question) }}
                  </span>
                  <span v-else class="trace-list-empty-placeholder">—</span>
                </td>
                <td class="ui-table-cell trace-col-trace-id">
                  <div class="trace-list-trace-id-row">
                    <span class="trace-list-trace-id-text">{{ run.traceId }}</span>
                    <button
                      type="button"
                      class="trace-list-trace-id-copy"
                      aria-label="复制 Trace Id"
                      title="复制 Trace Id"
                      @click="copyText(run.traceId, 'Trace Id')"
                    >
                      <Copy class="h-3 w-3" />
                    </button>
                  </div>
                </td>
                <td class="ui-table-cell trace-col-user">
                  <span
                    v-if="displayText(run.userName || run.username || run.userId)"
                    class="trace-list-run-meta-line line-clamp-1"
                    :title="displayText(run.userName || run.username || run.userId)"
                  >
                    {{ displayText(run.userName || run.username || run.userId) }}
                  </span>
                  <span v-else class="trace-list-empty-placeholder">—</span>
                </td>
                <td class="ui-table-cell trace-col-duration trace-list-duration-cell">
                  {{ formatDuration(run.durationMs ?? undefined) }}
                </td>
                <td class="ui-table-cell trace-col-duration trace-list-duration-cell">
                  <template v-if="run.ttftMs != null">{{ formatDuration(run.ttftMs) }}</template>
                  <span v-else class="trace-list-empty-placeholder">—</span>
                </td>
                <td class="ui-table-cell trace-col-status trace-list-status-cell">
                  <TraceStatusBadge :status="run.status" />
                </td>
                <td class="ui-table-cell trace-col-time">
                  <span
                    class="cursor-default truncate text-sm tabular-nums"
                    :title="formatFullTime(run.startTime)"
                  >
                    {{ formatRelativeTime(run.startTime) }}
                  </span>
                </td>
                <td class="ui-table-cell trace-col-action trace-list-action-cell">
                  <div class="trace-list-action-group">
                    <button
                      type="button"
                      class="ui-button trace-list-action-btn trace-list-action-btn-primary inline-flex items-center justify-center border"
                      data-variant="outline"
                      @click="briefRun = run"
                    >
                      <LayoutPanelTop class="h-3.5 w-3.5" />
                      概览
                    </button>
                    <button
                      type="button"
                      class="ui-button trace-list-action-btn inline-flex items-center justify-center"
                      data-variant="ghost"
                      @click="emit('open-run', run.traceId)"
                    >
                      <Eye class="h-3.5 w-3.5" />
                      详情
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="trace-list-table-footer">
        <span class="trace-list-table-meta">
          第 {{ current }} / {{ pages }} 页，共 {{ total.toLocaleString("zh-CN") }} 条
        </span>
        <div class="trace-list-pagination">
          <button
            type="button"
            class="ui-button trace-list-pagination-btn inline-flex items-center justify-center border px-3 disabled:cursor-not-allowed disabled:opacity-50"
            data-variant="outline"
            :disabled="current <= 1 || loading"
            @click="emit('prev-page')"
          >
            上一页
          </button>
          <button
            type="button"
            class="ui-button trace-list-pagination-btn inline-flex items-center justify-center border px-3 disabled:cursor-not-allowed disabled:opacity-50"
            data-variant="outline"
            :disabled="current >= pages || loading"
            @click="emit('next-page')"
          >
            下一页
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="briefRun"
      class="ui-dialog-overlay fixed inset-0 z-50 flex items-center justify-center p-4"
      role="presentation"
      @mousedown.self="closeBrief"
    >
      <section
        class="ui-dialog-content trace-brief-dialog w-full max-w-[560px] border"
        role="dialog"
        aria-modal="true"
        aria-labelledby="trace-brief-title"
      >
        <header class="trace-brief-dialog-header">
          <div class="flex min-w-0 items-center gap-3">
            <LayoutPanelTop class="h-4 w-4 shrink-0 text-indigo-500" />
            <h2 id="trace-brief-title" class="truncate text-base font-semibold text-slate-900">
              链路概览
            </h2>
            <TraceStatusBadge :status="briefRun.status" />
          </div>
          <button
            type="button"
            class="ui-button inline-flex h-8 w-8 items-center justify-center"
            data-variant="ghost"
            aria-label="关闭概览"
            title="关闭"
            @click="closeBrief"
          >
            <X class="h-4 w-4" />
          </button>
        </header>

        <div class="trace-brief-body trace-brief-dialog-body">
          <div class="trace-brief-id-row">
            <span class="trace-brief-label">Trace Id</span>
            <div class="trace-brief-id-value">
              <span class="trace-brief-id-text">{{ briefRun.traceId }}</span>
              <button
                type="button"
                class="trace-brief-id-copy"
                aria-label="复制 Trace Id"
                title="复制 Trace Id"
                @click="copyText(briefRun.traceId, 'Trace Id')"
              >
                <Copy class="h-3.5 w-3.5" />
              </button>
            </div>
          </div>

          <div v-if="briefLoading" class="trace-brief-loading">
            <Loader2 class="h-4 w-4 animate-spin" />
            <span>加载节点信息...</span>
          </div>

          <template v-else-if="briefStats">
            <div class="trace-brief-stat-row">
              <div class="trace-brief-stat">
                <span class="trace-brief-stat-value">{{ briefStats.totalNodes }}</span>
                <span class="trace-brief-stat-label">执行节点</span>
              </div>
              <div class="trace-brief-stat">
                <span
                  class="trace-brief-stat-value"
                  :class="{ 'is-danger': briefStats.failedNodes > 0 }"
                >
                  {{ briefStats.failedNodes }}
                </span>
                <span class="trace-brief-stat-label">失败节点</span>
              </div>
              <div class="trace-brief-stat">
                <span class="trace-brief-stat-value">{{ briefStats.maxDepth }}</span>
                <span class="trace-brief-stat-label">最大深度</span>
              </div>
              <div class="trace-brief-stat">
                <span class="trace-brief-stat-value">{{
                  formatDuration(briefStats.totalDurationMs)
                }}</span>
                <span class="trace-brief-stat-label">节点累计</span>
              </div>
            </div>

            <div class="trace-brief-section">
              <div class="trace-brief-section-head">
                <span class="trace-brief-section-title">耗时 Top 3</span>
                <span class="trace-brief-section-hint">
                  占总链路 {{ formatDuration(briefRun.durationMs ?? undefined) }} 比例
                </span>
              </div>
              <p v-if="briefStats.topSlowNodes.length === 0" class="trace-brief-empty">
                无可统计的节点耗时
              </p>
              <ol v-else class="trace-brief-top-list">
                <li
                  v-for="(node, index) in briefStats.topSlowNodes"
                  :key="`${node.nodeName}-${index}`"
                  class="trace-brief-top-item"
                >
                  <div class="trace-brief-top-head">
                    <span class="trace-brief-top-rank">{{ index + 1 }}</span>
                    <span class="trace-brief-top-name" :title="node.nodeName">{{
                      node.nodeName
                    }}</span>
                    <span class="trace-brief-top-duration">{{
                      formatDuration(node.durationMs)
                    }}</span>
                  </div>
                  <div class="trace-brief-top-bar">
                    <div
                      class="trace-brief-top-bar-fill"
                      :style="{ width: `${slowNodePercent(node.durationMs)}%` }"
                    ></div>
                    <span class="trace-brief-top-percent"
                      >{{ slowNodePercent(node.durationMs) }}%</span
                    >
                  </div>
                </li>
              </ol>
            </div>

            <div class="trace-brief-meta-row">
              <div class="trace-brief-meta-item">
                <span class="trace-brief-label">任务 ID</span>
                <span
                  class="trace-brief-meta-value font-mono text-xs"
                  :title="briefRun.taskId || undefined"
                >
                  {{ briefRun.taskId || "—" }}
                </span>
              </div>
              <div class="trace-brief-meta-item">
                <span class="trace-brief-label">会话 ID</span>
                <span
                  class="trace-brief-meta-value font-mono text-xs"
                  :title="briefRun.conversationId || undefined"
                >
                  {{ briefRun.conversationId || "—" }}
                </span>
              </div>
            </div>

            <div v-if="briefRun.errorMessage" class="trace-brief-error">
              <span class="trace-brief-label">错误信息</span>
              <p class="trace-brief-error-text">{{ briefRun.errorMessage }}</p>
            </div>
          </template>
        </div>

        <footer class="trace-brief-dialog-footer">
          <button
            type="button"
            class="ui-button inline-flex h-9 items-center justify-center border px-4 text-sm"
            data-variant="outline"
            @click="closeBrief"
          >
            关闭
          </button>
          <button
            type="button"
            class="ui-button inline-flex h-9 items-center justify-center gap-1.5 px-4 text-sm"
            data-variant="default"
            @click="openBriefDetail"
          >
            <Eye class="h-4 w-4" />
            查看详情
          </button>
        </footer>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { Copy, Eye, LayoutPanelTop, Loader2, X } from "lucide-vue-next";
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";

import TraceStatusBadge from "@/pages/admin/traces/components/TraceStatusBadge.vue";
import {
  formatDuration,
  normalizeStatus,
  prettifyNodeName,
  resolveNodeDuration
} from "@/pages/admin/traces/traceUtils";
import { getRagTraceNodes, type RagTraceNode, type RagTraceRun } from "@/services/ragTraceService";
import { getErrorMessage } from "@/utils/error";
import { toast } from "@/utils/toast";

interface NodeStat {
  nodeName: string;
  durationMs: number;
}

interface BriefStats {
  totalNodes: number;
  failedNodes: number;
  maxDepth: number;
  totalDurationMs: number;
  topSlowNodes: NodeStat[];
}

defineProps<{
  runs: RagTraceRun[];
  loading: boolean;
  current: number;
  pages: number;
  total: number;
}>();

const emit = defineEmits<{
  "open-run": [traceId: string];
  "prev-page": [];
  "next-page": [];
}>();

const briefRun = ref<RagTraceRun | null>(null);
const briefNodes = ref<RagTraceNode[] | null>(null);
const briefLoading = ref(false);
let briefRequestId = 0;

const NODE_ALIAS_GROUP: Record<string, string> = {
  "retrieval-engine": "retrieval-engine",
  "multi-channel-retrieval": "retrieval-engine"
};

const displayText = (value?: string | null) => {
  const trimmed = (value ?? "").trim();
  return trimmed && trimmed !== "-" ? trimmed : "";
};

const canonicalNodeKey = (rawName?: string | null): string => {
  const trimmed = (rawName || "").trim();
  return trimmed ? NODE_ALIAS_GROUP[trimmed] || trimmed : "";
};

const computeBriefStats = (nodes: RagTraceNode[]): BriefStats => {
  const failedNodes = nodes.filter((node) => {
    const status = normalizeStatus(node.status);
    return status === "failed" || status === "timeout";
  }).length;
  const maxDepth = nodes.reduce((max, node) => {
    const depth = Number(node.depth ?? 0);
    return Number.isFinite(depth) && depth > max ? depth : max;
  }, 0);
  const totalDurationMs = nodes.reduce((sum, node) => sum + resolveNodeDuration(node), 0);
  const grouped = new Map<string, NodeStat>();

  for (const node of nodes) {
    const durationMs = resolveNodeDuration(node);
    const key = canonicalNodeKey(node.nodeName);
    if (durationMs <= 0 || !key) continue;
    const existing = grouped.get(key);
    if (!existing || durationMs > existing.durationMs) {
      grouped.set(key, { nodeName: prettifyNodeName(key), durationMs });
    }
  }

  return {
    totalNodes: nodes.length,
    failedNodes,
    maxDepth,
    totalDurationMs,
    topSlowNodes: [...grouped.values()].sort((a, b) => b.durationMs - a.durationMs).slice(0, 3)
  };
};

const briefStats = computed(() => (briefNodes.value ? computeBriefStats(briefNodes.value) : null));

watch(briefRun, async (run) => {
  const requestId = ++briefRequestId;
  briefNodes.value = null;
  if (!run) {
    briefLoading.value = false;
    return;
  }

  briefLoading.value = true;
  try {
    const nodes = await getRagTraceNodes(run.traceId);
    if (requestId === briefRequestId) briefNodes.value = nodes || [];
  } catch (error) {
    if (requestId !== briefRequestId) return;
    briefNodes.value = [];
    toast.error(getErrorMessage(error, "加载链路节点失败"));
  } finally {
    if (requestId === briefRequestId) briefLoading.value = false;
  }
});

const copyText = async (value: string, label: string) => {
  try {
    await navigator.clipboard.writeText(value);
    toast.success(`${label} 已复制`);
  } catch {
    toast.error("复制失败");
  }
};

const closeBrief = () => {
  briefRun.value = null;
};

const openBriefDetail = () => {
  if (!briefRun.value) return;
  emit("open-run", briefRun.value.traceId);
  closeBrief();
};

const slowNodePercent = (durationMs: number) => {
  const totalDuration = Number(briefRun.value?.durationMs ?? 0);
  return totalDuration > 0 ? Math.min(100, Math.round((durationMs / totalDuration) * 100)) : 0;
};

const formatRelativeTime = (value?: string | null) => {
  if (!value) return "-";
  const timestamp = new Date(value).getTime();
  if (Number.isNaN(timestamp)) return value;
  const delta = Math.max(0, Date.now() - timestamp);
  const minutes = Math.floor(delta / 60_000);
  if (minutes < 1) return "刚刚";
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} 天前`;
  return new Date(timestamp).toLocaleDateString("zh-CN");
};

const formatFullTime = (value?: string | null) => {
  if (!value) return "";
  const timestamp = new Date(value).getTime();
  return Number.isNaN(timestamp) ? value : new Date(timestamp).toLocaleString("zh-CN");
};

const handleEscape = (event: KeyboardEvent) => {
  if (event.key === "Escape" && briefRun.value) closeBrief();
};

onMounted(() => window.addEventListener("keydown", handleEscape));
onBeforeUnmount(() => window.removeEventListener("keydown", handleEscape));
</script>

<style scoped>
.trace-brief-dialog {
  max-height: min(760px, calc(100vh - 32px));
  overflow-y: auto;
}

.trace-brief-dialog-header,
.trace-brief-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
}

.trace-brief-dialog-header {
  border-bottom: 1px solid #e2e8f0;
}

.trace-brief-dialog-body {
  padding: 20px;
}

.trace-brief-dialog-footer {
  justify-content: flex-end;
  border-top: 1px solid #e2e8f0;
}

@media (max-width: 720px) {
  .trace-list-table-wrap {
    overflow-x: auto;
  }

  .trace-list-table {
    min-width: 1080px;
  }

  .trace-list-table-footer {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
