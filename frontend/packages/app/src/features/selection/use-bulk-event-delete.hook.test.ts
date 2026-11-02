// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, API_BASE, HttpResponse, http, renderHook, server, waitFor } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { MyDataSource, MyLayerInfo } from "@opencop/api";
import type { Element } from "@opencop/elements";
import { layerQueries } from "~/features/layers/layers.queries";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { eventQueries } from "~/features/events/events.queries";
import { useAreaSelectionStore } from "./area-selection.store";
import { BULK_REQUEST_BATCH_SIZE } from "./settle-in-batches.util";
import { useBulkEventDelete } from "./use-bulk-event-delete.hook";
import { useSelectionEditability } from "./use-selection-editability.hook";

const mockDeleteElements = vi.fn();
const mockShowToast = vi.fn();
const mockClearPickedElementIfMatches = vi.fn();

// The fixtures below are plain objects, not elements the real ElementStore can hold, so the
// store's delete action is observed through a stand-in.
vi.mock("@opencop/ingest", () => ({
    useElementActions: () => ({ delete: mockDeleteElements }),
}));

vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast: mockShowToast }),
}));

vi.mock("~/features/map/use-picked-element.hook", () => ({
    clearPickedElementIfMatches: (elementKeys: ReadonlySet<string>) =>
        mockClearPickedElementIfMatches(elementKeys),
    useClearPickedElement: () => vi.fn(),
}));

const LAYER_DELETE = `${API_BASE}/events/broadcast/layer/:targetLayer/:objectId`;
const SOURCE_DELETE = `${API_BASE}/events/broadcast/source/:sourceId/:objectId`;

function element(key: string, source: string, objectId: string): Element {
    return { key, source, objectId } as unknown as Element;
}

const OWNER_LAYER = [
    {
        layerId: "layer-1",
        layerName: "Ops",
        role: "OWNER" as const,
        accessScope: "PRIVATE" as const,
        defaultVisible: true,
    },
];
const MEMBER_LAYER = [
    {
        layerId: "layer-2",
        layerName: "Read-only",
        role: "MEMBER" as const,
        accessScope: "PRIVATE" as const,
        defaultVisible: true,
    },
];
const OWNER_DATA_SOURCE = [
    {
        serverId: "source-1",
        role: "OWNER" as const,
    },
] as unknown as MyDataSource[];

function seed(myLayers: MyLayerInfo[], myDataSources: MyDataSource[] = []) {
    return {
        [JSON.stringify(layerQueries.myList().queryKey)]: myLayers,
        [JSON.stringify(dataSourceQueries.myList().queryKey)]: myDataSources,
    };
}

/** Mounts the hook the way the panel does: with the selection's editability passed in. */
function useBulkDeleteWithAccess(elements: Element[]) {
    const access = useSelectionEditability(elements);
    return { ...useBulkEventDelete(elements, access), permissionStatus: access.permissionStatus };
}

function renderBulkDelete(
    elements: Element[],
    myLayers?: MyLayerInfo[],
    myDataSources: MyDataSource[] = [],
) {
    return renderHook(() => useBulkDeleteWithAccess(elements), {
        queryData: myLayers ? seed(myLayers, myDataSources) : undefined,
    });
}

/** Records every DELETE the hook sends, answering each with `respond`. */
function captureDeletes(
    respond: (objectId: string) => Response | Promise<Response> = () => HttpResponse.json({}),
) {
    const calls: string[] = [];
    const handler =
        (kind: "layer" | "source") =>
        ({ params }: { params: Record<string, string | readonly string[] | undefined> }) => {
            const target = String(params.targetLayer ?? params.sourceId);
            const objectId = String(params.objectId);
            calls.push(`${kind}:${target}/${objectId}`);
            return respond(objectId);
        };
    server.use(
        http.delete(LAYER_DELETE, handler("layer")),
        http.delete(SOURCE_DELETE, handler("source")),
    );
    return calls;
}

