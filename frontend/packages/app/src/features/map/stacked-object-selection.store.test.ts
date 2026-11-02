// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { getElementHistoricalKey, type Element } from "@opencop/elements";
import { useStackedObjectSelectionStore } from "./stacked-object-selection.store";

function makeElement(objectId: string): Element {
    return {
        key: `test-${objectId}`,
        id: `${objectId}-id`,
        objectId,
        source: "test",
        getDisplayName: () => objectId,
        time: null,
    } as unknown as Element;
}

function makeHistoricalElement(objectId: string, id: string): Element {
    return {
        key: getElementHistoricalKey({ source: "test", id }),
        id,
        objectId,
        source: "test",
        elementClass: "historical",
        getDisplayName: () => objectId,
        time: null,
    } as unknown as Element;
}

const getState = () => useStackedObjectSelectionStore.getState();

beforeEach(() => getState().dismiss());
afterEach(() => getState().dismiss());

describe("open / dismiss", () => {
    it("opens on the elements under a click and anchors the popover there", () => {
        getState().open([makeElement("a"), makeElement("b")], { x: 12, y: 34 }, true);

        expect(getState().elements?.map((e) => e.objectId)).toEqual(["a", "b"]);
        expect(getState().position).toEqual({ x: 12, y: 34 });
        expect(getState().hitDepthLimit).toBe(true);
        expect(getState().currentDepth).toBe(20);
    });

    it("dismiss drops the elements, the anchor and the depth together", () => {
        getState().open([makeElement("a")], { x: 1, y: 2 }, true);
        getState().dismiss();

        expect(getState().elements).toBeNull();
        expect(getState().position).toBeNull();
        expect(getState().hitDepthLimit).toBe(false);
        expect(getState().currentDepth).toBe(20);
    });
});

describe("advanceDepth", () => {
    it("replaces the listed elements and records the new depth", () => {
        const initial = [makeElement("a"), makeElement("b")];
        getState().open(initial, { x: 0, y: 0 }, true);

        getState().advanceDepth(50, [...initial, makeElement("c")], false);

        expect(getState().currentDepth).toBe(50);
        expect(getState().elements?.map((e) => e.objectId)).toEqual(["a", "b", "c"]);
        expect(getState().hitDepthLimit).toBe(false);
    });

    it("is a no-op once the popover is closed", () => {
        getState().advanceDepth(50, [makeElement("a")], false);
        expect(getState().elements).toBeNull();
    });
});

describe("removeDeleted", () => {
    it("drops elements with deleted store keys", () => {
        const initial = [makeElement("a"), makeElement("b"), makeElement("c")];
        getState().open(initial, { x: 0, y: 0 }, false);

        getState().removeDeleted(new Set([initial[1].key]));

        expect(getState().elements?.map((e) => e.objectId)).toEqual(["a", "c"]);
    });

    it("closes the popover when every element has been deleted", () => {
        const initial = [makeElement("a"), makeElement("b")];
        getState().open(initial, { x: 0, y: 0 }, false);

        getState().removeDeleted(new Set(initial.map((element) => element.key)));

        expect(getState().elements).toBeNull();
        expect(getState().position).toBeNull();
    });

    it("is a no-op when no deleted key matches", () => {
        getState().open([makeElement("a")], { x: 0, y: 0 }, false);
        const before = getState().elements;

        getState().removeDeleted(new Set(["unrelated-key"]));

        expect(getState().elements).toBe(before);
    });

    it("is a no-op when the popover is closed", () => {
        getState().removeDeleted(new Set(["unrelated-key"]));
        expect(getState().elements).toBeNull();
    });

    it("prunes one historical version while another version of the same object lives", () => {
        // Same objectId, distinct id/key: the exact store key keeps them apart.
        const versionA = makeHistoricalElement("shared-object", "version-a");
        const versionB = makeHistoricalElement("shared-object", "version-b");
        getState().open([versionA, versionB], { x: 0, y: 0 }, false);

        getState().removeDeleted(new Set([versionA.key]));

        expect(getState().elements?.map((e) => e.id)).toEqual(["version-b"]);
    });
});
