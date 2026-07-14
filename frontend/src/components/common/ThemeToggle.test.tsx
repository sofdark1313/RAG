import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";

import { ThemeToggle } from "@/components/common/ThemeToggle";
import { useThemeStore } from "@/stores/themeStore";

describe("ThemeToggle", () => {
  beforeEach(() => {
    useThemeStore.setState({
      preference: "system",
      resolvedTheme: "light",
      theme: "light"
    });
    document.documentElement.classList.remove("dark");
    document.documentElement.style.colorScheme = "light";
  });

  it("provides three theme preferences and applies the selected dark theme", async () => {
    const user = userEvent.setup();
    render(<ThemeToggle />);

    expect(useThemeStore.getState().preference).toBe("system");
    expect(useThemeStore.getState().resolvedTheme).toBe("light");

    const trigger = screen.getByRole("button", { name: "切换主题" });
    expect(trigger).toHaveAccessibleName("切换主题");

    await user.click(trigger);

    const systemOption = screen.getByRole("menuitemradio", {
      name: "跟随系统"
    });
    const lightOption = screen.getByRole("menuitemradio", { name: "亮色" });
    const darkOption = screen.getByRole("menuitemradio", { name: "暗色" });

    expect(screen.getAllByRole("menuitemradio")).toHaveLength(3);
    expect(systemOption).toHaveAttribute("aria-checked", "true");
    expect(lightOption).toHaveAttribute("aria-checked", "false");
    expect(darkOption).toHaveAttribute("aria-checked", "false");

    await user.click(darkOption);

    expect(useThemeStore.getState().preference).toBe("dark");
    expect(useThemeStore.getState().resolvedTheme).toBe("dark");
    expect(document.documentElement).toHaveClass("dark");

    await user.click(trigger);
    expect(screen.getByRole("menuitemradio", { name: "暗色" })).toHaveAttribute(
      "aria-checked",
      "true"
    );
  });
});
