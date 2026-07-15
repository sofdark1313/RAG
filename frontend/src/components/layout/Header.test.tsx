import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { Header } from "@/components/layout/Header";
import { useChatStore } from "@/stores/chatStore";

describe("Header", () => {
  it("只显示会话标题和必要操作", () => {
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
