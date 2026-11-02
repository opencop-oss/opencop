// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { Line } from "@opencop/elements";
import type { DrawSession, EditSession } from "~/features/map/interaction-runtime/session/session";
import { emitSessionLifecycle } from "~/features/map/interaction-runtime/session/session-lifecycle";
import { attachEventsEditingPolicy } from "./events-editing.policy";
import { useEventsEditingStore } from "./events-editing.store";

const element = Line.create({ positions: [0, 0, 1, 1] });

function edit(custody: boolean): EditSession {
    return {
        kind: "edit",
        descriptorId: "tool",
        epoch: 1,
        product: element,
        ref: { sourceId: "local", objectId: element.key },
        custody,
        params: {},
        isNew: true,
        sub: { hotspot: null, dragging: null },
    };
}

const draw: DrawSession = {
    kind: "draw",
    descriptorId: "tool",
    epoch: 1,
    params: {},
    custody: true,
    locked: 0,
    draft: null,
};

const overlay = () => useEventsEditingStore.getState().overlay;

let detach: () => void;

beforeEach(() => {
    useEventsEditingStore.getState().actions.clearOverlay();
    detach = attachEventsEditingPolicy();
});

afterEach(() => {
    detach();
    useEventsEditingStore.getState().actions.clearOverlay();
});

describe("the editing overlay follows the custody edit session", () => {
    it("opens empty when a custody edit starts, and not for a custody-less one", () => {
        emitSessionLifecycle({ type: "edit-started", session: edit(false) });
        expect(overlay()).toBeNull();

        emitSessionLifecycle({ type: "edit-started", session: edit(true) });
        expect(overlay()).toEqual({});
    });

    it("clears when the custody edit ends, however it ends", () => {
        for (const how of ["saved", "cancelled", "exited"] as const) {
            emitSessionLifecycle({ type: "edit-started", session: edit(true) });
            useEventsEditingStore.getState().actions.setField(["name"], "x");
            useEventsEditingStore.getState().actions.markTouched("name");

            emitSessionLifecycle({ type: "ended", session: edit(true), how });
            expect(overlay()).toBeNull();
            expect(useEventsEditingStore.getState().touchedFields.size).toBe(0);
        }
    });

    it("leaves an open overlay alone when something other than a custody edit ends", () => {
        emitSessionLifecycle({ type: "edit-started", session: edit(true) });
        useEventsEditingStore.getState().actions.setField(["name"], "x");

        emitSessionLifecycle({ type: "ended", session: draw, how: "exited" });
        emitSessionLifecycle({ type: "ended", session: edit(false), how: "cancelled" });
        emitSessionLifecycle({ type: "activated", descriptorId: "tool", custody: true });
        expect(overlay()).toEqual({ name: "x" });
    });

    it("stops reacting once detached", () => {
        detach();
        emitSessionLifecycle({ type: "edit-started", session: edit(true) });
        expect(overlay()).toBeNull();
        detach = () => {};
    });
});
