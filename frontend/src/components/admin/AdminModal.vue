<template>
  <Teleport to="body">
    <Transition name="admin-modal">
      <div v-if="open" class="admin-modal-root" role="presentation" @keydown.esc="emitClose">
        <div class="admin-modal-overlay" @click="emitClose"></div>
        <section
          ref="panelRef"
          class="admin-modal-panel ui-dialog-content"
          :style="{ maxWidth: width, maxHeight }"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="titleId"
          tabindex="-1"
        >
          <header class="admin-modal-header">
            <div class="min-w-0">
              <h2 :id="titleId" class="admin-modal-title">{{ title }}</h2>
              <p v-if="description" class="admin-modal-description">{{ description }}</p>
            </div>
            <button class="admin-modal-close" type="button" title="关闭" aria-label="关闭" @click="emitClose">
              <X class="h-4 w-4" />
            </button>
          </header>
          <div class="admin-modal-body">
            <slot />
          </div>
          <footer v-if="$slots.footer" class="admin-modal-footer">
            <slot name="footer" />
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { X } from "lucide-vue-next";
import { nextTick, onBeforeUnmount, ref, watch } from "vue";

const props = withDefaults(
  defineProps<{
    open: boolean;
    title: string;
    description?: string;
    width?: string;
    maxHeight?: string;
    closeOnOverlay?: boolean;
  }>(),
  {
    description: "",
    width: "640px",
    maxHeight: "90vh",
    closeOnOverlay: true
  }
);

const emit = defineEmits<{
  close: [];
}>();

const panelRef = ref<HTMLElement | null>(null);
const titleId = `admin-modal-${Math.random().toString(36).slice(2)}`;
let previousOverflow = "";

function emitClose() {
  if (props.closeOnOverlay) {
    emit("close");
  }
}

watch(
  () => props.open,
  async (open) => {
    if (open) {
      previousOverflow = document.body.style.overflow;
      document.body.style.overflow = "hidden";
      await nextTick();
      panelRef.value?.focus();
      return;
    }
    document.body.style.overflow = previousOverflow;
  }
);

onBeforeUnmount(() => {
  document.body.style.overflow = previousOverflow;
});
</script>

<style scoped>
.admin-modal-root {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 24px;
}

.admin-modal-overlay {
  position: absolute;
  inset: 0;
  background: rgb(15 23 42 / 0.48);
}

.admin-modal-panel {
  position: relative;
  display: flex;
  width: min(100%, var(--modal-width, 640px));
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 24px 70px rgb(15 23 42 / 0.24);
}

.admin-modal-header,
.admin-modal-footer {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 22px;
}

.admin-modal-header {
  justify-content: space-between;
  border-bottom: 1px solid #f1f5f9;
}

.admin-modal-footer {
  justify-content: flex-end;
  border-top: 1px solid #f1f5f9;
}

.admin-modal-title {
  color: #0f172a;
  font-size: 16px;
  font-weight: 600;
}

.admin-modal-description {
  margin-top: 3px;
  color: #64748b;
  font-size: 13px;
}

.admin-modal-close {
  display: inline-flex;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: #94a3b8;
  transition: 150ms ease;
}

.admin-modal-close:hover {
  background: #f1f5f9;
  color: #334155;
}

.admin-modal-body {
  min-height: 0;
  overflow: auto;
  padding: 22px;
}

.admin-modal-enter-active,
.admin-modal-leave-active {
  transition: opacity 150ms ease;
}

.admin-modal-enter-active .admin-modal-panel,
.admin-modal-leave-active .admin-modal-panel {
  transition: transform 150ms ease;
}

.admin-modal-enter-from,
.admin-modal-leave-to {
  opacity: 0;
}

.admin-modal-enter-from .admin-modal-panel,
.admin-modal-leave-to .admin-modal-panel {
  transform: translateY(8px) scale(0.985);
}

@media (max-width: 640px) {
  .admin-modal-root {
    padding: 12px;
  }

  .admin-modal-panel {
    max-height: calc(100vh - 24px) !important;
  }
}
</style>
