<template>
  <div ref="rootRef" class="theme-switcher">
    <button type="button" class="theme-toggle" aria-label="切换主题" @click="menuOpen = !menuOpen">
      <Monitor v-if="preference === 'system'" :size="16" />
      <Moon v-else-if="resolvedTheme === 'dark'" :size="16" />
      <Sun v-else :size="16" />
    </button>

    <div v-if="menuOpen" class="theme-menu" role="menu" aria-label="主题选项">
      <button
        v-for="option in themeOptions"
        :key="option.value"
        type="button"
        role="menuitemradio"
        class="theme-menu__item"
        :aria-checked="preference === option.value"
        @click="setPreference(option.value)"
      >
        <component :is="option.icon" class="theme-menu__icon" />
        <span class="theme-menu__label">{{ option.label }}</span>
        <Check class="theme-menu__check" :class="{ 'is-hidden': preference !== option.value }" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Check, Monitor, Moon, Sun } from "lucide-vue-next";
import { onBeforeUnmount, onMounted, ref } from "vue";

import { storage } from "@/utils/storage";

type ThemeMode = "light" | "dark";
type ThemePreference = ThemeMode | "system";

const themeOptions = [
  { value: "system" as const, label: "跟随系统", icon: Monitor },
  { value: "light" as const, label: "亮色", icon: Sun },
  { value: "dark" as const, label: "暗色", icon: Moon }
];

const rootRef = ref<HTMLElement | null>(null);
const menuOpen = ref(false);
const preference = ref<ThemePreference>("system");
const resolvedTheme = ref<ThemeMode>("light");
let mediaQuery: MediaQueryList | null = null;

function resolveSystemTheme(): ThemeMode {
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function applyTheme(theme: ThemeMode) {
  resolvedTheme.value = theme;
  document.documentElement.classList.toggle("dark", theme === "dark");
  document.documentElement.style.colorScheme = theme;
}

function handleSystemThemeChange(event: MediaQueryListEvent) {
  if (preference.value === "system") applyTheme(event.matches ? "dark" : "light");
}

function setPreference(next: ThemePreference) {
  preference.value = next;
  storage.setTheme(next);
  applyTheme(next === "system" ? resolveSystemTheme() : next);
  menuOpen.value = false;
}

function handleDocumentPointerDown(event: PointerEvent) {
  if (rootRef.value && !rootRef.value.contains(event.target as Node)) menuOpen.value = false;
}

function handleDocumentKeyDown(event: KeyboardEvent) {
  if (event.key === "Escape") menuOpen.value = false;
}

onMounted(() => {
  const stored = storage.getTheme();
  preference.value = stored === "light" || stored === "dark" || stored === "system" ? stored : "system";
  applyTheme(preference.value === "system" ? resolveSystemTheme() : preference.value);
  mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
  mediaQuery.addEventListener("change", handleSystemThemeChange);
  document.addEventListener("pointerdown", handleDocumentPointerDown);
  document.addEventListener("keydown", handleDocumentKeyDown);
});

onBeforeUnmount(() => {
  mediaQuery?.removeEventListener("change", handleSystemThemeChange);
  document.removeEventListener("pointerdown", handleDocumentPointerDown);
  document.removeEventListener("keydown", handleDocumentKeyDown);
});
</script>
