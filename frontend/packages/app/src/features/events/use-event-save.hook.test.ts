// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { renderHook } from "~/test";
import { QueryClient } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { EventDetails } from "@opencop/api";

const mockCreateBroadcast = vi.fn();
const mockPatchBroadcast = vi.fn();
const mockPatchSourceBroadcast = vi.fn();
const mockDeleteBroadcast = vi.fn();
const mockSave = vi.fn();
const mockFindElement = vi.fn();
const mockDelete = vi.fn();
const mockClearSelection = vi.fn();
const mockGetLatestGeojson = vi.fn();
const mockUsePickedElementRef = vi.fn();
let mockOverlay: Record<string, unknown> | null;

const mockElementStore = {
    get: vi.fn(),
    getDraft: vi.fn(),
};

// The broadcast API functions are wired into the mutations via the
// `./events.queries` mock below (mutationFn: mockCreateBroadcast, …), so the
// hook never calls the real API client — no API-client mock is needed here
// (network goes through MSW when a hook does reach it).

// NOTE: @opencop/elements is intentionally NOT mocked. It is pure, in-process
// logic (no HTTP/auth/websocket/filesystem boundary), so per the repo's
// "mock only at system boundaries" convention it should run for real. Using the
// real module is also what makes this test meaningful: the real `Element` always
// assigns `objectId ?? uuid.v4()` (Element.ts), so a freshly-drawn element can
// never have an empty objectId — the impossible state the old MockElement allowed,
// which let the broadcast-create routing bug hide. The real `localEventSource`
// ("local") is what the discriminator keys off.

vi.mock("./events.queries", () => ({
    eventMutations: {
        broadcastCreate: (onSuccess?: (data: unknown, variables: unknown) => void) => ({
            mutationKey: ["events", "broadcast", "create"],
            mutationFn: mockCreateBroadcast,
            onSuccess,
        }),
        broadcastPatch: (onSuccess?: (data: unknown, variables: unknown) => void) => ({
            mutationKey: ["events", "broadcast", "patch"],
            mutationFn: mockPatchBroadcast,
            onSuccess,
        }),
        broadcastSourcePatch: (onSuccess?: (data: unknown, variables: unknown) => void) => ({
            mutationKey: ["events", "broadcast", "source", "patch"],
            mutationFn: mockPatchSourceBroadcast,
            onSuccess,
        }),
        broadcastDelete: () => ({
            mutationKey: ["events", "broadcast", "delete"],
            mutationFn: mockDeleteBroadcast,
        }),
    },
    eventQueries: {
        detail: (sourceId: string, objectId: string) => ({
            queryKey: ["events", sourceId, objectId, "detail"],
        }),
    },
}));

vi.mock("@opencop/ingest", () => ({
    useElementActions: () => mockElementStore,
    useSourcesWithState: () => new Set(),
}));

vi.mock("./use-event-details.hook", () => ({
    useEventDetails: () => ({
        getLatestGeojson: mockGetLatestGeojson,
    }),
}));

vi.mock("~/features/map/use-picked-element.hook", () => ({
    usePickedElementRef: () => mockUsePickedElementRef(),
}));

vi.mock("~/features/map/interaction-runtime/session/commands", () => ({
    save: (...args: unknown[]) => mockSave(...args),
}));

vi.mock("~/features/map/MapManagerContext", () => ({
    useMapManager: () => ({
        findElement: mockFindElement,
        delete: mockDelete,
        clearSelection: mockClearSelection,
    }),
}));

vi.mock("./events-editing.store", () => ({
    useEventsEditingStore: {
        getState: () => ({
            overlay: mockOverlay,
        }),
    },
}));

import { MoveRebroadcastError, useEventSave } from "./use-event-save.hook";

