// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, render } from "~/test";
import { getElementKey, Point, type Element } from "@opencop/elements";
import { ElementStore } from "@opencop/ingest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useStackedObjectSelectionStore } from "~/features/map/stacked-object-selection.store";
import { useAreaSelectionStore } from "~/features/selection/area-selection.store";
import { StackedElementsEffect } from "./StackedElementsEffect";

vi.mock("./MapManagerContext", () => ({
    useMapManager: vi.fn(),
}));

import { useMapManager } from "./MapManagerContext";

type StoreEvent = "add" | "remove" | "update";
type StoreHandler = (elements: Element[]) => void;

function makeElement(objectId: string, key = `test-${objectId}`): Element {
    return {
        key,
        id: `${objectId}-id`,
        objectId,
        source: "test",
        getDisplayName: () => objectId,
        time: null,
    } as unknown as Element;
}

function makeMapManager() {
    const handlers = new Map<StoreEvent, StoreHandler>();
    const unsub = vi.fn();

    return {
        onStore: vi.fn((event: StoreEvent, handler: StoreHandler) => {
            handlers.set(event, handler);
            return { unsub };
        }),
        emit(event: StoreEvent, elements: Element[]) {
            handlers.get(event)?.(elements);
        },
        unsub,
    };
}

beforeEach(() => {
    useStackedObjectSelectionStore.getState().dismiss();
    useAreaSelectionStore.getState().clear();
});

afterEach(() => {
    useStackedObjectSelectionStore.getState().dismiss();
    useAreaSelectionStore.getState().clear();
});

describe("StackedElementsEffect", () => {
    it("subscribes to deletes on mount, regardless of whether a snapshot is active", () => {
        const mapManager = makeMapManager();
        vi.mocked(useMapManager).mockReturnValue(mapManager as never);

        render(<StackedElementsEffect />);

        expect(mapManager.onStore).toHaveBeenCalledOnce();
        expect(mapManager.onStore).toHaveBeenCalledWith("remove", expect.any(Function));
    });

    it("removes exact deleted keys and resets when the snapshot becomes empty", () => {
        const versionA = makeElement("shared", "test-version-a");
        const versionB = makeElement("shared", "test-version-b");
        useAreaSelectionStore.getState().select([versionA, versionB], []);
        const mapManager = makeMapManager();
        vi.mocked(useMapManager).mockReturnValue(mapManager as never);

        render(<StackedElementsEffect />);

        act(() => mapManager.emit("remove", [versionA]));
        expect(useAreaSelectionStore.getState().elements).toEqual([versionB]);
        expect(useAreaSelectionStore.getState().checkedKeys.has(getElementKey(versionB))).toBe(
            true,
        );

        act(() => mapManager.emit("remove", [versionB]));
        expect(useAreaSelectionStore.getState().elements).toBeNull();
    });

    it("prunes both surfaces when a source is evicted, as it does for a delete", () => {
        const store = new ElementStore();
        const point = (objectId: string, source: string) =>
            Point.create({
                position: [0, 0],
                meta: { objectId, stale: Date.now() + 60_000, source },
            });
        const hidden = point("a", "hidden");
        const kept = point("b", "kept");
        store.add([hidden, kept]);
        useAreaSelectionStore.getState().select([hidden, kept], []);
        useStackedObjectSelectionStore.getState().open([hidden, kept], { x: 0, y: 0 }, false);
        vi.mocked(useMapManager).mockReturnValue({
            onStore: (event: StoreEvent, handler: StoreHandler) => {
                store.on(event, handler);
                return { unsub: () => store.off(event, handler) };
            },
        } as never);

        render(<StackedElementsEffect />);
        act(() => store.deleteBySource("hidden"));

        expect(useAreaSelectionStore.getState().elements).toEqual([kept]);
        expect(useStackedObjectSelectionStore.getState().elements).toEqual([kept]);
        store.dispose();
    });

    it("unsubscribes only on unmount, not when the snapshot empties", () => {
        const mapManager = makeMapManager();
        vi.mocked(useMapManager).mockReturnValue(mapManager as never);

        const { unmount } = render(<StackedElementsEffect />);

        act(() => mapManager.emit("remove", [makeElement("a")]));
        expect(mapManager.unsub).not.toHaveBeenCalled();

        unmount();
        expect(mapManager.unsub).toHaveBeenCalledOnce();
    });
});
