<template>
  <div class="markdown-content prose" v-html="html" @click="handleContentClick"></div>
</template>

<script lang="ts">
import MarkdownIt from "markdown-it";

const markdownRenderer = new MarkdownIt({
  breaks: true,
  html: false,
  linkify: true,
  typographer: true
});

const defaultFence = markdownRenderer.renderer.rules.fence?.bind(markdownRenderer.renderer.rules);
markdownRenderer.renderer.rules.fence = (tokens, index, options, env, self) => {
  const token = tokens[index];
  const language = token.info.trim().split(/\s+/)[0] || "text";
  const code = token.content;
  const encoded = markdownRenderer.utils.escapeHtml(encodeURIComponent(code));
  const highlighted = defaultFence
    ? defaultFence(tokens, index, options, env, self)
    : `<pre><code>${markdownRenderer.utils.escapeHtml(code)}</code></pre>`;
  return `<div class="markdown-code"><div class="markdown-code__header"><span>${markdownRenderer.utils.escapeHtml(language)}</span><button type="button" data-copy-code="${encoded}">复制</button></div>${highlighted}</div>`;
};

markdownRenderer.renderer.rules.link_open = (tokens, index, options, env, self) => {
  const token = tokens[index];
  token.attrSet("target", "_blank");
  token.attrSet("rel", "noreferrer noopener");
  return self.renderToken(tokens, index, options);
};
</script>

<script setup lang="ts">
import { computed } from "vue";

import { toast } from "@/utils/toast";

const props = defineProps<{ content: string }>();

const html = computed(() => markdownRenderer.render(props.content || ""));

async function handleContentClick(event: MouseEvent) {
  const button = (event.target as HTMLElement).closest<HTMLButtonElement>("[data-copy-code]");
  if (!button) return;
  try {
    await navigator.clipboard.writeText(decodeURIComponent(button.dataset.copyCode || ""));
    button.textContent = "已复制";
    setTimeout(() => {
      button.textContent = "复制";
    }, 1500);
  } catch {
    toast.error("复制失败");
  }
}
</script>
