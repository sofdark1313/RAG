import type { User } from "@/types";

export type StoredThemePreference = "system" | "light" | "dark";

const TOKEN_KEY = "ragent_token";
const USER_KEY = "ragent_user";
const THEME_KEY = "ragent_theme";

function safeGet(key: string) {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function safeSet(key: string, value: string) {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    return;
  }
}

function safeRemove(key: string) {
  try {
    window.localStorage.removeItem(key);
  } catch {
    return;
  }
}

export const storage = {
  getToken(): string | null {
    return safeGet(TOKEN_KEY);
  },
  setToken(token: string) {
    safeSet(TOKEN_KEY, token);
  },
  clearToken() {
    safeRemove(TOKEN_KEY);
  },
  getUser(): User | null {
    const raw = safeGet(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as User;
    } catch {
      return null;
    }
  },
  setUser(user: User) {
    safeSet(USER_KEY, JSON.stringify(user));
  },
  clearUser() {
    safeRemove(USER_KEY);
  },
  clearAuth() {
    safeRemove(TOKEN_KEY);
    safeRemove(USER_KEY);
  },
  getTheme(): StoredThemePreference | null {
    const theme = safeGet(THEME_KEY);
    return theme === "system" || theme === "light" || theme === "dark"
      ? theme
      : null;
  },
  setTheme(theme: StoredThemePreference) {
    safeSet(THEME_KEY, theme);
  }
};
