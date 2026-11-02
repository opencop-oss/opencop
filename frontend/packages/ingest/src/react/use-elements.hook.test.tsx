// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, renderHook } from "@testing-library/react";
import type { PropsWithChildren } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Circle } from "@opencop/elements";
import { ElementStore } from "../ElementStore";
import { IngestProvider } from "./IngestContext";
import {
    useAllElements,
    useElement,
    useElementActions,
    useElementCount,
    useElementsBySource,
    useElementsByType,
    useSourceCount,
} from "./use-elements.hook";
import { db } from "../sources/LocalSource";

function Wrapper({ children }: PropsWithChildren) {
    return <IngestProvider>{children}</IngestProvider>;
}

function makeCircle(objectId: string) {
    return Circle.create({
        origin: [10, 20],
        radius: 250,
        meta: { id: `rev-${objectId}`, objectId, source: "source-1" },
    });
}

describe("useAllElements", () => {
    beforeEach(() => {
        const toArray = vi.fn().mockResolvedValue([]);
        const equals = vi.fn().mockReturnValue({ toArray });
        vi.spyOn(db.elements, "where").mockReturnValue({ equals } as never);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("does not subscribe or rebuild the full snapshot while disabled", () => {
        const allTypes = vi.spyOn(ElementStore.prototype, "allTypes");
        const on = vi.spyOn(ElementStore.prototype, "on");
        const { result, rerender } = renderHook(
            ({ enabled }) => ({
                elements: useAllElements({ enabled }),
                actions: useElementActions(),
            }),
            { wrapper: Wrapper, initialProps: { enabled: false } },
        );
        const emptySnapshot = result.current.elements;

        rerender({ enabled: false });

        expect(result.current.elements).toBe(emptySnapshot);
        expect(allTypes).not.toHaveBeenCalled();
        expect(on).not.toHaveBeenCalledWith("update", expect.any(Function));

        rerender({ enabled: true });

        expect(allTypes).toHaveBeenCalled();
        expect(on).toHaveBeenCalledWith("update", expect.any(Function));

        act(() => result.current.actions.add(makeCircle("event-1")));
        expect(result.current.elements).toHaveLength(1);

        rerender({ enabled: false });
        const rebuildCount = allTypes.mock.calls.length;
        act(() => result.current.actions.add(makeCircle("event-2")));

        expect(result.current.elements).toBe(emptySnapshot);
        expect(allTypes).toHaveBeenCalledTimes(rebuildCount);
    });

    it("caches the initial snapshot and keeps all shared selectors reactive", () => {
        const allTypes = vi.spyOn(ElementStore.prototype, "allTypes");
        const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
        const element = makeCircle("event-1");
        const { result } = renderHook(
            () => ({
                actions: useElementActions(),
                all: useAllElements(),
                bySource: useElementsBySource(element.source),
                byType: useElementsByType(element.type),
                count: useElementCount(),
                element: useElement(element.key),
                sourceCount: useSourceCount(element.source),
            }),
            { wrapper: Wrapper },
        );

        expect(allTypes).toHaveBeenCalledTimes(1);
        expect(consoleError).not.toHaveBeenCalledWith(
            expect.stringContaining("The result of getSnapshot should be cached"),
        );

        act(() => result.current.actions.add(element));

        expect(result.current.all).toEqual([element]);
        expect(Array.from(result.current.bySource() ?? [])).toEqual([element]);
        expect(Array.from(result.current.byType ?? [])).toEqual([element]);
        expect(result.current.count).toBe(1);
        expect(result.current.element).toBe(element);
        expect(result.current.sourceCount).toBe(1);
    });
});
