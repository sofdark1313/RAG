import { createApp } from "vue";
import { createPinia } from "pinia";

import App from "@/App.vue";
import { router } from "@/router";
import "@/styles/globals.css";

let scrollIdleTimer: ReturnType<typeof setTimeout> | null = null;
const handleScrollActivity = () => {
  document.documentElement.classList.add("is-scrolling");
  if (scrollIdleTimer) clearTimeout(scrollIdleTimer);
  scrollIdleTimer = setTimeout(() => {
    document.documentElement.classList.remove("is-scrolling");
  }, 800);
};

window.addEventListener("scroll", handleScrollActivity, { capture: true, passive: true });
window.addEventListener("wheel", handleScrollActivity, { capture: true, passive: true });
window.addEventListener("touchmove", handleScrollActivity, { capture: true, passive: true });

const app = createApp(App);

app.use(createPinia());
app.use(router);
app.mount("#root");
