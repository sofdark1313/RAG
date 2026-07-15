<template>
  <div class="admin-page dashboard-page">
    <header class="dashboard-header">
      <div>
        <h1 class="dashboard-header__title">Dashboard</h1>
        <p class="dashboard-header__subtitle">系统运行与知识问答概览</p>
      </div>
      <div class="dashboard-header__actions">
        <div class="dashboard-range" aria-label="时间范围">
          <button
            v-for="option in windowOptions"
            :key="option.value"
            class="dashboard-range__item"
            :class="{ 'is-active': timeWindow === option.value }"
            type="button"
            @click="timeWindow = option.value"
          >
            {{ option.label }}
          </button>
        </div>
        <button class="ui-button dashboard-refresh" type="button" :disabled="loading" title="刷新数据" @click="loadData">
          <RefreshCw :size="16" :class="{ 'animate-spin': loading }" />
        </button>
        <div class="dashboard-updated"><span></span><span>{{ updatedLabel }}</span></div>
      </div>
    </header>

    <div v-if="error" class="dashboard-error">
      <AlertTriangle :size="17" />
      <span>{{ error }}</span>
      <button type="button" @click="loadData">重新加载</button>
    </div>

    <section class="dashboard-card">
      <h2 class="dashboard-card__title">核心指标</h2>
      <div class="dashboard-kpi-grid">
        <article v-for="card in kpiCards" :key="card.label" class="dashboard-kpi">
          <div class="dashboard-kpi__head">
            <span>{{ card.label }}</span>
            <component :is="card.icon" :size="17" />
          </div>
          <div v-if="loading && !overview" class="dashboard-skeleton h-8 w-24"></div>
          <strong v-else>{{ formatNumber(card.value) }}</strong>
          <div class="dashboard-kpi__foot" :class="deltaTone(card.delta)">
            <TrendingUp v-if="(card.delta || 0) > 0" :size="13" />
            <TrendingDown v-else-if="(card.delta || 0) < 0" :size="13" />
            <Minus v-else :size="13" />
            <span>{{ formatDelta(card.delta) }} 较上一周期</span>
          </div>
        </article>
      </div>
    </section>

    <section class="dashboard-main-grid">
      <article class="dashboard-card dashboard-traffic">
        <div class="dashboard-card-heading">
          <div>
            <h2 class="dashboard-card__title mb-0">问答流量</h2>
            <p>{{ windowLabel }}消息量趋势</p>
          </div>
          <div class="dashboard-traffic-total">
            <strong>{{ formatNumber(overview?.kpis.messages24h.value) }}</strong>
            <span>条消息</span>
          </div>
        </div>
        <div v-if="loading && !messagePoints.length" class="dashboard-chart-loading">加载趋势数据...</div>
        <div v-else-if="messagePoints.length" class="dashboard-area-chart" aria-label="消息趋势图">
          <div v-for="point in messagePoints" :key="point.ts" class="dashboard-area-column" :title="`${formatPointTime(point.ts)} · ${point.value}`">
            <span :style="{ height: `${point.height}%` }"></span>
          </div>
        </div>
        <div v-else class="dashboard-empty">暂无趋势数据</div>
        <div v-if="messagePoints.length" class="dashboard-axis">
          <span>{{ formatPointTime(messagePoints[0]?.ts) }}</span>
          <span>{{ formatPointTime(messagePoints[messagePoints.length - 1]?.ts) }}</span>
        </div>
      </article>

      <article class="dashboard-card dashboard-health">
        <div class="dashboard-card-heading">
          <div>
            <h2 class="dashboard-card__title mb-0">AI 性能</h2>
            <p>响应质量与链路健康度</p>
          </div>
          <span class="dashboard-health-badge" :data-status="healthStatus">{{ healthLabel }}</span>
        </div>
        <div class="dashboard-health-ring" :style="healthRingStyle">
          <div><strong>{{ formatPercent(performance?.successRate) }}</strong><span>成功率</span></div>
        </div>
        <div class="dashboard-health-metrics">
          <div><span>平均响应</span><strong>{{ formatDuration(performance?.avgLatencyMs) }}</strong></div>
          <div><span>P95 响应</span><strong>{{ formatDuration(performance?.p95LatencyMs) }}</strong></div>
          <div><span>错误率</span><strong>{{ formatPercent(performance?.errorRate) }}</strong></div>
          <div><span>无文档率</span><strong>{{ formatPercent(performance?.noDocRate) }}</strong></div>
        </div>
      </article>
    </section>

    <section class="dashboard-card">
      <h2 class="dashboard-card__title">趋势分析</h2>
      <div class="dashboard-trend-grid">
        <article v-for="chart in trendCards" :key="chart.key" class="dashboard-mini-chart">
          <div class="dashboard-mini-chart__head">
            <div><strong>{{ chart.label }}</strong><span>{{ chart.description }}</span></div>
            <component :is="chart.icon" :size="17" />
          </div>
          <div v-if="chart.points.length" class="dashboard-mini-bars">
            <span v-for="point in chart.points" :key="point.ts" :style="{ height: `${point.height}%` }" :title="String(point.value)"></span>
          </div>
          <div v-else class="dashboard-empty dashboard-empty--small">暂无数据</div>
        </article>
      </div>
    </section>

    <section class="dashboard-bottom-grid">
      <article class="dashboard-card">
        <h2 class="dashboard-card__title">质量快照</h2>
        <div class="dashboard-quality-list">
          <div v-for="metric in qualityMetrics" :key="metric.label">
            <div><span>{{ metric.label }}</span><strong>{{ formatPercent(metric.value) }}</strong></div>
            <div class="dashboard-quality-track"><span :style="{ width: `${clampPercent(metric.value)}%` }" :data-tone="metric.tone"></span></div>
          </div>
        </div>
      </article>

      <article class="dashboard-card">
        <h2 class="dashboard-card__title">运行洞察</h2>
        <div class="dashboard-insights">
          <div v-for="insight in insights" :key="insight.title" class="dashboard-insight">
            <component :is="insight.icon" :size="17" />
            <div><strong>{{ insight.title }}</strong><p>{{ insight.description }}</p></div>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import {
  Activity,
  AlertTriangle,
  Clock3,
  Gauge,
  Lightbulb,
  MessageSquare,
  Minus,
  RefreshCw,
  Sparkles,
  TrendingDown,
  TrendingUp,
  UserRoundCheck,
  Users
} from "lucide-vue-next";
import { computed, onMounted, ref, watch } from "vue";

