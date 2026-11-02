// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it } from "vitest";
import { useMapStore } from "~/features/map/core/map.store";
import { useMapbarStore } from "~/features/mapbar/mapbar.store";
import type { StorePickTarget } from "@opencop/contributions";
import { attachWatchboxSelectionSync, watchboxPickResponse } from "./watchbox-click";
import { WATCHBOX_PANEL_ID } from "./watchbox-panel.policy";
import { useWatchboxPanelStore } from "./watchbox-panel.store";
import { WATCHBOX_DRAFT_KEY, WATCHBOX_STORE_ID } from "./watchbox.store";

/** A click on the watchbox keyed `key`, as the runtime hands it to the response. */
const clickOn = (key: string): StorePickTarget => ({
    screen: { x: 0, y: 0 },
    hit: { responseId: WATCHBOX_STORE_ID, ref: { type: WATCHBOX_STORE_ID, key } },
    elements: [],
});

afterEach(() => {
    useMapbarStore.getState().closePanel();
    useWatchboxPanelStore.setState(useWatchboxPanelStore.getInitialState(), true);
    useMapStore.getState().resetSelection();
});

describe("watchboxPickResponse", () => {
    it("a click on a saved watchbox opens the Watchbox panel on it", () => {
        expect(useMapbarStore.getState().sidebarPanel).not.toBe(WATCHBOX_PANEL_ID);

        watchboxPickResponse.onClick(clickOn("w1"));

        expect(useMapbarStore.getState().sidebarPanel).toBe(WATCHBOX_PANEL_ID);
        expect(useWatchboxPanelStore.getState().selected).toBe("w1");
    });

    it("a click on the watchbox being drawn does nothing", () => {
        watchboxPickResponse.onClick(clickOn(WATCHBOX_DRAFT_KEY));

        expect(useMapbarStore.getState().sidebarPanel).not.toBe(WATCHBOX_PANEL_ID);
        expect(useWatchboxPanelStore.getState().selected).toBeNull();
    });
});

describe("attachWatchboxSelectionSync", () => {
    it("clears the panel's selected watchbox when the map selection changes", () => {
        const detach = attachWatchboxSelectionSync();
        useWatchboxPanelStore.getState().select("w1");

        useMapStore.getState().select("some-element-key");

        expect(useWatchboxPanelStore.getState().selected).toBeNull();
        detach();
    });

    // An empty click when nothing was already selected leaves `selected` at
    // `null` on both sides, so `dismissTick` is the signal that the click
    // happened at all.
    it("clears it on an empty click even when the map selection was already null", () => {
        const detach = attachWatchboxSelectionSync();
        useWatchboxPanelStore.getState().select("w1");

        useMapStore.getState().bumpDismiss();

        expect(useWatchboxPanelStore.getState().selected).toBeNull();
        detach();
    });

    it("does nothing once detached", () => {
        const detach = attachWatchboxSelectionSync();
        detach();
        useWatchboxPanelStore.getState().select("w1");

        useMapStore.getState().select("some-element-key");

        expect(useWatchboxPanelStore.getState().selected).toBe("w1");
    });
});
