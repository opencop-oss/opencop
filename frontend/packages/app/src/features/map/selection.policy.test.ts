// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Line } from "@opencop/elements";
import type { DrawSession, EditSession } from "~/features/map/interaction-runtime/session/session";
import { emitSessionLifecycle } from "~/features/map/interaction-runtime/session/session-lifecycle";
import type { MapManager } from "./MapManager";
import { attachSelectionPolicy } from "./selection.policy";

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

function draw(custody: boolean): DrawSession {
    return {
        kind: "draw",
        descriptorId: "tool",
        epoch: 1,
        params: {},
        custody,
        locked: 0,
        draft: null,
    };
}

let mapManager: {
    handleEmptyClick: ReturnType<typeof vi.fn>;
    selectElement: ReturnType<typeof vi.fn>;
};
let detach: () => void;

beforeEach(() => {
    mapManager = { handleEmptyClick: vi.fn(), selectElement: vi.fn() };
    detach = attachSelectionPolicy(mapManager as unknown as MapManager);
});

afterEach(() => {
    detach();
});

describe("selection follows the session lifecycle", () => {
    it("a custody activation dismisses the current selection; a custody-less one leaves it alone", () => {
        emitSessionLifecycle({ type: "activated", descriptorId: "selection", custody: false });
        expect(mapManager.handleEmptyClick).not.toHaveBeenCalled();

        emitSessionLifecycle({ type: "activated", descriptorId: "line", custody: true });
        expect(mapManager.handleEmptyClick).toHaveBeenCalledTimes(1);
    });

    it("a custody edit selects its element as it starts; a custody-less one selects nothing", () => {
        emitSessionLifecycle({ type: "edit-started", session: edit(false) });
        expect(mapManager.selectElement).not.toHaveBeenCalled();

        emitSessionLifecycle({ type: "edit-started", session: edit(true) });
        expect(mapManager.selectElement).toHaveBeenCalledWith(element);
    });

    it("only a cancelled custody edit dismisses the selection on the way out", () => {
        emitSessionLifecycle({ type: "ended", session: edit(true), how: "saved" });
        emitSessionLifecycle({ type: "ended", session: edit(true), how: "exited" });
        emitSessionLifecycle({ type: "ended", session: edit(false), how: "cancelled" });
        emitSessionLifecycle({ type: "ended", session: draw(true), how: "exited" });
        expect(mapManager.handleEmptyClick).not.toHaveBeenCalled();

        emitSessionLifecycle({ type: "ended", session: edit(true), how: "cancelled" });
        expect(mapManager.handleEmptyClick).toHaveBeenCalledTimes(1);
    });

    it("stops reacting once detached", () => {
        detach();
        emitSessionLifecycle({ type: "edit-started", session: edit(true) });
        expect(mapManager.selectElement).not.toHaveBeenCalled();
        detach = () => {};
    });
});
