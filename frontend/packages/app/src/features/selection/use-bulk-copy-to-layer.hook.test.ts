// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, API_BASE, HttpResponse, http, renderHook, server, waitFor } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { MyDataSource, MyLayerInfo } from "@opencop/api";
import { Element, getElementKey, IconStyle, PointGeometry } from "@opencop/elements";
import { layerQueries } from "~/features/layers/layers.queries";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { useFilterPanelStore } from "~/features/filters/filter-panel.store";
import { useAreaSelectionStore } from "./area-selection.store";
import { BULK_REQUEST_BATCH_SIZE } from "./settle-in-batches.util";
import { useBulkCopyToLayer } from "./use-bulk-copy-to-layer.hook";
import { useSelectionEditability } from "./use-selection-editability.hook";

const mockShowToast = vi.fn();
const { mockDisabledSources } = vi.hoisted(() => ({
    mockDisabledSources: { current: [] as string[] },
}));

vi.mock("@opencop/ingest", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@opencop/ingest")>()),
    useSourcesWithState: () => mockDisabledSources.current,
}));

vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast: mockShowToast }),
}));

const LAYER_CREATE = `${API_BASE}/events/broadcast/layer/:targetLayer`;
const TARGET = { layerId: "layer-target", layerName: "Target Layer" };

const FUTURE = Date.now() + 60 * 60 * 1000;
const PAST = Date.now() - 60 * 60 * 1000;

function element(source: string, objectId: string, stale: number = FUTURE): Element {
    return new Element({
        type: "symbol",
        objectId,
        source,
        displayName: objectId,
        stale,
        geometry: new PointGeometry([1, 2]),
        style: new IconStyle({ size: 1, angle: 0 }),
    });
}

const layer = (layerId: string, role: MyLayerInfo["role"]): MyLayerInfo => ({
    layerId,
    layerName: layerId,
    role,
    accessScope: "PRIVATE",
    defaultVisible: true,
});

const EDITABLE = layer("layer-1", "EDITOR");
const READ_ONLY = layer("layer-2", "MEMBER");
const OWNED_SOURCE = [{ serverId: "source-1", role: "OWNER" }] as unknown as MyDataSource[];

function seed(myLayers: MyLayerInfo[], myDataSources: MyDataSource[] = []) {
    return {
        [JSON.stringify(layerQueries.myList().queryKey)]: myLayers,
        [JSON.stringify(dataSourceQueries.myList().queryKey)]: myDataSources,
    };
}

/** Mounts the hook the way the panel does: with the selection's editability passed in. */
function useBulkCopyWithAccess(elements: Element[]) {
    const access = useSelectionEditability(elements);
    return { ...useBulkCopyToLayer(elements, access), permissionStatus: access.permissionStatus };
}

function renderBulkCopy(
    elements: Element[],
    myLayers?: MyLayerInfo[],
    myDataSources: MyDataSource[] = [],
) {
    return renderHook(() => useBulkCopyWithAccess(elements), {
        queryData: myLayers ? seed(myLayers, myDataSources) : undefined,
    });
}

interface CreateCall {
    targetLayer: string;
    body: { properties: { opencop: { source: string; objectId: string } } };
}

/** Records every create the hook sends, answering each with `respond`. */
function captureCreates(
    respond: (call: CreateCall) => Response | Promise<Response> = () => HttpResponse.json({}),
) {
    const calls: CreateCall[] = [];
    server.use(
        http.post(LAYER_CREATE, async ({ request, params }) => {
            const call: CreateCall = {
                targetLayer: String(params.targetLayer),
                body: (await request.json()) as CreateCall["body"],
            };
            calls.push(call);
            return respond(call);
        }),
    );
    return calls;
}

function select(...elements: Element[]) {
    act(() => {
        useAreaSelectionStore.getState().select(elements, []);
    });
}

