import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

const themeCss = readFileSync(resolve(process.cwd(), "src/styles/theme.css"), "utf8");

type Rgb = [number, number, number];

function extractBlock(selector: string) {
  const blockStart = themeCss.indexOf(`${selector} {`);
  expect(blockStart, `missing ${selector} block`).toBeGreaterThanOrEqual(0);

  const declarationStart = themeCss.indexOf("{", blockStart) + 1;
  const declarationEnd = themeCss.indexOf("}", declarationStart);
  expect(declarationEnd, `unterminated ${selector} block`).toBeGreaterThan(declarationStart);

  return themeCss.slice(declarationStart, declarationEnd);
}

function extractDeclaration(block: string, name: string) {
  const match = block.match(new RegExp(`--${name}:\\s*([^;]+);`));
  expect(match, `missing --${name} declaration`).not.toBeNull();
  return match?.[1].trim() ?? "";
}

function hslToRgb(hue: number, saturation: number, lightness: number): Rgb {
  const h = ((hue % 360) + 360) % 360;
  const s = saturation / 100;
  const l = lightness / 100;
  const chroma = (1 - Math.abs(2 * l - 1)) * s;
  const x = chroma * (1 - Math.abs(((h / 60) % 2) - 1));
  const match = l - chroma / 2;

  let rgb: Rgb;
  if (h < 60) rgb = [chroma, x, 0];
  else if (h < 120) rgb = [x, chroma, 0];
  else if (h < 180) rgb = [0, chroma, x];
  else if (h < 240) rgb = [0, x, chroma];
  else if (h < 300) rgb = [x, 0, chroma];
  else rgb = [chroma, 0, x];

  return rgb.map((channel) => (channel + match) * 255) as Rgb;
}

function parseColor(value: string): Rgb {
  const hexMatch = value.match(/^#([\da-f]{2})([\da-f]{2})([\da-f]{2})$/i);
  if (hexMatch) {
    return hexMatch.slice(1).map((channel) => Number.parseInt(channel, 16)) as Rgb;
  }

  const hslMatch = value.match(/^(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)%\s+(\d+(?:\.\d+)?)%$/);
  if (hslMatch) {
    return hslToRgb(Number(hslMatch[1]), Number(hslMatch[2]), Number(hslMatch[3]));
  }

  throw new Error(`unsupported color value: ${value}`);
}

function relativeLuminance(rgb: Rgb) {
  const [red, green, blue] = rgb.map((channel) => {
    const srgb = channel / 255;
    return srgb <= 0.04045 ? srgb / 12.92 : ((srgb + 0.055) / 1.055) ** 2.4;
  });

  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

function contrastRatio(foreground: string, background: string) {
  const foregroundLuminance = relativeLuminance(parseColor(foreground));
  const backgroundLuminance = relativeLuminance(parseColor(background));
  const lighter = Math.max(foregroundLuminance, backgroundLuminance);
  const darker = Math.min(foregroundLuminance, backgroundLuminance);
  return (lighter + 0.05) / (darker + 0.05);
}

describe("semantic theme tokens", () => {
  it("keeps the exact light theme values", () => {
    const rootBlock = extractBlock(":root");
    const declarations = [
      "--primary: 168 81% 25%;",
      "--secondary-foreground: 60 2% 20%;",
      "--muted: 60 5% 92%;",
      "--input: 60 5% 86%;",
      "--ring: 168 81% 25%;"
    ];

    declarations.forEach((declaration) => {
      expect(rootBlock).toContain(declaration);
    });
  });

  it("keeps the exact dark theme values", () => {
    const darkBlock = extractBlock(".dark");
    const declarations = [
      "--background: 0 0% 9%;",
      "--foreground: 60 5% 92%;",
      "--card: 0 0% 13%;",
      "--card-foreground: 60 5% 92%;",
      "--popover: 0 0% 15%;",
      "--popover-foreground: 60 5% 92%;",
      "--primary: 165 54% 46%;",
      "--primary-foreground: 0 0% 8%;",
      "--secondary: 60 2% 17%;",
      "--secondary-foreground: 60 5% 90%;",
      "--muted: 60 2% 18%;",
      "--muted-foreground: 60 3% 64%;",
      "--accent: 165 25% 20%;",
      "--accent-foreground: 165 54% 64%;",
      "--destructive: 0 91% 71%;",
      "--destructive-foreground: 0 0% 8%;",
      "--border: 60 3% 22%;",
      "--input: 60 3% 24%;",
      "--ring: 165 54% 46%;",
      "--chat-user: 60 2% 18%;",
      "--chat-assistant: 0 0% 13%;",
      "--shadow-float: 0 10px 32px rgba(0, 0, 0, 0.28);"
    ];

    declarations.forEach((declaration) => {
      expect(darkBlock).toContain(declaration);
    });
  });

  it("meets WCAG AA contrast for the light primary color pair", () => {
    const rootBlock = extractBlock(":root");
    const ratio = contrastRatio(
      extractDeclaration(rootBlock, "primary-foreground"),
      extractDeclaration(rootBlock, "primary")
    );

    expect(ratio).toBeGreaterThanOrEqual(4.5);
  });

  it("meets WCAG AA contrast for the selected theme item color pair", () => {
    const rootBlock = extractBlock(":root");
    const ratio = contrastRatio(
      extractDeclaration(rootBlock, "accent-foreground"),
      extractDeclaration(rootBlock, "brand-soft")
    );

    expect(ratio).toBeGreaterThanOrEqual(4.5);
  });

  it("uses the accessible accent foreground for the selected theme item", () => {
    const selectedItemBlock = extractBlock('.theme-menu__item[aria-checked="true"]');

    expect(selectedItemBlock).toContain("color: hsl(var(--accent-foreground));");
  });
});
