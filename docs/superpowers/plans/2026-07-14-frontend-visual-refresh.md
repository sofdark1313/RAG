# Ragent Frontend Visual Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Ragent 用户端改造成 GPT 式精简聊天体验，将内部管理后台改造成朴素高效的管理台，并为所有页面提供可持久化的亮色、暗色和跟随系统主题。

**Architecture:** 先建立可测试的三态主题基础和语义设计令牌，再分别改造聊天端、登录页与后台外壳。现有路由、Zustand 业务 Store、Service 和后端接口保持不变；视觉改造通过聚焦的组件标记和分层样式文件落地，避免继续扩大已有的 `globals.css`。

**Tech Stack:** React 18、TypeScript、Vite 5、Tailwind CSS 3、Zustand、Radix UI、Vitest、Testing Library、Lucide React。

---

## 文件结构

### 新增文件

- `frontend/vitest.config.ts`：Vitest、jsdom 和路径别名配置。
- `frontend/src/test/setup.ts`：Testing Library 清理与 DOM 断言初始化。
- `frontend/src/stores/themeStore.test.ts`：三态主题解析、持久化和系统主题监听测试。
- `frontend/src/components/common/ThemeToggle.tsx`：全站复用的主题选择控件。
- `frontend/src/components/common/ThemeToggle.test.tsx`：主题选择控件交互测试。
- `frontend/src/components/layout/Header.test.tsx`：聊天顶部栏内容与可访问性测试。
- `frontend/src/components/chat/ChatInput.test.tsx`：输入、发送、停止生成与键盘行为测试。
- `frontend/src/pages/LoginPage.test.tsx`：登录校验与主题入口测试。
- `frontend/src/pages/admin/adminNavigation.ts`：后台菜单和面包屑的纯数据配置。
- `frontend/src/pages/admin/adminNavigation.test.ts`：后台菜单路径完整性测试。
- `frontend/src/styles/theme.css`：亮暗主题语义令牌和基础页面色彩。
- `frontend/src/styles/chat.css`：聊天端、登录页和 Markdown 的页面样式，在聊天外壳任务中创建。
- `frontend/src/styles/admin.css`：内部管理后台的朴素布局与控件样式，在后台外壳任务中创建。

### 重点修改文件

- `frontend/package.json`、`frontend/package-lock.json`：增加测试命令和测试依赖。
- `frontend/src/main.tsx`：按顺序加载新样式并初始化主题。
- `frontend/src/utils/storage.ts`：为主题持久化增加类型约束。
- `frontend/src/stores/themeStore.ts`：从二态主题改为 `system | light | dark`。
- `frontend/src/styles/globals.css`：保留 Tailwind、重置与现有专项样式，移除冲突的根主题定义。
- `frontend/tailwind.config.cjs`：字体、颜色和阴影改为语义令牌。
- `frontend/src/components/layout/MainLayout.tsx`、`Header.tsx`、`Sidebar.tsx`：改造聊天端外壳。
- `frontend/src/pages/ChatPage.tsx`：统一聊天内容与输入区布局。
- `frontend/src/components/chat/WelcomeScreen.tsx`、`MessageList.tsx`、`MessageItem.tsx`、`ThinkingIndicator.tsx`、`ChatInput.tsx`、`MarkdownRenderer.tsx`：精简聊天内容层级。
- `frontend/src/pages/LoginPage.tsx`、`frontend/src/pages/NotFoundPage.tsx`：统一登录和异常页面。
- `frontend/src/pages/admin/AdminLayout.tsx`：改造成传统内部管理台并接入主题切换。
- `frontend/src/pages/admin/dashboard/DashboardPage.tsx`、`frontend/src/components/admin/SimpleLineChart.tsx`：去除宣传型卡片和装饰性图表效果。
- `frontend/src/pages/admin/traces/RagTracePage.tsx`、`RagTraceDetailPage.tsx`、`components/PageHeader.tsx`、`components/StatCard.tsx`、`components/FilterBar.tsx`、`components/RunsTable.tsx`：保留链路数据，移除英雄区、渐变与过度卡片化。

---

### Task 1: 建立前端测试基础

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Create: `frontend/vitest.config.ts`
- Create: `frontend/src/test/setup.ts`

- [ ] **Step 1: 安装测试依赖并添加脚本**

Run:

```powershell
cd frontend
npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom
```

将 `package.json` 的脚本补充为：

```json
{
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview",
    "lint": "eslint . --ext .ts,.tsx --max-warnings 0",
    "test": "vitest run",
    "test:watch": "vitest"
  }
}
```

Expected: `package-lock.json` 更新，命令退出码为 0。

- [ ] **Step 2: 创建 Vitest 配置**

Create `frontend/vitest.config.ts`:

```ts
import path from "node:path";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src")
    }
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: true
  }
});
```

Create `frontend/src/test/setup.ts`:

```ts
import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

afterEach(() => {
  cleanup();
  window.localStorage.clear();
  document.documentElement.className = "";
  document.documentElement.style.colorScheme = "";
});
```

- [ ] **Step 3: 运行空测试套件**

Run: `npm test -- --passWithNoTests`

Expected: PASS，Vitest 正常启动并提示没有测试文件。

- [ ] **Step 4: 提交测试基础**

```powershell
git add frontend/package.json frontend/package-lock.json frontend/vitest.config.ts frontend/src/test/setup.ts
git commit -m "test(frontend): add vitest foundation"
```

---

### Task 2: 实现三态主题 Store

**Files:**
- Create: `frontend/src/stores/themeStore.test.ts`
- Modify: `frontend/src/stores/themeStore.ts`
- Modify: `frontend/src/utils/storage.ts`

- [ ] **Step 1: 编写失败的主题 Store 测试**

Create `frontend/src/stores/themeStore.test.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useThemeStore } from "@/stores/themeStore";

const createMediaQuery = (matches: boolean) => ({
  matches,
  media: "(prefers-color-scheme: dark)",
  onchange: null,
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
  addListener: vi.fn(),
  removeListener: vi.fn(),
  dispatchEvent: vi.fn()
});

describe("themeStore", () => {
  beforeEach(() => {
    useThemeStore.setState({ preference: "system", resolvedTheme: "light" });
  });

  it("首次初始化时跟随系统暗色主题", () => {
    vi.stubGlobal("matchMedia", vi.fn(() => createMediaQuery(true)));

    useThemeStore.getState().initialize();

    expect(useThemeStore.getState().preference).toBe("system");
    expect(useThemeStore.getState().resolvedTheme).toBe("dark");
    expect(document.documentElement).toHaveClass("dark");
    expect(document.documentElement.style.colorScheme).toBe("dark");
  });

  it("保存用户选择并覆盖系统主题", () => {
    vi.stubGlobal("matchMedia", vi.fn(() => createMediaQuery(true)));

    useThemeStore.getState().setPreference("light");

    expect(window.localStorage.getItem("ragent_theme")).toBe("light");
    expect(useThemeStore.getState().resolvedTheme).toBe("light");
    expect(document.documentElement).not.toHaveClass("dark");
  });

  it("system 模式注册系统主题变化监听", () => {
    const mediaQuery = createMediaQuery(false);
    vi.stubGlobal("matchMedia", vi.fn(() => mediaQuery));

    useThemeStore.getState().initialize();

    expect(mediaQuery.addEventListener).toHaveBeenCalledWith("change", expect.any(Function));
  });
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- src/stores/themeStore.test.ts`

