// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { renderHook, act } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockMutateAsync = vi.fn();
const mockMutate = vi.fn();
const mockUseMutation = vi.fn();
const mockUseQueryClient = vi.fn();

// Partial mock: keep QueryClientProvider/useQuery real (the harness AuthProvider
// needs them) and override only the mutation entry points this hook uses.
vi.mock("@tanstack/react-query", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@tanstack/react-query")>()),
    useMutation: (...args: unknown[]) => mockUseMutation(...args),
    useQueryClient: () => mockUseQueryClient(),
}));

vi.mock("./events.queries", () => ({
    eventMutations: {
        attachments: {
            upload: (queryClient: unknown) => ({
                mutationKey: ["attachments", "upload"],
                queryClient,
            }),
            registerUrl: (queryClient: unknown) => ({
                mutationKey: ["attachments", "registerUrl"],
                queryClient,
            }),
            delete: (queryClient: unknown) => ({
                mutationKey: ["attachments", "delete"],
                queryClient,
            }),
        },
    },
}));

import { useAttachmentMutations } from "./use-attachment-mutations.hook";

const SOURCE_ID = "source-1";
const OBJECT_ID = "object-1";
const MEDIA_ID = "media-1";
const mockFile = new File(["content"], "test.png", { type: "image/png" });
const mockQueryClient = {};

describe("useAttachmentMutations", () => {
    beforeEach(() => {
        mockMutateAsync.mockReset();
        mockMutate.mockReset();
        mockUseMutation.mockReset();
        mockUseQueryClient.mockReset();

        mockUseQueryClient.mockReturnValue(mockQueryClient);
        mockUseMutation.mockImplementation(({ mutationKey }) => {
            if (mutationKey[1] === "upload") return { mutateAsync: mockMutateAsync };
            if (mutationKey[1] === "registerUrl")
                return { mutateAsync: mockMutateAsync, isPending: false };
            if (mutationKey[1] === "delete") return { mutate: mockMutate };
            return {};
        });
    });

    it("calls uploadAsync with sourceId, objectId and file", async () => {
        mockMutateAsync.mockResolvedValue(undefined);

        const { result } = renderHook(() =>
            useAttachmentMutations({ sourceId: SOURCE_ID, objectId: OBJECT_ID }),
        );

        await act(() => result.current.uploadAttachment(mockFile));

        expect(mockMutateAsync).toHaveBeenCalledWith({
            sourceId: SOURCE_ID,
            objectId: OBJECT_ID,
            file: mockFile,
        });
    });

    it("calls deleteMutate with sourceId, objectId and mediaId", () => {
        const { result } = renderHook(() =>
            useAttachmentMutations({ sourceId: SOURCE_ID, objectId: OBJECT_ID }),
        );

        act(() => result.current.deleteAttachment(MEDIA_ID));

        expect(mockMutate).toHaveBeenCalledWith({
            sourceId: SOURCE_ID,
            objectId: OBJECT_ID,
            mediaId: MEDIA_ID,
        });
    });

    it("passes queryClient to all mutation factories", () => {
        renderHook(() => useAttachmentMutations({ sourceId: SOURCE_ID, objectId: OBJECT_ID }));

        const calls = mockUseMutation.mock.calls;
        expect(calls[0][0].queryClient).toBe(mockQueryClient);
        expect(calls[1][0].queryClient).toBe(mockQueryClient);
        expect(calls[2][0].queryClient).toBe(mockQueryClient);
    });

    it("returns stable references when sourceId and objectId do not change", () => {
        const { result, rerender } = renderHook(() =>
            useAttachmentMutations({ sourceId: SOURCE_ID, objectId: OBJECT_ID }),
        );

        const first = result.current;
        rerender();
        const second = result.current;

        expect(first.uploadAttachment).toBe(second.uploadAttachment);
        expect(first.deleteAttachment).toBe(second.deleteAttachment);
    });
});
