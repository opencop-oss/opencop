// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// Real-browser test: the theme's light and dark values only resolve against a
// real stylesheet and real computed styles, which happy-dom does not evaluate.
// It guards the provider wiring — the system is mounted and the color mode
// switches which semantic-token values apply.
import { render } from "@testing-library/react";
import { createElement } from "react";
import { beforeEach, describe, expect, it } from "vitest";
import { page } from "vitest/browser";
import { Box } from "@chakra-ui/react";
import { OcThemeProvider } from "./OcThemeProvider";
import { useColorMode } from "./ColorMode";

// `bg` is white in light mode and `slate.bg` (#1f2530) in dark mode.
const LIGHT_BG = "rgb(255, 255, 255)";
const DARK_BG = "rgb(31, 37, 48)";

function Probe() {
    const { setColorMode } = useColorMode();
    return createElement(
        Box,
        { id: "probe", bg: "bg" },
        createElement("button", { type: "button", onClick: () => setColorMode("light") }, "Light"),
    );
}

function probeBackground(container: HTMLElement) {
    const probe = container.querySelector("#probe");
    if (!probe) throw new Error("probe not rendered");
    return getComputedStyle(probe).backgroundColor;
}

describe("OcThemeProvider", () => {
    // next-themes persists the chosen mode; start every case from the default.
    beforeEach(() => localStorage.removeItem("theme"));

    it("starts in dark mode and resolves the dark semantic-token values", () => {
        const { container } = render(createElement(OcThemeProvider, null, createElement(Probe)));

        expect(document.documentElement).toHaveClass("dark");
        expect(probeBackground(container)).toBe(DARK_BG);
    });

    it("resolves the light semantic-token values after switching to light mode", async () => {
        const { container } = render(createElement(OcThemeProvider, null, createElement(Probe)));

        await page.getByRole("button", { name: "Light" }).click();

        await expect.poll(() => probeBackground(container)).toBe(LIGHT_BG);
        expect(document.documentElement).toHaveClass("light");
    });
});