Expected: FAIL，提示 `preference`、`resolvedTheme` 或 `setPreference` 不存在。

- [ ] **Step 3: 为 storage 增加主题类型约束**

在 `frontend/src/utils/storage.ts` 顶部增加：

```ts
export type StoredThemePreference = "system" | "light" | "dark";
```

将主题方法替换为：

```ts
getTheme(): StoredThemePreference | null {
  const value = safeGet(THEME_KEY);
  return value === "system" || value === "light" || value === "dark" ? value : null;
},
setTheme(theme: StoredThemePreference) {
  safeSet(THEME_KEY, theme);
}
```

- [ ] **Step 4: 实现三态主题 Store**

Replace `frontend/src/stores/themeStore.ts` with:

```ts
import { create } from "zustand";

import { storage, type StoredThemePreference } from "@/utils/storage";

export type ThemePreference = StoredThemePreference;
export type ResolvedTheme = "light" | "dark";

interface ThemeState {
  preference: ThemePreference;
  resolvedTheme: ResolvedTheme;
  setPreference: (preference: ThemePreference) => void;
  toggleTheme: () => void;
  initialize: () => void;
}

let mediaQuery: MediaQueryList | null = null;
let mediaListener: ((event: MediaQueryListEvent) => void) | null = null;

function resolveTheme(preference: ThemePreference): ResolvedTheme {
  if (preference !== "system") return preference;
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function applyTheme(theme: ResolvedTheme) {
  document.documentElement.classList.toggle("dark", theme === "dark");
  document.documentElement.style.colorScheme = theme;
}

function stopSystemListener() {
  if (mediaQuery && mediaListener) {
    mediaQuery.removeEventListener("change", mediaListener);
  }
  mediaQuery = null;
  mediaListener = null;
}

export const useThemeStore = create<ThemeState>((set, get) => ({
  preference: "system",
  resolvedTheme: "light",
  setPreference: (preference) => {
    storage.setTheme(preference);
    stopSystemListener();
    const resolvedTheme = resolveTheme(preference);
    applyTheme(resolvedTheme);
    set({ preference, resolvedTheme });
    if (preference === "system") get().initialize();
  },
  toggleTheme: () => {
    get().setPreference(get().resolvedTheme === "light" ? "dark" : "light");
  },
  initialize: () => {
    stopSystemListener();
    const preference = storage.getTheme() ?? "system";
    const resolvedTheme = resolveTheme(preference);
    applyTheme(resolvedTheme);
    set({ preference, resolvedTheme });
    if (preference === "system") {
      mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
      mediaListener = (event) => {
        const next = event.matches ? "dark" : "light";
        applyTheme(next);
        set({ resolvedTheme: next });
      };
      mediaQuery.addEventListener("change", mediaListener);
    }
  }
}));
```

- [ ] **Step 5: 运行测试并确认通过**

Run: `npm test -- src/stores/themeStore.test.ts`

Expected: 3 tests PASS。

- [ ] **Step 6: 提交主题状态实现**

```powershell
git add frontend/src/stores/themeStore.ts frontend/src/stores/themeStore.test.ts frontend/src/utils/storage.ts
git commit -m "feat(frontend): support system light and dark themes"
```

---

### Task 3: 建立主题令牌与主题选择控件

**Files:**
- Create: `frontend/src/components/common/ThemeToggle.tsx`
- Create: `frontend/src/components/common/ThemeToggle.test.tsx`
- Create: `frontend/src/styles/theme.css`
- Modify: `frontend/src/styles/globals.css:5-155`
- Modify: `frontend/src/main.tsx:7-10`
- Modify: `frontend/tailwind.config.cjs`

- [ ] **Step 1: 编写失败的主题控件测试**

Create `frontend/src/components/common/ThemeToggle.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ThemeToggle } from "@/components/common/ThemeToggle";
import { useThemeStore } from "@/stores/themeStore";

describe("ThemeToggle", () => {
  beforeEach(() => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn()
    })));
    useThemeStore.setState({ preference: "system", resolvedTheme: "light" });
  });

  it("允许选择暗色主题", async () => {
    const user = userEvent.setup();
    render(<ThemeToggle />);

    await user.click(screen.getByRole("button", { name: "切换主题" }));
    await user.click(screen.getByRole("menuitemradio", { name: "暗色" }));

    expect(useThemeStore.getState().preference).toBe("dark");
    expect(document.documentElement).toHaveClass("dark");
  });
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- src/components/common/ThemeToggle.test.tsx`

Expected: FAIL，提示无法解析 `ThemeToggle`。

- [ ] **Step 3: 实现可访问的主题选择菜单**

Create `frontend/src/components/common/ThemeToggle.tsx`:

```tsx
import { Check, Monitor, Moon, Sun } from "lucide-react";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import { useThemeStore, type ThemePreference } from "@/stores/themeStore";

const options: Array<{
  value: ThemePreference;
  label: string;
  icon: typeof Monitor;
}> = [
  { value: "system", label: "跟随系统", icon: Monitor },
  { value: "light", label: "亮色", icon: Sun },
  { value: "dark", label: "暗色", icon: Moon }
];

interface ThemeToggleProps {
  className?: string;
}

export function ThemeToggle({ className }: ThemeToggleProps) {
  const { preference, resolvedTheme, setPreference } = useThemeStore();
  const TriggerIcon = resolvedTheme === "dark" ? Moon : Sun;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button type="button" className={cn("theme-toggle", className)} aria-label="切换主题">
          <TriggerIcon className="h-4 w-4" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="theme-menu">
        {options.map(({ value, label, icon: Icon }) => (
          <DropdownMenuItem
            key={value}
            role="menuitemradio"
            aria-checked={preference === value}
            className="theme-menu__item"
            onSelect={() => setPreference(value)}
          >
            <Icon className="h-4 w-4" />
            <span className="flex-1">{label}</span>
            <Check className={cn("h-4 w-4", preference !== value && "opacity-0")} />
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
```

- [ ] **Step 4: 创建语义主题令牌**

Create `frontend/src/styles/theme.css`:

