import * as React from "react";
import { Menu } from "lucide-react";

import { ThemeToggle } from "@/components/common/ThemeToggle";
import { useChatStore } from "@/stores/chatStore";

interface HeaderProps {
  onToggleSidebar: () => void;
}

export function Header({ onToggleSidebar }: HeaderProps) {
  const { currentSessionId, sessions } = useChatStore();
  const currentSession = React.useMemo(
    () => sessions.find((session) => session.id === currentSessionId),
    [sessions, currentSessionId]
  );

  return (
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
  );
}
