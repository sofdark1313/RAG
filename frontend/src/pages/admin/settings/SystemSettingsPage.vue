<template>
  <div v-if="loading" class="admin-page">
    <div class="text-sm text-muted-foreground">加载中...</div>
  </div>

  <div v-else-if="!settings" class="admin-page">
    <div class="text-sm text-muted-foreground">暂无可展示的配置</div>
  </div>

  <div v-else class="admin-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">系统配置</h1>
        <p class="admin-page-subtitle">只读展示当前 application 配置</p>
      </div>
    </header>

    <section class="ui-card">
      <div class="ui-card-header">
        <h2 class="ui-card-title">RAG 默认配置</h2>
        <p class="ui-card-description">向量空间与检索基础参数</p>
      </div>
      <div class="ui-card-content grid gap-4 px-6 pt-6 md:grid-cols-3">
        <InfoItem label="Collection" :value="settings.rag.default.collectionName" />
        <InfoItem label="Dimension" :value="settings.rag.default.dimension" />
        <InfoItem label="Metric Type" :value="settings.rag.default.metricType" />
      </div>
    </section>

    <section class="ui-card">
      <div class="ui-card-header">
        <h2 class="ui-card-title">查询改写</h2>
        <p class="ui-card-description">历史上下文压缩与改写策略</p>
      </div>
      <div class="ui-card-content grid gap-4 px-6 pt-6 md:grid-cols-3">
        <InfoItem label="Enabled">
          <BoolBadge :value="settings.rag.queryRewrite.enabled" />
        </InfoItem>
      </div>
    </section>

    <section class="ui-card">
      <div class="ui-card-header">
        <h2 class="ui-card-title">全局限流</h2>
        <p class="ui-card-description">并发与租约控制</p>
      </div>
      <div class="ui-card-content grid gap-4 px-6 pt-6 md:grid-cols-3">
        <InfoItem label="Enabled">
          <BoolBadge :value="settings.rag.rateLimit.global.enabled" />
        </InfoItem>
        <InfoItem label="Max Concurrent" :value="settings.rag.rateLimit.global.maxConcurrent" />
        <InfoItem label="Max Wait Seconds" :value="settings.rag.rateLimit.global.maxWaitSeconds" />
        <InfoItem label="Lease Seconds" :value="settings.rag.rateLimit.global.leaseSeconds" />
        <InfoItem
          label="Poll Interval (ms)"
          :value="settings.rag.rateLimit.global.pollIntervalMs"
        />
      </div>
    </section>

    <section class="ui-card">
      <div class="ui-card-header">
        <h2 class="ui-card-title">记忆管理</h2>
        <p class="ui-card-description">摘要与上下文保留策略</p>
      </div>
      <div class="ui-card-content grid gap-4 px-6 pt-6 md:grid-cols-3">
        <InfoItem label="History Keep Turns" :value="settings.rag.memory.historyKeepTurns" />
        <InfoItem label="Summary Start Turns" :value="settings.rag.memory.summaryStartTurns" />
        <InfoItem label="Summary Enabled">
          <BoolBadge :value="settings.rag.memory.summaryEnabled" />
        </InfoItem>
        <InfoItem label="Summary Max Chars" :value="settings.rag.memory.summaryMaxChars" />
        <InfoItem label="Title Max Length" :value="settings.rag.memory.titleMaxLength" />
      </div>
    </section>

    <section class="ui-card">
      <div class="ui-card-header">
        <h2 class="ui-card-title">模型服务提供方</h2>
        <p class="ui-card-description">接入地址与端点配置</p>
      </div>
      <div class="ui-card-content px-6 pt-6">
        <div class="ui-table-wrap">
          <table class="ui-table min-w-[760px] w-full">
            <thead class="ui-table-header">
              <tr class="ui-table-row">
                <th class="ui-table-head w-[140px] text-left">Provider</th>
                <th class="ui-table-head w-[240px] text-left">URL</th>
                <th class="ui-table-head w-[200px] text-left">API Key</th>
                <th class="ui-table-head text-left">Endpoints</th>
              </tr>
            </thead>
            <tbody class="ui-table-body">
              <tr v-for="[name, provider] in providers" :key="name" class="ui-table-row">
                <td class="ui-table-cell font-medium text-slate-900">{{ name }}</td>
                <td class="ui-table-cell">{{ provider.url }}</td>
                <td class="ui-table-cell break-all">{{ provider.apiKey || "-" }}</td>
                <td class="ui-table-cell">
                  <div class="space-y-1 text-xs text-muted-foreground">
                    <div v-for="[key, value] in Object.entries(provider.endpoints)" :key="key">
                      {{ key }}: {{ value }}
                    </div>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <section class="ui-card">
      <div class="ui-card-header">
        <h2 class="ui-card-title">模型选择策略</h2>
        <p class="ui-card-description">熔断与选择阈值</p>
      </div>
      <div class="ui-card-content grid gap-4 px-6 pt-6 md:grid-cols-2">
        <InfoItem label="Failure Threshold" :value="settings.ai.selection.failureThreshold" />
        <InfoItem label="Open Duration (ms)" :value="settings.ai.selection.openDurationMs" />
      </div>
    </section>

    <section class="ui-card">
      <div class="ui-card-header">
        <h2 class="ui-card-title">流式响应</h2>
        <p class="ui-card-description">输出分片大小</p>
      </div>
      <div class="ui-card-content grid gap-4 px-6 pt-6 md:grid-cols-2">
        <InfoItem label="Message Chunk Size" :value="settings.ai.stream.messageChunkSize" />
      </div>
    </section>

    <section class="ui-card">
      <div class="ui-card-header">
        <h2 class="ui-card-title">Chat 模型配置</h2>
        <p class="ui-card-description">默认模型与候选列表</p>
      </div>
      <div class="ui-card-content space-y-4 px-6 pt-6">
        <div class="grid gap-4 md:grid-cols-2">
          <InfoItem label="Default Model" :value="settings.ai.chat.defaultModel" />
          <InfoItem label="Deep Thinking Model" :value="settings.ai.chat.deepThinkingModel" />
        </div>
        <div class="ui-table-wrap">
          <table class="ui-table min-w-[720px] w-full">
            <thead class="ui-table-header">
              <tr class="ui-table-row">
                <th class="ui-table-head w-[220px] text-left">ID</th>
                <th class="ui-table-head w-[120px] text-left">Provider</th>
                <th class="ui-table-head w-[200px] text-left">Model</th>
                <th class="ui-table-head w-[100px] text-left">Thinking</th>
                <th class="ui-table-head w-[90px] text-left">Priority</th>
              </tr>
            </thead>
            <tbody class="ui-table-body">
              <tr v-for="item in settings.ai.chat.candidates" :key="item.id" class="ui-table-row">
                <td class="ui-table-cell font-medium text-slate-900">{{ item.id }}</td>
                <td class="ui-table-cell">{{ item.provider }}</td>
                <td class="ui-table-cell">{{ item.model }}</td>
                <td class="ui-table-cell">{{ item.supportsThinking ? "支持" : "-" }}</td>
                <td class="ui-table-cell">{{ item.priority }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <section class="ui-card">
      <div class="ui-card-header">
        <h2 class="ui-card-title">Embedding 模型配置</h2>
        <p class="ui-card-description">向量化模型列表</p>
      </div>
      <div class="ui-card-content space-y-4 px-6 pt-6">
        <div class="grid gap-4 md:grid-cols-2">
          <InfoItem label="Default Model" :value="settings.ai.embedding.defaultModel" />
        </div>
        <div class="ui-table-wrap">
          <table class="ui-table min-w-[720px] w-full">
            <thead class="ui-table-header">
              <tr class="ui-table-row">
                <th class="ui-table-head w-[220px] text-left">ID</th>
                <th class="ui-table-head w-[120px] text-left">Provider</th>
                <th class="ui-table-head w-[200px] text-left">Model</th>
                <th class="ui-table-head w-[110px] text-left">Dimension</th>
                <th class="ui-table-head w-[90px] text-left">Priority</th>
              </tr>
            </thead>
            <tbody class="ui-table-body">
              <tr
                v-for="item in settings.ai.embedding.candidates"
                :key="item.id"
                class="ui-table-row"
              >
                <td class="ui-table-cell font-medium text-slate-900">{{ item.id }}</td>
                <td class="ui-table-cell">{{ item.provider }}</td>
                <td class="ui-table-cell">{{ item.model }}</td>
                <td class="ui-table-cell">{{ item.dimension }}</td>
                <td class="ui-table-cell">{{ item.priority }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <section class="ui-card">
      <div class="ui-card-header">
        <h2 class="ui-card-title">Rerank 模型配置</h2>
        <p class="ui-card-description">重排模型列表</p>
      </div>
      <div class="ui-card-content space-y-4 px-6 pt-6">
        <div class="grid gap-4 md:grid-cols-2">
          <InfoItem label="Default Model" :value="settings.ai.rerank.defaultModel" />
        </div>
        <div class="ui-table-wrap">
          <table class="ui-table min-w-[640px] w-full">
            <thead class="ui-table-header">
              <tr class="ui-table-row">
                <th class="ui-table-head w-[220px] text-left">ID</th>
                <th class="ui-table-head w-[120px] text-left">Provider</th>
                <th class="ui-table-head w-[200px] text-left">Model</th>
                <th class="ui-table-head w-[90px] text-left">Priority</th>
              </tr>
            </thead>
            <tbody class="ui-table-body">
              <tr v-for="item in settings.ai.rerank.candidates" :key="item.id" class="ui-table-row">
                <td class="ui-table-cell font-medium text-slate-900">{{ item.id }}</td>
                <td class="ui-table-cell">{{ item.provider }}</td>
                <td class="ui-table-cell">{{ item.model }}</td>
                <td class="ui-table-cell">{{ item.priority }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, ref, type PropType } from "vue";

import { getSystemSettings, type SystemSettings } from "@/services/settingsService";
import { getErrorMessage } from "@/utils/error";
import { toast } from "@/utils/toast";

const settings = ref<SystemSettings | null>(null);
const loading = ref(true);

const providers = computed(() => Object.entries(settings.value?.ai.providers || {}));

const BoolBadge = defineComponent({
  props: {
    value: { type: Boolean, required: true }
  },
  setup(props) {
    return () =>
      h(
        "span",
        {
          class: "ui-badge inline-flex border px-2 py-1",
          "data-variant": props.value ? "default" : "outline"
        },
        props.value ? "启用" : "禁用"
      );
  }
});

const InfoItem = defineComponent({
  props: {
    label: { type: String, required: true },
    value: {
      type: [String, Number] as PropType<string | number | null | undefined>,
      default: ""
    }
  },
  setup(props, { slots }) {
    return () =>
      h("div", { class: "settings-info-item" }, [
        h("span", { class: "text-xs text-slate-500" }, props.label),
        h(
          "div",
          { class: "text-sm font-medium text-slate-800" },
          slots.default?.() || String(props.value ?? "")
        )
      ]);
  }
});

async function loadSettings() {
  try {
    loading.value = true;
    settings.value = await getSystemSettings();
  } catch (error) {
    toast.error(getErrorMessage(error, "加载系统配置失败"));
    console.error(error);
  } finally {
    loading.value = false;
  }
}

onMounted(loadSettings);
</script>

<style scoped>
.settings-info-item {
  @apply flex min-w-0 flex-col gap-1 rounded-lg border border-slate-200/70 bg-white px-4 py-3;
}
</style>
