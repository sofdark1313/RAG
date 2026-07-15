import * as React from "react";
import { Brain, ChevronDown } from "lucide-react";

import { FeedbackButtons } from "@/components/chat/FeedbackButtons";
import { MarkdownRenderer } from "@/components/chat/MarkdownRenderer";
import { ThinkingIndicator } from "@/components/chat/ThinkingIndicator";
import { cn } from "@/lib/utils";
import type { Message } from "@/types";

interface MessageItemProps {
  message: Message;
  isLast?: boolean;
}

export const MessageItem = React.memo(function MessageItem({ message, isLast }: MessageItemProps) {
  const isUser = message.role === "user";
  const showFeedback =
    message.role === "assistant" &&
    message.status !== "streaming" &&
    message.id &&
    !message.id.startsWith("assistant-");
  const isThinking = Boolean(message.isThinking);
  const [thinkingExpanded, setThinkingExpanded] = React.useState(false);
  const hasThinking = Boolean(message.thinking?.trim());
  const hasContent = message.content.trim().length > 0;
  const isWaiting = message.status === "streaming" && !isThinking && !hasContent;

  if (isUser) {
    return (
      <div className="message message--user">
        <div className="message__user-bubble">
          <p className="whitespace-pre-wrap break-words">{message.content}</p>
        </div>
      </div>
    );
  }

  const thinkingDuration = message.thinkingDuration ? `${message.thinkingDuration} 秒` : "";
  return (
    <div className="message message--assistant">
      <div className="message__avatar" aria-hidden="true">R</div>
      <div className="message__body">
        {isThinking ? <ThinkingIndicator content={message.thinking} duration={message.thinkingDuration} /> : null}
        {!isThinking && hasThinking ? (
          <div className="thinking-disclosure-wrap">
            <button
              type="button"
              className="thinking-disclosure"
              onClick={() => setThinkingExpanded((prev) => !prev)}
              aria-expanded={thinkingExpanded}
            >
              <Brain className="h-4 w-4" />
              <span className="flex-1">查看深度思考{thinkingDuration ? ` · ${thinkingDuration}` : ""}</span>
              <ChevronDown className={cn("h-4 w-4 transition-transform", thinkingExpanded && "rotate-180")} />
            </button>
            {thinkingExpanded ? <div className="thinking-detail">{message.thinking}</div> : null}
          </div>
        ) : null}
        {isWaiting ? <div className="ai-wait" aria-label="思考中"><span /><span /><span /></div> : null}
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
});