describe("useBulkEventDelete", () => {
    beforeEach(() => {
        mockDeleteElements.mockReset();
        mockShowToast.mockReset();
        mockClearPickedElementIfMatches.mockReset();
        act(() => {
            useAreaSelectionStore.getState().clear();
        });
        server.use(
            http.get(`${API_BASE}/me/layers`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/me/sources`, () => HttpResponse.json([])),
        );
    });

    it("identifies checked elements that make the whole operation unavailable", () => {
        const local = element("local\0obj-1", "local", "obj-1");
        const editable = element("layer-1\0obj-2", "layer-1", "obj-2");
        const readOnly = element("layer-2\0obj-3", "layer-2", "obj-3");

        const { result } = renderBulkDelete(
            [local, editable, readOnly],
            [...OWNER_LAYER, ...MEMBER_LAYER],
        );

        expect(result.current.permissionStatus).toBe("ready");
        expect(result.current.blockedElements).toEqual([readOnly]);
    });

    it("counts checked elements owned via a data source, which remain deletable", () => {
        const local = element("local\0obj-1", "local", "obj-1");
        const sourced = element("source-1\0obj-2", "source-1", "obj-2");

        const { result } = renderBulkDelete([local, sourced], [], OWNER_DATA_SOURCE);

        expect(result.current.permissionStatus).toBe("ready");
        expect(result.current.blockedElements).toEqual([]);
        expect(result.current.dataSourceDeleteCount).toBe(1);
    });

    it("keeps an all-local selection ready when membership queries fail", async () => {
        server.use(
            http.get(`${API_BASE}/me/layers`, () => new HttpResponse(null, { status: 500 })),
            http.get(`${API_BASE}/me/sources`, () => new HttpResponse(null, { status: 500 })),
        );
        const local = element("local\0obj-1", "local", "obj-1");

        const { result, queryClient } = renderBulkDelete([local]);

        await waitFor(() => {
            expect(queryClient.getQueryState(layerQueries.myList().queryKey)?.status).toBe("error");
            expect(queryClient.getQueryState(dataSourceQueries.myList().queryKey)?.status).toBe(
                "error",
            );
        });
        expect(result.current.permissionStatus).toBe("ready");
        expect(result.current.blockedElements).toEqual([]);
    });

    it("reports an error instead of treating unresolved remote permissions as denial, and deletes nothing", async () => {
        server.use(
            http.get(`${API_BASE}/me/layers`, () => new HttpResponse(null, { status: 500 })),
            http.get(`${API_BASE}/me/sources`, () => new HttpResponse(null, { status: 500 })),
        );
        const calls = captureDeletes();
        const remote = element("remote-source\0obj-1", "remote-source", "obj-1");

        const { result } = renderBulkDelete([remote]);

        await waitFor(() => {
            expect(result.current.permissionStatus).toBe("error");
        });
        expect(result.current.blockedElements).toEqual([]);

        await act(async () => {
            await result.current.deleteSelected();
        });
        expect(calls).toEqual([]);
    });

    it("deletes every deletable element through its endpoint, prunes the selection and drops cached details", async () => {
        const calls = captureDeletes();
        const local = element("local\0obj-1", "local", "obj-1");
        const editable = element("layer-1\0obj-2", "layer-1", "obj-2");
        const sourced = element("source-1\0obj-3", "source-1", "obj-3");
        act(() => {
            useAreaSelectionStore.getState().select([local, editable, sourced], []);
        });

        const { result, queryClient } = renderBulkDelete(
            [local, editable, sourced],
            OWNER_LAYER,
            OWNER_DATA_SOURCE,
        );
        const detailKey = eventQueries.detail("layer-1", "obj-2").queryKey;
        // A plain copy of the key: only the entry's presence matters, not its typed contents.
        queryClient.setQueryData([...detailKey], { cached: true });

        await act(async () => {
            await result.current.deleteSelected();
        });

        // The local element has no server copy, so only the two remote ones send a request.
        expect(calls.sort()).toEqual(["layer:layer-1/obj-2", "source:source-1/obj-3"]);
        const deleted = new Set([local.key, editable.key, sourced.key]);
        expect(mockDeleteElements).toHaveBeenCalledWith(deleted);
        expect(mockClearPickedElementIfMatches).toHaveBeenCalledWith(deleted);
        expect(queryClient.getQueryState(detailKey)).toBeUndefined();
        expect(useAreaSelectionStore.getState().elements).toBeNull();
        expect(mockShowToast).not.toHaveBeenCalled();
    });

    it("batches a large selection's delete requests instead of firing them all at once", async () => {
        const BATCH_SIZE = BULK_REQUEST_BATCH_SIZE;
        const TOTAL = BATCH_SIZE + 3;
        const elements = Array.from({ length: TOTAL }, (_, i) =>
            element(`layer-1\0obj-${i}`, "layer-1", `obj-${i}`),
        );
        act(() => {
            useAreaSelectionStore.getState().select(elements, []);
        });

        let inFlight = 0;
        let maxInFlight = 0;
        const calls = captureDeletes(async () => {
            inFlight++;
            maxInFlight = Math.max(maxInFlight, inFlight);
            // Hold each response long enough for the rest of its batch to arrive.
            await new Promise((resolve) => setTimeout(resolve, 10));
            inFlight--;
            return HttpResponse.json({});
        });

        const { result } = renderBulkDelete(elements, OWNER_LAYER);

        await act(async () => {
            await result.current.deleteSelected();
        });

        expect(calls).toHaveLength(TOTAL);
        expect(maxInFlight).toBe(BATCH_SIZE);
        expect(useAreaSelectionStore.getState().elements).toBeNull();
    });

    it("deletes nothing while a read-only element is checked", async () => {
        const calls = captureDeletes();
        const editable = element("layer-1\0obj-2", "layer-1", "obj-2");
        const readOnly = element("layer-2\0obj-3", "layer-2", "obj-3");
        act(() => {
            useAreaSelectionStore.getState().select([editable, readOnly], []);
        });

        const { result } = renderBulkDelete(
            [editable, readOnly],
            [...OWNER_LAYER, ...MEMBER_LAYER],
        );

        expect(result.current.blockedElements).toEqual([readOnly]);

        await act(async () => {
            await result.current.deleteSelected();
        });

        expect(calls).toEqual([]);
        expect(mockDeleteElements).not.toHaveBeenCalled();
        expect(useAreaSelectionStore.getState().elements).toEqual([editable, readOnly]);
        expect(mockShowToast).not.toHaveBeenCalled();
    });

    it("treats a 404 as already-deleted, not a failure", async () => {
        captureDeletes(() => HttpResponse.json({ message: "Resource not found" }, { status: 404 }));
        const editable = element("layer-1\0obj-2", "layer-1", "obj-2");
        act(() => {
            useAreaSelectionStore.getState().select([editable], []);
        });

        const { result } = renderBulkDelete([editable], OWNER_LAYER);

        await act(async () => {
            await result.current.deleteSelected();
        });

        expect(mockDeleteElements).toHaveBeenCalledWith(new Set([editable.key]));
        expect(useAreaSelectionStore.getState().elements).toBeNull();
        expect(mockShowToast).not.toHaveBeenCalled();
    });

    it("reports a non-404 failure without blocking the other elements' deletions", async () => {
        captureDeletes((objectId) =>
            objectId === "obj-2"
                ? HttpResponse.json({ message: "boom" }, { status: 500 })
                : HttpResponse.json({}),
        );
        const failing = element("layer-1\0obj-2", "layer-1", "obj-2");
        const succeeding = element("layer-1\0obj-3", "layer-1", "obj-3");
        act(() => {
            useAreaSelectionStore.getState().select([failing, succeeding], []);
        });

        const { result } = renderBulkDelete([failing, succeeding], OWNER_LAYER);

        await act(async () => {
            await result.current.deleteSelected();
        });

        expect(mockDeleteElements).toHaveBeenCalledWith(new Set([succeeding.key]));
        expect(useAreaSelectionStore.getState().elements).toEqual([failing]);
        expect(mockShowToast).toHaveBeenCalledWith(expect.objectContaining({ type: "error" }));
    });
});
