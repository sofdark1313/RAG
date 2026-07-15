<template>
  <div class="admin-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">数据概览</h1>
        <p class="admin-page-subtitle">查看最近 24 小时和累计运行指标</p>
      </div>
      <div class="admin-page-actions">
        <button class="ui-button rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm" type="button" @click="load">
          刷新数据
        </button>
      </div>
    </header>

    <section class="admin-stat-grid">
      <div v-for="card in cards" :key="card.label" class="admin-stat-card">
        <div>
          <p class="admin-stat-label">{{ card.label }}</p>
          <p class="admin-stat-value">{{ card.value }}</p>
          <p class="admin-stat-trend" :class="card.delta >= 0 ? 'text-emerald-600' : 'text-red-500'">
            {{ card.delta >= 0 ? "+" : "" }}{{ card.delta }}
          </p>
        </div>
        <div class="admin-stat-icon">
          <component :is="card.icon" class="h-5 w-5" />
        </div>
      </div>
    </section>

    <section class="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
      <div class="ui-card">
        <div class="ui-card-header">
          <h2 class="ui-card-title">趋势概览</h2>
          <p class="ui-card-description">最近 7 天消息量变化</p>
        </div>
        <div class="ui-card-content px-6 pt-6">
          <div class="dashboard-bars">
            <div v-for="point in trendPoints" :key="point.label" class="dashboard-bar">
              <div class="dashboard-bar__track">
                <span :style="{ height: `${point.height}%` }"></span>
              </div>
              <p>{{ point.label }}</p>
            </div>
          </div>
        </div>
      </div>

      <div class="ui-card">
        <div class="ui-card-header">
          <h2 class="ui-card-title">性能状态</h2>
          <p class="ui-card-description">问答链路健康度</p>
        </div>
        <div class="ui-card-content space-y-4 px-6 pt-6">
          <div v-for="item in performanceRows" :key="item.label">
            <div class="mb-2 flex items-center justify-between text-sm">
              <span class="text-slate-600">{{ item.label }}</span>
              <span class="font-medium text-slate-900">{{ item.value }}</span>
            </div>
            <div class="h-2 rounded-full bg-slate-100">
              <div class="h-2 rounded-full bg-indigo-500" :style="{ width: item.width }"></div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { Activity, Clock3, MessageSquare, Users } from "lucide-vue-next";
import { computed, onMounted, ref } from "vue";

import {
  getDashboardOverview,
  getDashboardPerformance,
  getDashboardTrends,
  type DashboardOverview,
  type DashboardPerformance,
  type DashboardTrends
} from "@/services/dashboardService";
import { toast } from "@/utils/toast";

const overview = ref<DashboardOverview | null>(null);
const performance = ref<DashboardPerformance | null>(null);
const trends = ref<DashboardTrends | null>(null);

const cards = computed(() => [
  {
    label: "累计用户",
    value: overview.value?.kpis.totalUsers.value ?? 0,
    delta: overview.value?.kpis.totalUsers.delta ?? 0,
    icon: Users
  },
  {
    label: "活跃用户",
    value: overview.value?.kpis.activeUsers.value ?? 0,
    delta: overview.value?.kpis.activeUsers.delta ?? 0,
    icon: Activity
  },
  {
    label: "累计会话",
    value: overview.value?.kpis.totalSessions.value ?? 0,
    delta: overview.value?.kpis.sessions24h.value ?? 0,
    icon: MessageSquare
  },
  {
    label: "平均延迟",
    value: `${performance.value?.avgLatencyMs ?? 0}ms`,
    delta: 0,
    icon: Clock3
  }
]);

const trendPoints = computed(() => {
  const data = trends.value?.series?.[0]?.data || [];
  const max = Math.max(...data.map((item) => item.value), 1);
  return data.map((item) => ({
    label: new Date(item.ts).toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" }),
    height: Math.max(8, Math.round((item.value / max) * 100))
  }));
});

const performanceRows = computed(() => [
  { label: "成功率", value: `${Math.round((performance.value?.successRate ?? 0) * 100)}%`, width: `${Math.round((performance.value?.successRate ?? 0) * 100)}%` },
  { label: "错误率", value: `${Math.round((performance.value?.errorRate ?? 0) * 100)}%`, width: `${Math.round((performance.value?.errorRate ?? 0) * 100)}%` },
  { label: "无文档率", value: `${Math.round((performance.value?.noDocRate ?? 0) * 100)}%`, width: `${Math.round((performance.value?.noDocRate ?? 0) * 100)}%` },
  { label: "慢请求率", value: `${Math.round((performance.value?.slowRate ?? 0) * 100)}%`, width: `${Math.round((performance.value?.slowRate ?? 0) * 100)}%` }
]);

async function load() {
  try {
    const [overviewData, performanceData, trendData] = await Promise.all([
      getDashboardOverview("24h"),
      getDashboardPerformance("24h"),
      getDashboardTrends("messages", "7d", "day")
    ]);
    overview.value = overviewData;
    performance.value = performanceData;
    trends.value = trendData;
  } catch (error) {
    toast.error((error as Error).message || "加载仪表盘失败");
  }
}

onMounted(load);
</script>

<style scoped>
.dashboard-bars {
  display: flex;
  align-items: end;
  gap: 14px;
  height: 260px;
}

.dashboard-bar {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 10px;
  text-align: center;
  color: #64748b;
  font-size: 12px;
}

.dashboard-bar__track {
  display: flex;
  align-items: end;
  height: 220px;
  border-radius: 10px;
  background: #f1f5f9;
  overflow: hidden;
}

.dashboard-bar__track span {
  display: block;
  width: 100%;
  border-radius: 10px 10px 0 0;
  background: linear-gradient(180deg, #818cf8 0%, #4f46e5 100%);
}
</style>
