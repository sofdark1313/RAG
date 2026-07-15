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

const mainEntry = read("src/main.ts");
const router = read("src/router.ts");

assert(mainEntry.includes("createApp"), "Vue entry must call createApp");
assert(mainEntry.includes("createPinia"), "Vue entry must install Pinia");
assert(router.includes("createRouter"), "Router must be created with Vue Router");
assert(router.includes("beforeEach"), "Router must provide auth/admin guards");

console.log("Vue migration checks passed");
