<template>
  <div class="admin-page trace-page trace-list-page">
    <div class="trace-list-shell">
      <header class="admin-page-header trace-list-page-header">
        <div class="min-w-0">
          <h1 class="admin-page-title">链路追踪</h1>
          <p class="admin-page-subtitle">
            独立列表页聚焦运行检索，点击任意运行记录进入详情页分析慢节点与失败节点
          </p>
        </div>
        <div class="admin-page-actions trace-list-page-actions">
          <input
            v-model="traceIdFilter"
            class="ui-input trace-search-input h-9 border px-3 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
            placeholder="搜索 Trace Id"
            type="search"
            @keyup.enter="handleSearch"
          />
          <button
            type="button"
            class="ui-button admin-primary-gradient inline-flex h-9 items-center justify-center gap-2 px-4 text-sm"
            data-variant="default"
            @click="handleSearch"
          >
            <Search class="h-4 w-4" />
            查询
          </button>
          <button
            type="button"
            class="ui-button inline-flex h-9 items-center justify-center gap-2 border px-4 text-sm disabled:cursor-not-allowed disabled:opacity-50"
            data-variant="outline"
            :disabled="loading"
            @click="loadRuns(pageNo, queryTraceId)"
          >
            <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" />
            刷新
          </button>
        </div>
      </header>

      <section class="trace-list-stat-section">
        <div class="trace-list-stat-caption">
          <span class="trace-list-stat-caption-label">当前页统计</span>
          <span class="trace-list-stat-caption-hint">仅反映本页 {{ runs.length }} 条记录</span>
        </div>
        <div class="trace-list-stat-grid">
          <StatCard
            v-for="stat in statCards"
            :key="stat.key"
            :title="stat.title"
            :value="stat.value"
            :unit="stat.unit"
            :icon="stat.icon"
            :tone="stat.tone"
          />
        </div>
      </section>

      <RunsTable
        :runs="runs"
        :loading="loading"
        :current="current"
        :pages="pages"
        :total="total"
        @open-run="openRun"
        @prev-page="pageNo = Math.max(1, pageNo - 1)"
        @next-page="pageNo += 1"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { Activity, Clock3, RefreshCw, Search, TrendingUp } from "lucide-vue-next";
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";

import RunsTable from "@/pages/admin/traces/components/RunsTable.vue";
import StatCard, { type StatCardTone } from "@/pages/admin/traces/components/StatCard.vue";
import { PAGE_SIZE, normalizeStatus } from "@/pages/admin/traces/traceUtils";
import { getRagTraceRuns, type PageResult, type RagTraceRun } from "@/services/ragTraceService";
import { getErrorMessage } from "@/utils/error";
import { toast } from "@/utils/toast";

type DurationMetric = {
  value: string;
  unit: string;
};

const router = useRouter();
const traceIdFilter = ref("");
const queryTraceId = ref("");
const pageNo = ref(1);
const pageData = ref<PageResult<RagTraceRun> | null>(null);
const loading = ref(false);
let runsRequestId = 0;

const runs = computed(() => pageData.value?.records || []);
const current = computed(() => pageData.value?.current || pageNo.value);
const pages = computed(() => pageData.value?.pages || 1);
const total = computed(() => pageData.value?.total || 0);

const formatDurationMetric = (durationMs: number): DurationMetric => {
  const duration = Number.isFinite(durationMs) && durationMs > 0 ? durationMs : 0;
  if (duration < 1000) return { value: `${Math.round(duration)}`, unit: "ms" };
  if (duration < 60_000) return { value: (duration / 1000).toFixed(2), unit: "s" };
  return { value: (duration / 1000).toFixed(1), unit: "s" };
};

const loadRuns = async (currentPage = pageNo.value, nextTraceId = queryTraceId.value) => {
  const requestId = ++runsRequestId;
  loading.value = true;
  try {
    const result = await getRagTraceRuns({
      current: currentPage,
      size: PAGE_SIZE,
      traceId: nextTraceId.trim() || undefined
    });
    if (requestId === runsRequestId) pageData.value = result;
  } catch (error) {
    if (requestId !== runsRequestId) return;
    toast.error(getErrorMessage(error, "加载链路运行列表失败"));
    console.error(error);
  } finally {
    if (requestId === runsRequestId) loading.value = false;
  }
};

watch(
  [pageNo, queryTraceId],
  ([nextPage, nextTraceId]) => {
    void loadRuns(nextPage, nextTraceId);
  },
  { immediate: true }
);

const handleSearch = () => {
  const nextTraceId = traceIdFilter.value.trim();
  const changed = pageNo.value !== 1 || queryTraceId.value !== nextTraceId;
  pageNo.value = 1;
  queryTraceId.value = nextTraceId;
  if (!changed) void loadRuns(1, nextTraceId);
};

const traceStats = computed(() => {
  const durations = runs.value
    .map((item) => Number(item.durationMs ?? 0))
    .filter((value) => Number.isFinite(value) && value > 0);
  const ttftValues = runs.value
    .map((item) => Number(item.ttftMs ?? 0))
    .filter((value) => Number.isFinite(value) && value > 0);
  const successCount = runs.value.filter(
    (item) => normalizeStatus(item.status) === "success"
  ).length;
  const failedCount = runs.value.filter((item) => normalizeStatus(item.status) === "failed").length;
  const runningCount = runs.value.filter(
    (item) => normalizeStatus(item.status) === "running"
  ).length;
  const avgDuration = durations.length
    ? Math.round(durations.reduce((sum, value) => sum + value, 0) / durations.length)
    : 0;
  const avgTtft = ttftValues.length
    ? Math.round(ttftValues.reduce((sum, value) => sum + value, 0) / ttftValues.length)
    : 0;
  const successRate = runs.value.length
    ? Math.round((successCount / runs.value.length) * 1000) / 10
    : 0;

  return { successCount, failedCount, runningCount, avgDuration, avgTtft, successRate };
});

const statCards = computed<
  {
    key: string;
    title: string;
    value: string;
    unit?: string;
    icon: typeof Activity;
    tone: StatCardTone;
  }[]
>(() => {
  const avgDuration = formatDurationMetric(traceStats.value.avgDuration);
  const avgTtft = formatDurationMetric(traceStats.value.avgTtft);
  return [
    {
      key: "status",
      title: "成功 / 失败 / 运行中",
      value: `${traceStats.value.successCount} / ${traceStats.value.failedCount} / ${traceStats.value.runningCount}`,
      icon: Activity,
      tone: "emerald"
    },
    {
      key: "successRate",
      title: "成功率",
      value: `${traceStats.value.successRate}%`,
      icon: TrendingUp,
      tone: "cyan"
    },
    {
      key: "avg",
      title: "平均耗时",
      value: avgDuration.value,
      unit: avgDuration.unit,
      icon: Clock3,
      tone: "indigo"
    },
    {
      key: "avgTtft",
      title: "平均首字",
      value: avgTtft.value,
      unit: avgTtft.unit,
      icon: Clock3,
      tone: "sky"
    }
  ];
});

const openRun = (traceId: string) => {
  void router.push(`/admin/traces/${encodeURIComponent(traceId)}`);
};
</script>

<style scoped>
.trace-search-input {
  width: min(300px, 38vw);
}

@media (max-width: 960px) {
  .trace-list-page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .trace-list-page-actions {
    width: 100%;
  }

  .trace-search-input {
    min-width: 0;
    flex: 1;
    width: auto;
  }
}

@media (max-width: 640px) {
  .trace-list-page-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .trace-search-input,
  .trace-list-page-actions button {
    width: 100%;
  }
}
</style>
