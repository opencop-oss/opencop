// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it, vi } from "vitest";
import { Element, PointGeometry, PointStyle } from "@opencop/elements";
import Logger from "js-logger";
import { useMapFilterStore, type MapFilter } from "./map-filter.store";

const PERSIST_KEY = "opencop:map-filter";

function actions() {
    return useMapFilterStore.getState().actions;
}

function filterNames() {
    return useMapFilterStore.getState().filters.map(({ name }) => name);
}

/** Names of the *compiled* filters — what the map actually evaluates. */
function compiledNames() {
    return useMapFilterStore.getState().active.map(({ name }) => name);
}

/** Matches `displayName === value`; `value` defaults to the filter's own name. */
function namedFilter(name: string, value = name): MapFilter {
    return {
        name,
        combine: "ALL",
        predicates: [
            { operation: "EQUALS", path: "properties.opencop.displayName", values: [value] },
        ],
    };
}

/** A path `accessorFromPredicate` cannot resolve, so `compile` throws on it. */
function unusableFilter(name: string): MapFilter {
    return {
        name,
        combine: "ALL",
        predicates: [{ operation: "EQUALS", path: "properties.opencop.removed", values: ["x"] }],
    };
}

function element(displayName: string) {
    return new Element({
        type: "point",
        displayName,
        geometry: new PointGeometry([0, 0]),
        style: new PointStyle({ color: "", radius: 0 }),
    });
}

describe("map-filter store", () => {
    beforeEach(() => {
        useMapFilterStore.setState({ filters: [], activeNames: [], active: [] });
        localStorage.clear();
        vi.restoreAllMocks();
    });

    describe("addFilter", () => {
        it("registers a filter without activating it", () => {
            actions().addFilter(namedFilter("alpha"));

            expect(filterNames()).toEqual(["alpha"]);
            expect(useMapFilterStore.getState().activeNames).toEqual([]);
            expect(compiledNames()).toEqual([]);
        });

        it("replaces a filter of the same name in place rather than appending a duplicate", () => {
            actions().addFilter(namedFilter("alpha"));
            actions().addFilter(namedFilter("beta"));

            actions().addFilter(namedFilter("alpha", "changed"));

            expect(filterNames()).toEqual(["alpha", "beta"]);
        });

        it("replaces a numerically named filter in place, keeping registration order", () => {
            actions().addFilter(namedFilter("alpha"));
            actions().addFilter(namedFilter("0"));
            actions().addFilter(namedFilter("beta"));

            actions().addFilter(namedFilter("0", "changed"));

            // "0" must neither duplicate nor hoist to the front the way an
            // integer-like key would if the list were ever keyed by name.
            expect(filterNames()).toEqual(["alpha", "0", "beta"]);
        });

        it("recompiles an active filter when its definition is replaced", () => {
            actions().addFilter(namedFilter("alpha", "before"));
            actions().activateFilter("alpha");

            actions().addFilter(namedFilter("alpha", "after"));

            const [compiled] = useMapFilterStore.getState().active;
            expect(compiled.execute({ element: element("after") })).toBe(true);
            expect(compiled.execute({ element: element("before") })).toBe(false);
        });
    });

    describe("activateFilter", () => {
        it("compiles the named filter", () => {
            actions().addFilter(namedFilter("alpha"));

            actions().activateFilter("alpha");

            expect(useMapFilterStore.getState().activeNames).toEqual(["alpha"]);
            expect(compiledNames()).toEqual(["alpha"]);
        });

        it("returns the identical state when the filter is already active", () => {
            actions().addFilter(namedFilter("alpha"));
            actions().activateFilter("alpha");
            const before = useMapFilterStore.getState();

            actions().activateFilter("alpha");

            expect(useMapFilterStore.getState()).toBe(before);
        });

        it("returns the identical state for a name no filter claims", () => {
            actions().addFilter(namedFilter("alpha"));
            const before = useMapFilterStore.getState();

            actions().activateFilter("missing");

            expect(useMapFilterStore.getState()).toBe(before);
        });
    });

    describe("deactivateFilter", () => {
        it("stops compiling the filter but keeps its definition", () => {
            actions().addFilter(namedFilter("alpha"));
            actions().addFilter(namedFilter("beta"));
            actions().activateFilter("alpha");
            actions().activateFilter("beta");

            actions().deactivateFilter("alpha");

            expect(filterNames()).toEqual(["alpha", "beta"]);
            expect(useMapFilterStore.getState().activeNames).toEqual(["beta"]);
            expect(compiledNames()).toEqual(["beta"]);
        });
    });

    describe("removeFilter", () => {
        it("discards the definition and its activation together", () => {
            actions().addFilter(namedFilter("alpha"));
            actions().addFilter(namedFilter("beta"));
            actions().activateFilter("alpha");
            actions().activateFilter("beta");

            actions().removeFilter("alpha");

            expect(filterNames()).toEqual(["beta"]);
            expect(useMapFilterStore.getState().activeNames).toEqual(["beta"]);
            expect(compiledNames()).toEqual(["beta"]);
        });
    });

    describe("uncompilable filters", () => {
        it("drops one that no longer compiles, keeping the others active", () => {
            const warnSpy = vi
                .spyOn(Logger.get("map-filter"), "warn")
                .mockImplementation(() => undefined);
            actions().addFilter(unusableFilter("broken"));
            actions().addFilter(namedFilter("alpha"));

            actions().activateFilter("broken");
            actions().activateFilter("alpha");

            // Still activated as far as the user is concerned — only the compiled form is missing.
            expect(useMapFilterStore.getState().activeNames).toEqual(["broken", "alpha"]);
            expect(compiledNames()).toEqual(["alpha"]);
            expect(warnSpy).toHaveBeenCalled();
        });
    });

    describe("persistence", () => {
        it("persists the definitions and activation intent, never the compiled closures", () => {
            actions().addFilter(namedFilter("alpha"));
            actions().activateFilter("alpha");

            const persisted = JSON.parse(localStorage.getItem(PERSIST_KEY)!) as {
                state: Record<string, unknown>;
            };

            expect(Object.keys(persisted.state).sort()).toEqual(["activeNames", "filters"]);
            expect(persisted.state.activeNames).toEqual(["alpha"]);
        });

        it("recompiles the active set on rehydration", async () => {
            localStorage.setItem(
                PERSIST_KEY,
                JSON.stringify({
                    state: { filters: [namedFilter("alpha")], activeNames: ["alpha"] },
                    version: 0,
                }),
            );

            await useMapFilterStore.persist.rehydrate();

            expect(filterNames()).toEqual(["alpha"]);
            expect(compiledNames()).toEqual(["alpha"]);
        });

        it("hydrates around a persisted filter that no longer compiles", async () => {
            const warnSpy = vi
                .spyOn(Logger.get("map-filter"), "warn")
                .mockImplementation(() => undefined);
            localStorage.setItem(
                PERSIST_KEY,
                JSON.stringify({
                    state: {
                        filters: [unusableFilter("broken"), namedFilter("alpha")],
                        activeNames: ["broken", "alpha"],
                    },
                    version: 0,
                }),
            );

            await useMapFilterStore.persist.rehydrate();

            expect(filterNames()).toEqual(["broken", "alpha"]);
            expect(compiledNames()).toEqual(["alpha"]);
            expect(warnSpy).toHaveBeenCalled();
        });
    });
});
