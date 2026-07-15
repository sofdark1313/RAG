import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { LoginPage } from "@/pages/LoginPage";

describe("LoginPage", () => {
  it("空字段提交时显示错误并提供主题入口", async () => {
    const user = userEvent.setup();
    render(<LoginPage />, { wrapper: MemoryRouter });

    await user.clear(screen.getByLabelText("用户名"));
    await user.clear(screen.getByLabelText("密码"));
    await user.click(screen.getByRole("button", { name: "登录" }));

    expect(screen.getByRole("alert")).toHaveTextContent("请输入用户名和密码。");
    expect(screen.getByRole("button", { name: "切换主题" })).toBeInTheDocument();
  });
});
