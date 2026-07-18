<template>
  <div class="admin-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">用户管理</h1>
        <p class="admin-page-subtitle">管理后台账号与角色权限</p>
      </div>
      <div class="admin-page-actions">
        <input
          v-model="searchInput"
          class="ui-input h-10 w-[220px] border px-3 text-sm"
          placeholder="搜索用户名或角色"
        />
        <button
          class="ui-button h-10 border px-4 text-sm"
          data-variant="outline"
          type="button"
          @click="handleSearch"
        >
          搜索
        </button>
        <button
          class="ui-button h-10 border px-4 text-sm"
          data-variant="outline"
          type="button"
          @click="handleRefresh"
        >
          <RefreshCw :size="16" />
          刷新
        </button>
        <button
          class="ui-button admin-primary-gradient h-10 px-4 text-sm"
          type="button"
          @click="openCreateDialog"
        >
          <UserPlus :size="16" />
          新增用户
        </button>
      </div>
    </header>

    <section class="ui-card">
      <div class="ui-card-content px-6 pt-6">
        <div v-if="loading" class="py-8 text-center text-muted-foreground">加载中...</div>
        <div v-else-if="users.length === 0" class="py-8 text-center text-muted-foreground">
          暂无用户
        </div>
        <div v-else class="ui-table-wrap">
          <table class="ui-table min-w-[860px] w-full">
            <thead class="ui-table-header">
              <tr class="ui-table-row">
                <th class="ui-table-head w-[240px] text-left">用户</th>
                <th class="ui-table-head w-[140px] text-left">角色</th>
                <th class="ui-table-head w-[180px] text-left">创建时间</th>
                <th class="ui-table-head w-[180px] text-left">更新时间</th>
                <th class="ui-table-head w-[160px] text-left">操作</th>
              </tr>
            </thead>
            <tbody class="ui-table-body">
              <tr v-for="user in users" :key="user.id" class="ui-table-row">
                <td class="ui-table-cell">
                  <div class="flex items-center gap-3">
                    <span class="user-avatar">
                      <img
                        v-if="user.avatar?.trim() && !avatarErrors.has(user.id)"
                        :alt="user.username || '用户'"
                        class="h-full w-full rounded-full object-cover"
                        :src="user.avatar.trim()"
                        @error="avatarErrors.add(user.id)"
                      />
                      <span v-else class="select-none">{{
                        avatarFallback(user.username || "用户")
                      }}</span>
                    </span>
                    <span>
                      <span class="block font-medium text-slate-900">{{
                        user.username || "-"
                      }}</span>
                      <span v-if="isProtectedAdmin(user)" class="block text-xs text-slate-400"
                        >默认管理员</span
                      >
                    </span>
                  </div>
                </td>
                <td class="ui-table-cell">
                  <span
                    class="ui-badge inline-flex border px-2 py-1"
                    :data-variant="user.role === 'admin' ? 'default' : 'secondary'"
                  >
                    {{ user.role === "admin" ? "管理员" : "成员" }}
                  </span>
                </td>
                <td
                  class="ui-table-cell cursor-default truncate text-sm tabular-nums"
                  :title="formatTimeTooltip(user.createTime)"
                >
                  {{ formatTime(user.createTime) }}
                </td>
                <td
                  class="ui-table-cell cursor-default truncate text-sm tabular-nums"
                  :title="formatTimeTooltip(user.updateTime)"
                >
                  {{ formatTime(user.updateTime) }}
                </td>
                <td class="ui-table-cell">
                  <div class="flex gap-2">
                    <button
                      class="ui-button h-8 border px-3 text-xs"
                      data-variant="outline"
                      type="button"
                      :disabled="isProtectedAdmin(user)"
                      @click="openEditDialog(user)"
                    >
                      <Pencil :size="16" />
                      编辑
                    </button>
                    <button
                      class="ui-button h-8 px-3 text-xs text-red-600 hover:text-red-600"
                      data-variant="ghost"
                      type="button"
                      :disabled="isProtectedAdmin(user)"
                      @click="deleteTarget = user"
                    >
                      <Trash2 :size="16" />
                      删除
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <div
      v-if="pageData"
      class="flex flex-wrap items-center justify-between gap-2 text-sm text-slate-500"
    >
      <span>共 {{ pageData.total }} 条</span>
      <div class="flex items-center gap-2">
        <button
          class="ui-button h-8 border px-3 text-xs"
          data-variant="outline"
          type="button"
          :disabled="pageData.current <= 1"
          @click="pageNo = Math.max(1, pageNo - 1)"
        >
          上一页
        </button>
        <span>{{ pageData.current }} / {{ pageData.pages }}</span>
        <button
          class="ui-button h-8 border px-3 text-xs"
          data-variant="outline"
          type="button"
          :disabled="pageData.current >= pageData.pages"
          @click="pageNo = Math.min(pageData.pages || 1, pageNo + 1)"
        >
          下一页
        </button>
      </div>
    </div>

    <div
      v-if="deleteTarget"
      class="admin-modal-backdrop"
      role="presentation"
      @mousedown.self="deleteTarget = null"
    >
      <section
        aria-labelledby="user-delete-title"
        aria-modal="true"
        class="admin-modal max-w-[440px]"
        role="alertdialog"
      >
        <div class="admin-modal-header">
          <h2 id="user-delete-title" class="text-lg font-semibold text-slate-900">确认删除</h2>
          <p class="mt-2 text-sm text-slate-500">
            此操作将永久删除该用户，无法恢复。确定要继续吗？
          </p>
        </div>
        <div class="admin-modal-footer">
          <button
            class="ui-button h-10 border px-4 text-sm"
            data-variant="outline"
            type="button"
            @click="deleteTarget = null"
          >
            取消
          </button>
          <button
            class="ui-button h-10 bg-red-600 px-4 text-sm text-white hover:bg-red-700"
            type="button"
            @click="handleDelete"
          >
            删除
          </button>
        </div>
      </section>
    </div>

    <div
      v-if="dialogState.open"
      class="admin-modal-backdrop"
      role="presentation"
      @mousedown.self="closeDialog"
    >
      <section
        :aria-labelledby="dialogTitleId"
        aria-modal="true"
        class="admin-modal max-w-[420px]"
        role="dialog"
      >
        <div class="admin-modal-header">
          <h2 :id="dialogTitleId" class="text-lg font-semibold text-slate-900">
            {{ dialogState.mode === "create" ? "新增用户" : "编辑用户" }}
          </h2>
          <p class="mt-1 text-sm text-slate-500">
            {{
              dialogState.mode === "create" ? "配置账号基本信息" : "更新账号信息，密码留空则不修改"
            }}
          </p>
          <button class="admin-modal-close" title="关闭" type="button" @click="closeDialog">
            <X :size="18" />
          </button>
        </div>
        <div class="space-y-3 px-6 py-5">
          <label class="admin-form-field">
            <span>用户名</span>
            <input
              v-model="form.username"
              autofocus
              class="ui-input h-10 border px-3 text-sm"
              placeholder="请输入用户名"
            />
          </label>
          <label class="admin-form-field">
            <span>密码</span>
            <input
              v-model="form.password"
              class="ui-input h-10 border px-3 text-sm"
              :placeholder="dialogState.mode === 'create' ? '设置初始密码' : '留空则不修改'"
              type="password"
            />
          </label>
          <label class="admin-form-field">
            <span>角色</span>
            <select v-model="form.role" class="ui-input h-10 border px-3 text-sm">
              <option v-for="option in roleOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label class="admin-form-field">
            <span>头像</span>
            <input
              v-model="form.avatar"
              class="ui-input h-10 border px-3 text-sm"
              placeholder="可选，填写头像 URL"
            />
          </label>
        </div>
        <div class="admin-modal-footer">
          <button
            class="ui-button h-10 border px-4 text-sm"
            data-variant="outline"
            type="button"
            @click="closeDialog"
          >
            取消
          </button>
          <button
            class="ui-button h-10 px-4 text-sm"
            data-variant="default"
            type="button"
            @click="handleSave"
          >
            <Plus v-if="dialogState.mode === 'create'" :size="16" />
            <Pencil v-else :size="16" />
            {{ dialogState.mode === "create" ? "创建" : "保存" }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Pencil, Plus, RefreshCw, Trash2, UserPlus, X } from "lucide-vue-next";
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";

