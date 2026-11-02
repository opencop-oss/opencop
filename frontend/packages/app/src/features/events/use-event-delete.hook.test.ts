// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { renderHook } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockMutateAsync = vi.fn();
const mockGetLatestGeojson = vi.fn();
const mockClearPickedElement = vi.fn();
const mockGet = vi.fn();
const mockDelete = vi.fn();
const mockUseMutation = vi.fn();
const mockShowToast = vi.fn();

vi.mock("@tanstack/react-query", async () => {
    const actual =
        await vi.importActual<typeof import("@tanstack/react-query")>("@tanstack/react-query");
    return {
        ...actual,
        useMutation: (...args: unknown[]) => mockUseMutation(...args),
    };
});

// Real ApiError / getErrorMessage from @opencop/api (they are pure utilities, no
// HTTP) — no vi.mock of the API client (banned; network goes through MSW).

vi.mock("@opencop/elements", () => ({
    localEventSource: "local",
    getElementKey: ({ source, objectId }: { source: string; objectId: string }) =>
        `${source}\0${objectId}`,
    Element: class {},
}));

vi.mock("@opencop/ingest", () => ({
    useElementActions: () => ({
        get: mockGet,
        delete: mockDelete,
    }),
}));

vi.mock("./events.queries", () => ({
    eventMutations: {
        broadcastDelete: () => ({ mutationKey: ["events", "broadcast", "delete"] }),
        broadcastSourceDelete: () => ({
            mutationKey: ["events", "broadcast", "source", "delete"],
        }),
    },
    eventQueries: {
        detail: (sourceId: string, objectId: string) => ({
            queryKey: ["events", sourceId, objectId, "detail"],
        }),
    },
}));

vi.mock("./use-event-details.hook", () => ({
    useEventDetails: () => ({
        getLatestGeojson: mockGetLatestGeojson,
    }),
}));

vi.mock("~/features/map/use-picked-element.hook", () => ({
    useClearPickedElement: () => mockClearPickedElement,
}));

vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({
        showToast: mockShowToast,
    }),
}));

import { useEventDelete } from "./use-event-delete.hook";
import { ApiError } from "@opencop/api";

function makeGeojson(source: string) {
    return {
        properties: {
            opencop: {
                objectId: "event-1",
                source,
            },
        },
    };
}

describe("useEventDelete", () => {
    beforeEach(() => {
        mockMutateAsync.mockReset();
        mockGetLatestGeojson.mockReset();
        mockClearPickedElement.mockReset();
        mockGet.mockReset();
        mockDelete.mockReset();
        mockUseMutation.mockReset();
        mockShowToast.mockReset();

        mockUseMutation.mockReturnValue({ mutateAsync: mockMutateAsync });
        mockGet.mockReturnValue({ key: "element-1" });
    });

    it("bypasses the delete mutation for local elements", async () => {
        mockGetLatestGeojson.mockReturnValue(makeGeojson("local"));

        const { result } = renderHook(() => useEventDelete());

        await expect(result.current()).resolves.toBeUndefined();

        expect(mockMutateAsync).not.toHaveBeenCalled();
        expect(mockDelete).toHaveBeenCalledWith("element-1");
        expect(mockClearPickedElement).toHaveBeenCalledTimes(1);
    });

    it("uses the provided mutation target for broadcast elements", async () => {
        mockGetLatestGeojson.mockReturnValue(makeGeojson("server-1"));
        mockMutateAsync.mockResolvedValue(undefined);

        const { result } = renderHook(() => useEventDelete({ kind: "source", id: "server-1" }));

        await expect(result.current()).resolves.toBeUndefined();

        expect(mockMutateAsync).toHaveBeenCalledWith({
            sourceId: "server-1",
            objectId: "event-1",
        });
        expect(mockDelete).toHaveBeenCalledWith("element-1");
        expect(mockClearPickedElement).toHaveBeenCalledTimes(1);
    });

    it("treats a 404 delete as already removed", async () => {
        mockGetLatestGeojson.mockReturnValue(makeGeojson("layer-1"));
        mockMutateAsync.mockRejectedValue(new ApiError(404, { message: "Resource not found" }));

        const { result } = renderHook(() => useEventDelete({ kind: "layer", id: "layer-1" }));

        await expect(result.current()).resolves.toBeUndefined();

        expect(mockDelete).toHaveBeenCalledWith("element-1");
        expect(mockClearPickedElement).toHaveBeenCalledTimes(1);
        expect(mockShowToast).not.toHaveBeenCalled();
    });

    it("shows a toast for non-404 delete failures", async () => {
        mockGetLatestGeojson.mockReturnValue(makeGeojson("layer-1"));
        mockMutateAsync.mockRejectedValue(new Error("boom"));

        const { result } = renderHook(() => useEventDelete({ kind: "layer", id: "layer-1" }));

        await expect(result.current()).resolves.toBeUndefined();

        expect(mockDelete).not.toHaveBeenCalled();
        expect(mockClearPickedElement).not.toHaveBeenCalled();
        expect(mockShowToast).toHaveBeenCalledWith(
            expect.objectContaining({
                type: "error",
                title: "Delete failed",
                description: "boom",
            }),
        );
    });
});
