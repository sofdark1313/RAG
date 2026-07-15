<template>
  <div v-if="detailLoading" class="flex min-h-[400px] items-center justify-center">
    <div class="flex flex-col items-center gap-3 text-slate-500">
      <Loader2 class="h-8 w-8 animate-spin" />
      <p>加载链路详情中...</p>
    </div>
  </div>

  <div v-else-if="!traceId || !selectedRun" class="space-y-6">
    <div class="flex items-center justify-between gap-4">
      <div class="flex items-center gap-1.5 text-sm">
        <RouterLink to="/admin/traces" class="text-slate-500 hover:text-slate-700">
          链路追踪
        </RouterLink>
        <span class="text-slate-300">/</span>
        <span class="text-slate-400">详情</span>
      </div>
      <RouterLink
        to="/admin/traces"
        class="ui-button inline-flex h-9 items-center justify-center gap-1.5 border px-3 text-sm text-slate-600 hover:text-slate-800"
        data-variant="outline"
      >
        <ArrowLeft class="h-4 w-4" />
        返回列表
      </RouterLink>
    </div>
    <div class="flex min-h-[300px] items-center justify-center">
      <div class="text-center text-slate-500">
        <AlertTriangle class="mx-auto mb-4 h-12 w-12 text-slate-300" />
        <p>{{ !traceId ? "缺少 Trace Id" : "暂无数据" }}</p>
      </div>
    </div>
  </div>

  <div v-else class="space-y-4 pb-8">
    <header class="trace-detail-titlebar flex items-center justify-between gap-4">
      <div class="flex min-w-0 items-center gap-3">
        <div class="flex shrink-0 items-center gap-1.5 text-sm">
          <RouterLink
            to="/admin/traces"
            class="text-slate-500 transition-colors hover:text-slate-700"
          >
            RAG 链路列表
          </RouterLink>
          <span class="text-slate-300">/</span>
        </div>
        <div class="flex min-w-0 items-center gap-2">
          <h1 class="truncate text-lg font-semibold text-slate-900">
            {{ selectedRun.traceName || "未命名链路" }}
          </h1>
          <TraceStatusBadge class="shrink-0" :status="selectedRun.status" />
        </div>
      </div>

      <div class="flex shrink-0 items-center gap-2">
        <RouterLink
          to="/admin/traces"
          class="ui-button inline-flex h-9 items-center justify-center gap-1.5 border px-3 text-sm text-slate-600 hover:text-slate-800"
          data-variant="outline"
        >
          <ArrowLeft class="h-4 w-4" />
          返回列表
        </RouterLink>
        <button
          type="button"
          class="ui-button inline-flex h-9 items-center justify-center gap-1.5 border px-3 text-sm text-slate-600 hover:text-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
          data-variant="outline"
          :disabled="detailLoading"
          @click="loadDetail(traceId)"
        >
          <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': detailLoading }" />
          刷新
        </button>
      </div>
    </header>

    <div
      class="trace-detail-meta flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-slate-500"
    >
      <button
        type="button"
        class="flex items-center gap-1 font-mono transition-colors hover:text-slate-700"
        title="点击复制 Trace Id"
        @click="copyToClipboard(traceId, 'Trace Id')"
      >
        <Hash class="h-3 w-3" />
        {{ shortTraceId }}
      </button>
      <span class="flex items-center gap-1">
        <Calendar class="h-3 w-3" />
        {{ formatDateTime(selectedRun.startTime ?? undefined) }}
      </span>
      <span v-if="selectedRun.username || selectedRun.userId" class="flex items-center gap-1">
        <User class="h-3 w-3" />
        {{ selectedRun.username || selectedRun.userId }}
      </span>
    </div>

    <div
      v-if="selectedRun.errorMessage"
      class="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-3"
    >
      <AlertTriangle class="mt-0.5 h-4 w-4 shrink-0 text-red-500" />
      <div class="min-w-0 text-sm">
        <span class="font-medium text-red-800">执行出错：</span>
        <span class="ml-1 break-words text-red-600">{{ selectedRun.errorMessage }}</span>
      </div>
    </div>

    <div
      class="trace-metric-strip flex items-center divide-x divide-slate-200 overflow-x-auto rounded-lg border border-slate-200 bg-slate-50"
    >
      <div
        v-for="metric in metrics"
        :key="metric.label"
        class="flex shrink-0 items-center gap-2 px-4 py-2"
      >
        <component :is="metric.icon" class="h-4 w-4" :class="metric.colorClass" />
        <span class="text-lg font-semibold" :class="metric.colorClass">{{ metric.value }}</span>
        <span class="text-xs text-slate-500">{{ metric.label }}</span>
      </div>
    </div>

    <section class="ui-card overflow-hidden">
      <header class="flex items-center justify-between gap-4 border-b border-slate-100 px-4 py-3">
        <h2 class="text-sm font-medium text-slate-700">执行时序</h2>
        <span class="text-xs text-slate-500"
          >窗口 {{ formatDuration(timeline.totalWindowMs) }}</span
        >
      </header>

      <div v-if="timeline.nodes.length === 0" class="py-16 text-center text-slate-400">
        <Activity class="mx-auto mb-3 h-10 w-10 opacity-50" />
        <p>暂无节点记录</p>
      </div>

      <div v-else class="overflow-x-auto">
        <div class="trace-waterfall-table min-w-[820px]">
          <div
            class="grid grid-cols-[minmax(180px,1fr)_120px_2fr_100px] gap-4 border-y border-slate-100 bg-slate-50 px-4 py-2 text-xs font-medium text-slate-500"
          >
            <span>节点</span>
            <span>类型</span>
            <span>时间线</span>
            <span class="text-right">耗时</span>
          </div>

          <div class="grid grid-cols-[minmax(180px,1fr)_120px_2fr_100px] gap-4 bg-white px-4">
            <div></div>
            <div></div>
            <div class="relative h-6 border-b border-slate-200">
              <div
                v-for="percent in TIME_SCALE_TICKS"
                :key="percent"
                class="absolute bottom-0 top-0 flex flex-col items-center"
                :style="{ left: `${percent}%`, transform: 'translateX(-50%)' }"
              >
                <div class="h-2 w-px bg-slate-300"></div>
                <span class="mt-0.5 text-[10px] text-slate-400">
                  {{ formatDuration((timeline.totalWindowMs * percent) / 100) }}
                </span>
              </div>
            </div>
            <div></div>
          </div>

          <div class="divide-y divide-slate-50">
            <div
              v-for="node in timeline.nodes"
              :key="node.nodeId"
              class="group grid grid-cols-[minmax(180px,1fr)_120px_2fr_100px] gap-4 px-4 py-2.5 transition-colors"
              :class="waterfallRowClass(node)"
              @click="selectNode(node)"
            >
              <div class="flex min-w-0 items-center gap-1.5">
                <div v-if="rowDepth(node) > 0" class="flex shrink-0 self-stretch">
                  <span
                    v-for="depthIndex in rowDepth(node)"
                    :key="depthIndex"
                    class="w-4 border-l border-slate-200"
                    :class="{ 'border-b': depthIndex === rowDepth(node) }"
                    :style="{ marginTop: depthIndex === rowDepth(node) ? '-10px' : '0' }"
                  ></span>
                </div>
                <span
                  class="h-2 w-2 shrink-0 rounded-full transition-transform group-hover:scale-125"
                  :class="statusColors(node.status).dot"
                ></span>
                <span
                  class="truncate text-sm"
                  :class="
                    node.nodeId === ROOT_NODE_ID
                      ? 'font-semibold text-indigo-900'
                      : 'text-slate-700'
                  "
                  :title="nodeDisplayName(node)"
                >
                  {{ nodeDisplayName(node) }}
                </span>
                <Zap
                  v-if="node.nodeId === stats.topSlowestId && node.nodeId !== ROOT_NODE_ID"
                  class="h-3 w-3 shrink-0 text-amber-500"
                />
              </div>

              <div class="flex items-center">
                <span
                  class="truncate rounded px-2 py-0.5 text-xs font-medium"
                  :class="nodeTypeChipClass(node.nodeType)"
                  :title="node.nodeType || '-'"
                >
                  {{ node.nodeType || "-" }}
                </span>
              </div>

              <div class="flex items-center">
                <div class="relative h-6 w-full overflow-hidden rounded bg-slate-50">
                  <div
                    v-for="percent in [25, 50, 75]"
                    :key="percent"
                    class="absolute bottom-0 top-0 w-px bg-slate-200"
                    :style="{ left: `${percent}%` }"
                  ></div>
                  <div
                    class="absolute bottom-1 top-1 rounded transition-all group-hover:brightness-110"
                    :class="statusColors(node.status).bar"
                    :style="waterfallBarStyle(node)"
                    :title="`${nodeDisplayName(node)} - ${formatDuration(node.resolvedDurationMs)}`"
                  ></div>
                </div>
              </div>

              <div class="text-right">
                <p class="text-sm font-medium text-slate-700">
                  {{ formatDuration(node.resolvedDurationMs) }}
                </p>
                <p class="text-[10px] text-slate-400">@{{ formatDuration(node.offsetMs) }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-if="activeNode" class="ui-card">
      <header class="flex items-center justify-between gap-2 border-b border-slate-100 px-4 py-3">
        <div class="flex min-w-0 items-center gap-2">
          <h2
            class="truncate text-sm font-medium text-slate-700"
            :title="nodeDisplayName(activeNode)"
          >
            {{ nodeDisplayName(activeNode) }}
          </h2>
          <TraceStatusBadge :status="activeNode.status" />
          <span
            class="rounded px-2 py-0.5 text-xs font-medium"
            :class="nodeTypeChipClass(activeNode.nodeType)"
          >
            {{ activeNode.nodeType || "-" }}
          </span>
        </div>
        <button
          type="button"
          class="ui-button inline-flex h-7 w-7 shrink-0 items-center justify-center text-slate-500 hover:text-slate-800"
          data-variant="ghost"
          aria-label="关闭节点详情"
          title="关闭"
          @click="activeNodeId = null"
        >
          <X class="h-4 w-4" />
        </button>
      </header>

      <div class="space-y-3 px-4 pb-4 pt-3">
        <div class="grid grid-cols-1 gap-x-6 gap-y-2 text-xs md:grid-cols-2">
          <div
            v-for="field in activeNodeFields"
            :key="field.label"
            class="flex min-w-0 items-center gap-2"
          >
            <span class="shrink-0 text-slate-500">{{ field.label }}</span>
            <button
              v-if="field.copyable"
              type="button"
              class="truncate text-left transition-colors hover:text-blue-600"
              :class="[field.mono ? 'font-mono' : '', field.valueClass]"
              :title="field.value"
              @click="copyToClipboard(field.value, field.label)"
            >
              {{ field.value }}
            </button>
            <span
              v-else
              class="truncate"
              :class="[field.mono ? 'font-mono' : '', field.valueClass]"
              :title="field.value"
            >
              {{ field.value }}
            </span>
            <Copy v-if="field.copyable" class="h-3 w-3 shrink-0 text-slate-300" />
          </div>
        </div>

        <div
          v-if="activeNode.errorMessage"
          class="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3"
        >
          <AlertTriangle class="mt-0.5 h-4 w-4 shrink-0 text-red-500" />
          <div class="min-w-0 text-xs">
            <p class="mb-1 font-medium text-red-800">错误信息</p>
            <p class="break-all whitespace-pre-wrap text-red-700">{{ activeNode.errorMessage }}</p>
          </div>
        </div>

        <div v-if="activeNode.extraData">
          <p class="mb-1 text-xs font-medium text-slate-500">额外数据</p>
          <pre
            class="overflow-x-auto whitespace-pre-wrap break-all rounded border border-slate-200 bg-slate-50 p-2 text-xs text-slate-700"
            >{{ tryPrettyJson(activeNode.extraData) }}</pre
          >
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import {
  Activity,
  AlertTriangle,
  ArrowLeft,
  Calendar,
  CheckCircle2,
  Clock,
  Copy,
  Hash,
  Loader2,
  RefreshCw,
  User,
  X,
  XCircle,
  Zap
} from "lucide-vue-next";
import type { Component, CSSProperties } from "vue";
import { computed, ref, watch } from "vue";
import { RouterLink, useRoute } from "vue-router";

import TraceStatusBadge from "@/pages/admin/traces/components/TraceStatusBadge.vue";
import {
  clamp,
  formatDateTime,
  formatDuration,
  nodeTypeChipClass,
  normalizeStatus,
  prettifyNodeName,
  resolveNodeDuration,
  toTimestamp
} from "@/pages/admin/traces/traceUtils";
import {
  getRagTraceDetail,
  type RagTraceDetail,
  type RagTraceNode
} from "@/services/ragTraceService";
import { getErrorMessage } from "@/utils/error";
import { toast } from "@/utils/toast";

type TimelineBaseNode = RagTraceNode & {
  depthValue: number;
  resolvedDurationMs: number;
  startTs: number;
  endTs: number;
};

type WaterfallTimelineNode = TimelineBaseNode & {
  offsetMs: number;
  leftPercent: number;
  widthPercent: number;
};

type TimelineViewModel = {
  totalWindowMs: number;
  nodes: WaterfallTimelineNode[];
};

type Metric = {
  label: string;
  value: string | number;
  icon: Component;
  colorClass: string;
};

type DetailField = {
  label: string;
  value: string;
  mono?: boolean;
  copyable?: boolean;
  valueClass: string;
};

const ROOT_NODE_ID = "__root__";
const TIME_SCALE_TICKS = [0, 25, 50, 75, 100];
const route = useRoute();
const detail = ref<RagTraceDetail | null>(null);
const detailLoading = ref(false);
const activeNodeId = ref<string | null>(null);
let detailRequestId = 0;

const decodeTraceId = (value?: string): string => {
  if (!value) return "";
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
};

const traceId = computed(() => {
  const value = route.params.traceId;
  return decodeTraceId(Array.isArray(value) ? value[0] : value);
});

const selectedRun = computed(() => detail.value?.run || null);
const shortTraceId = computed(() =>
  traceId.value.length > 28
    ? `${traceId.value.slice(0, 12)}...${traceId.value.slice(-8)}`
    : traceId.value
);

const loadDetail = async (nextTraceId: string) => {
  if (!nextTraceId) return;
  const requestId = ++detailRequestId;
  detailLoading.value = true;
  try {
    const result = await getRagTraceDetail(nextTraceId);
    if (requestId === detailRequestId) detail.value = result;
  } catch (error) {
    if (requestId !== detailRequestId) return;
    detail.value = null;
    toast.error(getErrorMessage(error, "加载链路详情失败"));
    console.error(error);
  } finally {
    if (requestId === detailRequestId) detailLoading.value = false;
  }
};

watch(
  traceId,
  (nextTraceId) => {
    activeNodeId.value = null;
    if (!nextTraceId) {
      detailRequestId += 1;
      detail.value = null;
      detailLoading.value = false;
      return;
    }
    void loadDetail(nextTraceId);
  },
  { immediate: true }
);

const timeline = computed<TimelineViewModel>(() => {
  const nodes = detail.value?.nodes || [];
  const run = selectedRun.value;
  if (!nodes.length && !run) return { totalWindowMs: 0, nodes: [] };

  const normalized: TimelineBaseNode[] = nodes.map((node) => {
    const startTs = toTimestamp(node.startTime);
    const endTs = toTimestamp(node.endTime);
    const resolvedDurationMs = resolveNodeDuration(node);
    const resolvedStartTs = startTs ?? 0;
    const resolvedEndTs = endTs ?? (resolvedStartTs > 0 ? resolvedStartTs + resolvedDurationMs : 0);
    return {
      ...node,
      depthValue: Math.max(0, Number(node.depth ?? 0)) + 1,
      resolvedDurationMs,
      startTs: resolvedStartTs,
      endTs: resolvedEndTs
    };
  });

  const withTime = normalized.filter((item) => item.startTs > 0);
  const runStartTs = toTimestamp(run?.startTime);
  const baseStart =
    runStartTs ??
    (withTime.length
      ? withTime.reduce((min, item) => Math.min(min, item.startTs), withTime[0].startTs)
      : Date.now());
  const runEndTs = toTimestamp(run?.endTime);
  const maxEnd = withTime.length
    ? withTime.reduce(
        (max, item) => Math.max(max, item.endTs || item.startTs),
        withTime[0].endTs || withTime[0].startTs
      )
    : baseStart;
  const runDuration = Number(run?.durationMs ?? 0);
  const resolvedRunEnd = runEndTs ?? (runDuration > 0 ? baseStart + runDuration : maxEnd);
  const windowDuration = Math.max(resolvedRunEnd - baseStart, runDuration, maxEnd - baseStart, 1);

  const rootRow: TimelineBaseNode | null = run
    ? {
        traceId: run.traceId,
        nodeId: ROOT_NODE_ID,
        parentNodeId: null,
        depth: -1,
        nodeType: "ROOT",
        nodeName: run.traceName || "rag-stream-chat",
        className: null,
        methodName: run.entryMethod || null,
        status: run.status,
        errorMessage: run.errorMessage,
        durationMs: run.durationMs ?? null,
        startTime: run.startTime ?? null,
        endTime: run.endTime ?? null,
        extraData: null,
        depthValue: 0,
        resolvedDurationMs: Math.max(resolvedRunEnd - baseStart, 1),
        startTs: baseStart,
        endTs: resolvedRunEnd
      }
    : null;

  const childrenMap = new Map<string, TimelineBaseNode[]>();
  const treeRoots: TimelineBaseNode[] = [];

  for (const node of normalized) {
    if (!node.parentNodeId) {
      treeRoots.push(node);
      continue;
    }
    const siblings = childrenMap.get(node.parentNodeId) || [];
    siblings.push(node);
    childrenMap.set(node.parentNodeId, siblings);
  }

  const sortSiblings = (items: TimelineBaseNode[]) =>
    items.sort((a, b) => a.startTs - b.startTs || a.depthValue - b.depthValue);

  sortSiblings(treeRoots);
  for (const siblings of childrenMap.values()) sortSiblings(siblings);

  const ordered: TimelineBaseNode[] = [];
  const stack = [...treeRoots].reverse();
  while (stack.length) {
    const node = stack.pop();
    if (!node) break;
    ordered.push(node);
    const children = childrenMap.get(node.nodeId);
    if (!children) continue;
    for (let index = children.length - 1; index >= 0; index -= 1) {
      stack.push(children[index]);
    }
  }

  const orderedIds = new Set(ordered.map((node) => node.nodeId));
  for (const node of normalized) {
    if (!orderedIds.has(node.nodeId)) ordered.push(node);
  }

  const rows = [...(rootRow ? [rootRow] : []), ...ordered].map((node) => {
    const offsetMs = node.startTs > 0 ? Math.max(0, node.startTs - baseStart) : 0;
    const leftPercent = clamp((offsetMs / windowDuration) * 100, 0, 99.2);
    const widthPercent = clamp(
      (Math.max(node.resolvedDurationMs, 1) / windowDuration) * 100,
      0.8,
      100 - leftPercent
    );
    return { ...node, offsetMs, leftPercent, widthPercent };
  });

  return { totalWindowMs: windowDuration, nodes: rows };
});

const stats = computed(() => {
  const nodes = detail.value?.nodes || [];
  const total = nodes.length;
  const failed = nodes.filter((node) => normalizeStatus(node.status) === "failed").length;
  const success = nodes.filter((node) => normalizeStatus(node.status) === "success").length;
  const running = nodes.filter((node) => normalizeStatus(node.status) === "running").length;
  const durations = nodes.map((node) => resolveNodeDuration(node));
  const avgDuration = total
    ? Math.round(durations.reduce((sum, duration) => sum + duration, 0) / total)
    : 0;
  const topSlowestId = [...nodes].sort((a, b) => resolveNodeDuration(b) - resolveNodeDuration(a))[0]
    ?.nodeId;
  const userTtftNode = nodes.find((node) => (node.nodeType || "").toUpperCase() === "USER_TTFT");
  const llmTtftNode = nodes.find((node) => (node.nodeType || "").toUpperCase() === "LLM_TTFT");
  const ttftMs = userTtftNode
    ? resolveNodeDuration(userTtftNode)
    : llmTtftNode
      ? resolveNodeDuration(llmTtftNode)
      : null;
  const ttftKind: "user" | "llm" | null = userTtftNode ? "user" : llmTtftNode ? "llm" : null;

  return { total, failed, success, running, avgDuration, topSlowestId, ttftMs, ttftKind };
});

const metrics = computed<Metric[]>(() => {
  const run = selectedRun.value;
  if (!run) return [];
  const values: Metric[] = [
    {
      icon: Clock,
      label: "总耗时",
      value: formatDuration(run.durationMs ?? undefined),
      colorClass: "text-blue-600"
    }
  ];
  if (stats.value.ttftMs !== null) {
    values.push({
      icon: Zap,
      label: stats.value.ttftKind === "user" ? "首包" : "LLM 首包",
      value: formatDuration(stats.value.ttftMs),
      colorClass: stats.value.ttftKind === "user" ? "text-blue-600" : "text-emerald-600"
    });
  }
  values.push(
    { icon: Activity, label: "节点", value: stats.value.total, colorClass: "text-slate-600" },
    {
      icon: CheckCircle2,
      label: "成功",
      value: stats.value.success,
      colorClass: "text-emerald-600"
    },
    {
      icon: XCircle,
      label: "失败",
      value: stats.value.failed,
      colorClass: stats.value.failed > 0 ? "text-red-600" : "text-slate-600"
    }
  );
  if (stats.value.running > 0) {
    values.push({
      icon: Loader2,
      label: "运行中",
      value: stats.value.running,
      colorClass: "text-amber-600"
    });
  }
  values.push({
    icon: Zap,
    label: "平均耗时",
    value: formatDuration(stats.value.avgDuration),
    colorClass: "text-slate-600"
  });
  return values;
});

const activeNode = computed(() => {
  if (!activeNodeId.value) return null;
  return detail.value?.nodes.find((node) => node.nodeId === activeNodeId.value) || null;
});

const activeNodeFields = computed<DetailField[]>(() => {
  const node = activeNode.value;
  if (!node) return [];
  const status = normalizeStatus(node.status);
  const fields: DetailField[] = [
    {
      label: "Node Id",
      value: node.nodeId,
      mono: true,
      copyable: true,
      valueClass: "text-slate-700"
    },
    {
      label: "Parent Node Id",
      value: node.parentNodeId || "-",
      mono: true,
      copyable: Boolean(node.parentNodeId),
      valueClass: "text-slate-700"
    },
    {
      label: "耗时",
      value: formatDuration(resolveNodeDuration(node)),
      valueClass: status === "failed" ? "font-medium text-red-600" : "font-medium text-blue-600"
    },
    { label: "深度", value: String(node.depth ?? 0), valueClass: "text-slate-700" },
    {
      label: "开始时间",
      value: formatDateTime(node.startTime ?? undefined),
      valueClass: "text-slate-700"
    },
    {
      label: "结束时间",
      value: formatDateTime(node.endTime ?? undefined),
      valueClass: "text-slate-700"
    }
  ];
  if (node.className) {
    fields.push({ label: "类", value: node.className, mono: true, valueClass: "text-slate-700" });
  }
  if (node.methodName) {
    fields.push({
      label: "方法",
      value: node.methodName,
      mono: true,
      valueClass: "text-slate-700"
    });
  }
  return fields;
});

const statusColors = (status?: string | null) => {
  const normalized = normalizeStatus(status);
  if (normalized === "success") return { dot: "bg-emerald-500", bar: "bg-emerald-400" };
  if (normalized === "failed") return { dot: "bg-red-500", bar: "bg-red-400" };
  if (normalized === "running") return { dot: "bg-amber-500", bar: "bg-amber-400" };
  return { dot: "bg-slate-300", bar: "bg-slate-300" };
};

const nodeDisplayName = (node: RagTraceNode) =>
  prettifyNodeName(node.nodeName || node.methodName || node.nodeId);

const rowDepth = (node: WaterfallTimelineNode) =>
  node.nodeId === ROOT_NODE_ID ? 0 : Math.min(Math.max(node.depthValue - 1, 0), 6);

const waterfallRowClass = (node: WaterfallTimelineNode) => ({
  "cursor-pointer hover:bg-slate-50/80": node.nodeId !== ROOT_NODE_ID,
  "border-b border-indigo-100 bg-indigo-50/40": node.nodeId === ROOT_NODE_ID,
  "bg-amber-50/40":
    node.nodeId === stats.value.topSlowestId &&
    node.nodeId !== ROOT_NODE_ID &&
    node.nodeId !== activeNodeId.value,
  "bg-blue-50/60 ring-1 ring-inset ring-blue-200": node.nodeId === activeNodeId.value
});

const waterfallBarStyle = (node: WaterfallTimelineNode): CSSProperties => ({
  left: `${node.leftPercent}%`,
  width: `${Math.max(node.widthPercent, 0.5)}%`,
  minWidth: "4px"
});

const selectNode = (node: WaterfallTimelineNode) => {
  if (node.nodeId === ROOT_NODE_ID) return;
  activeNodeId.value = activeNodeId.value === node.nodeId ? null : node.nodeId;
};

const copyToClipboard = async (text: string, label: string) => {
  try {
    await navigator.clipboard.writeText(text);
    toast.success(`${label} 已复制`);
  } catch {
    toast.error("复制失败");
  }
};

const tryPrettyJson = (raw: string): string => {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
};
</script>

<style scoped>
@media (max-width: 760px) {
  .trace-detail-titlebar {
    align-items: flex-start;
    flex-direction: column;
  }

  .trace-detail-titlebar > div:last-child {
    width: 100%;
  }

  .trace-detail-titlebar > div:last-child > * {
    flex: 1;
  }
}
</style>