import {
  createUser,
  deleteUser,
  getUsersPage,
  updateUser,
  type PageResult,
  type UserCreatePayload,
  type UserItem,
  type UserUpdatePayload
} from "@/services/userService";
import { getErrorMessage } from "@/utils/error";
import { formatRelativeTime, formatTooltipTime } from "@/utils/time";
import { toast } from "@/utils/toast";

const PAGE_SIZE = 10;
const roleOptions = [
  { value: "admin", label: "管理员" },
  { value: "user", label: "成员" }
];

const pageData = ref<PageResult<UserItem> | null>(null);
const loading = ref(true);
const searchInput = ref("");
const keyword = ref("");
const pageNo = ref(1);
const deleteTarget = ref<UserItem | null>(null);
const avatarErrors = reactive(new Set<string>());
const dialogState = reactive<{
  open: boolean;
  mode: "create" | "edit";
  user: UserItem | null;
}>({ open: false, mode: "create", user: null });
const form = reactive({ username: "", password: "", role: "user", avatar: "" });

const users = computed(() => pageData.value?.records || []);
const dialogTitleId = computed(() => `user-${dialogState.mode}-title`);

function resetForm() {
  form.username = "";
  form.password = "";
  form.role = "user";
  form.avatar = "";
}

async function loadUsers(current = pageNo.value, name = keyword.value) {
  try {
    loading.value = true;
    pageData.value = await getUsersPage(current, PAGE_SIZE, name || undefined);
  } catch (error) {
    toast.error(getErrorMessage(error, "加载用户列表失败"));
    console.error(error);
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pageNo.value = 1;
  keyword.value = searchInput.value.trim();
}

async function handleRefresh() {
  pageNo.value = 1;
  await loadUsers(1, keyword.value);
}

async function handleDelete() {
  if (!deleteTarget.value) return;

  try {
    await deleteUser(deleteTarget.value.id);
    toast.success("删除成功");
    pageNo.value = 1;
    await loadUsers(1, keyword.value);
  } catch (error) {
    toast.error(getErrorMessage(error, "删除失败"));
    console.error(error);
  } finally {
    deleteTarget.value = null;
  }
}

function openCreateDialog() {
  resetForm();
  dialogState.open = true;
  dialogState.mode = "create";
  dialogState.user = null;
}

function openEditDialog(user: UserItem) {
  form.username = user.username || "";
  form.password = "";
  form.role = user.role || "user";
  form.avatar = user.avatar || "";
  dialogState.open = true;
  dialogState.mode = "edit";
  dialogState.user = user;
}

function closeDialog() {
  dialogState.open = false;
  dialogState.mode = "create";
  dialogState.user = null;
  resetForm();
}

async function handleSave() {
  const trimmedUsername = form.username.trim();
  const trimmedPassword = form.password.trim();

  if (!trimmedUsername) {
    toast.error("请输入用户名");
    return;
  }

  try {
    if (dialogState.mode === "create") {
      if (!trimmedPassword) {
        toast.error("请输入初始密码");
        return;
      }
      const payload: UserCreatePayload = {
        username: trimmedUsername,
        password: trimmedPassword,
        role: form.role || "user",
        avatar: form.avatar.trim() || undefined
      };
      await createUser(payload);
      toast.success("创建成功");
      pageNo.value = 1;
      await loadUsers(1, keyword.value);
    } else if (dialogState.user) {
      const payload: UserUpdatePayload = {
        username: trimmedUsername,
        role: form.role || "user",
        avatar: form.avatar.trim() || undefined,
        password: trimmedPassword || undefined
      };
      await updateUser(dialogState.user.id, payload);
      toast.success("更新成功");
      await loadUsers(pageNo.value, keyword.value);
    }
    closeDialog();
  } catch (error) {
    toast.error(getErrorMessage(error, "保存失败"));
    console.error(error);
  }
}

function isProtectedAdmin(user: UserItem) {
  return user.username === "admin";
}

function avatarFallback(name: string) {
  if (!name) return "?";
  return name
    .trim()
    .split(" ")
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
}

function formatTime(value?: string | null) {
  return formatRelativeTime(value);
}

function formatTimeTooltip(value?: string | null) {
  return value ? formatTooltipTime(value) : "";
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key !== "Escape") return;
  if (deleteTarget.value) {
    deleteTarget.value = null;
  } else if (dialogState.open) {
    closeDialog();
  }
}

