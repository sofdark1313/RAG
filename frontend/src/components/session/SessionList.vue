<template>
  <div class="chat-sidebar__sessions sidebar-scroll">
    <div v-if="sessions.length === 0 && (!sessionsLoaded || isLoading)" class="chat-sidebar__empty">
      <LoaderCircle class="session-loading" :size="20" />
      <span>加载会话中</span>
    </div>
    <div v-else-if="filteredSessions.length === 0" class="chat-sidebar__empty">
      <MessageSquare :size="20" />
      <span>暂无对话</span>
    </div>
    <template v-else>
      <section v-for="group in groupedSessions" :key="group.label" class="chat-session-group">
        <p>{{ group.label }}</p>
        <SessionItem
          v-for="session in group.items"
          :key="session.id"
          :session="session"
          :active="currentSessionId === session.id"
          @select="$emit('select', session.id)"
          @rename="$emit('rename', $event)"
          @delete="$emit('delete', session)"
        />
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { LoaderCircle, MessageSquare } from "lucide-vue-next";
import { computed } from "vue";

import SessionItem from "@/components/session/SessionItem.vue";
import type { Session } from "@/types";

const props = defineProps<{
  sessions: Session[];
  currentSessionId: string | null;
  isLoading: boolean;
  sessionsLoaded: boolean;
  query: string;
}>();

defineEmits<{
  (event: "select", sessionId: string): void;
  (event: "rename", payload: { id: string; title: string }): void;
  (event: "delete", session: Session): void;
}>();

const filteredSessions = computed(() => {
  const keyword = props.query.trim().toLowerCase();
  if (!keyword) return props.sessions;
  return props.sessions.filter((session) =>
    `${session.title || "新对话"} ${session.id}`.toLowerCase().includes(keyword)
  );
});

const groupedSessions = computed(() => {
  const groups = new Map<string, Session[]>();
  for (const session of filteredSessions.value) {
    const label = resolveDateGroup(session.lastTime);
    const items = groups.get(label) || [];
    items.push(session);
    groups.set(label, items);
  }
  return ["今天", "7 天内", "30 天内", "更早"]
    .filter((label) => groups.has(label))
    .map((label) => ({ label, items: groups.get(label) || [] }));
});

function resolveDateGroup(value?: string) {
  const date = value ? new Date(value) : new Date();
  const timestamp = Number.isNaN(date.getTime()) ? Date.now() : date.getTime();
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(timestamp);
  target.setHours(0, 0, 0, 0);
  const diff = Math.max(0, Math.floor((today.getTime() - target.getTime()) / 86_400_000));
  if (diff === 0) return "今天";
  if (diff <= 7) return "7 天内";
  if (diff <= 30) return "30 天内";
  return "更早";
}
</script>
