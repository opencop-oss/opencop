// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { getElementHistoricalKey, getElementKey, type Element } from "@opencop/elements";
import { useEventsNavigationStore } from "~/features/events/events-navigation.store";
import { clearAreaSelection, useAreaSelectionStore } from "./area-selection.store";

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

const getState = () => useAreaSelectionStore.getState();
const RING: readonly [number, number][] = [
    [0, 0],
    [1, 0],
    [1, 1],
];

beforeEach(() => getState().clear());
afterEach(() => getState().clear());

describe("select / clear", () => {
    it("selects the enclosed elements, keeps the ring, and ticks everything", () => {
        getState().select([makeElement("a"), makeElement("b")], RING);

        expect(getState().elements?.map((e) => e.objectId)).toEqual(["a", "b"]);
        expect(getState().boundary).toEqual(RING);
        expect(getState().checkedKeys.size).toBe(2);
    });

    it("clear drops the elements, the ring and the ticks together", () => {
        getState().select([makeElement("a")], RING);
        getState().clear();

        expect(getState().elements).toBeNull();
        expect(getState().boundary).toBeNull();
        expect(getState().checkedKeys.size).toBe(0);
    });

    it("a second lasso replaces the first outright", () => {
        getState().select([makeElement("a")], RING);
        getState().select([makeElement("b")], [[9, 9]]);

        expect(getState().elements?.map((e) => e.objectId)).toEqual(["b"]);
        expect(getState().boundary).toEqual([[9, 9]]);
    });
});

describe("checked subset", () => {
    it("toggles one key without disturbing the others", () => {
        getState().select([makeElement("a"), makeElement("b")], RING);

        getState().toggleChecked(getElementKey(makeElement("a")));

        expect(getState().checkedKeys.has(getElementKey(makeElement("a")))).toBe(false);
        expect(getState().checkedKeys.has(getElementKey(makeElement("b")))).toBe(true);
    });

    it("setChecked ticks or unticks only the given keys", () => {
        const [a, b, c] = [makeElement("a"), makeElement("b"), makeElement("c")];
        getState().select([a, b, c], RING);

        getState().setChecked([getElementKey(a), getElementKey(b)], false);
        expect([...getState().checkedKeys]).toEqual([getElementKey(c)]);

        getState().setChecked([getElementKey(a)], true);
        expect(getState().checkedKeys).toEqual(new Set([getElementKey(c), getElementKey(a)]));
    });
});

describe("removeDeleted", () => {
    it("drops deleted elements and forgets their ticks", () => {
        const initial = [makeElement("a"), makeElement("b"), makeElement("c")];
        getState().select(initial, RING);

        getState().removeDeleted(new Set([initial[1].key]));

        expect(getState().elements?.map((e) => e.objectId)).toEqual(["a", "c"]);
        expect(getState().checkedKeys.has(getElementKey(makeElement("b")))).toBe(false);
        expect(getState().checkedKeys.has(getElementKey(makeElement("a")))).toBe(true);
    });

    it("keeps a manual uncheck across a prune", () => {
        const initial = [makeElement("a"), makeElement("b"), makeElement("c")];
        getState().select(initial, RING);
        getState().toggleChecked(getElementKey(makeElement("a")));

        getState().removeDeleted(new Set([initial[2].key]));

        expect(getState().checkedKeys.has(getElementKey(makeElement("a")))).toBe(false);
        expect(getState().checkedKeys.has(getElementKey(makeElement("b")))).toBe(true);
    });

    it("clears the selection and the ring when every element has been deleted", () => {
        const initial = [makeElement("a"), makeElement("b")];
        getState().select(initial, RING);

        getState().removeDeleted(new Set(initial.map((element) => element.key)));

        expect(getState().elements).toBeNull();
        expect(getState().boundary).toBeNull();
    });

    it("is a no-op when no deleted key matches", () => {
        getState().select([makeElement("a")], RING);
        const before = getState().elements;

        getState().removeDeleted(new Set(["unrelated-key"]));

        expect(getState().elements).toBe(before);
    });

    it("is a no-op when there is no selection", () => {
        getState().removeDeleted(new Set(["unrelated-key"]));
        expect(getState().elements).toBeNull();
    });

    it("prunes one historical version while another version of the same object lives", () => {
        const versionA = makeHistoricalElement("shared-object", "version-a");
        const versionB = makeHistoricalElement("shared-object", "version-b");
        getState().select([versionA, versionB], RING);

        getState().removeDeleted(new Set([versionA.key]));

        expect(getState().elements?.map((e) => e.id)).toEqual(["version-b"]);
        // Both versions share one tick (`getElementKey` is source|objectId), and
        // one of them survives, so the tick survives with it.
        expect(getState().checkedKeys.has(getElementKey(versionB))).toBe(true);
    });
});

describe("clearAreaSelection — the one dismissal verb", () => {
    const nav = () => useEventsNavigationStore.getState();

    beforeEach(() =>
        useEventsNavigationStore.setState({
            mode: "search",
            listOrigin: null,
            slideDirection: null,
            listPickPending: false,
            listFocusTarget: null,
        }),
    );

    it("drops the selection and the ring together", () => {
        getState().select([makeElement("a")], RING);

        clearAreaSelection();

        expect(getState().elements).toBeNull();
        expect(getState().boundary).toBeNull();
        expect(getState().checkedKeys.size).toBe(0);
    });

    it("a detail opened from the working set loses its back-to-list origin — the breadcrumb cannot outlive the list", () => {
        // lasso → row → detail: `select("selection")` recorded where the detail
        // came from. A zero-match lasso armed over that detail clears the working
        // set; the detail stays, and must not offer a way back to a list that is
        // gone (`listOrigin: null` — "no list to go back to").
        getState().select([makeElement("a")], RING);
        nav().select("selection");

        clearAreaSelection();

        expect(nav().mode).toBe("detail");
        expect(nav().listOrigin).toBeNull();
    });

    it("the selection list falls back to search when its selection is gone", () => {
        getState().select([makeElement("a")], RING);
        nav().areaSelected();
        expect(nav().mode).toBe("selection");

        clearAreaSelection();

        expect(nav().mode).toBe("search");
        expect(nav().listOrigin).toBeNull();
    });

    it("a detail opened from the search list is untouched", () => {
        getState().select([makeElement("a")], RING);
        nav().select("search");

        clearAreaSelection();

        expect(nav().mode).toBe("detail");
        expect(nav().listOrigin).toBe("search");
    });

    it("does not reach into what the panel decides for itself — the auto-open channels and the slide", () => {
        getState().select([makeElement("a")], RING);
        nav().areaSelected();
        useEventsNavigationStore.setState({ slideDirection: "back" });

        clearAreaSelection();

        expect(nav().selectionAutoDismissed).toBe(false);
        expect(nav().autoOpenDismissed).toBe(false);
        expect(nav().slideDirection).toBe("back");
    });
});
