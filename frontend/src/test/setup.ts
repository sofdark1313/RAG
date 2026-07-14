import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

import { useThemeStore } from "@/stores/themeStore";

afterEach(() => {
  cleanup();
  useThemeStore.setState({ theme: "light" });
  window.localStorage.clear();
  document.documentElement.className = "";
  document.documentElement.style.colorScheme = "";
});
