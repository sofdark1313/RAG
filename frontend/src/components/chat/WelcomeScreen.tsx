import * as React from "react";
import { ArrowUpRight, BookOpen, Brain, Check, Lightbulb, Send, Square } from "lucide-react";

import { cn } from "@/lib/utils";
import { listSampleQuestions } from "@/services/sampleQuestionService";
import { useChatStore } from "@/stores/chatStore";

type PromptPreset = {
  id?: string;
  title: string;
  description: string;
  prompt: string;
  icon: React.ComponentType<{ className?: string }>;
};

const PRESET_ICONS = [BookOpen, Check, Lightbulb];
const DEFAULT_PRESETS: PromptPreset[] = [
  { title: "总结内容", description: "提炼重点与行动项", prompt: "请总结相关资料并列出关键要点。", icon: BookOpen },
  { title: "拆解任务", description: "整理步骤与优先级", prompt: "请把这个目标拆成可执行步骤，并给出优先级。", icon: Check },
  { title: "比较方案", description: "分析选项与取舍", prompt: "请给出可选方案，并比较各自的优缺点。", icon: Lightbulb }
];

export function WelcomeScreen() {
  const [value, setValue] = React.useState("");
  const [isFocused, setIsFocused] = React.useState(false);
  const [promptPresets, setPromptPresets] = React.useState(DEFAULT_PRESETS);
  const textareaRef = React.useRef<HTMLTextAreaElement | null>(null);
  const isComposingRef = React.useRef(false);
  const { sendMessage, isStreaming, cancelGeneration, deepThinkingEnabled, setDeepThinkingEnabled } = useChatStore();

  React.useEffect(() => {
    let active = true;
    listSampleQuestions()
      .then((data) => {
        if (!active || !data.length) return;
        const mapped = data
          .filter((item) => item.question?.trim())
          .slice(0, 4)
          .map((item, index) => {
            const prompt = item.question.trim();
            return {
              id: item.id,
              title: item.title?.trim() || (prompt.length > 12 ? `${prompt.slice(0, 12)}…` : prompt),
              description: item.description?.trim() || "从知识库中查找答案",
              prompt,
              icon: PRESET_ICONS[index % PRESET_ICONS.length]
            };
          });
        if (mapped.length) setPromptPresets(mapped);
      })
      .catch(() => null);
    return () => { active = false; };
  }, []);

  React.useEffect(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 160)}px`;
  }, [value]);

  const handleSubmit = async () => {
    if (isStreaming) {
      cancelGeneration();
      return;
    }
    const next = value.trim();
    if (!next) return;
    setValue("");
    await sendMessage(next);
  };

  const hasContent = value.trim().length > 0;

  return (
    <section className="welcome-screen">
      <div className="welcome-screen__mark">R</div>
      <h1 className="welcome-screen__title">有什么可以帮你？</h1>
      <p className="welcome-screen__description">我会从已授权的知识库中查找答案</p>

      <div className="welcome-screen__composer">
        <div className={cn("chat-composer", isFocused && "chat-composer--focused")}>
          <textarea
            ref={textareaRef}
            value={value}
            onChange={(event) => setValue(event.target.value)}
            placeholder={deepThinkingEnabled ? "输入需要深入分析的问题" : "给 Ragent 发送消息"}
            className="chat-composer__input"
            rows={1}
            onFocus={() => setIsFocused(true)}
            onBlur={() => setIsFocused(false)}
            onCompositionStart={() => { isComposingRef.current = true; }}
            onCompositionEnd={() => { isComposingRef.current = false; }}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                if (event.nativeEvent.isComposing || isComposingRef.current || event.nativeEvent.keyCode === 229) return;
                event.preventDefault();
                handleSubmit();
              }
            }}
            aria-label="发送消息"
          />
          <div className="chat-composer__toolbar">
            <button
              type="button"
              className={cn("chat-composer__mode", deepThinkingEnabled && "is-active")}
              onClick={() => setDeepThinkingEnabled(!deepThinkingEnabled)}
              disabled={isStreaming}
              aria-pressed={deepThinkingEnabled}
            >
              <Brain className="h-4 w-4" />深度思考
            </button>
            <button
              type="button"
              className={cn("chat-composer__send", isStreaming && "is-stopping")}
              onClick={handleSubmit}
              disabled={!hasContent && !isStreaming}
              aria-label={isStreaming ? "停止生成" : "发送消息"}
            >
              {isStreaming ? <Square className="h-4 w-4" /> : <Send className="h-4 w-4" />}
            </button>
          </div>
        </div>
      </div>

      <div className="welcome-screen__prompts">
        {promptPresets.map((preset) => {
          const Icon = preset.icon;
          return (
            <button
              key={preset.id ?? preset.title}
              type="button"
              className="welcome-prompt"
              onClick={() => {
                setValue(preset.prompt);
                requestAnimationFrame(() => textareaRef.current?.focus());
              }}
              disabled={isStreaming}
            >
              <Icon className="h-4 w-4" />
              <span><strong>{preset.title}</strong><small>{preset.description}</small></span>
              <ArrowUpRight className="h-4 w-4" />
            </button>
          );
        })}
      </div>
    </section>
  );
}