```css
:root {
  color-scheme: light;
  --background: 60 9% 96%;
  --foreground: 60 2% 13%;
  --card: 0 0% 100%;
  --card-foreground: 60 2% 13%;
  --popover: 0 0% 100%;
  --popover-foreground: 60 2% 13%;
  --primary: 168 81% 31%;
  --primary-foreground: 0 0% 100%;
  --secondary: 60 6% 93%;
  --secondary-foreground: 60 2% 20%;
  --muted: 60 5% 92%;
  --muted-foreground: 60 2% 42%;
  --accent: 168 33% 92%;
  --accent-foreground: 168 81% 25%;
  --destructive: 0 72% 51%;
  --destructive-foreground: 0 0% 100%;
  --border: 60 5% 88%;
  --input: 60 5% 86%;
  --ring: 168 81% 31%;
  --chat-user: 60 6% 93%;
  --chat-assistant: 0 0% 100%;
  --surface: #ffffff;
  --surface-subtle: #f7f7f5;
  --surface-hover: #eeeeeb;
  --text-primary: #202123;
  --text-secondary: #6b6b68;
  --text-tertiary: #92928d;
  --brand: #0f8f74;
  --brand-soft: #e8f4f1;
  --border-subtle: #e1e1de;
  --shadow-float: 0 8px 28px rgba(30, 30, 28, 0.1);
}

.dark {
  color-scheme: dark;
  --background: 0 0% 9%;
  --foreground: 60 5% 92%;
  --card: 0 0% 13%;
  --card-foreground: 60 5% 92%;
  --popover: 0 0% 15%;
  --popover-foreground: 60 5% 92%;
  --primary: 165 54% 46%;
  --primary-foreground: 0 0% 8%;
  --secondary: 60 2% 17%;
  --secondary-foreground: 60 5% 90%;
  --muted: 60 2% 18%;
  --muted-foreground: 60 3% 64%;
  --accent: 165 25% 20%;
  --accent-foreground: 165 54% 64%;
  --destructive: 0 91% 71%;
  --destructive-foreground: 0 0% 8%;
  --border: 60 3% 22%;
  --input: 60 3% 24%;
  --ring: 165 54% 46%;
  --chat-user: 60 2% 18%;
  --chat-assistant: 0 0% 13%;
  --surface: #212121;
  --surface-subtle: #171717;
  --surface-hover: #2a2a28;
  --text-primary: #ececea;
  --text-secondary: #a3a39d;
  --text-tertiary: #777772;
  --brand: #35b695;
  --brand-soft: #1d332e;
  --border-subtle: #383834;
  --shadow-float: 0 10px 32px rgba(0, 0, 0, 0.28);
}

body {
  background: var(--surface-subtle);
  color: var(--text-primary);
}

.theme-toggle {
  display: inline-flex;
  width: 2.25rem;
  height: 2.25rem;
  align-items: center;
  justify-content: center;
  border-radius: 0.5rem;
  color: var(--text-secondary);
}

.theme-toggle:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}

.theme-menu {
  border-color: var(--border-subtle);
  background: var(--surface);
  color: var(--text-primary);
}

.theme-menu__item {
  display: flex;
  gap: 0.625rem;
}
```

- [ ] **Step 5: 调整全局样式入口和 Tailwind 字体**

在 `frontend/src/main.tsx` 中保持 `globals.css` 最先加载，再加载主题样式：

```ts
import "@/styles/globals.css";
import "@/styles/theme.css";
```

从 `globals.css` 删除旧的 `:root` 主题色、`.dark { color-scheme: light; }` 和 `body` 的硬编码 `#FAFAFA`，保留 Tailwind 指令、滚动条、Markdown 基础规则和现有后台专项规则。

将 `tailwind.config.cjs` 的字体改为：

```js
fontFamily: {
  display: ["Inter", "PingFang SC", "Microsoft YaHei", "ui-sans-serif", "system-ui"],
  body: ["Inter", "PingFang SC", "Microsoft YaHei", "ui-sans-serif", "system-ui"],
  mono: ["JetBrains Mono", "Cascadia Code", "ui-monospace", "SFMono-Regular"]
}
```

- [ ] **Step 6: 运行主题测试、构建和 lint**

Run:

```powershell
npm test -- src/stores/themeStore.test.ts src/components/common/ThemeToggle.test.tsx
npm run build
npm run lint
```

Expected: 主题测试 PASS，Vite build PASS，ESLint 退出码为 0；如 ESLint 暴露仓库既有问题，记录完整文件和规则名后继续，不掩盖新增错误。

- [ ] **Step 7: 提交主题视觉基础**

```powershell
git add frontend/src/components/common/ThemeToggle.tsx frontend/src/components/common/ThemeToggle.test.tsx frontend/src/styles/theme.css frontend/src/styles/globals.css frontend/src/main.tsx frontend/tailwind.config.cjs
git commit -m "feat(frontend): add semantic theme system"
```

---

### Task 4: 改造聊天端外壳

**Files:**
- Create: `frontend/src/components/layout/Header.test.tsx`
- Create: `frontend/src/styles/chat.css`
- Modify: `frontend/src/main.tsx`
- Modify: `frontend/src/components/layout/MainLayout.tsx`
- Modify: `frontend/src/components/layout/Header.tsx`
- Modify: `frontend/src/components/layout/Sidebar.tsx`
- Modify: `frontend/src/styles/chat.css`

- [ ] **Step 1: 编写失败的顶部栏测试**

Create `frontend/src/components/layout/Header.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { Header } from "@/components/layout/Header";
import { useChatStore } from "@/stores/chatStore";

describe("Header", () => {
  it("显示当前会话标题和必要操作，不请求 GitHub Star", () => {
    useChatStore.setState({
      currentSessionId: "session-1",
      sessions: [{ id: "session-1", title: "制度查询" }]
    });

    render(<Header onToggleSidebar={vi.fn()} />);

    expect(screen.getByText("制度查询")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "打开会话列表" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "切换主题" })).toBeInTheDocument();
    expect(screen.queryByText("Star")).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- src/components/layout/Header.test.tsx`

Expected: FAIL，现有 Header 仍包含 Star，且不存在主题按钮或新的菜单名称。

- [ ] **Step 3: 将 MainLayout 改为语义结构**

Replace the return block in `MainLayout.tsx` with:

```tsx
return (
  <div className="chat-shell">
    <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />
    <div className="chat-shell__main">
      <Header onToggleSidebar={() => setSidebarOpen((prev) => !prev)} />
      <main className="chat-shell__content">{children}</main>
    </div>
  </div>
);
```

- [ ] **Step 4: 精简 Header**

删除 GitHub API 请求、Star 数量状态和外链按钮。Header 的返回结构改为：

```tsx
<header className="chat-header">
  <div className="chat-header__leading">
    <button
      type="button"
      onClick={onToggleSidebar}
      aria-label="打开会话列表"
      className="chat-header__menu"
    >
      <Menu className="h-5 w-5" />
    </button>
    <p className="chat-header__title">{currentSession?.title || "新对话"}</p>
  </div>
  <ThemeToggle />
</header>
```

Imports use `Menu` and `ThemeToggle`; remove `Github` and all Star-related React state/effects.

- [ ] **Step 5: 精简 Sidebar 标记但保留业务逻辑**

保留会话加载、搜索、重命名、删除、新建、管理员入口和用户菜单逻辑，删除“快速开始”渐变卡片与独立搜索卡片。对现有 JSX 做以下机械替换，不改事件处理函数：

