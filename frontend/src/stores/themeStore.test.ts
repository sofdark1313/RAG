import { afterEach, describe, expect, it, vi } from "vitest";

import { useThemeStore } from "@/stores/themeStore";
import { storage } from "@/utils/storage";

type MediaQueryChangeListener = (event: MediaQueryListEvent) => void;

function createMediaQuery(initialMatches: boolean) {
  const listeners = new Set<MediaQueryChangeListener>();
  const addEventListener = vi.fn(
    (_type: string, listener: MediaQueryChangeListener) => {
      listeners.add(listener);
    }
  );
  const removeEventListener = vi.fn(
    (_type: string, listener: MediaQueryChangeListener) => {
      listeners.delete(listener);
    }
  );
  const mediaQuery = {
    matches: initialMatches,
    media: "(prefers-color-scheme: dark)",
    onchange: null,
    addEventListener,
    removeEventListener,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(() => true)
  } as unknown as MediaQueryList;

  return {
    addEventListener,
    mediaQuery,
    removeEventListener,
    emit(matches: boolean) {
      (mediaQuery as { matches: boolean }).matches = matches;
      const event = {
        matches,
        media: mediaQuery.media
      } as MediaQueryListEvent;

      listeners.forEach((listener) => listener(event));
    }
  };
}

function useSystemTheme(initialMatches: boolean) {
  const fake = createMediaQuery(initialMatches);
  vi.stubGlobal("matchMedia", vi.fn(() => fake.mediaQuery));
  return fake;
}

afterEach(() => {
  const setPreference = Reflect.get(
    useThemeStore.getState(),
    "setPreference"
  );
  if (typeof setPreference === "function") {
    setPreference("light");
  }
  vi.unstubAllGlobals();
});

describe("theme store", () => {
  it("defaults to system and applies a dark system theme", () => {
    useSystemTheme(true);

    useThemeStore.getState().initialize();

    const state = useThemeStore.getState();
    expect(state.preference).toBe("system");
    expect(state.resolvedTheme).toBe("dark");
    expect(document.documentElement).toHaveClass("dark");
    expect(document.documentElement.style.colorScheme).toBe("dark");
  });

  it("lets a manual light preference override and persist over dark system", () => {
    useSystemTheme(true);
    useThemeStore.getState().initialize();

    useThemeStore.getState().setPreference("light");

    const state = useThemeStore.getState();
    expect(state.preference).toBe("light");
    expect(state.resolvedTheme).toBe("light");
    expect(window.localStorage.getItem("ragent_theme")).toBe("light");
    expect(document.documentElement).not.toHaveClass("dark");
    expect(document.documentElement.style.colorScheme).toBe("light");
  });

  it("registers a system color-scheme change listener", () => {
    const fake = useSystemTheme(false);

    useThemeStore.getState().initialize();

    expect(fake.addEventListener).toHaveBeenCalledOnce();
    expect(fake.addEventListener).toHaveBeenCalledWith(
      "change",
      expect.any(Function)
    );
  });

  it("updates the resolved theme and DOM when the system theme changes", () => {
    const fake = useSystemTheme(true);
    useThemeStore.getState().initialize();

    fake.emit(false);

    expect(useThemeStore.getState().resolvedTheme).toBe("light");
    expect(document.documentElement).not.toHaveClass("dark");
    expect(document.documentElement.style.colorScheme).toBe("light");
  });

  it("keeps system listening idempotent and removes it for manual themes", () => {
    const fake = useSystemTheme(false);

    useThemeStore.getState().initialize();
    useThemeStore.getState().initialize();

    expect(fake.addEventListener).toHaveBeenCalledOnce();

    useThemeStore.getState().setPreference("dark");

    expect(fake.removeEventListener).toHaveBeenCalledOnce();
    expect(fake.removeEventListener).toHaveBeenCalledWith(
      "change",
      expect.any(Function)
    );
  });

  it("toggles from the resolved system theme into a manual preference", () => {
    useSystemTheme(true);
    useThemeStore.getState().initialize();

    useThemeStore.getState().toggleTheme();

    const state = useThemeStore.getState();
    expect(state.preference).toBe("light");
    expect(state.resolvedTheme).toBe("light");
    expect(window.localStorage.getItem("ragent_theme")).toBe("light");
  });

  it("rejects invalid stored theme preferences", () => {
    window.localStorage.setItem("ragent_theme", "sepia");

    expect(storage.getTheme()).toBeNull();
  });
});
