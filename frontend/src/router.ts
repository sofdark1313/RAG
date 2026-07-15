import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";

import { useAuthStore } from "@/stores/auth";

const adminChildren: RouteRecordRaw[] = [
  {
    path: "",
    redirect: "/admin/dashboard"
  },
  {
    path: "dashboard",
    name: "admin-dashboard",
    component: () => import("@/pages/admin/DashboardPage.vue"),
    meta: { title: "数据概览", subtitle: "查看问答、知识库和系统运行概况" }
  },
  {
    path: "knowledge",
    name: "admin-knowledge",
    component: () => import("@/pages/admin/GenericAdminPage.vue"),
    meta: { title: "知识库管理", subtitle: "维护知识库、文档和分片内容" }
  },
  {
    path: "knowledge/:kbId",
    name: "admin-knowledge-documents",
    component: () => import("@/pages/admin/GenericAdminPage.vue"),
    meta: { title: "文档管理", subtitle: "查看知识库文档、解析状态和更新计划" }
  },
  {
    path: "knowledge/:kbId/docs/:docId",
    name: "admin-knowledge-chunks",
    component: () => import("@/pages/admin/GenericAdminPage.vue"),
    meta: { title: "分片管理", subtitle: "检查文档分片、向量状态和召回文本" }
  },
  {
    path: "intent-tree",
    name: "admin-intent-tree",
    component: () => import("@/pages/admin/GenericAdminPage.vue"),
    meta: { title: "意图树", subtitle: "配置多级意图与问答路由策略" }
  },
  {
    path: "intent-list",
    name: "admin-intent-list",
    component: () => import("@/pages/admin/GenericAdminPage.vue"),
    meta: { title: "意图列表", subtitle: "维护意图节点、关键词和命中规则" }
  },
  {
    path: "intent-list/:id/edit",
    name: "admin-intent-edit",
    component: () => import("@/pages/admin/GenericAdminPage.vue"),
    meta: { title: "编辑意图", subtitle: "调整意图说明、样例问题与召回配置" }
  },
  {
    path: "ingestion",
    name: "admin-ingestion",
    component: () => import("@/pages/admin/GenericAdminPage.vue"),
    meta: { title: "数据接入", subtitle: "跟踪接入流水线、解析节点和失败重试" }
  },
  {
    path: "traces",
    name: "admin-traces",
    component: () => import("@/pages/admin/GenericAdminPage.vue"),
    meta: { title: "RAG Trace", subtitle: "分析检索、重排、生成和工具调用链路" }
  },
  {
    path: "traces/:traceId",
    name: "admin-trace-detail",
    component: () => import("@/pages/admin/GenericAdminPage.vue"),
    meta: { title: "Trace 详情", subtitle: "查看单次问答的完整执行路径" }
  },
  {
    path: "settings",
    name: "admin-settings",
    component: () => import("@/pages/admin/GenericAdminPage.vue"),
    meta: { title: "系统设置", subtitle: "管理模型、检索、记忆和默认参数" }
  },
  {
    path: "sample-questions",
    name: "admin-sample-questions",
    component: () => import("@/pages/admin/GenericAdminPage.vue"),
    meta: { title: "样例问题", subtitle: "维护聊天首页推荐问题" }
  },
  {
    path: "mappings",
    name: "admin-mappings",
    component: () => import("@/pages/admin/GenericAdminPage.vue"),
    meta: { title: "术语映射", subtitle: "配置同义词、业务词和查询改写提示" }
  },
  {
    path: "users",
    name: "admin-users",
    component: () => import("@/pages/admin/GenericAdminPage.vue"),
    meta: { title: "用户管理", subtitle: "管理账号、角色和访问权限" }
  }
];

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      redirect: "/chat"
    },
    {
      path: "/login",
      name: "login",
      component: () => import("@/pages/LoginPage.vue"),
      meta: { public: true, guestOnly: true }
    },
    {
      path: "/chat/:sessionId?",
      name: "chat",
      component: () => import("@/pages/ChatPage.vue"),
      meta: { requiresAuth: true }
    },
    {
      path: "/admin",
      component: () => import("@/pages/admin/AdminLayout.vue"),
      meta: { requiresAuth: true, requiresAdmin: true },
      children: adminChildren
    },
    {
      path: "/:pathMatch(.*)*",
      name: "not-found",
      component: () => import("@/pages/NotFoundPage.vue"),
      meta: { public: true }
    }
  ]
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (!auth.initialized) {
    await auth.checkAuth();
  }

  if (to.meta.guestOnly && auth.isAuthenticated) {
    return "/chat";
  }

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { path: "/login", query: { redirect: to.fullPath } };
  }

  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return "/chat";
  }

  return true;
});
