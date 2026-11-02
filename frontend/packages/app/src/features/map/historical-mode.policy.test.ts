// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useHistoricalStore } from "~/features/historical/data-plane/historical.store";
import { getInteractionState } from "~/features/map/interaction-runtime/session/interaction.store";
import { resetInteractionForTest, setSessionForTest } from "~/test/interaction";
import type { MapManager } from "./MapManager";
import { attachHistoricalModePolicy } from "./historical-mode.policy";

/**
 * The live ↔ historical flip drops the map's interaction state and ends the
 * session — on a transition, never on attach, never on an unrelated change.
 */

let mapManager: { resetInteractionState: ReturnType<typeof vi.fn> };
let detach: () => void;

beforeEach(() => {
    useHistoricalStore.getState().actions.exitHistorical();
    mapManager = { resetInteractionState: vi.fn() };
    detach = attachHistoricalModePolicy(mapManager as unknown as MapManager);
});

afterEach(() => {
    detach();
    useHistoricalStore.getState().actions.exitHistorical();
    resetInteractionForTest();
});

describe("attachHistoricalModePolicy", () => {
    it("attaching resets nothing", () => {
        expect(mapManager.resetInteractionState).not.toHaveBeenCalled();
    });

    it("entering historical drops the map's interaction state and ends the session", () => {
        setSessionForTest({
            kind: "draw",
            descriptorId: "tool",
            epoch: 1,
            params: {},
            custody: false,
            locked: 0,
            draft: null,
        });

        useHistoricalStore.getState().actions.enterHistorical();

        expect(mapManager.resetInteractionState).toHaveBeenCalledTimes(1);
        expect(getInteractionState().session.kind).toBe("idle");
    });

    it("returning to live resets again; an unrelated store change does not", () => {
        useHistoricalStore.getState().actions.enterHistorical();
        useHistoricalStore.getState().actions.toggleSettings();
        expect(mapManager.resetInteractionState).toHaveBeenCalledTimes(1);

        useHistoricalStore.getState().actions.exitHistorical();
        expect(mapManager.resetInteractionState).toHaveBeenCalledTimes(2);
    });

    it("the detach stops it listening", () => {
        detach();
        useHistoricalStore.getState().actions.enterHistorical();
        expect(mapManager.resetInteractionState).not.toHaveBeenCalled();
    });
});