import {
  getDashboardOverview,
  getDashboardPerformance,
  getDashboardTrends,
  type DashboardOverview,
  type DashboardPerformance,
  type DashboardTrendPoint,
  type DashboardTrends
} from "@/services/dashboardService";

type DashboardTimeWindow = "24h" | "7d" | "30d";
type TrendKey = "sessions" | "messages" | "activeUsers" | "latency" | "quality";
type ChartPoint = DashboardTrendPoint & { height: number };

const windowOptions: Array<{ value: DashboardTimeWindow; label: string }> = [
  { value: "24h", label: "24 小时" },
  { value: "7d", label: "7 天" },
  { value: "30d", label: "30 天" }
];

const timeWindow = ref<DashboardTimeWindow>("24h");
const overview = ref<DashboardOverview | null>(null);
const performance = ref<DashboardPerformance | null>(null);
const trends = ref<Record<TrendKey, DashboardTrends | null>>({ sessions: null, messages: null, activeUsers: null, latency: null, quality: null });
const loading = ref(true);
const error = ref("");
const lastUpdated = ref<number | null>(null);
let requestId = 0;

const windowLabel = computed(() => windowOptions.find((item) => item.value === timeWindow.value)?.label || timeWindow.value);
const updatedLabel = computed(() => lastUpdated.value ? `更新于 ${new Date(lastUpdated.value).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" })}` : "等待更新");
const kpiCards = computed(() => [
  { label: "累计用户", value: overview.value?.kpis.totalUsers.value, delta: overview.value?.kpis.totalUsers.deltaPct, icon: Users },
  { label: "活跃用户", value: overview.value?.kpis.activeUsers.value, delta: overview.value?.kpis.activeUsers.deltaPct, icon: UserRoundCheck },
  { label: "累计会话", value: overview.value?.kpis.totalSessions.value, delta: overview.value?.kpis.totalSessions.deltaPct, icon: Activity },
  { label: "周期会话", value: overview.value?.kpis.sessions24h.value, delta: overview.value?.kpis.sessions24h.deltaPct, icon: Sparkles },
  { label: "累计消息", value: overview.value?.kpis.totalMessages.value, delta: overview.value?.kpis.totalMessages.deltaPct, icon: MessageSquare },
  { label: "周期消息", value: overview.value?.kpis.messages24h.value, delta: overview.value?.kpis.messages24h.deltaPct, icon: Gauge }
]);
const messagePoints = computed(() => normalizePoints(trends.value.messages));
const healthStatus = computed(() => {
  if (!performance.value) return "unknown";
  if (performance.value.errorRate > 5 || performance.value.successRate < 85) return "critical";
  if (performance.value.noDocRate > 20 || performance.value.avgLatencyMs > 15000) return "attention";
  return "healthy";
});
const healthLabel = computed(() => ({ healthy: "运行健康", attention: "需要关注", critical: "存在异常", unknown: "暂无数据" }[healthStatus.value]));
const healthRingStyle = computed(() => {
  const rate = clampPercent(performance.value?.successRate);
  const color = rate >= 95 ? "#2f9e73" : rate >= 85 ? "#d99116" : "#df4545";
  return { background: `conic-gradient(${color} ${rate * 3.6}deg, var(--surface-hover) 0deg)` };
});
const trendCards = computed(() => [
  { key: "sessions", label: "会话趋势", description: windowLabel.value, icon: MessageSquare, points: normalizePoints(trends.value.sessions) },
  { key: "activeUsers", label: "活跃用户", description: windowLabel.value, icon: Users, points: normalizePoints(trends.value.activeUsers) },
  { key: "latency", label: "平均延迟", description: windowLabel.value, icon: Clock3, points: normalizePoints(trends.value.latency) },
  { key: "quality", label: "问答质量", description: windowLabel.value, icon: Gauge, points: normalizePoints(trends.value.quality) }
]);
const qualityMetrics = computed(() => [
  { label: "错误率", value: performance.value?.errorRate, tone: "bad" },
  { label: "无文档率", value: performance.value?.noDocRate, tone: "warning" },
  { label: "慢请求率", value: performance.value?.slowRate, tone: "warning" }
]);
const insights = computed(() => {
  if (!performance.value) return [{ title: "等待数据", description: "系统返回运行指标后将在这里生成洞察。", icon: Lightbulb }];
  const items = [];
  if (performance.value.successRate < 95) items.push({ title: "成功率需要关注", description: `当前成功率为 ${formatPercent(performance.value.successRate)}，建议检查错误 Trace。`, icon: AlertTriangle });
  if (performance.value.noDocRate > 20) items.push({ title: "知识覆盖不足", description: `无文档率达到 ${formatPercent(performance.value.noDocRate)}，建议补充高频问题资料。`, icon: Lightbulb });
  if (performance.value.avgLatencyMs > 15000) items.push({ title: "响应速度偏慢", description: `平均响应 ${formatDuration(performance.value.avgLatencyMs)}，建议检查检索和模型耗时。`, icon: Clock3 });
  if (!items.length) items.push({ title: "链路运行稳定", description: `${windowLabel.value}内关键质量指标保持在健康区间。`, icon: Sparkles });
  return items;
});

