import MarkdownIt from "markdown-it";

export const markdown = new MarkdownIt({
  breaks: true,
  html: false,
  linkify: true,
  typographer: true
});
