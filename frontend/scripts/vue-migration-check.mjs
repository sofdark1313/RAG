import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(fileURLToPath(new URL("..", import.meta.url)));

function read(relativePath) {
  return readFileSync(join(root, relativePath), "utf8");
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

const packageJson = JSON.parse(read("package.json"));
const viteConfig = read("vite.config.ts");

assert(packageJson.dependencies.vue?.startsWith("^3"), "package.json must depend on Vue 3");
assert(packageJson.dependencies["vue-router"], "package.json must depend on vue-router");
assert(!packageJson.dependencies.react, "package.json must not keep React as a runtime dependency");
assert(!packageJson.dependencies["react-dom"], "package.json must not keep React DOM as a runtime dependency");
assert(viteConfig.includes("@vitejs/plugin-vue"), "Vite must use the Vue plugin");
assert(existsSync(join(root, "src/main.ts")), "Vue entry src/main.ts must exist");
assert(existsSync(join(root, "src/App.vue")), "Vue root component src/App.vue must exist");
assert(existsSync(join(root, "src/router.ts")), "Vue router src/router.ts must exist");
assert(existsSync(join(root, "src/pages/ChatPage.vue")), "Vue chat page must exist");
assert(existsSync(join(root, "src/pages/admin/AdminLayout.vue")), "Vue admin layout must exist");
assert(!existsSync(join(root, "src/pages/admin/GenericAdminPage.vue")), "Generic admin placeholder must be removed after migration");
assert(existsSync(join(root, "src/styles/theme.css")), "UI branch theme tokens must be migrated");
assert(existsSync(join(root, "src/styles/chat.css")), "UI branch chat styles must be migrated");
assert(existsSync(join(root, "src/styles/admin.css")), "UI branch admin styles must be migrated");

const requiredAdminPages = [
  "src/pages/admin/knowledge/KnowledgeListPage.vue",
  "src/pages/admin/knowledge/KnowledgeDocumentsPage.vue",
  "src/pages/admin/knowledge/KnowledgeChunksPage.vue",
  "src/pages/admin/intent-tree/IntentTreePage.vue",
  "src/pages/admin/intent-tree/IntentListPage.vue",
  "src/pages/admin/intent-tree/IntentEditPage.vue",
  "src/pages/admin/ingestion/IngestionPage.vue",
  "src/pages/admin/traces/RagTracePage.vue",
  "src/pages/admin/traces/RagTraceDetailPage.vue",
  "src/pages/admin/settings/SystemSettingsPage.vue",
  "src/pages/admin/sample-questions/SampleQuestionPage.vue",
  "src/pages/admin/query-term-mapping/QueryTermMappingPage.vue",
  "src/pages/admin/users/UserListPage.vue"
];

for (const page of requiredAdminPages) {
  assert(existsSync(join(root, page)), `Migrated admin page ${page} must exist`);
}

const mainEntry = read("src/main.ts");
const router = read("src/router.ts");
const dashboard = read("src/pages/admin/DashboardPage.vue");

assert(mainEntry.includes("createApp"), "Vue entry must call createApp");
assert(mainEntry.includes("createPinia"), "Vue entry must install Pinia");
assert(router.includes("createRouter"), "Router must be created with Vue Router");
assert(router.includes("beforeEach"), "Router must provide auth/admin guards");
assert(!router.includes("GenericAdminPage.vue"), "Admin business routes must not use the generic placeholder page");
assert(dashboard.includes('value: "24h"') && dashboard.includes('value: "7d"') && dashboard.includes('value: "30d"'), "Dashboard must restore the UI branch time-window control");

console.log("Vue migration checks passed");
