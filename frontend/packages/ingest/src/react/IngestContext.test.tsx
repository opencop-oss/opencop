// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, renderHook } from "@testing-library/react";
import type { PropsWithChildren } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Circle, localEventSource } from "@opencop/elements";
import { IngestProvider, useIngestContext } from "./IngestContext";
import { useElementActions } from "./use-elements.hook";
import { useLocalSource } from "./use-sources.hook";
import { LocalSource, db } from "../sources/LocalSource";

function Wrapper({ children }: PropsWithChildren) {
    return <IngestProvider>{children}</IngestProvider>;
}

function makeCircle(objectId = "event-1", revId = "rev-1") {
    return Circle.create({
        origin: [10, 20],
        radius: 250,
        meta: { id: revId, objectId, source: localEventSource },
    });
}

describe("IngestProvider", () => {
    beforeEach(() => {
        const toArray = vi.fn().mockResolvedValue([]);
        const equals = vi.fn().mockReturnValue({ toArray });
        vi.spyOn(db.elements, "where").mockReturnValue({ equals } as never);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("routes local element add/delete through LocalSource so it owns the persisted rows", () => {
        const persist = vi.spyOn(LocalSource.prototype, "persist").mockImplementation(() => {});
        const remove = vi.spyOn(LocalSource.prototype, "remove").mockImplementation(() => {});
        const { result } = renderHook(() => useElementActions(), { wrapper: Wrapper });
        const element = makeCircle();

        act(() => {
            result.current.add(element);
        });
        expect(result.current.get(element.key)).toBeDefined();

        act(() => {
            result.current.delete(element.key);
        });

        expect(persist).toHaveBeenCalledWith(element);
        expect(remove).toHaveBeenCalledWith(element.key);
        // The delete is projected into the store as a stale-dated element (the
        // same mechanism a WebSocket delete uses), so the element is gone.
        expect(result.current.get(element.key)).toBeUndefined();
    });

    // Load-bearing property for the live/historical store unification: a source being
    // *evicted* (disabled on a mode switch) must clear its rows from the ElementStore
    // WITHOUT deleting the user's persisted data. Eviction ("the source went away") and
    // deletion ("the user removed this element") are different operations.
    it("disabling the local source evicts its elements from the store without deleting the persisted rows", () => {
        vi.spyOn(db.elements, "put").mockResolvedValue("local\0event-1");
        const dbDelete = vi.spyOn(db.elements, "delete").mockResolvedValue(undefined);

        const { result } = renderHook(
            () => ({
                elements: useElementActions(),
                controller: useIngestContext().controller,
                localSource: useLocalSource(),
            }),
            { wrapper: Wrapper },
        );
        // The local source is an ordinary source: the app admits it by contributing
        // a slice (see RegisterMySources), so do the same here via the controller.
        act(() => {
            result.current.controller.getState().reconcile("test", [result.current.localSource]);
        });
        const element = makeCircle();

        act(() => {
            result.current.elements.add(element);
        });
        expect(result.current.elements.get(element.key)).toBeDefined();

        act(() => {
            result.current.controller.getState().disable(localEventSource);
        });

        // Evicted from the store...
        expect(result.current.elements.get(element.key)).toBeUndefined();
        // ...but the persisted row survives, so re-enabling can re-hydrate it.
        expect(dbDelete).not.toHaveBeenCalled();
    });
});
