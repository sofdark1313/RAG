import { create } from "zustand";

import {
  storage,
  type StoredThemePreference
} from "@/utils/storage";

export type ThemeMode = "light" | "dark";
export type ThemePreference = StoredThemePreference;

interface ThemeState {
  preference: ThemePreference;
  resolvedTheme: ThemeMode;
  /** @deprecated Use resolvedTheme instead. */
  theme: ThemeMode;
  setPreference: (preference: ThemePreference) => void;
  toggleTheme: () => void;
  initialize: () => void;
}

const SYSTEM_THEME_QUERY = "(prefers-color-scheme: dark)";

let mediaQuery: MediaQueryList | null = null;
let mediaListener: ((event: MediaQueryListEvent) => void) | null = null;

function applyTheme(theme: ThemeMode) {
  document.documentElement.classList.toggle("dark", theme === "dark");
  document.documentElement.style.colorScheme = theme;
}

function resolveTheme(matches: boolean): ThemeMode {
  return matches ? "dark" : "light";
}

function stopSystemListener() {
  if (mediaQuery && mediaListener) {
    mediaQuery.removeEventListener("change", mediaListener);
  }
  mediaQuery = null;
  mediaListener = null;
}

export const useThemeStore = create<ThemeState>((set, get) => {
  const setResolvedTheme = (resolvedTheme: ThemeMode) => {
    applyTheme(resolvedTheme);
    set({ resolvedTheme, theme: resolvedTheme });
  };

  const startSystemListener = () => {
    if (!mediaQuery || !mediaListener) {
      mediaQuery = window.matchMedia(SYSTEM_THEME_QUERY);
      mediaListener = (event) => {
        setResolvedTheme(resolveTheme(event.matches));
      };
      mediaQuery.addEventListener("change", mediaListener);
    }

    return resolveTheme(mediaQuery.matches);
  };

  const setPreference = (preference: ThemePreference) => {
    storage.setTheme(preference);

    if (preference === "system") {
      const resolvedTheme = startSystemListener();
      applyTheme(resolvedTheme);
      set({ preference, resolvedTheme, theme: resolvedTheme });
      return;
    }

    stopSystemListener();
    applyTheme(preference);
    set({ preference, resolvedTheme: preference, theme: preference });
  };

  return {
    preference: "system",
    resolvedTheme: "light",
    theme: "light",
    setPreference,
    toggleTheme: () => {
      const next = get().resolvedTheme === "light" ? "dark" : "light";
      setPreference(next);
    },
    initialize: () => {
      const preference = storage.getTheme() ?? "system";

      if (preference === "system") {
        const resolvedTheme = startSystemListener();
        applyTheme(resolvedTheme);
        set({ preference, resolvedTheme, theme: resolvedTheme });
        return;
      }

      stopSystemListener();
      applyTheme(preference);
      set({ preference, resolvedTheme: preference, theme: preference });
    }
  };
});