watch(timeWindow, loadData);

async function loadData() {
  const currentRequest = ++requestId;
  loading.value = true;
  error.value = "";
  const granularity = timeWindow.value === "24h" ? "hour" : "day";
  try {
    const [overviewData, performanceData] = await Promise.all([
      getDashboardOverview(timeWindow.value),
      getDashboardPerformance(timeWindow.value)
    ]);
    if (currentRequest !== requestId) return;
    overview.value = overviewData;
    performance.value = performanceData;
    lastUpdated.value = overviewData.updatedAt || Date.now();

    const [sessions, messages, activeUsers, latency, quality] = await Promise.all([
      getDashboardTrends("sessions", timeWindow.value, granularity),
      getDashboardTrends("messages", timeWindow.value, granularity),
      getDashboardTrends("activeUsers", timeWindow.value, granularity),
      getDashboardTrends("avgLatency", timeWindow.value, granularity),
      getDashboardTrends("quality", timeWindow.value, granularity)
    ]);
    if (currentRequest === requestId) trends.value = { sessions, messages, activeUsers, latency, quality };
  } catch (loadError) {
    if (currentRequest === requestId) error.value = (loadError as Error).message || "加载 Dashboard 失败";
  } finally {
    if (currentRequest === requestId) loading.value = false;
  }
}

