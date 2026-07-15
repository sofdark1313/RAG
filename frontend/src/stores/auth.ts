import { defineStore } from "pinia";

import { getCurrentUser, login as loginRequest, logout as logoutRequest } from "@/services/authService";
import type { CurrentUser, User } from "@/types";
import { setAuthToken } from "@/services/api";
import { storage } from "@/utils/storage";
import { toast } from "@/utils/toast";

interface AuthState {
  user: CurrentUser | null;
  token: string | null;
  initialized: boolean;
}

export const useAuthStore = defineStore("auth", {
  state: (): AuthState => ({
    user: storage.getUser(),
    token: storage.getToken(),
    initialized: false
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token && state.user),
    isAdmin: (state) => state.user?.role === "admin"
  },
  actions: {
    async login(username: string, password: string) {
      const user = (await loginRequest(username, password)) as User;
      this.user = user;
      this.token = user.token;
      storage.setUser(user);
      storage.setToken(user.token);
      setAuthToken(user.token);
      toast.success("登录成功");
    },
    async logout() {
      try {
        await logoutRequest();
      } catch {
        // Local logout should still proceed when the server is unreachable.
      } finally {
        this.user = null;
        this.token = null;
        storage.clearAuth();
        setAuthToken(null);
      }
    },
    async checkAuth() {
      const token = storage.getToken();
      const cachedUser = storage.getUser();
      if (!token || !cachedUser) {
        this.user = null;
        this.token = null;
        this.initialized = true;
        return;
      }

      this.token = token;
      this.user = cachedUser;
      setAuthToken(token);

      try {
        const currentUser = await getCurrentUser();
        this.user = { ...cachedUser, ...currentUser };
        storage.setUser({ ...cachedUser, ...currentUser, token });
      } catch {
        storage.clearAuth();
        this.user = null;
        this.token = null;
        setAuthToken(null);
      } finally {
        this.initialized = true;
      }
    }
  }
});