function makeGeojson(source = "layer-1", objectId = "event-1") {
    return {
        type: "Feature" as const,
        geometry: { type: "Point" as const, coordinates: [0, 0] as [number, number] },
        properties: {
            opencop: {
                id: "rev-1",
                objectId,
                displayName: "Original Name",
                source,
                element: { type: "point" },
                timestampz: {
                    start: 10,
                    time: 15,
                    stale: 25,
                },
                attributes: {},
            },
        },
    };
}

function makeStoredElement() {
    return {
        type: "point",
        id: "local-rev-1",
        objectId: "event-1",
        source: "layer-1",
        start: 10,
        stale: 25,
        heading: null,
        course: null,
        speed: null,
        hae: undefined,
        geometry: { kind: "geometry" },
        getDisplayName: () => "Original Name",
        getCode: () => undefined,
        getModifiers: () => ({}),
        getStyle: () => ({ kind: "style" }),
        getAttributes: () => ({ existing: true }),
    };
}

function makeQueryClient() {
    return new QueryClient({
        defaultOptions: {
            queries: { retry: false },
            mutations: { retry: false },
        },
    });
}

describe("useEventSave", () => {
    beforeEach(() => {
        mockCreateBroadcast.mockReset();
        mockPatchBroadcast.mockReset();
        mockPatchSourceBroadcast.mockReset();
        mockDeleteBroadcast.mockReset();
        mockSave.mockReset();
        mockFindElement.mockReset();
        mockDelete.mockReset();
        mockClearSelection.mockReset();
        mockGetLatestGeojson.mockReset();
        mockUsePickedElementRef.mockReset();
        mockElementStore.get.mockReset();

        mockOverlay = {
            source: "layer-1",
            displayName: "Overlay Name",
            timestampz: {
                start: 20,
                stale: 30,
            },
            attributes: {
                priority: "high",
            },
        };

        mockGetLatestGeojson.mockReturnValue(makeGeojson());
        mockUsePickedElementRef.mockReturnValue({ sourceId: "layer-1", objectId: "event-1" });
        mockElementStore.get.mockReturnValue(makeStoredElement());
    });

    it("falls back to the optimistic element when the create response is empty", async () => {
        const queryClient = makeQueryClient();
        mockGetLatestGeojson.mockReturnValue(makeGeojson("local", "opt-uuid-1"));
        mockCreateBroadcast.mockResolvedValue(undefined);

        const { result } = renderHook(() => useEventSave(), { queryClient });

        await expect(result.current()).resolves.toBeUndefined();

        expect(mockCreateBroadcast).toHaveBeenCalledTimes(1);
        expect(mockPatchBroadcast).not.toHaveBeenCalled();
        expect(mockSave).toHaveBeenCalledTimes(1);

        const savedElement = mockSave.mock.calls[0][0];
        expect(savedElement.id).toBe("local-rev-1");
        expect(savedElement.objectId).toBe("event-1");
        expect(savedElement.source).toBe("layer-1");

        expect(
            queryClient.getQueryData(["events", "layer-1", "event-1", "detail"]),
        ).toBeUndefined();
    });

    it("creates (not patches) a freshly-drawn local shape broadcast to a layer", async () => {
        const queryClient = makeQueryClient();
        // A real new element: original source "local" but a non-empty
        // client-generated objectId (every Element gets one via uuid.v4()).
        // overlay.source = "layer-1" (from beforeEach), so the user targeted a
        // layer — exactly the scenario that triggered the regression. There is
        // no mutationTarget (editability returns none for local elements), so the
        // routing must pick broadcast-create, not broadcast-patch.
        mockGetLatestGeojson.mockReturnValue(makeGeojson("local", "local-uuid-1"));
        mockUsePickedElementRef.mockReturnValue({ sourceId: "local", objectId: "local-uuid-1" });
        mockElementStore.get.mockReturnValue({
            ...makeStoredElement(),
            source: "local",
            objectId: "local-uuid-1",
        });
        mockCreateBroadcast.mockResolvedValue(undefined);

        const { result } = renderHook(() => useEventSave(), { queryClient });

        // Pre-fix this rejects with "Missing mutation target for remote event".
        await expect(result.current()).resolves.toBeUndefined();

        expect(mockCreateBroadcast).toHaveBeenCalledTimes(1);
        expect(mockPatchBroadcast).not.toHaveBeenCalled();
        expect(mockPatchSourceBroadcast).not.toHaveBeenCalled();
    });

    it("saves a new shape locally when no layer is chosen", async () => {
        const queryClient = makeQueryClient();
        // Overlay with no source → resolved source falls back to the geojson's
        // "local" → the saveLocal() branch, which no prior test exercised.
        mockOverlay = { displayName: "Local Only", timestampz: {}, attributes: {} };
        mockGetLatestGeojson.mockReturnValue(makeGeojson("local", "local-uuid-2"));
        mockUsePickedElementRef.mockReturnValue({ sourceId: "local", objectId: "local-uuid-2" });
        mockElementStore.get.mockReturnValue({
            ...makeStoredElement(),
            source: "local",
            objectId: "local-uuid-2",
        });

        const { result } = renderHook(() => useEventSave(), { queryClient });

        await expect(result.current()).resolves.toBeUndefined();

        // Local path: delegates to the tool-action save, hits no broadcast endpoint.
        expect(mockSave).toHaveBeenCalledTimes(1);
        // Pin the branch directly: saveLocal() rebuilds the element keeping the local
        // source, whereas any broadcast path would set it to the target layer id. This
        // makes the assertion prove the isLocal → saveLocal() route rather than infer it
        // from the absence of broadcast calls.
        expect(mockSave.mock.calls[0][0].source).toBe("local");
        expect(mockCreateBroadcast).not.toHaveBeenCalled();
        expect(mockPatchBroadcast).not.toHaveBeenCalled();
        expect(mockPatchSourceBroadcast).not.toHaveBeenCalled();
    });

    it("uses the patch mutation and applies server identity plus cache merge data", async () => {
        const queryClient = makeQueryClient();
        queryClient.setQueryData<EventDetails>(["events", "layer-2", "server-object-2", "detail"], {
            id: "old-id",
            objectId: "old-object",
            source: "old-source",
            displayName: "Old name",
            element: { type: "point", fillColor: "#ffffff" },
            version: "1",
            contacts: [],
            kind: "track",
            attributes: { existing: 1 },
            extensions: [],
            timestampz: { start: 1, time: 2, stale: 3 },
        });

        mockPatchBroadcast.mockResolvedValue({
            type: "Feature",
            geometry: { type: "Point", coordinates: [1, 1] },
            properties: {
                opencop: {
                    id: "server-rev-2",
                    objectId: "server-object-2",
                    displayName: "Server Name",
                    source: "layer-2",
                    element: { type: "point" },
                    timestampz: {
                        time: 100,
                        start: 200,
                        stale: 300,
                    },
                },
            },
        });

        const { result } = renderHook(() => useEventSave({ kind: "layer", id: "layer-1" }), {
            queryClient,
        });

        await expect(result.current()).resolves.toBeUndefined();

        expect(mockPatchBroadcast).toHaveBeenCalledTimes(1);
        expect(mockCreateBroadcast).not.toHaveBeenCalled();

        const savedElement = mockSave.mock.calls[0][0];
        expect(savedElement.id).toBe("server-rev-2");
        expect(savedElement.objectId).toBe("server-object-2");
        expect(savedElement.source).toBe("layer-2");
        expect(savedElement.time).toBe(100);
        expect(savedElement.start).toBe(200);
        expect(savedElement.stale).toBe(300);

        expect(
            queryClient.getQueryData(["events", "layer-2", "server-object-2", "detail"]),
        ).toEqual({
            id: "server-rev-2",
            objectId: "server-object-2",
            source: "layer-2",
            displayName: "Server Name",
            element: { type: "point" },
            version: "1",
            contacts: [],
            kind: "track",
            attributes: { existing: 1 },
            extensions: [],
            timestampz: { time: 100, start: 200, stale: 300 },
        });
    });

    it("routes data-source events through the source mutation target", async () => {
        const queryClient = makeQueryClient();
        mockOverlay = {
            displayName: "Overlay Name",
            timestampz: {
                start: 20,
                stale: 30,
            },
            attributes: {
                priority: "high",
            },
        };
        mockGetLatestGeojson.mockReturnValue(makeGeojson("server-1"));
        mockUsePickedElementRef.mockReturnValue({ sourceId: "server-1", objectId: "event-1" });
        mockElementStore.get.mockReturnValue({
            ...makeStoredElement(),
            source: "server-1",
        });
        mockPatchSourceBroadcast.mockResolvedValue(undefined);

        const { result } = renderHook(() => useEventSave({ kind: "source", id: "server-1" }), {
            queryClient,
        });

        await expect(result.current()).resolves.toBeUndefined();

        expect(mockPatchSourceBroadcast).toHaveBeenCalledWith(
            expect.objectContaining({
                sourceId: "server-1",
                objectId: "event-1",
                feature: expect.objectContaining({
                    properties: expect.objectContaining({
                        opencop: expect.objectContaining({
                            source: "server-1",
                        }),
                    }),
                }),
            }),
            expect.anything(),
        );
        expect(mockPatchBroadcast).not.toHaveBeenCalled();
    });

    describe("move", () => {
        beforeEach(() => {
            // The user retargeted the layer field with "move" ticked.
            mockOverlay = { ...mockOverlay, source: "layer-2" };
            mockDeleteBroadcast.mockResolvedValue(undefined);
            mockCreateBroadcast.mockResolvedValue(undefined);
        });

        it("deletes from the old layer, then creates on the new one", async () => {
            const queryClient = makeQueryClient();

            const { result } = renderHook(
                () => useEventSave({ kind: "layer", id: "layer-1" }, true),
                { queryClient },
            );

            await expect(result.current()).resolves.toBeUndefined();

            expect(mockDeleteBroadcast).toHaveBeenCalledWith(
                { targetLayer: "layer-1", objectId: "event-1" },
                expect.anything(),
            );
            expect(mockCreateBroadcast).toHaveBeenCalledWith(
                expect.objectContaining({
                    targetLayer: "layer-2",
                    feature: expect.objectContaining({
                        properties: expect.objectContaining({
                            opencop: expect.objectContaining({ source: "layer-2" }),
                        }),
                    }),
                }),
                expect.anything(),
            );
            // Ordering is load-bearing: the event must never be live in both
            // sources at once.
            expect(mockDeleteBroadcast.mock.invocationCallOrder[0]).toBeLessThan(
                mockCreateBroadcast.mock.invocationCallOrder[0],
            );
            // A move is a delete + create, never a patch of either source.
            expect(mockPatchBroadcast).not.toHaveBeenCalled();
            expect(mockPatchSourceBroadcast).not.toHaveBeenCalled();

            // The element survives the remote delete and is re-saved under the
            // target layer, so the panel keeps a selected event throughout.
            expect(mockSave).toHaveBeenCalledTimes(1);
            expect(mockSave.mock.calls[0][0].source).toBe("layer-2");
        });

        it("still re-adds the element when the delete evicts it from the store first", async () => {
            const queryClient = makeQueryClient();
            const stored = makeStoredElement();

            // The server republishes a deleted event as stale, and ElementStore
            // treats a stale arrival as a removal — ahead of its own edit lock.
            // So by the time the create resolves, the store lookup is empty.
            mockDeleteBroadcast.mockImplementation(() => {
                mockElementStore.get.mockReturnValue(undefined);
                return Promise.resolve(undefined);
            });

            const { result } = renderHook(
                () => useEventSave({ kind: "layer", id: "layer-1" }, true),
                { queryClient },
            );

            await expect(result.current()).resolves.toBeUndefined();

            // Without the pre-mutation snapshot this saves `undefined` and the
            // event disappears from the map instead of landing on layer-2.
            expect(mockSave).toHaveBeenCalledTimes(1);
            const savedElement = mockSave.mock.calls[0][0];
            expect(savedElement).toBeDefined();
            expect(savedElement.source).toBe("layer-2");
            expect(savedElement.objectId).toBe(stored.objectId);
        });

        it("drops the old detail cache entry", async () => {
            const queryClient = makeQueryClient();
            queryClient.setQueryData(["events", "layer-1", "event-1", "detail"], {
                id: "rev-1",
                objectId: "event-1",
                source: "layer-1",
            });

            const { result } = renderHook(
                () => useEventSave({ kind: "layer", id: "layer-1" }, true),
                { queryClient },
            );

            await expect(result.current()).resolves.toBeUndefined();

            expect(
                queryClient.getQueryData(["events", "layer-1", "event-1", "detail"]),
            ).toBeUndefined();
        });

        it("rejects a data-source move before anything reaches the network", async () => {
            // getEditability never grants canBroadcast to a data-source event, so
            // the form never offers the move — planSave backs that gate up by
            // throwing rather than forking the event out of its upstream feed.
            const queryClient = makeQueryClient();
            mockGetLatestGeojson.mockReturnValue(makeGeojson("server-1"));
            mockUsePickedElementRef.mockReturnValue({ sourceId: "server-1", objectId: "event-1" });
            mockElementStore.get.mockReturnValue({
                ...makeStoredElement(),
                source: "server-1",
            });

            const { result } = renderHook(
                () => useEventSave({ kind: "source", id: "server-1" }, true),
                { queryClient },
            );

            await expect(result.current()).rejects.toThrow("Data-source events cannot be moved");

            expect(mockDeleteBroadcast).not.toHaveBeenCalled();
            expect(mockCreateBroadcast).not.toHaveBeenCalled();
            expect(mockPatchSourceBroadcast).not.toHaveBeenCalled();
        });

        it("rejects with MoveRebroadcastError when the create fails after the delete", async () => {
            const queryClient = makeQueryClient();
            mockCreateBroadcast.mockRejectedValue(new Error("boom"));

            const { result } = renderHook(
                () => useEventSave({ kind: "layer", id: "layer-1" }, true),
                { queryClient },
            );

            // The typed rejection lets the form say the delete already committed
            // instead of presenting this like any other save failure.
            await expect(result.current()).rejects.toThrow(MoveRebroadcastError);
            expect(mockDeleteBroadcast).toHaveBeenCalledTimes(1);
            expect(mockSave).not.toHaveBeenCalled();
        });

        it("does not delete anything when move is off", async () => {
            const queryClient = makeQueryClient();
            mockPatchBroadcast.mockResolvedValue(undefined);

            const { result } = renderHook(() => useEventSave({ kind: "layer", id: "layer-1" }), {
                queryClient,
            });

            await expect(result.current()).resolves.toBeUndefined();

            expect(mockDeleteBroadcast).not.toHaveBeenCalled();
            expect(mockPatchBroadcast).toHaveBeenCalledTimes(1);
        });
    });

    it("ignores malformed responses and keeps the optimistic identity intact", async () => {
        const queryClient = makeQueryClient();
        mockPatchBroadcast.mockResolvedValue({ ok: true });

        const { result } = renderHook(() => useEventSave({ kind: "layer", id: "layer-1" }), {
            queryClient,
        });

        await expect(result.current()).resolves.toBeUndefined();

        const savedElement = mockSave.mock.calls[0][0];
        expect(savedElement.id).toBe("local-rev-1");
        expect(savedElement.objectId).toBe("event-1");
        expect(savedElement.source).toBe("layer-1");

        expect(
            queryClient.getQueryData(["events", "layer-1", "event-1", "detail"]),
        ).toBeUndefined();
    });
});