| 区域 | 新类名 |
| --- | --- |
| 顶层 `<aside>` | `cn("chat-sidebar", isOpen && "chat-sidebar--open")` |
| 品牌区域 | `chat-sidebar__brand` |
| 新建会话按钮 | `chat-sidebar__new` |
| 搜索 `<label>` | `chat-sidebar__search` |
| 可滚动会话容器 | `chat-sidebar__sessions` |
| 底部用户菜单触发按钮 | `chat-sidebar__account` |

会话项使用：

```tsx
className={cn("chat-session", currentSessionId === session.id && "chat-session--active")}
```

菜单按钮使用：

```tsx
className="chat-session__menu"
```

- [ ] **Step 6: 创建聊天外壳样式**

Create `frontend/src/styles/chat.css` with:

```css
.chat-shell {
  display: flex;
  min-height: 100vh;
  background: var(--surface-subtle);
}

.chat-shell__main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  background: var(--surface);
}

.chat-shell__content {
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

.chat-header {
  display: flex;
  height: 3.5rem;
  align-items: center;
  justify-content: space-between;
  padding: 0 1rem;
  background: var(--surface);
}

.chat-header__leading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.5rem;
}

.chat-header__menu {
  display: none;
  width: 2.25rem;
  height: 2.25rem;
  align-items: center;
  justify-content: center;
  border-radius: 0.5rem;
  color: var(--text-secondary);
}

.chat-header__title {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 0.875rem;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-sidebar {
  display: flex;
  width: 16rem;
  flex-shrink: 0;
  flex-direction: column;
  border-right: 1px solid var(--border-subtle);
  background: var(--surface-subtle);
  padding: 0.625rem;
}

.chat-sidebar__new,
.chat-sidebar__search,
.chat-session,
.chat-sidebar__account {
  border-radius: 0.5rem;
}

.chat-sidebar__new:hover,
.chat-session:hover,
.chat-sidebar__account:hover {
  background: var(--surface-hover);
}

.chat-session--active {
  background: var(--surface-hover);
  color: var(--text-primary);
}

@media (max-width: 1023px) {
  .chat-header__menu { display: inline-flex; }
  .chat-sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 40;
    transform: translateX(-100%);
    transition: transform 180ms ease;
  }
  .chat-sidebar--open { transform: translateX(0); }
}

@media (prefers-reduced-motion: reduce) {
  .chat-sidebar { transition: none; }
}
```

在 `frontend/src/main.tsx` 的 `theme.css` 导入之后增加：

```ts
import "@/styles/chat.css";
```

- [ ] **Step 7: 运行 Header 测试和构建**

Run:

```powershell
npm test -- src/components/layout/Header.test.tsx
npm run build
```

Expected: Header test PASS，build PASS。

- [ ] **Step 8: 提交聊天外壳**

```powershell
git add frontend/src/main.tsx frontend/src/components/layout/MainLayout.tsx frontend/src/components/layout/Header.tsx frontend/src/components/layout/Header.test.tsx frontend/src/components/layout/Sidebar.tsx frontend/src/styles/chat.css
git commit -m "feat(frontend): simplify chat shell"
```

---

### Task 5: 精简欢迎页、消息和输入区

**Files:**
- Create: `frontend/src/components/chat/ChatInput.test.tsx`
- Modify: `frontend/src/pages/ChatPage.tsx`
- Modify: `frontend/src/components/chat/WelcomeScreen.tsx`
- Modify: `frontend/src/components/chat/MessageList.tsx`
- Modify: `frontend/src/components/chat/MessageItem.tsx`
- Modify: `frontend/src/components/chat/ThinkingIndicator.tsx`
- Modify: `frontend/src/components/chat/ChatInput.tsx`
- Modify: `frontend/src/components/chat/MarkdownRenderer.tsx`
- Modify: `frontend/src/styles/chat.css`

- [ ] **Step 1: 编写失败的 ChatInput 行为测试**

Create `frontend/src/components/chat/ChatInput.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ChatInput } from "@/components/chat/ChatInput";
import { useChatStore } from "@/stores/chatStore";

describe("ChatInput", () => {
  const sendMessage = vi.fn().mockResolvedValue(undefined);

  beforeEach(() => {
    sendMessage.mockClear();
    useChatStore.setState({
      isStreaming: false,
      deepThinkingEnabled: false,
      sendMessage,
      cancelGeneration: vi.fn()
    });
  });

  it("按 Enter 发送非空消息", async () => {
    const user = userEvent.setup();
    render(<ChatInput />);

    const input = screen.getByRole("textbox", { name: "聊天输入框" });
    await user.type(input, "解释混合检索{enter}");

    expect(sendMessage).toHaveBeenCalledWith("解释混合检索");
    expect(input).toHaveValue("");
  });

  it("流式生成时发送按钮变为停止按钮", () => {
    useChatStore.setState({ isStreaming: true });
    render(<ChatInput />);

    expect(screen.getByRole("button", { name: "停止生成" })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 运行测试并确认当前行为基线**

Run: `npm test -- src/components/chat/ChatInput.test.tsx`

Expected: 测试应通过现有核心行为；若 Zustand 类型要求完整字段，只向 `setState` 补充测试报错中明确缺少的函数，不改产品代码。

- [ ] **Step 3: 精简 ChatPage 与欢迎页结构**

`ChatPage.tsx` 的内容区改用语义类名：

```tsx
<MainLayout>
  <div className="chat-page">
    <div className="chat-page__messages">
      <MessageList
        messages={messages}
        isLoading={isLoading}
        isStreaming={isStreaming}
        sessionKey={currentSessionId}
      />
    </div>
    {showWelcome ? null : (
      <div className="chat-page__composer">
        <ChatInput />
      </div>
    )}
  </div>
</MainLayout>
```

`WelcomeScreen.tsx` 保留现有 `value`、输入法组合事件、自动高度、`applyPreset`、`handleSubmit` 和示例问题加载逻辑。最外层改为 `welcome-screen`，品牌方块使用 `welcome-screen__mark`，标题固定为“有什么可以帮你？”，说明固定为“我会从已授权的知识库中查找答案”。示例问题容器使用 `welcome-screen__prompts`，每个 `promptPresets.map` 产生的按钮使用 `welcome-prompt`；现有输入容器改用 `chat-composer`、`chat-composer__input`、`chat-composer__toolbar`、`chat-composer__mode` 和 `chat-composer__send`。删除彩色图标背景、渐变、网格、光斑和漂浮装饰。

- [ ] **Step 4: 精简消息与思考状态**

`MessageItem.tsx` 使用以下结构：

```tsx
if (isUser) {
  return (
    <div className="message message--user">
      <div className="message__user-bubble">
        <p className="whitespace-pre-wrap break-words">{message.content}</p>
      </div>
    </div>
  );
}