watch([pageNo, keyword], () => loadUsers(), { immediate: true });
onMounted(() => window.addEventListener("keydown", handleKeydown));
onBeforeUnmount(() => window.removeEventListener("keydown", handleKeydown));
</script>

<style scoped>
.admin-modal-backdrop {
  @apply fixed inset-0 z-[80] flex items-center justify-center bg-slate-900/45 p-4;
}

.admin-modal {
  @apply max-h-[calc(100vh-2rem)] w-full overflow-y-auto rounded-lg border border-slate-200 bg-white p-0 shadow-xl;
}

.admin-modal-header {
  @apply relative border-b border-slate-100 px-6 py-5 pr-14;
}

.admin-modal-footer {
  @apply flex justify-end gap-2 border-t border-slate-100 px-6 py-4;
}

.admin-form-field {
  @apply grid gap-2 text-sm font-medium text-slate-700;
}

.admin-modal-close {
  @apply absolute right-4 top-4 inline-flex h-8 w-8 items-center justify-center rounded-md text-slate-400 transition hover:bg-slate-100 hover:text-slate-700;
}

.ui-button {
  @apply inline-flex items-center justify-center gap-2;
}

.user-avatar {
  @apply inline-flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full border border-slate-200 bg-indigo-50 text-xs font-semibold text-indigo-600;
}

button:disabled {
  @apply cursor-not-allowed opacity-50;
}
</style>
