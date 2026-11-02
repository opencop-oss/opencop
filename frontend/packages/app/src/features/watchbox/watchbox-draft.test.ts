// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { Polygon, type DraftObject } from "@opencop/elements";
import { ELEMENT_STORE_ID, type ToolDescriptor } from "@opencop/tools";
import { bumpRevForTest, resetInteractionForTest, setSessionForTest } from "~/test/interaction";
import type { Session } from "~/features/map/interaction-runtime/session/session";
import {
    WATCHBOX_DRAFT_KEY,
    WATCHBOX_STORE_ID,
    WATCHBOX_TYPE,
    type WatchboxStore,
} from "./watchbox.store";
import { followWatchboxDraft } from "./watchbox-draft";

const tool = (id: string, store: string): ToolDescriptor => ({
    id,
    label: id,
    icon: () => null,
    produces: { store, type: store === WATCHBOX_STORE_ID ? WATCHBOX_TYPE : "polygon" },
});
const tools: Record<string, ToolDescriptor> = {
    watchboxTool: tool("watchboxTool", WATCHBOX_STORE_ID),
    polygonTool: tool("polygonTool", ELEMENT_STORE_ID),
};

function drawing(descriptorId: string, draft: DraftObject, placed = true): Session {
    return {
        kind: "draw",
        descriptorId,
        epoch: 1,
        params: {},
        custody: false,
        locked: 1,
        draft: { object: draft, placed },
    } as Session;
}

function fakeStore() {
    return { setDraft: vi.fn(), clearDraft: vi.fn() } as unknown as WatchboxStore & {
        setDraft: ReturnType<typeof vi.fn>;
        clearDraft: ReturnType<typeof vi.fn>;
    };
}

const stops: (() => void)[] = [];
afterEach(() => {
    stops.splice(0).forEach((stop) => stop());
    resetInteractionForTest();
});

describe("followWatchboxDraft", () => {
    it("hands the store the placed draft of a watchbox session, on every change", () => {
        const store = fakeStore();
        stops.push(followWatchboxDraft(store, (id) => tools[id]));
        const polygon = Polygon.create({ positions: [0, 0, 1, 0, 1, 1] });

        setSessionForTest(drawing("watchboxTool", polygon));
        bumpRevForTest();

        expect(store.setDraft).toHaveBeenCalledTimes(2);
        expect(store.setDraft).toHaveBeenLastCalledWith(
            expect.objectContaining({ key: WATCHBOX_DRAFT_KEY, polygon: [0, 0, 1, 0, 1, 1] }),
        );
    });

    it("holds no draft before the first click, for another tool's session, or once it ends", () => {
        const store = fakeStore();
        stops.push(followWatchboxDraft(store, (id) => tools[id]));
        const polygon = Polygon.create({ positions: [0, 0, 1, 0, 1, 1] });

        setSessionForTest(drawing("watchboxTool", polygon, false));
        setSessionForTest(drawing("polygonTool", polygon));
        resetInteractionForTest();

        expect(store.setDraft).not.toHaveBeenCalled();
        expect(store.clearDraft).toHaveBeenCalled();
    });

    it("clears the draft when it stops following", () => {
        const store = fakeStore();
        const stop = followWatchboxDraft(store, (id) => tools[id]);
        setSessionForTest(drawing("watchboxTool", Polygon.create({ positions: [0, 0, 1, 1] })));
        store.clearDraft.mockClear();

        stop();
        setSessionForTest(drawing("watchboxTool", Polygon.create({ positions: [0, 0, 1, 1] })));

        expect(store.clearDraft).toHaveBeenCalledTimes(1);
        expect(store.setDraft).toHaveBeenCalledTimes(1);
    });
});
