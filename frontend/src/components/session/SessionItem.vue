<template>
  <div
    ref="rootRef"
    class="chat-session"
    :class="{ 'chat-session--active': active }"
    role="button"
    tabindex="0"
    @click="!renaming && $emit('select')"
    @keydown.enter.prevent="!renaming && $emit('select')"
    @keydown.space.prevent="!renaming && $emit('select')"
  >
    <input
      v-if="renaming"
      ref="renameRef"
      v-model="renameValue"
      class="chat-session__rename"
      aria-label="会话标题"
      @click.stop
      @keydown.enter.prevent="commitRename"
      @keydown.esc.prevent="cancelRename"
      @blur="commitRename"
    />
    <span v-else class="chat-session__title">{{ session.title || "新对话" }}</span>

    <button
      type="button"
      class="chat-session__menu"
      aria-label="会话操作"
      @click.stop="menuOpen = !menuOpen"
    >
      <MoreHorizontal :size="16" />
    </button>

    <div v-if="menuOpen" class="session-menu-popover" role="menu" @click.stop>
      <button type="button" role="menuitem" @click="startRename"><Pencil :size="15" />重命名</button>
      <button type="button" role="menuitem" class="is-destructive" @click="requestDelete">
        <Trash2 :size="15" />删除
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { MoreHorizontal, Pencil, Trash2 } from "lucide-vue-next";
import { nextTick, onBeforeUnmount, onMounted, ref } from "vue";

import type { Session } from "@/types";

const props = defineProps<{ session: Session; active: boolean }>();
const emit = defineEmits<{
  (event: "select"): void;
  (event: "rename", payload: { id: string; title: string }): void;
  (event: "delete"): void;
}>();

const rootRef = ref<HTMLElement | null>(null);
const renameRef = ref<HTMLInputElement | null>(null);
const menuOpen = ref(false);
const renaming = ref(false);
const renameValue = ref("");

function handleDocumentPointerDown(event: PointerEvent) {
  if (rootRef.value && !rootRef.value.contains(event.target as Node)) menuOpen.value = false;
}

function startRename() {
  menuOpen.value = false;
  renaming.value = true;
  renameValue.value = props.session.title || "新对话";
  void nextTick(() => {
    renameRef.value?.focus();
    renameRef.value?.select();
  });
}

function cancelRename() {
  renaming.value = false;
  renameValue.value = "";
}

function commitRename() {
  if (!renaming.value) return;
  const nextTitle = renameValue.value.trim();
  renaming.value = false;
  if (nextTitle && nextTitle !== (props.session.title || "新对话")) {
    emit("rename", { id: props.session.id, title: nextTitle });
  }
}

function requestDelete() {
  menuOpen.value = false;
  emit("delete");
}

onMounted(() => document.addEventListener("pointerdown", handleDocumentPointerDown));
onBeforeUnmount(() => document.removeEventListener("pointerdown", handleDocumentPointerDown));
</script>