describe("useBulkCopyToLayer", () => {
    beforeEach(() => {
        mockShowToast.mockReset();
        mockDisabledSources.current = [];
        act(() => {
            useAreaSelectionStore.getState().clear();
            useFilterPanelStore.setState({ hiddenSources: [] });
        });
        server.use(
            http.get(`${API_BASE}/me/layers`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/me/sources`, () => HttpResponse.json([])),
        );
    });

    it("blocks the copy when a checked element is on a layer the user can't edit", () => {
        const local = element("local", "obj-1");
        const editable = element(EDITABLE.layerId, "obj-2");
        const readOnly = element(READ_ONLY.layerId, "obj-3");

        const { result } = renderBulkCopy([local, editable, readOnly], [EDITABLE, READ_ONLY]);

        expect(result.current.permissionStatus).toBe("ready");
        expect(result.current.blockedElements).toEqual([readOnly]);
    });

    it("sends nothing while a checked element is read-only", async () => {
        const calls = captureCreates();
        const editable = element(EDITABLE.layerId, "obj-1");
        const readOnly = element(READ_ONLY.layerId, "obj-2");
        select(editable, readOnly);

        const { result } = renderBulkCopy([editable, readOnly], [EDITABLE, READ_ONLY]);
        let outcome: unknown;
        await act(async () => {
            outcome = await result.current.copySelected(TARGET);
        });

        expect(outcome).toBe("skipped");
        expect(calls).toEqual([]);
        expect(mockShowToast).not.toHaveBeenCalled();
    });

    it("skips and counts elements already on the destination layer instead of duplicating them", async () => {
        const calls = captureCreates();
        const onDestination = element(EDITABLE.layerId, "obj-1");
        const elsewhere = element("local", "obj-2");
        select(onDestination, elsewhere);

        const { result } = renderBulkCopy([onDestination, elsewhere], [EDITABLE]);
        await act(async () => {
            await result.current.copySelected(EDITABLE);
        });

        expect(calls.map((c) => c.body.properties.opencop.objectId)).toEqual(["obj-2"]);
        expect(mockShowToast).toHaveBeenCalledWith(
            expect.objectContaining({
                type: "warning",
                title: "Copied to layer-1",
                description: "Copied 1 item. Skipped 1 already on that layer.",
            }),
        );
        // The skipped one stays ticked; only the copied one is unticked.
        expect([...useAreaSelectionStore.getState().checkedKeys]).toEqual([
            getElementKey(onDestination),
        ]);
    });

    it("sends nothing and says so when every element is already on the destination", async () => {
        const calls = captureCreates();
        const a = element(EDITABLE.layerId, "obj-1");
        const b = element(EDITABLE.layerId, "obj-2");
        select(a, b);

        const { result } = renderBulkCopy([a, b], [EDITABLE]);
        await act(async () => {
            await result.current.copySelected(EDITABLE);
        });

        expect(calls).toEqual([]);
        expect(mockShowToast).toHaveBeenCalledWith(
            expect.objectContaining({
                type: "warning",
                title: "Nothing was copied",
                description: "Skipped 2 already on that layer.",
            }),
        );
    });

    it("counts the non-stale checked elements on each layer", () => {
        const a = element(EDITABLE.layerId, "obj-1");
        const b = element(EDITABLE.layerId, "obj-2");
        const c = element("local", "obj-3");
        const stale = element(EDITABLE.layerId, "obj-4", PAST);

        const { result } = renderBulkCopy([a, b, c, stale], [EDITABLE]);

        expect(result.current.countFreshByLayer()).toEqual({ [EDITABLE.layerId]: 2, local: 1 });
    });

    it("reports an error instead of treating unresolved remote permissions as denial, and sends nothing", async () => {
        server.use(
            http.get(`${API_BASE}/me/layers`, () => new HttpResponse(null, { status: 500 })),
            http.get(`${API_BASE}/me/sources`, () => new HttpResponse(null, { status: 500 })),
        );
        const calls = captureCreates();
        const remote = element("remote-source", "obj-1");

        const { result } = renderBulkCopy([remote]);
        await waitFor(() => {
            expect(result.current.permissionStatus).toBe("error");
        });
        await act(async () => {
            await result.current.copySelected(TARGET);
        });

        expect(calls).toEqual([]);
    });

    it("creates one copy per element in the target layer, retargeting each source", async () => {
        const calls = captureCreates();
        const local = element("local", "obj-1");
        const fromLayer = element(EDITABLE.layerId, "obj-2");
        const fromSource = element("source-1", "obj-3");
        select(local, fromLayer, fromSource);

        const { result } = renderBulkCopy([local, fromLayer, fromSource], [EDITABLE], OWNED_SOURCE);
        await act(async () => {
            await result.current.copySelected(TARGET);
        });

        expect(calls.map((c) => c.targetLayer)).toEqual([
            TARGET.layerId,
            TARGET.layerId,
            TARGET.layerId,
        ]);
        expect(calls.map((c) => c.body.properties.opencop.source)).toEqual([
            TARGET.layerId,
            TARGET.layerId,
            TARGET.layerId,
        ]);
        expect(mockShowToast).toHaveBeenCalledWith(
            expect.objectContaining({
                type: "success",
                title: "Copied to Target Layer",
                description: "Copied 3 items.",
            }),
        );
    });

    it("unchecks only what was copied and leaves the originals in the selection", async () => {
        const calls = captureCreates();
        const a = element(EDITABLE.layerId, "obj-1");
        const b = element(EDITABLE.layerId, "obj-2");
        select(a, b);

        const { result } = renderBulkCopy([a, b], [EDITABLE]);
        await act(async () => {
            await result.current.copySelected(TARGET);
        });

        expect(calls).toHaveLength(2);
        const state = useAreaSelectionStore.getState();
        expect(state.elements).toHaveLength(2);
        expect(state.checkedKeys.size).toBe(0);
    });

    it("skips elements past their stale time without sending them, and reports the count", async () => {
        const calls = captureCreates();
        const fresh = element(EDITABLE.layerId, "obj-1");
        const stale = element(EDITABLE.layerId, "obj-2", PAST);
        select(fresh, stale);

        const { result } = renderBulkCopy([fresh, stale], [EDITABLE]);
        expect(result.current.countStale()).toBe(1);
        await act(async () => {
            await result.current.copySelected(TARGET);
        });

        expect(calls.map((c) => c.body.properties.opencop.objectId)).toEqual([fresh.objectId]);
        expect(mockShowToast).toHaveBeenCalledWith(
            expect.objectContaining({
                type: "warning",
                description: "Copied 1 item. Skipped 1 past its stale time.",
            }),
        );
        // The skipped element stays checked; only the copied one is unchecked.
        expect([...useAreaSelectionStore.getState().checkedKeys]).toEqual([stale.key]);
    });

    it("sends nothing and says so when every checked element is stale", async () => {
        const calls = captureCreates();
        const stale = element(EDITABLE.layerId, "obj-1", PAST);
        select(stale);

        const { result } = renderBulkCopy([stale], [EDITABLE]);
        await act(async () => {
            await result.current.copySelected(TARGET);
        });

        expect(calls).toEqual([]);
        expect(mockShowToast).toHaveBeenCalledWith(
            expect.objectContaining({
                type: "warning",
                title: "Nothing was copied",
                description: "Skipped 1 past its stale time.",
            }),
        );
    });

    it("reports a partial failure and unchecks only the copies that landed", async () => {
        captureCreates((call) =>
            call.body.properties.opencop.objectId === "obj-2"
                ? new HttpResponse(null, { status: 500 })
                : HttpResponse.json({}),
        );
        const ok = element(EDITABLE.layerId, "obj-1");
        const bad = element(EDITABLE.layerId, "obj-2");
        select(ok, bad);

        const { result } = renderBulkCopy([ok, bad], [EDITABLE]);
        await act(async () => {
            await result.current.copySelected(TARGET);
        });

        expect(mockShowToast).toHaveBeenCalledWith(
            expect.objectContaining({
                type: "error",
                title: "Some items were not copied",
                description: "Copied 1 item. 1 failed.",
            }),
        );
        expect([...useAreaSelectionStore.getState().checkedKeys]).toEqual([bad.key]);
    });

    it("reports a total failure with an error toast and leaves everything checked", async () => {
        captureCreates(() => new HttpResponse(null, { status: 500 }));
        const a = element(EDITABLE.layerId, "obj-1");
        select(a);

        const { result } = renderBulkCopy([a], [EDITABLE]);
        await act(async () => {
            await result.current.copySelected(TARGET);
        });

        expect(mockShowToast).toHaveBeenCalledWith(
            expect.objectContaining({ type: "error", title: "Copy failed" }),
        );
        expect(useAreaSelectionStore.getState().checkedKeys.size).toBe(1);
    });

    it("warns that the copies won't be visible when the target layer is hidden", async () => {
        captureCreates();
        const a = element(EDITABLE.layerId, "obj-1");
        select(a);
        act(() => useFilterPanelStore.setState({ hiddenSources: [TARGET.layerId] }));

        const { result } = renderBulkCopy([a], [EDITABLE]);
        await act(async () => {
            await result.current.copySelected(TARGET);
        });

        expect(mockShowToast).toHaveBeenCalledWith(
            expect.objectContaining({
                description: expect.stringContaining("Target Layer is hidden on the map"),
            }),
        );
    });

    it("warns that the copies won't be visible when ingest has the target layer's source disabled", async () => {
        captureCreates();
        const a = element(EDITABLE.layerId, "obj-1");
        select(a);
        mockDisabledSources.current = [TARGET.layerId];

        const { result } = renderBulkCopy([a], [EDITABLE]);
        await act(async () => {
            await result.current.copySelected(TARGET);
        });

        expect(mockShowToast).toHaveBeenCalledWith(
            expect.objectContaining({
                description: expect.stringContaining("Target Layer is hidden on the map"),
            }),
        );
    });

    it("never has more than the batch size in flight", async () => {
        let inFlight = 0;
        let peak = 0;
        captureCreates(async () => {
            inFlight++;
            peak = Math.max(peak, inFlight);
            await new Promise((resolve) => setTimeout(resolve, 5));
            inFlight--;
            return HttpResponse.json({});
        });
        const elements = Array.from({ length: BULK_REQUEST_BATCH_SIZE * 2 + 4 }, (_, i) =>
            element(EDITABLE.layerId, `obj-${i}`),
        );
        select(...elements);

        const { result } = renderBulkCopy(elements, [EDITABLE]);
        await act(async () => {
            await result.current.copySelected(TARGET);
        });

        expect(peak).toBeLessThanOrEqual(BULK_REQUEST_BATCH_SIZE);
        expect(peak).toBeGreaterThan(1);
    });

    it("ignores a second call made while a copy is still running", async () => {
        const calls = captureCreates(async () => {
            await new Promise((resolve) => setTimeout(resolve, 5));
            return HttpResponse.json({});
        });
        const elements = [element(EDITABLE.layerId, "obj-1"), element(EDITABLE.layerId, "obj-2")];
        select(...elements);

        const { result } = renderBulkCopy(elements, [EDITABLE]);
        // Both calls run before React re-renders, so only a synchronous guard can stop the second.
        let results: unknown[] = [];
        await act(async () => {
            results = await Promise.all([
                result.current.copySelected(TARGET),
                result.current.copySelected(TARGET),
            ]);
        });

        expect(results).toEqual(["ran", "busy"]);
        expect(calls).toHaveLength(elements.length);
        expect(mockShowToast).toHaveBeenCalledTimes(1);
    });
});
