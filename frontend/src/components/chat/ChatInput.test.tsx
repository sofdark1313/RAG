import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ChatInput } from "@/components/chat/ChatInput";
import { useChatStore } from "@/stores/chatStore";

describe("ChatInput", () => {
  const sendMessage = vi.fn().mockResolvedValue(undefined);

  beforeEach(() => {
    sendMessage.mockClear();
    useChatStore.setState({
      isStreaming: false,
      deepThinkingEnabled: false,
      sendMessage,
      cancelGeneration: vi.fn()
    });
  });

  it("按 Enter 发送非空消息", async () => {
    const user = userEvent.setup();
    render(<ChatInput />);
    const input = screen.getByRole("textbox", { name: "聊天输入框" });

    await user.type(input, "解释混合检索{enter}");

    expect(sendMessage).toHaveBeenCalledWith("解释混合检索");
    expect(input).toHaveValue("");
  });

  it("流式生成时显示停止按钮", () => {
    useChatStore.setState({ isStreaming: true });
    render(<ChatInput />);
    expect(screen.getByRole("button", { name: "停止生成" })).toBeInTheDocument();
  });
});