function normalizePoints(trend: DashboardTrends | null): ChartPoint[] {
  const raw = trend?.series?.[0]?.data || [];
  const max = Math.max(...raw.map((point) => point.value), 1);
  return raw.map((point) => ({ ...point, height: Math.max(5, (point.value / max) * 100) }));
}

function formatNumber(value?: number | null) {
  return value == null ? "--" : new Intl.NumberFormat("zh-CN", { notation: value > 9999 ? "compact" : "standard", maximumFractionDigits: 1 }).format(value);
}

function formatPercent(value?: number | null) {
  return value == null ? "--" : `${value.toFixed(1)}%`;
}

function formatDuration(value?: number | null) {
  if (value == null) return "--";
  return value >= 1000 ? `${(value / 1000).toFixed(2)}s` : `${Math.round(value)}ms`;
}

function formatDelta(value?: number | null) {
  if (value == null) return "--";
  return `${value > 0 ? "+" : ""}${value.toFixed(1)}%`;
}

function deltaTone(value?: number | null) {
  if ((value || 0) > 0) return "is-positive";
  if ((value || 0) < 0) return "is-negative";
  return "";
}

function clampPercent(value?: number | null) {
  return Math.max(0, Math.min(100, value || 0));
}

function formatPointTime(timestamp?: number) {
  if (!timestamp) return "--";
  const options: Intl.DateTimeFormatOptions = timeWindow.value === "24h" ? { hour: "2-digit", minute: "2-digit" } : { month: "2-digit", day: "2-digit" };
  return new Date(timestamp).toLocaleString("zh-CN", options);
}

onMounted(loadData);
</script>

