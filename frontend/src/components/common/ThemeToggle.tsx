import { Check, Monitor, Moon, Sun, type LucideIcon } from "lucide-react";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import { useThemeStore, type ThemePreference } from "@/stores/themeStore";

interface ThemeToggleProps {
  className?: string;
}

interface ThemeOption {
  icon: LucideIcon;
  label: string;
  value: ThemePreference;
}

const themeOptions = [
  { value: "system", label: "跟随系统", icon: Monitor },
  { value: "light", label: "亮色", icon: Sun },
  { value: "dark", label: "暗色", icon: Moon }
] satisfies readonly ThemeOption[];

export function ThemeToggle({ className }: ThemeToggleProps) {
  const preference = useThemeStore((state) => state.preference);
  const resolvedTheme = useThemeStore((state) => state.resolvedTheme);
  const setPreference = useThemeStore((state) => state.setPreference);

  const TriggerIcon = preference === "system" ? Monitor : resolvedTheme === "dark" ? Moon : Sun;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button type="button" className={cn("theme-toggle", className)} aria-label="切换主题">
          <TriggerIcon aria-hidden="true" className="h-4 w-4" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="theme-menu">
        {themeOptions.map((option) => {
          const Icon = option.icon;
          const isSelected = preference === option.value;

          return (
            <DropdownMenuItem
              key={option.value}
              role="menuitemradio"
              aria-checked={isSelected}
              className="theme-menu__item"
              onSelect={() => setPreference(option.value)}
            >
              <Icon aria-hidden="true" className="theme-menu__icon" />
              <span className="theme-menu__label">{option.label}</span>
              <Check
                aria-hidden="true"
                className={cn("theme-menu__check", !isSelected && "opacity-0")}
              />
            </DropdownMenuItem>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
