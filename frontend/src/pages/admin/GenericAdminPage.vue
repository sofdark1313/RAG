<template>
  <div class="admin-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">{{ route.meta.title }}</h1>
        <p class="admin-page-subtitle">{{ route.meta.subtitle }}</p>
      </div>
      <div class="admin-page-actions">
        <button class="ui-button rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm" type="button">
          导出
        </button>
        <button class="ui-button admin-primary-gradient rounded-lg px-4 py-2 text-sm" type="button">
          新建
        </button>
      </div>
    </header>

    <section class="admin-stat-grid">
      <div v-for="item in statCards" :key="item.label" class="admin-stat-card">
        <div>
          <p class="admin-stat-label">{{ item.label }}</p>
          <p class="admin-stat-value">{{ item.value }}</p>
          <p class="admin-stat-scope">{{ item.scope }}</p>
        </div>
        <div class="admin-stat-icon">
          <component :is="item.icon" class="h-5 w-5" />
        </div>
      </div>
    </section>

    <section class="ui-card">
      <div class="ui-card-header">
        <h2 class="ui-card-title">{{ route.meta.title }}列表</h2>
        <p class="ui-card-description">保持原管理后台的卡片、表格和筛选样式</p>
      </div>
      <div class="ui-card-content px-6 pt-5">
        <div class="mb-4 grid gap-3 md:grid-cols-[1fr_180px_120px]">
          <input class="ui-input h-10 rounded-lg border border-slate-200 px-3 text-sm" placeholder="输入关键词筛选" />
          <select class="ui-input h-10 rounded-lg border border-slate-200 px-3 text-sm">
            <option>全部状态</option>
            <option>启用</option>
            <option>停用</option>
          </select>
          <button class="ui-button rounded-lg bg-slate-900 px-4 py-2 text-sm text-white" type="button">查询</button>
        </div>

        <div class="ui-table-wrap">
          <table class="ui-table w-full">
            <thead class="ui-table-header">
              <tr class="ui-table-row">
                <th class="ui-table-head text-left">名称</th>
                <th class="ui-table-head text-left">状态</th>
                <th class="ui-table-head text-left">更新时间</th>
                <th class="ui-table-head text-right">操作</th>
              </tr>
            </thead>
            <tbody class="ui-table-body">
              <tr v-for="row in rows" :key="row.name" class="ui-table-row">
                <td class="ui-table-cell font-medium text-slate-900">{{ row.name }}</td>
                <td class="ui-table-cell">
                  <span class="ui-badge border border-emerald-200 bg-emerald-50 px-2 py-1 text-emerald-700">
                    {{ row.status }}
                  </span>
                </td>
                <td class="ui-table-cell text-slate-500">{{ row.time }}</td>
                <td class="ui-table-cell text-right">
                  <button class="admin-link" type="button">查看</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { Activity, Database, FileText, ShieldCheck } from "lucide-vue-next";
import { computed } from "vue";
import { useRoute } from "vue-router";

const route = useRoute();

const statCards = computed(() => [
  { label: "总记录", value: "128", scope: "累计", icon: Database },
  { label: "今日更新", value: "24", scope: "24h", icon: Activity },
  { label: "启用项", value: "96", scope: "当前", icon: ShieldCheck },
  { label: "待处理", value: "8", scope: "队列", icon: FileText }
]);

const rows = computed(() => [
  { name: `${String(route.meta.title)} A`, status: "启用", time: "2026-07-15 21:30" },
  { name: `${String(route.meta.title)} B`, status: "启用", time: "2026-07-15 20:18" },
  { name: `${String(route.meta.title)} C`, status: "启用", time: "2026-07-14 18:42" }
]);
</script>
