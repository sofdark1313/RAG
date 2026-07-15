import { Loader2 } from "lucide-react";

interface ThinkingIndicatorProps {
  content?: string;
  duration?: number;
}

export function ThinkingIndicator({ content, duration }: ThinkingIndicatorProps) {
  return (
    <div className="thinking-status" role="status">
      <Loader2 className="h-4 w-4 animate-spin" />
      <span>{content?.trim() || "正在分析知识库…"}</span>
      {duration ? <span className="thinking-status__duration">{duration} 秒</span> : null}
    </div>
  );
}
