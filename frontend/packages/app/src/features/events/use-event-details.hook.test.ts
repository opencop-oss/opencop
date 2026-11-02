// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { renderHook } from "~/test";
import { describe, expect, it, vi, beforeEach } from "vitest";

const mockUseQuery = vi.fn();
const mockUsePickedElementRef = vi.fn();
const mockUsePickedElement = vi.fn();
const mockFlush = vi.fn();

// Partial mock: keep QueryClientProvider real, and route the harness
// AuthProvider's ["auth","session"] query to the *real* useQuery (resolved from
// the seeded auth cache) so it renders children, while the hook-under-test's
// detail query is driven by mockUseQuery.
vi.mock("@tanstack/react-query", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@tanstack/react-query")>();
    return {
        ...actual,
        useQuery: (options: { queryKey?: readonly unknown[] }) =>
            options?.queryKey?.[0] === "auth"
                ? actual.useQuery(options as Parameters<typeof actual.useQuery>[0])
                : mockUseQuery(options),
    };
});

vi.mock("@tanstack/react-pacer", () => ({
    useThrottledValue: (value: unknown) => [value, { flush: mockFlush }],
}));

vi.mock("~/features/map/use-picked-element.hook", () => ({
    usePickedElementRef: () => mockUsePickedElementRef(),
    usePickedElement: () => mockUsePickedElement(),
}));

vi.mock("./events.queries", () => ({
    eventQueries: {
        detail: (sourceId: string, objectId: string) => ({
            queryKey: ["events", "detail", sourceId, objectId],
            queryFn: vi.fn(),
            enabled: !!sourceId && !!objectId,
        }),
    },
}));

import { useEventDetails } from "./use-event-details.hook";

const makeLiveElement = () => ({
    source: "layer-1",
    toFeature: () => ({
        type: "Feature" as const,
        geometry: { type: "Point" as const, coordinates: [0, 0] as [number, number] },
        properties: {
            opencop: {
                id: "element-1",
                objectId: "event-1",
                displayName: "Test Event",
                source: "layer-1",
                element: { type: "point" },
                timestampz: {
                    start: 2_000,
                    time: 3_000,
                    stale: 4_000,
                },
            },
        },
    }),
});

describe("useEventDetails", () => {
    beforeEach(() => {
        mockUseQuery.mockReset();
        mockUsePickedElementRef.mockReset();
        mockUsePickedElement.mockReset();
        mockFlush.mockReset();

        mockUsePickedElementRef.mockReturnValue({
            sourceId: "layer-1",
            objectId: "event-1",
        });
        mockUsePickedElement.mockReturnValue(makeLiveElement());
    });

    it("keeps live websocket timestamps when same-event detail data is placeholder", () => {
        mockUseQuery.mockReturnValue({
            isLoading: false,
            error: null,
            data: {
                objectId: "event-1",
                source: "layer-1",
                version: "1",
                contacts: [],
                kind: "track",
                attributes: {},
                extensions: [],
                remarks: [],
                timestampz: {
                    start: 100,
                    time: 200,
                    stale: 300,
                },
            },
            isPlaceholderData: true,
        });

        const { result } = renderHook(() => useEventDetails());

        expect(result.current.details?.timestampz).toEqual({
            start: 2_000,
            time: 3_000,
            stale: 4_000,
        });
    });

    it("exposes the live element alongside the merged details", () => {
        mockUseQuery.mockReturnValue({ isLoading: false, error: null, data: undefined });

        const { result } = renderHook(() => useEventDetails());

        expect(result.current.element).toBe(mockUsePickedElement.mock.results[0]?.value);
        expect(result.current.hasElement).toBe(true);
    });

    it("ignores placeholder detail data from a different event", () => {
        mockUseQuery.mockReturnValue({
            isLoading: false,
            error: null,
            data: {
                objectId: "event-2",
                source: "layer-1",
                version: "1",
                contacts: [],
                kind: "track",
                attributes: {},
                extensions: [],
                remarks: [],
                timestampz: {
                    start: 100,
                    time: 200,
                    stale: 300,
                },
            },
            isPlaceholderData: true,
        });

        const { result } = renderHook(() => useEventDetails());

        expect(result.current.details?.timestampz).toEqual({
            start: 2_000,
            time: 3_000,
            stale: 4_000,
        });
    });
});