return (
  <div className="message message--assistant">
    <div className="message__avatar" aria-hidden="true">R</div>
    <div className="message__body">
      {isThinking ? (
        <ThinkingIndicator content={message.thinking} duration={message.thinkingDuration} />
      ) : null}
      {!isThinking && hasThinking ? (
        <button
          type="button"
          className="thinking-disclosure"
          onClick={() => setThinkingExpanded((prev) => !prev)}
          aria-expanded={thinkingExpanded}
        >
          <Brain className="h-4 w-4" />
          <span>查看深度思考{thinkingDuration ? ` · ${thinkingDuration}` : ""}</span>
          <ChevronDown className={cn("h-4 w-4", thinkingExpanded && "rotate-180")} />
        </button>
      ) : null}
      {!isThinking && hasThinking && thinkingExpanded ? (
        <div className="thinking-detail">{message.thinking}</div>
      ) : null}
      {isWaiting ? <div className="ai-wait" aria-label="思考中" /> : null}
      {hasContent ? <MarkdownRenderer content={message.content} /> : null}
      {message.status === "error" ? <p className="message__error">生成已中断。</p> : null}
      {showFeedback ? (
        <FeedbackButtons
          messageId={message.id}
          feedback={message.feedback ?? null}
          content={message.content}
          alwaysVisible={Boolean(isLast)}
        />
      ) : null}
    </div>
  </div>
);
```

`ThinkingIndicator.tsx` 改为单行状态：

```tsx
<div className="thinking-status" role="status">
  <Loader2 className="h-4 w-4 animate-spin" />
  <span>正在分析知识库…</span>
  {duration ? <span className="thinking-status__duration">{duration} 秒</span> : null}
</div>
```

折叠后的深度思考使用中性边框和普通文字，不再使用整块蓝色背景。

- [ ] **Step 5: 精简输入区标记**

保留 `ChatInput.tsx` 的高度自适应、中文输入法处理、深度思考开关、发送和停止逻辑，只替换视觉标记：顶层使用 `chat-composer-wrap`，输入容器使用 `cn("chat-composer", isFocused && "chat-composer--focused")`，`Textarea` 的 `className` 使用 `chat-composer__input`，工具栏使用 `chat-composer__toolbar`，深度思考按钮使用 `cn("chat-composer__mode", deepThinkingEnabled && "is-active")`，发送按钮使用 `chat-composer__send`，底部提示使用 `chat-composer__hint`。发送按钮继续渲染现有的 `Square` 或 `Send` 图标。

- [ ] **Step 6: 添加聊天内容样式**

Append to `chat.css`:

```css
.chat-page {
  display: flex;
  height: 100%;
  flex-direction: column;
  background: var(--surface);
}

.chat-page__messages { min-height: 0; flex: 1; }
.chat-page__composer { padding: 0.25rem 1.5rem 1rem; background: var(--surface); }

.welcome-screen {
  display: flex;
  height: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2rem 1.5rem;
  text-align: center;
}

.welcome-screen__mark,
.message__avatar {
  display: grid;
  place-items: center;
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 700;
}

