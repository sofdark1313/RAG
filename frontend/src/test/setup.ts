import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";

import { useThemeStore } from "@/stores/themeStore";

afterEach(() => {
  cleanup();
  useThemeStore.getState().setPreference("light");
  useThemeStore.setState({ preference: "system", resolvedTheme: "light" });
  vi.unstubAllGlobals();
  window.localStorage.clear();
  document.documentElement.className = "";
  document.documentElement.style.colorScheme = "";
});