<style scoped>
.dashboard-page { gap: 1rem; }
.dashboard-refresh { display: grid; width: 2.25rem; height: 2.25rem; place-items: center; border: 1px solid var(--border-subtle); background: var(--surface); color: var(--text-secondary); }
.dashboard-error { display: flex; align-items: center; gap: 0.55rem; border: 1px solid #efb9b9; border-radius: 0.5rem; background: #fff1f1; padding: 0.75rem 0.9rem; color: #a72e2e; }
.dashboard-error button { margin-left: auto; font-weight: 600; }
.dashboard-card-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; }
.dashboard-card-heading p { color: var(--text-tertiary); font-size: 0.75rem; }
.dashboard-kpi-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 0.75rem; }
.dashboard-kpi__head { display: flex; align-items: center; justify-content: space-between; color: var(--text-tertiary); font-size: 0.75rem; }
.dashboard-kpi strong { display: block; margin-top: 0.55rem; color: var(--text-primary); font-size: 1.35rem; line-height: 1; }
.dashboard-kpi__foot { display: flex; margin-top: 0.55rem; align-items: center; gap: 0.25rem; color: var(--text-tertiary); font-size: 0.6875rem; }
.dashboard-kpi__foot.is-positive { color: #2f8f6c; }
.dashboard-kpi__foot.is-negative { color: #c74646; }
.dashboard-main-grid { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(19rem, 0.8fr); gap: 1rem; }
.dashboard-traffic-total { display: flex; align-items: baseline; gap: 0.35rem; color: var(--text-tertiary); font-size: 0.75rem; }
.dashboard-traffic-total strong { color: var(--text-primary); font-size: 1.25rem; }
.dashboard-area-chart { display: flex; height: 15rem; margin-top: 1rem; align-items: end; gap: clamp(2px, 0.45vw, 7px); border-bottom: 1px solid var(--border-subtle); background: repeating-linear-gradient(to bottom, transparent 0, transparent 24%, var(--border-subtle) 25%); }
.dashboard-area-column { min-width: 2px; flex: 1; height: 100%; display: flex; align-items: end; }
.dashboard-area-column span { width: 100%; min-height: 0.25rem; border-radius: 0.2rem 0.2rem 0 0; background: #0f8f74; opacity: 0.8; transition: opacity 120ms ease; }
.dashboard-area-column:hover span { opacity: 1; }
.dashboard-axis { display: flex; margin-top: 0.45rem; justify-content: space-between; color: var(--text-tertiary); font-size: 0.6875rem; }
.dashboard-chart-loading, .dashboard-empty { display: grid; min-height: 15rem; place-items: center; color: var(--text-tertiary); }
.dashboard-health-ring { width: 8.75rem; height: 8.75rem; margin: 1.25rem auto; border-radius: 50%; padding: 0.8rem; }
.dashboard-health-ring > div { display: flex; width: 100%; height: 100%; align-items: center; justify-content: center; flex-direction: column; border-radius: 50%; background: var(--surface); }
.dashboard-health-ring strong { color: var(--text-primary); font-size: 1.35rem; }
.dashboard-health-ring span { color: var(--text-tertiary); font-size: 0.7rem; }
.dashboard-health-badge { border-radius: 999px; padding: 0.25rem 0.55rem; background: var(--surface-hover); color: var(--text-secondary); font-size: 0.7rem; }
.dashboard-health-badge[data-status="healthy"] { background: #e6f5ef; color: #247a5c; }
.dashboard-health-badge[data-status="attention"] { background: #fff5dc; color: #966313; }
.dashboard-health-badge[data-status="critical"] { background: #fff0f0; color: #b43737; }
.dashboard-health-metrics { display: grid; grid-template-columns: 1fr 1fr; gap: 0.65rem; }
.dashboard-health-metrics div { display: flex; flex-direction: column; border-top: 1px solid var(--border-subtle); padding-top: 0.65rem; }
.dashboard-health-metrics span { color: var(--text-tertiary); font-size: 0.7rem; }
.dashboard-health-metrics strong { margin-top: 0.15rem; color: var(--text-primary); font-size: 0.875rem; }
.dashboard-trend-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0.75rem; }
.dashboard-mini-chart { border: 1px solid var(--border-subtle); border-radius: 0.4rem; padding: 0.85rem; }
.dashboard-mini-chart__head { display: flex; align-items: flex-start; justify-content: space-between; color: var(--text-secondary); }
.dashboard-mini-chart__head div { display: flex; flex-direction: column; }
.dashboard-mini-chart__head strong { color: var(--text-primary); font-size: 0.8rem; }
.dashboard-mini-chart__head span { color: var(--text-tertiary); font-size: 0.68rem; }
.dashboard-mini-bars { display: flex; height: 5.5rem; margin-top: 0.8rem; align-items: end; gap: 3px; }
.dashboard-mini-bars span { min-width: 2px; flex: 1; border-radius: 2px 2px 0 0; background: var(--text-secondary); opacity: 0.5; }
.dashboard-empty--small { min-height: 5.5rem; font-size: 0.75rem; }
.dashboard-bottom-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.dashboard-quality-list { display: grid; gap: 0.9rem; }
.dashboard-quality-list > div > div:first-child { display: flex; margin-bottom: 0.35rem; justify-content: space-between; color: var(--text-secondary); font-size: 0.75rem; }
.dashboard-quality-list strong { color: var(--text-primary); }
.dashboard-quality-track { height: 0.4rem; overflow: hidden; border-radius: 999px; background: var(--surface-hover); }
.dashboard-quality-track span { display: block; height: 100%; background: #d99116; }
.dashboard-quality-track span[data-tone="bad"] { background: #df4545; }
.dashboard-insights { display: grid; gap: 0.7rem; }
.dashboard-insight { display: flex; gap: 0.7rem; border-bottom: 1px solid var(--border-subtle); padding-bottom: 0.7rem; color: var(--text-secondary); }
.dashboard-insight:last-child { border-bottom: 0; padding-bottom: 0; }
.dashboard-insight svg { margin-top: 0.1rem; flex: none; }
.dashboard-insight strong { color: var(--text-primary); font-size: 0.8rem; }
.dashboard-insight p { margin-top: 0.15rem; color: var(--text-tertiary); font-size: 0.72rem; line-height: 1.5; }
.dashboard-skeleton { border-radius: 0.3rem; background: var(--surface-hover); animation: dashboard-pulse 1.4s ease-in-out infinite; }
@keyframes dashboard-pulse { 50% { opacity: 0.45; } }
@media (max-width: 1180px) { .dashboard-kpi-grid { grid-template-columns: repeat(3, 1fr); } .dashboard-trend-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 760px) { .dashboard-main-grid, .dashboard-bottom-grid { grid-template-columns: 1fr; } .dashboard-kpi-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 480px) { .dashboard-kpi-grid, .dashboard-trend-grid { grid-template-columns: 1fr; } }
@media (prefers-reduced-motion: reduce) { .dashboard-skeleton, .animate-spin { animation: none; } }
</style>
