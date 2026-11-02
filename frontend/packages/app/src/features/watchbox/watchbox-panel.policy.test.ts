// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { Polygon } from "@opencop/elements";
import { getInteractionState } from "~/features/map/interaction-runtime/session/interaction.store";
import type { DrawSession, EditSession } from "~/features/map/interaction-runtime/session/session";
import { resetInteractionForTest, setSessionForTest } from "~/test/interaction";
import { useMapbarStore } from "~/features/mapbar/mapbar.store";
import { attachWatchboxPanelPolicy, WATCHBOX_PANEL_ID } from "./watchbox-panel.policy";
import { WATCHBOX_TOOL_ID } from "./watchbox.descriptor";

/** The polygon being drawn — waiting for its first click. */
function drawing(descriptorId: string): DrawSession {
    return {
        kind: "draw",
        descriptorId,
        epoch: 1,
        params: {},
        custody: false,
        locked: 0,
        draft: null,
    };
}

/** The finished polygon: an edit session the runtime holds no custody over. */
function finished(descriptorId: string): EditSession {
    const element = Polygon.create({ positions: [0, 0, 1, 0, 1, 1] });
    return {
        kind: "edit",
        descriptorId,
        epoch: 2,
        product: element,
        custody: false,
        params: {},
        isNew: true,
        sub: { hotspot: null, dragging: null },
    };
}

const panel = () => useMapbarStore.getState().sidebarPanel;
const isIdle = () => getInteractionState().session.kind === "idle";
const currentDescriptorId = () => {
    const { session } = getInteractionState();
    return session.kind === "idle" ? null : (session.descriptorId ?? null);
};

let detach: () => void;

beforeEach(() => {
    useMapbarStore.setState({ sidebarPanel: "" });
    resetInteractionForTest();
    detach = attachWatchboxPanelPolicy();
});

afterEach(() => {
    detach();
    useMapbarStore.setState({ sidebarPanel: "" });
    resetInteractionForTest();
});

describe("watchbox panel policy", () => {
    it("does nothing while the tool is only activated or the panel stays open — the panel's own button starts the draw now, not this policy", () => {
        useMapbarStore.getState().openPanel(WATCHBOX_PANEL_ID);
        setSessionForTest(drawing(WATCHBOX_TOOL_ID));
        expect(panel()).toBe(WATCHBOX_PANEL_ID);
        expect(isIdle()).toBe(false);

        // Re-selecting the same panel must not re-fire the exit guard.
        useMapbarStore.getState().openPanel(WATCHBOX_PANEL_ID);
        expect(isIdle()).toBe(false);
    });

    it("exits a mid-draw watchbox session when the panel is closed", () => {
        useMapbarStore.getState().openPanel(WATCHBOX_PANEL_ID);
        setSessionForTest(drawing(WATCHBOX_TOOL_ID));

        useMapbarStore.getState().closePanel();

        expect(isIdle()).toBe(true);
    });

    it("exits a finished-but-unsaved watchbox polygon when the panel is closed", () => {
        useMapbarStore.getState().openPanel(WATCHBOX_PANEL_ID);
        setSessionForTest(finished(WATCHBOX_TOOL_ID));

        useMapbarStore.getState().closePanel();

        expect(isIdle()).toBe(true);
    });

    it("exits the watchbox session when the operator switches to another panel, not just when they close it", () => {
        useMapbarStore.getState().openPanel(WATCHBOX_PANEL_ID);
        setSessionForTest(drawing(WATCHBOX_TOOL_ID));

        useMapbarStore.getState().openPanel("events");

        expect(isIdle()).toBe(true);
        expect(panel()).toBe("events");
    });

    it("leaves a panel the operator already switched to alone — it only reacts to LEAVING watchbox's own panel", () => {
        useMapbarStore.getState().openPanel("events");
        setSessionForTest(drawing(WATCHBOX_TOOL_ID));

        useMapbarStore.getState().closePanel();

        expect(isIdle()).toBe(false);
    });

    it("ignores other tools' sessions when the watchbox panel is left", () => {
        useMapbarStore.getState().openPanel(WATCHBOX_PANEL_ID);
        setSessionForTest(drawing("measureTool"));

        useMapbarStore.getState().closePanel();

        expect(currentDescriptorId()).toBe("measureTool");
    });

    it("stops reacting once detached", () => {
        useMapbarStore.getState().openPanel(WATCHBOX_PANEL_ID);
        setSessionForTest(drawing(WATCHBOX_TOOL_ID));
        detach();

        useMapbarStore.getState().closePanel();

        expect(isIdle()).toBe(false);
        detach = () => {};
    });
});