.welcome-screen__mark { width: 2.75rem; height: 2.75rem; border-radius: 0.875rem; }
.welcome-screen__title { margin-top: 1rem; font-size: 1.75rem; font-weight: 600; letter-spacing: -0.03em; }
.welcome-screen__description { margin-top: 0.375rem; color: var(--text-secondary); font-size: 0.875rem; }
.welcome-screen__prompts { display: grid; width: min(100%, 42rem); gap: 0.5rem; margin-top: 1.5rem; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.welcome-prompt { border: 1px solid var(--border-subtle); border-radius: 0.75rem; padding: 0.75rem 0.875rem; text-align: left; color: var(--text-secondary); }
.welcome-prompt:hover { background: var(--surface-hover); color: var(--text-primary); }
.welcome-screen__composer { width: min(100%, 48rem); margin-top: 1.25rem; }

.message { display: flex; width: min(100%, 48rem); margin: 0 auto; padding: 1rem 1.5rem; }
.message--user { justify-content: flex-end; }
.message__user-bubble { max-width: 80%; border-radius: 1rem 1rem 0.25rem 1rem; background: hsl(var(--chat-user)); padding: 0.75rem 1rem; }
.message--assistant { gap: 0.75rem; }
.message__avatar { width: 1.75rem; height: 1.75rem; flex: 0 0 auto; border-radius: 0.5rem; font-size: 0.75rem; }
.message__body { min-width: 0; flex: 1; }
.thinking-status { display: flex; align-items: center; gap: 0.5rem; color: var(--text-secondary); font-size: 0.875rem; }

.chat-composer { width: min(100%, 48rem); margin: 0 auto; border: 1px solid var(--border-subtle); border-radius: 1rem; background: var(--surface); padding: 0.625rem; box-shadow: var(--shadow-float); }
.chat-composer--focused { border-color: var(--text-tertiary); }
.chat-composer__input { min-height: 2.75rem; max-height: 10rem; resize: none; border: 0; background: transparent; box-shadow: none; }
.chat-composer__toolbar { display: flex; align-items: center; justify-content: space-between; }
.chat-composer__mode { display: inline-flex; align-items: center; gap: 0.375rem; border-radius: 999px; padding: 0.375rem 0.625rem; color: var(--text-secondary); font-size: 0.75rem; }
.chat-composer__mode.is-active { background: var(--brand-soft); color: var(--brand); }
.chat-composer__send { display: grid; width: 2rem; height: 2rem; place-items: center; border-radius: 999px; background: var(--text-primary); color: var(--surface); }
.chat-composer__hint { margin-top: 0.5rem; text-align: center; color: var(--text-tertiary); font-size: 0.6875rem; }

@media (max-width: 640px) {
  .welcome-screen__prompts { grid-template-columns: 1fr; }
  .chat-page__composer { padding-inline: 0.75rem; }
  .message { padding-inline: 1rem; }
}
```

`MarkdownRenderer.tsx` 保留安全过滤和复制功能，只把硬编码颜色替换为 `prose-ragent` 语义类；在 `chat.css` 为标题、代码、表格、链接和引用定义亮暗主题颜色。

当前聊天 Store 和请求协议没有附件、知识库范围选择或结构化来源字段，因此本任务不渲染无功能的附件/知识库按钮，也不改变发送请求。Markdown 中已有的链接、引用和脚注按轻量来源样式呈现。

- [ ] **Step 7: 运行聊天测试、构建与 lint**

Run:

```powershell
npm test -- src/components/chat/ChatInput.test.tsx src/components/layout/Header.test.tsx
npm run build
npm run lint
```

Expected: tests PASS，build PASS，新增代码无 lint 错误。

- [ ] **Step 8: 提交聊天内容改造**

```powershell
git add frontend/src/pages/ChatPage.tsx frontend/src/components/chat frontend/src/styles/chat.css
git commit -m "feat(frontend): simplify chat experience"
```

---

### Task 6: 改造登录与通用异常页面

**Files:**
- Create: `frontend/src/pages/LoginPage.test.tsx`
- Modify: `frontend/src/pages/LoginPage.tsx`
- Modify: `frontend/src/pages/NotFoundPage.tsx`
- Modify: `frontend/src/components/common/Loading.tsx`
- Modify: `frontend/src/components/common/ErrorBoundary.tsx`
- Modify: `frontend/src/styles/chat.css`

- [ ] **Step 1: 编写失败的登录页测试**

Create `frontend/src/pages/LoginPage.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { LoginPage } from "@/pages/LoginPage";

describe("LoginPage", () => {
  it("空字段提交时显示明确错误并提供主题入口", async () => {
    const user = userEvent.setup();
    render(<LoginPage />, { wrapper: MemoryRouter });

    await user.clear(screen.getByLabelText("用户名"));
    await user.clear(screen.getByLabelText("密码"));
    await user.click(screen.getByRole("button", { name: "登录" }));

    expect(screen.getByText("请输入用户名和密码。")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "切换主题" })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- src/pages/LoginPage.test.tsx`

Expected: FAIL，现有输入框没有稳定的可访问名称，且页面没有主题切换入口。

- [ ] **Step 3: 重写登录页视觉结构**

保留认证逻辑，将最外层改为 `<main className="login-page">`，在表单卡片之前插入 `<div className="login-page__theme"><ThemeToggle /></div>`。表单卡片使用 `login-card`，品牌方块使用 `login-card__brand`，表单使用 `login-form`。标题固定为“登录 Ragent”，说明固定为“使用管理员分配的账号继续访问知识问答。”用户名和密码使用显式 `<Label htmlFor="username">`、`<Label htmlFor="password">` 与对应输入 `id`；错误信息添加 `role="alert"`；移除全屏渐变和 `backdrop-blur`。

- [ ] **Step 4: 统一异常与加载状态**

`NotFoundPage.tsx` 使用 `empty-state` 类，提供返回聊天按钮。`Loading.tsx` 使用中性旋转图标和 `role="status"`。`ErrorBoundary.tsx` 保留错误捕获逻辑，视觉结构改为 `error-state` 并提供刷新按钮。

Append to `chat.css`:

```css
.login-page,
.empty-state,
.error-state {
  display: grid;
  min-height: 100vh;
  place-items: center;
  background: var(--surface-subtle);
  padding: 1.5rem;
}

.login-page__theme { position: fixed; top: 1rem; right: 1rem; }
.login-card { width: min(100%, 24rem); border: 1px solid var(--border-subtle); border-radius: 1rem; background: var(--surface); padding: 2rem; }
.login-card__brand { display: grid; width: 2.5rem; height: 2.5rem; place-items: center; border-radius: 0.75rem; background: var(--brand-soft); color: var(--brand); font-weight: 700; }
.login-card h1 { margin-top: 1rem; font-size: 1.5rem; font-weight: 600; }
.login-card > p { margin-top: 0.375rem; color: var(--text-secondary); font-size: 0.875rem; }
.login-form { margin-top: 1.5rem; display: grid; gap: 1rem; }
```

- [ ] **Step 5: 运行登录测试和构建**

Run:

```powershell
npm test -- src/pages/LoginPage.test.tsx
npm run build
```

Expected: LoginPage test PASS，build PASS。

- [ ] **Step 6: 提交登录与异常页面**

```powershell
git add frontend/src/pages/LoginPage.tsx frontend/src/pages/LoginPage.test.tsx frontend/src/pages/NotFoundPage.tsx frontend/src/components/common/Loading.tsx frontend/src/components/common/ErrorBoundary.tsx frontend/src/styles/chat.css
git commit -m "feat(frontend): simplify auth and empty states"
```

---

### Task 7: 重构后台导航配置与管理台外壳

**Files:**
- Create: `frontend/src/pages/admin/adminNavigation.ts`
- Create: `frontend/src/pages/admin/adminNavigation.test.ts`
- Create: `frontend/src/styles/admin.css`
- Modify: `frontend/src/main.tsx`
- Modify: `frontend/src/pages/admin/AdminLayout.tsx`

- [ ] **Step 1: 编写失败的后台导航配置测试**

Create `frontend/src/pages/admin/adminNavigation.test.ts`:

```ts
import { describe, expect, it } from "vitest";

import { adminMenuGroups, breadcrumbMap } from "@/pages/admin/adminNavigation";

describe("adminNavigation", () => {
  it("菜单路径唯一且覆盖后台核心页面", () => {
    const targets = adminMenuGroups.flatMap((group) =>
      group.items.flatMap((item) =>
        item.children?.map((child) => `${child.path}${child.search ?? ""}`) ??
        [`${item.path}${item.search ?? ""}`]
      )
    );

    expect(new Set(targets).size).toBe(targets.length);
    expect(targets).toEqual(expect.arrayContaining([
      "/admin/dashboard",
      "/admin/knowledge",
      "/admin/ingestion?tab=pipelines",
      "/admin/ingestion?tab=tasks",
      "/admin/traces",
      "/admin/users",
      "/admin/settings"
    ]));
    expect(breadcrumbMap.knowledge).toBe("知识库管理");
  });
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- src/pages/admin/adminNavigation.test.ts`

Expected: FAIL，配置文件尚不存在。

- [ ] **Step 3: 抽取菜单和面包屑配置**

将 `AdminLayout.tsx` 现有的菜单和面包屑配置移入 `adminNavigation.ts`。文件完整内容为：

```ts
import {
  ClipboardList,
  Database,
  FolderKanban,
  GitBranch,
  KeyRound,
  Layers,
  LayoutDashboard,
  Lightbulb,
  Settings,
  Upload,
  Users,
  Workflow,
  type LucideIcon
} from "lucide-react";

export type AdminMenuChild = { label: string; path: string; search?: string; icon: LucideIcon };
export type AdminMenuItem = { id?: string; label: string; path: string; search?: string; icon: LucideIcon; children?: AdminMenuChild[] };
export type AdminMenuGroup = { title: string; items: AdminMenuItem[] };

export const adminMenuGroups: AdminMenuGroup[] = [
  {
    title: "导航",
    items: [
      { path: "/admin/dashboard", label: "Dashboard", icon: LayoutDashboard },
      { path: "/admin/knowledge", label: "知识库管理", icon: Database },
      {
        id: "intent",
        path: "/admin/intent-tree",
        label: "意图管理",
        icon: Layers,
        children: [
          { path: "/admin/intent-tree", label: "意图树配置", icon: GitBranch },
          { path: "/admin/intent-list", label: "意图列表", icon: ClipboardList }
        ]
      },
      {
        id: "ingestion",
        path: "/admin/ingestion",
        label: "数据通道",
        icon: Upload,
        children: [
          { path: "/admin/ingestion", label: "流水线管理", icon: FolderKanban, search: "?tab=pipelines" },
          { path: "/admin/ingestion", label: "流水线任务", icon: ClipboardList, search: "?tab=tasks" }
        ]
      },
      { path: "/admin/mappings", label: "关键词映射", icon: KeyRound },
      { path: "/admin/traces", label: "链路追踪", icon: Workflow }
    ]
  },
  {
    title: "设置",
    items: [
      { path: "/admin/users", label: "用户管理", icon: Users },
      { path: "/admin/sample-questions", label: "示例问题", icon: Lightbulb },
      { path: "/admin/settings", label: "系统设置", icon: Settings }
    ]
  }
];

export const breadcrumbMap: Record<string, string> = {
  dashboard: "Dashboard",
  knowledge: "知识库管理",
  "intent-tree": "意图树配置",
  "intent-list": "意图列表",
  ingestion: "数据通道",
  traces: "链路追踪",
  "sample-questions": "示例问题",
  mappings: "关键词映射",
  settings: "系统设置",
  users: "用户管理"
};
```

完成后 `AdminLayout.tsx` 改为导入 `adminMenuGroups` 和 `breadcrumbMap`。

- [ ] **Step 4: 将 AdminLayout 改为朴素管理台**

保留折叠、子菜单、搜索、面包屑、用户信息、退出和返回聊天功能。做以下精确修改：

- 顶层使用 `admin-layout`、`admin-sidebar`、`admin-main`。
- 删除侧栏渐变、在线绿点、角色胶囊和 `Knowledge Console` 宣传副标题。
- Logo 使用纯色方块。
- 顶栏保留搜索、面包屑、用户菜单和 `ThemeToggle`。
- 删除所有装饰性背景层和渐变主按钮类。

在现有用户菜单 `DropdownMenu` 之前插入 `<ThemeToggle />`，并把主题控件与该用户菜单的共同父元素类名设为 `admin-topbar__actions`。

- [ ] **Step 5: 创建后台基础样式**

Create `frontend/src/styles/admin.css` with:

```css
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: var(--surface-subtle);
  color: var(--text-primary);
}

.admin-layout .admin-sidebar {
  display: flex;
  width: 15rem;
  flex-direction: column;
  border-right: 1px solid var(--border-subtle);
  background: var(--surface);
  color: var(--text-secondary);
  transition: width 180ms ease;
}

.admin-layout .admin-sidebar--collapsed { width: 4rem; }
.admin-layout .admin-sidebar__logo { background: hsl(var(--primary)); color: hsl(var(--primary-foreground)); box-shadow: none; }
.admin-layout .admin-sidebar__group-title { color: var(--text-tertiary); letter-spacing: 0; text-transform: none; }
.admin-layout .admin-sidebar__item { border-radius: 0.375rem; color: var(--text-secondary); }
.admin-layout .admin-sidebar__item:hover { background: var(--surface-hover); color: var(--text-primary); }
.admin-layout .admin-sidebar__item--active { background: hsl(var(--accent)); color: hsl(var(--accent-foreground)); }
.admin-layout .admin-sidebar__item-indicator { display: none; }
.admin-layout .admin-sidebar__item-icon { color: currentColor; }
.admin-layout .admin-main { min-width: 0; flex: 1; background: var(--surface-subtle); }
.admin-layout .admin-topbar { position: sticky; top: 0; z-index: 20; border-bottom: 1px solid var(--border-subtle); background: color-mix(in srgb, var(--surface) 92%, transparent); backdrop-filter: blur(10px); }
.admin-layout .admin-topbar-inner { height: 3.5rem; padding-inline: 1.5rem; }
.admin-layout .admin-content { max-width: none; padding: 1.5rem; }
.admin-layout .admin-topbar__actions { display: flex; align-items: center; gap: 0.25rem; }
.admin-layout .ui-card,
.admin-layout .ui-table-wrap { border-color: var(--border-subtle); border-radius: 0.5rem; background: var(--surface); box-shadow: none; }
.admin-layout .ui-button { border-radius: 0.375rem; box-shadow: none; }
.admin-layout .ui-input,
.admin-layout .ui-textarea,
.admin-layout .ui-select-trigger { border-color: var(--border-subtle); border-radius: 0.375rem; background: var(--surface); color: var(--text-primary); }
.admin-layout .ui-table-header { background: var(--surface-subtle); }
.admin-layout .ui-table-row { border-color: var(--border-subtle); }
.admin-layout .ui-table-row:hover { background: var(--surface-hover); }

@media (max-width: 900px) {
  .admin-layout .admin-sidebar { width: 4rem; }
  .admin-layout .admin-content { padding: 1rem; }
}

@media (prefers-reduced-motion: reduce) {
  .admin-layout .admin-sidebar { transition: none; }
}
```

在 `frontend/src/main.tsx` 的 `chat.css` 导入之后增加：

```ts
import "@/styles/admin.css";
```

- [ ] **Step 6: 运行导航测试和构建**

Run:

```powershell
npm test -- src/pages/admin/adminNavigation.test.ts
npm run build
```

Expected: navigation test PASS，build PASS。

- [ ] **Step 7: 提交后台外壳**

```powershell
git add frontend/src/main.tsx frontend/src/pages/admin/AdminLayout.tsx frontend/src/pages/admin/adminNavigation.ts frontend/src/pages/admin/adminNavigation.test.ts frontend/src/styles/admin.css
git commit -m "feat(frontend): simplify admin shell"
```

---

### Task 8: 清理后台概览与链路追踪的装饰样式

**Files:**
- Modify: `frontend/src/pages/admin/dashboard/DashboardPage.tsx`
- Modify: `frontend/src/components/admin/SimpleLineChart.tsx`
- Modify: `frontend/src/pages/admin/traces/RagTracePage.tsx`
- Modify: `frontend/src/pages/admin/traces/RagTraceDetailPage.tsx`
- Modify: `frontend/src/pages/admin/traces/components/PageHeader.tsx`
- Modify: `frontend/src/pages/admin/traces/components/StatCard.tsx`
- Modify: `frontend/src/pages/admin/traces/components/FilterBar.tsx`
- Modify: `frontend/src/pages/admin/traces/components/RunsTable.tsx`
- Modify: `frontend/src/styles/globals.css:641-2411`
- Modify: `frontend/src/styles/admin.css`

- [ ] **Step 1: 记录并运行装饰类基线检查**

Run:

```powershell
rg -n "gradient|shadow-xl|shadow-2xl|backdrop-blur|animate-float|trace-hero" frontend/src/pages/admin frontend/src/components/admin
```

Expected: 当前命令匹配 Dashboard、AdminLayout 或 Trace 页面中的装饰类，作为清理基线。

- [ ] **Step 2: 简化 Dashboard 页面**

保留现有 KPI、趋势图、排行和性能数据。Dashboard 根节点使用 `admin-page`，标题区使用 `admin-page-header`，标题与说明分别使用 `admin-page-title` 和 `admin-page-subtitle`，刷新与时间窗口控件的父容器使用 `admin-page-actions`，KPI 容器使用 `admin-stat-grid`，图表和排行的双列容器使用 `admin-dashboard-grid`。删除渐变标题、背景光斑、装饰标签、悬浮上移动画和无数据含义的图标色块。KPI 卡只保留标签、数值、单位和趋势。

`SimpleLineChart.tsx` 仅使用一个主题强调色和灰色网格；移除发光滤镜、渐变面积和装饰圆点。

- [ ] **Step 3: 简化 Trace 列表和详情页**

`PageHeader.tsx` 改为普通页面标题组件：

```tsx
<header className="admin-page-header">
  <div>
    {tag ? <p className="admin-page-kicker">{tag}</p> : null}
    <h1 className="admin-page-title">{title}</h1>
    <p className="admin-page-subtitle">{description}</p>
    {meta}
  </div>
  <div className="admin-page-actions">{actions}</div>
</header>
```

`StatCard.tsx` 使用 `admin-stat-card`，去掉 `tone` 对应的大面积背景色；颜色只用于小型状态图标。`FilterBar.tsx` 使用标准 `admin-filter-bar`。`RunsTable.tsx` 保留列、分页、复制、详情弹层和状态标签，去除英雄区和悬浮变换。

详情页保留瀑布图，因为它承载真实链路信息，但使用普通边框、灰色轨道和语义状态色。

- [ ] **Step 4: 移除冲突的旧后台主题块**

从 `globals.css` 删除或收敛以下已被 `admin.css` 替代的规则：

- `.admin-sidebar` 的深色渐变和白色专用文字规则。
- `.admin-primary-gradient`。
- `.trace-hero-*`、装饰性伪元素和发光阴影。
- KPI 彩色背景与非语义色块。
- hover 上移、缩放和高强度阴影。

保留 Trace 表格列宽、瀑布图定位、代码块折叠和响应式尺寸等结构性规则。

在 `admin.css` 补充：

```css
.admin-layout .admin-page-kicker { color: var(--text-tertiary); font-size: 0.75rem; font-weight: 600; }
.admin-layout .admin-dashboard-grid { display: grid; gap: 1rem; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.admin-layout .admin-stat-card { border: 1px solid var(--border-subtle); border-radius: 0.5rem; background: var(--surface); padding: 1rem; box-shadow: none; transform: none; }
.admin-layout .admin-filter-bar { display: flex; flex-wrap: wrap; gap: 0.75rem; border: 1px solid var(--border-subtle); border-radius: 0.5rem; background: var(--surface); padding: 1rem; }
.admin-layout .trace-waterfall-track { background: var(--surface-hover); }
.admin-layout .trace-waterfall-bar { box-shadow: none; }

@media (max-width: 1100px) {
  .admin-layout .admin-dashboard-grid { grid-template-columns: 1fr; }
}
```

- [ ] **Step 5: 再次运行装饰类检查**

Run:

```powershell
rg -n "gradient|shadow-xl|shadow-2xl|animate-float|trace-hero" frontend/src/pages/admin frontend/src/components/admin
```

Expected: 不再匹配管理后台外壳、Dashboard 或 Trace 页面；若第三方预览组件需要阴影，只允许在弹层内容中保留并在提交说明中列出。

- [ ] **Step 6: 运行完整测试、构建和 lint**

Run:

```powershell
npm test
npm run build
npm run lint
```

Expected: tests PASS，build PASS，新增和修改文件无 lint 错误。

- [ ] **Step 7: 提交后台内容清理**

```powershell
git add frontend/src/pages/admin/dashboard frontend/src/pages/admin/traces frontend/src/components/admin/SimpleLineChart.tsx frontend/src/styles/globals.css frontend/src/styles/admin.css
git commit -m "refactor(frontend): make admin pages utilitarian"
```

---

### Task 9: 响应式、无障碍与视觉验证

**Files:**
- Modify: `frontend/src/styles/theme.css`
- Modify: `frontend/src/styles/chat.css`
- Modify: `frontend/src/styles/admin.css`
- Modify: `frontend/src/components/layout/Sidebar.tsx`
- Modify: `frontend/src/pages/admin/AdminLayout.tsx`
- Modify: `frontend/TESTING.md`

- [ ] **Step 1: 增加键盘焦点与减少动画规则**

在 `theme.css` 添加：

```css
:where(button, a, input, textarea, select, [role="button"]):focus-visible {
  outline: 2px solid hsl(var(--ring));
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    scroll-behavior: auto !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

确保聊天抽屉和后台折叠按钮提供 `aria-expanded`、`aria-controls`，移动端遮罩使用可识别的关闭按钮或 Escape 处理。

- [ ] **Step 2: 更新手工测试文档**

在 `frontend/TESTING.md` 追加“前端主题与布局回归”章节，包含以下明确检查项：

```markdown
## 前端主题与布局回归

1. 清除 `ragent_theme`，分别在系统亮色和暗色下刷新登录页，确认默认跟随系统。
2. 在登录页选择暗色，登录后确认聊天端和后台继续使用暗色。
3. 选择“跟随系统”，切换操作系统主题，确认页面无需刷新即可变化。
4. 在 1440、1024、768、390 像素宽度检查登录页和聊天页。
5. 在聊天页验证新建、选择、重命名、删除会话，发送、停止和深度思考。
6. 在后台检查概览、知识库、摄取、意图树、链路追踪、用户和设置页面。
7. 只使用键盘完成主题切换、侧栏开关、会话选择和后台导航。
8. 开启“减少动态效果”后确认没有非必要动画。
```

- [ ] **Step 3: 启动本地前端并完成浏览器视觉检查**

Run: `npm run dev -- --host 127.0.0.1`

Expected: Vite 输出本地 URL，无启动错误。

使用浏览器依次检查：

- `/login`：亮色、暗色、浏览器自动填充、错误提示。
- `/chat`：欢迎页、已有会话、流式生成、来源引用、移动抽屉。
- `/admin/dashboard`：朴素 KPI 与趋势布局。
- `/admin/knowledge`：筛选、表格、弹层。
- `/admin/traces` 和一条 Trace 详情：表格与瀑布图。

在 1440×900 和 390×844 两种视口截图对比设计文档。发现遮挡、横向滚动或对比度问题时只修改对应语义样式文件，然后重新截图。

- [ ] **Step 4: 运行最终自动验证**

Run:

```powershell
npm test
npm run build
npm run lint
git diff --check
```

Expected:

- 所有 Vitest 测试 PASS。
- Vite 生产构建 PASS。
- ESLint 无新增错误或警告。
- `git diff --check` 无空白错误。

- [ ] **Step 5: 提交响应式与验证文档**

```powershell
git add frontend/src/styles frontend/src/components/layout/Sidebar.tsx frontend/src/pages/admin/AdminLayout.tsx frontend/TESTING.md
git commit -m "test(frontend): verify responsive themed layouts"
```

---

## 完成标准

- 主题支持 `system`、`light`、`dark`，能持久化并响应系统变化。
- 登录页、聊天端和后台均使用同一主题机制。
- 聊天端不再展示常驻 GitHub Star、渐变快速开始卡或重装饰思考面板。
- 聊天内容居中，用户消息、助手消息、来源引用和输入区层级清晰。
- 后台没有渐变侧栏、发光 KPI、宣传型英雄区或无业务含义的悬浮动画。
- 后台所有现有页面、数据字段和操作路径保持可用。
- 桌面、平板和手机宽度无页面级横向滚动或固定区域遮挡。
- 测试、构建、lint 和空白检查全部完成并记录结果。
