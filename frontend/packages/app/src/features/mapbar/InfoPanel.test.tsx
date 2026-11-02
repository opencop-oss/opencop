// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { render, screen } from "~/test";
import type { PanelDefinition } from "./mapbar.config";
import { InfoPanel } from "./InfoPanel";

const { mockOpen, mockVisiblePanels } = vi.hoisted(() => ({
    mockOpen: { value: "" },
    mockVisiblePanels: { value: [] as PanelDefinition[] },
}));

vi.mock("./mapbar.hook", () => ({
    useMapbar: () => ({ open: mockOpen.value }),
}));

// Isolates InfoPanel's own lookup logic from the real panel registry (which
// pulls in every panel's content component, including the heavy map/weather
// ones) and from the runtime-feature-flag plumbing `useVisiblePanels` itself
// depends on.
vi.mock("./use-visible-panels.hook", () => ({
    useVisiblePanels: () => mockVisiblePanels.value,
}));

const WATCHBOX_PANEL: PanelDefinition = {
    id: "watchbox",
    translationKey: "mapbar.watchbox",
    icon: () => null,
    content: () => <div data-testid="watchbox-panel-content" />,
};

const SETTINGS_PANEL: PanelDefinition = {
    id: "settings",
    translationKey: "mapbar.settings",
    icon: () => null,
    content: () => <div data-testid="settings-panel-content" />,
};

describe("InfoPanel", () => {
    it("mounts the panel whose id is both open and in the visible list", () => {
        mockOpen.value = "settings";
        mockVisiblePanels.value = [SETTINGS_PANEL];

        render(<InfoPanel />);

        expect(screen.getByTestId("settings-panel-content")).toBeInTheDocument();
        expect(screen.getByLabelText("Details panel")).toBeInTheDocument();
    });

    // Regression guard: a runtime flag turning a panel off must not leave a
    // one-paint window where InfoPanel still mounts it. `open` (sourced from
    // the mapbar store) can be stale for a render or two after the flag
    // flips — useMapbarSync's own correction only runs in an effect, after
    // paint. Resolving against the *visible* list (not the raw registry)
    // means that stale render finds nothing and renders null immediately,
    // instead of flashing the now-disabled panel's content.
    it("renders nothing when the stale open panel id is no longer in the visible list", () => {
        mockOpen.value = "watchbox";
        mockVisiblePanels.value = []; // watchbox flag just turned off

        render(<InfoPanel />);

        expect(screen.queryByTestId("watchbox-panel-content")).not.toBeInTheDocument();
        expect(screen.queryByLabelText("Details panel")).not.toBeInTheDocument();
    });

    it("renders nothing when no panel is open", () => {
        mockOpen.value = "";
        mockVisiblePanels.value = [SETTINGS_PANEL, WATCHBOX_PANEL];

        render(<InfoPanel />);

        expect(screen.queryByLabelText("Details panel")).not.toBeInTheDocument();
    });
});
