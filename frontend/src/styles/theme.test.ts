import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

const themeCss = readFileSync(resolve(process.cwd(), "src/styles/theme.css"), "utf8");

function extractBlock(selector: string) {
  const blockStart = themeCss.indexOf(`${selector} {`);
  expect(blockStart, `missing ${selector} block`).toBeGreaterThanOrEqual(0);

  const declarationStart = themeCss.indexOf("{", blockStart) + 1;
  const declarationEnd = themeCss.indexOf("}", declarationStart);
  expect(declarationEnd, `unterminated ${selector} block`).toBeGreaterThan(declarationStart);

  return themeCss.slice(declarationStart, declarationEnd);
}

describe("semantic theme tokens", () => {
  it("keeps the exact light theme values", () => {
    const rootBlock = extractBlock(":root");
    const declarations = [
      "--secondary-foreground: 60 2% 20%;",
      "--muted: 60 5% 92%;",
      "--input: 60 5% 86%;"
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
});
