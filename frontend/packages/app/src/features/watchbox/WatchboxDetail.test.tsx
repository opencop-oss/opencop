// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyDataSource, MyLayerInfo } from "@opencop/api";
import { Point, getElementKey, type Element } from "@opencop/elements";
import { useElementActions } from "@opencop/ingest";
import { QueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { describe, expect, it, onTestFinished, vi } from "vitest";
import { installPlugins, withPlugins } from "~/test/plugins";
import {
    act,
    API_BASE,
    http,
    HttpResponse,
    render,
    screen,
    server,
    userEvent,
    waitFor,
} from "~/test";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { layerQueries } from "~/features/layers/layers.queries";
import { useMapStore } from "~/features/map/core/map.store";
import { WatchboxDetail, type WatchboxDetailProps } from "./WatchboxDetail";
import { watchboxQueries } from "./watchbox.queries";
import type { Watchbox } from "./watchbox.model";
import type { WatchboxDetection } from "./watchbox.api";

// The editor renders WatchboxForm, which toasts. Same double the panel test uses.
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast: vi.fn() }),
}));

const LAYER_A: MyLayerInfo = {
    layerId: "layer-a",
    layerName: "Pineapple",
    role: "OWNER",
    accessScope: "PRIVATE",
    defaultVisible: true,
};

const LAYER_B: MyLayerInfo = {
    layerId: "layer-b",
    layerName: "Coconut",
    role: "ADMIN",
    accessScope: "PRIVATE",
    defaultVisible: true,
};

const WATCHBOX: Watchbox = {
    id: "wb-1",
    name: "Test2",
    description: "Watching the north approach",
    enabled: true,
    direction: "BOTH",
    polygon: {
        type: "Polygon",
        coordinates: [
            [
                [0, 0],
                [1, 0],
                [1, 1],
                [0, 0],
            ],
        ],
    },
    filter: {
        combine: "ALL",
        predicates: [
            {
                path: "properties.opencop.displayName",
                operation: "EQUALS",
                values: ["DOG"],
            },
        ],
    },
    layerIds: [LAYER_A.layerId],
    configEpoch: 1,
    alertingStatus: "ACTIVE",
    alertingReasons: [],
    // Deliberately empty: the Notifications tab resolves source names through
    // the caller's own data-source list (dataSourceQueries.myList()), not this
    // field — see WatchboxDetail's NotificationRow.
    sourceStatuses: [],
    capabilities: { canDelete: true, canPatch: true },
    createdAt: "2026-01-02T03:04:05Z",
    updatedAt: "2026-01-02T03:04:05Z",
};

const DETECTION_ENTER: WatchboxDetection = {
    id: "det-1",
    watchboxId: WATCHBOX.id,
    configEpoch: 1,
    sourceId: "src-1",
    objectId: "track-17",
    eventId: "evt-1",
    effectiveEventTime: "2026-01-02T03:04:05Z",
    kind: "ENTER",
    objectDisplayName: "Truck 17",
    detectedAt: "2026-01-02T03:04:06Z",
};

const DETECTION_FILTER_LOST: WatchboxDetection = {
    id: "det-2",
    watchboxId: WATCHBOX.id,
    configEpoch: 1,
    sourceId: "src-unknown",
    objectId: "track-9",
    eventId: "evt-2",
    effectiveEventTime: "2026-01-02T02:00:00Z",
    kind: "FILTER_MATCH_LOST",
    objectDisplayName: null,
    detectedAt: "2026-01-02T02:00:01Z",
};

function moreActionsTrigger(name = WATCHBOX.name) {
    return screen.getByRole("button", { name: `More actions for ${name}` });
}

async function openActionsMenu(user: ReturnType<typeof userEvent.setup>, name = WATCHBOX.name) {
    await user.click(moreActionsTrigger(name));
}

function detectionsHandler(
    content: WatchboxDetection[] = [DETECTION_ENTER, DETECTION_FILTER_LOST],
) {
    return http.get(`${API_BASE}/watchbox-detections`, ({ request }) => {
        const url = new URL(request.url);
        expect(url.searchParams.get("watchboxId")).toBe(WATCHBOX.id);
        return HttpResponse.json({
            content,
            page: 0,
            size: 50,
            totalElements: content.length,
            totalPages: 1,
        });
    });
}

/** Puts an element in the harness's real ElementStore, the way ingest would. */
function Seed({ element }: { element: Element }) {
    const { add } = useElementActions();
    useEffect(() => {
        add(element);
    }, [add, element]);
    return null;
}

function renderDetail(
    watchbox: Watchbox,
    layers: MyLayerInfo[] = [LAYER_A, LAYER_B],
    {
        isMuted = false,
        onToggleMuted = vi.fn(),
    }: Partial<Pick<WatchboxDetailProps, "isMuted" | "onToggleMuted">> = {},
) {
    const onBack = vi.fn();
    const onDelete = vi.fn();
    render(
        <WatchboxDetail
            watchboxId={watchbox.id}
            onBack={onBack}
            onDelete={onDelete}
            isMuted={isMuted}
            onToggleMuted={onToggleMuted}
            actionsTriggerRef={{ current: null }}
        />,
        {
            queryData: {
                [JSON.stringify(watchboxQueries.watchbox(watchbox.id).queryKey)]: watchbox,
                [JSON.stringify(layerQueries.myList().queryKey)]: layers,
                // Notifications tab resolves a detection's source name through this
                // list, the same as RegisterWatchboxAlerts.tsx's toast — "src-1"
                // resolves to a name; "src-unknown" is deliberately absent so a
                // detection from it falls back to the raw id.
                [JSON.stringify(dataSourceQueries.myList().queryKey)]: [
                    { serverId: "src-1", serverName: "cot-source" },
                ],
                // The editor reuses the create form, which checks each candidate
                // layer for a data source.
                ...Object.fromEntries(
                    layers.map((layer) => [
                        JSON.stringify(layerQueries.dataSources(layer.layerId).queryKey),
                        [{ serverId: "src-0" }],
                    ]),
                ),
            },
        },
    );
    return { onBack, onDelete };
}

describe("WatchboxDetail", () => {
    withPlugins(["opencop.symbology"]);

    it("shows the configuration the create form collected", () => {
        renderDetail(WATCHBOX);

        expect(screen.getByRole("heading", { name: "TEST2" })).toBeInTheDocument();
        expect(screen.getByText(WATCHBOX.description as string)).toBeInTheDocument();
        expect(screen.getByText("Both")).toBeInTheDocument();
        // Resolved through the caller's layer list, not shown as a raw id.
        expect(screen.getByText(LAYER_A.layerName)).toBeInTheDocument();
        expect(screen.queryByText(LAYER_A.layerId)).not.toBeInTheDocument();
    });

    it("renders each saved condition as a readable sentence", () => {
        renderDetail(WATCHBOX);

        // The Trans-rendered summary is split across styled spans, so match
        // the assembled sentence, not a single text node.
        expect(screen.getByText(/is equal to/)).toBeInTheDocument();
        expect(screen.getByText("DOG")).toBeInTheDocument();
        expect(screen.getByText("Display Name")).toBeInTheDocument();
    });

    // Reusing the create form's segmented All/Any control here would put a
    // focusable control the reader cannot move into a read-only view.
    it("states the combinator as text rather than as a control", () => {
        renderDetail(WATCHBOX);

        expect(screen.getByText("All conditions must match")).toBeInTheDocument();
        expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
        expect(screen.queryByRole("radio")).not.toBeInTheDocument();
    });

    it("collapses a section when its header is clicked, like the create form's", async () => {
        const user = userEvent.setup();
        renderDetail(WATCHBOX);

        expect(screen.getByText("Both")).toBeVisible();
        await user.click(screen.getByRole("button", { name: /Details/i }));

        await waitFor(() => expect(screen.getByText("Both")).not.toBeVisible());
    });

    // The list already holds full rows, so a card the user can see opens
    // straight into its configuration rather than flashing a spinner.
    it("renders from the list cache without waiting for the fetch", () => {
        const queryClient = new QueryClient({
            defaultOptions: { queries: { retry: false, staleTime: Infinity } },
        });
        queryClient.setQueryData(watchboxQueries.watchboxes().queryKey, [WATCHBOX]);
        queryClient.setQueryData(layerQueries.myList().queryKey, [LAYER_A]);
        // placeholderData renders now and still refreshes in the background.
        server.use(
            http.get(`${API_BASE}/watchboxes/${WATCHBOX.id}`, () => HttpResponse.json(WATCHBOX)),
        );

        render(
            <WatchboxDetail
                watchboxId={WATCHBOX.id}
                onBack={vi.fn()}
                onDelete={vi.fn()}
                isMuted={false}
                onToggleMuted={vi.fn()}
                actionsTriggerRef={{ current: null }}
            />,
            {
                queryClient,
            },
        );

        expect(screen.getByRole("heading", { name: "TEST2" })).toBeInTheDocument();
        expect(screen.queryByText("Loading...")).not.toBeInTheDocument();
    });

    it("names every watched layer when a watchbox covers more than one", () => {
        renderDetail({ ...WATCHBOX, layerIds: [LAYER_A.layerId, LAYER_B.layerId] });

        expect(screen.getByText(LAYER_A.layerName)).toBeInTheDocument();
        expect(screen.getByText(LAYER_B.layerName)).toBeInTheDocument();
    });

    it("falls back to the layer id when the caller can no longer see that layer", () => {
        renderDetail({ ...WATCHBOX, layerIds: ["layer-gone"] }, []);

        expect(screen.getByText("layer-gone")).toBeInTheDocument();
    });

    it("says so plainly when the watchbox has no description and no rules", () => {
        renderDetail({
            ...WATCHBOX,
            description: null,
            filter: { combine: "ALL", predicates: [] },
        });

        expect(screen.getByText("No description")).toBeInTheDocument();
        expect(
            screen.getByText("No conditions added. All objects will match."),
        ).toBeInTheDocument();
    });

    it("still lists a condition whose custom field this browser has never seen", () => {
        renderDetail({
            ...WATCHBOX,
            filter: {
                combine: "ANY",
                predicates: [
                    {
                        path: "properties.opencop.attributes.callsign",
                        operation: "EQUALS",
                        values: ["BRAVO"],
                    },
                ],
            },
        });

        expect(screen.getByText("callsign")).toBeInTheDocument();
        expect(screen.getByText("BRAVO")).toBeInTheDocument();
    });

    // Characterization: a symbol condition is saved as a 2525D predicate and its 2525C
    // companion, and is read back as the one condition the operator built.
    it("reads a saved symbol condition and its 2525C companion as one sentence", () => {
        renderDetail({
            ...WATCHBOX,
            filter: {
                combine: "ANY",
                predicates: [
                    {
                        path: "properties.opencop.element.symbol",
                        operation: "SUBSTRING_IN",
                        values: ["6"],
                        offset: 3,
                        length: 1,
                        negate: false,
                        caseInsensitive: false,
                    },
                    {
                        path: "properties.opencop.element.symbol",
                        operation: "SUBSTRING_IN",
                        values: ["H", "K"],
                        offset: 1,
                        length: 1,
                        negate: false,
                        caseInsensitive: false,
                    },
                ],
            },
        });

        expect(screen.getAllByText(/is equal to/)).toHaveLength(1);
        expect(screen.getByText("SIDC: Standard Identity")).toBeInTheDocument();
        expect(screen.getByText("Hostile/Faker")).toBeInTheDocument();
        expect(screen.queryByText(/between characters/)).not.toBeInTheDocument();
    });

    // Characterization: how each kind of saved path is named when read back.
    it("names each saved condition by the field its path belongs to", () => {
        renderDetail({
            ...WATCHBOX,
            filter: {
                combine: "ALL",
                predicates: [
                    { path: "properties.opencop.kind", operation: "EQUALS", values: ["track"] },
                    { path: "properties.opencop.displayName", operation: "EXISTS" },
                    {
                        // A slice the picker could not have produced stays on the SIDC field.
                        path: "properties.opencop.element.symbol",
                        operation: "SUBSTRING_IN",
                        values: ["10", "11"],
                        offset: 4,
                        length: 2,
                    },
                    {
                        path: "properties.opencop.attributes.callsign",
                        operation: "IN",
                        values: ["ALPHA", "BRAVO"],
                    },
                ],
            },
        });

        expect(screen.getByText("Kind")).toBeInTheDocument();
        expect(screen.getByText("track")).toBeInTheDocument();
        expect(screen.getByText("Display Name")).toBeInTheDocument();
        expect(screen.getByText(/exists/)).toBeInTheDocument();
        expect(screen.getByText("SIDC")).toBeInTheDocument();
        expect(screen.getByText(/between characters/)).toBeInTheDocument();
        expect(screen.getByText("callsign")).toBeInTheDocument();
        expect(screen.getByText("ALPHA, BRAVO")).toBeInTheDocument();
    });

    describe("notifications tab", () => {
        it("marks the active tab as pressed for assistive tech", async () => {
            const user = userEvent.setup();
            server.use(detectionsHandler([]));
            renderDetail(WATCHBOX);

            expect(screen.getByRole("button", { name: "Info" })).toHaveAttribute(
                "aria-pressed",
                "true",
            );
            expect(screen.getByRole("button", { name: "Notifications" })).toHaveAttribute(
                "aria-pressed",
                "false",
            );

            await user.click(screen.getByRole("button", { name: "Notifications" }));

            expect(screen.getByRole("button", { name: "Notifications" })).toHaveAttribute(
                "aria-pressed",
                "true",
            );
        });

        it("fetches this watchbox's detections on open and renders them newest-first, as returned", async () => {
            const user = userEvent.setup();
            server.use(detectionsHandler());
            renderDetail(WATCHBOX);

            await user.click(screen.getByRole("button", { name: "Notifications" }));

            // objectDisplayName preferred when present; falls back to the raw
            // objectId when null (DETECTION_FILTER_LOST).
            await waitFor(() => expect(screen.getByText("Truck 17")).toBeInTheDocument());
            expect(screen.getByText("track-9")).toBeInTheDocument();

            // Source name resolved via the caller's own data-source list; falls
            // back to the raw id for a source the caller can no longer see.
            expect(screen.getByText(/cot-source/)).toBeInTheDocument();
            expect(screen.getByText(/src-unknown/)).toBeInTheDocument();

            // Server order preserved (ENTER row before the FILTER_MATCH_LOST
            // row) — the endpoint rejects a sort param, so this must not re-sort.
            const enterRow = screen.getByText("Truck 17");
            const lostRow = screen.getByText("track-9");
            expect(
                enterRow.compareDocumentPosition(lostRow) & Node.DOCUMENT_POSITION_FOLLOWING,
            ).toBeTruthy();
        });

        it("selects and focuses the object when a notification's linked name is clicked", async () => {
            const user = userEvent.setup();
            useMapStore.setState({ selected: null, flyToTarget: undefined });
            server.use(detectionsHandler([DETECTION_ENTER]));
            const key = getElementKey({
                sourceId: DETECTION_ENTER.sourceId,
                objectId: DETECTION_ENTER.objectId,
            });
            const position: [number, number] = [10, 20];
            const element = Point.create({
                position,
                meta: {
                    source: DETECTION_ENTER.sourceId,
                    objectId: DETECTION_ENTER.objectId,
                    displayName: "Truck 17",
                },
            });

            render(
                <>
                    <Seed element={element} />
                    <WatchboxDetail
                        watchboxId={WATCHBOX.id}
                        onBack={vi.fn()}
                        onDelete={vi.fn()}
                        isMuted={false}
                        onToggleMuted={vi.fn()}
                        actionsTriggerRef={{ current: null }}
                    />
                </>,
                {
                    queryData: {
                        [JSON.stringify(watchboxQueries.watchbox(WATCHBOX.id).queryKey)]: WATCHBOX,
                        [JSON.stringify(layerQueries.myList().queryKey)]: [LAYER_A, LAYER_B],
                        [JSON.stringify(dataSourceQueries.myList().queryKey)]: [
                            { serverId: "src-1", serverName: "cot-source" },
                        ],
                    },
                },
            );

            await user.click(screen.getByRole("button", { name: "Notifications" }));
            await user.click(await screen.findByRole("button", { name: "Truck 17" }));

            expect(useMapStore.getState().selected).toBe(key);
            // A point has no ground extent, so focus flies rather than fits.
            expect(useMapStore.getState().flyToTarget).toMatchObject({ center: position });
        });

        it("has an always-present refresh button that forces a manual refetch, using the My Layers refresh icon", async () => {
            const user = userEvent.setup();
            let requestCount = 0;
            server.use(
                http.get(`${API_BASE}/watchbox-detections`, () => {
                    requestCount++;
                    return HttpResponse.json({
                        content: [DETECTION_ENTER],
                        page: 0,
                        size: 50,
                        totalElements: 1,
                        totalPages: 1,
                    });
                }),
            );
            renderDetail(WATCHBOX);

            await user.click(screen.getByRole("button", { name: "Notifications" }));
            await waitFor(() => expect(screen.getByText("Truck 17")).toBeInTheDocument());
            expect(requestCount).toBe(1);

            // Same shared RefreshButton (and FaSync icon) My Layers uses, not a
            // bespoke control — its default tooltip/label is "Refresh".
            const refreshButton = screen.getByRole("button", { name: "Refresh" });
            await user.click(refreshButton);

            await waitFor(() => expect(requestCount).toBe(2));
        });

        it("keeps the refresh button available even when the initial fetch fails", async () => {
            const user = userEvent.setup();
            server.use(
                http.get(`${API_BASE}/watchbox-detections`, () =>
                    HttpResponse.json({ message: "boom" }, { status: 500 }),
                ),
            );
            renderDetail(WATCHBOX);

            await user.click(screen.getByRole("button", { name: "Notifications" }));

            expect(await screen.findByText("Could not load notifications.")).toBeInTheDocument();
            expect(screen.getByRole("button", { name: "Refresh" })).toBeInTheDocument();
        });

        it("shows an empty state when there are no recent notifications", async () => {
            const user = userEvent.setup();
            server.use(detectionsHandler([]));
            renderDetail(WATCHBOX);

            await user.click(screen.getByRole("button", { name: "Notifications" }));

            expect(await screen.findByText("No recent notifications.")).toBeInTheDocument();
        });

        it("announces the unavailable message to assistive tech when the initial fetch fails", async () => {
            const user = userEvent.setup();
            server.use(
                http.get(`${API_BASE}/watchbox-detections`, () =>
                    HttpResponse.json({ message: "boom" }, { status: 500 }),
                ),
            );
            renderDetail(WATCHBOX);

            await user.click(screen.getByRole("button", { name: "Notifications" }));

            // role="alert", not just visible text: this fails asynchronously
            // after the tab click (past the query's retries), so nothing else
            // tells a screen-reader user the load failed unless it's announced.
            expect(await screen.findByRole("alert")).toHaveTextContent(
                "Could not load notifications.",
            );
        });

        it("keeps the last loaded notifications and warns when a background refresh fails", async () => {
            const user = userEvent.setup();
            const queryClient = new QueryClient({
                defaultOptions: { queries: { retry: false, staleTime: Infinity } },
            });
            queryClient.setQueryData(watchboxQueries.watchbox(WATCHBOX.id).queryKey, WATCHBOX);
            queryClient.setQueryData(layerQueries.myList().queryKey, [LAYER_A]);
            queryClient.setQueryData(dataSourceQueries.myList().queryKey, [
                { serverId: "src-1", serverName: "cot-source" } as MyDataSource,
            ]);
            queryClient.setQueryData(watchboxQueries.detections(WATCHBOX.id).queryKey, {
                content: [DETECTION_ENTER],
                page: 0,
                size: 50,
                totalElements: 1,
                totalPages: 1,
            });
            server.use(
                http.get(`${API_BASE}/watchbox-detections`, () =>
                    HttpResponse.json({ message: "boom" }, { status: 500 }),
                ),
            );

            render(
                <WatchboxDetail
                    watchboxId={WATCHBOX.id}
                    onBack={vi.fn()}
                    onDelete={vi.fn()}
                    isMuted={false}
                    onToggleMuted={vi.fn()}
                    actionsTriggerRef={{ current: null }}
                />,
                {
                    queryClient,
                },
            );
            await user.click(screen.getByRole("button", { name: "Notifications" }));

            await queryClient.refetchQueries({
                queryKey: watchboxQueries.detections(WATCHBOX.id).queryKey,
            });

            await waitFor(() =>
                expect(
                    screen.getByText(
                        "Could not refresh notifications. Showing the last loaded list.",
                    ),
                ).toBeInTheDocument(),
            );
            expect(screen.getByText("Truck 17")).toBeInTheDocument();
        });
    });

    describe("when the watchbox cannot be read", () => {
        it("reports a watchbox that no longer exists instead of an empty configuration", async () => {
            const user = userEvent.setup();
            const onBack = vi.fn();
            server.use(
                http.get(`${API_BASE}/watchboxes/wb-gone`, () =>
                    HttpResponse.json(
                        { code: "WATCHBOX_NOT_FOUND", message: "", fields: [], reasons: [] },
                        { status: 404 },
                    ),
                ),
            );

            render(
                <WatchboxDetail
                    watchboxId="wb-gone"
                    onBack={onBack}
                    onDelete={vi.fn()}
                    isMuted={false}
                    onToggleMuted={vi.fn()}
                    actionsTriggerRef={{ current: null }}
                />,
            );

            await waitFor(() =>
                expect(screen.getByText("Watchbox unavailable")).toBeInTheDocument(),
            );
            expect(screen.getByText("Watchbox not found")).toBeInTheDocument();

            // Without a trailing crumb the root renders as plain text, which
            // strands the user on a view with no way back to the list.
            await user.click(screen.getByRole("button", { name: "Watchbox" }));
            expect(onBack).toHaveBeenCalledOnce();
        });

        it("keeps the loaded configuration when a background refresh fails", async () => {
            const queryClient = new QueryClient({
                defaultOptions: { queries: { retry: false, staleTime: Infinity } },
            });
            queryClient.setQueryData(watchboxQueries.watchbox(WATCHBOX.id).queryKey, WATCHBOX);
            queryClient.setQueryData(layerQueries.myList().queryKey, [LAYER_A]);
            server.use(
                http.get(`${API_BASE}/watchboxes/${WATCHBOX.id}`, () =>
                    HttpResponse.json({ message: "boom" }, { status: 500 }),
                ),
            );

            render(
                <WatchboxDetail
                    watchboxId={WATCHBOX.id}
                    onBack={vi.fn()}
                    onDelete={vi.fn()}
                    isMuted={false}
                    onToggleMuted={vi.fn()}
                    actionsTriggerRef={{ current: null }}
                />,
                { queryClient },
            );

            await queryClient.refetchQueries({
                queryKey: watchboxQueries.watchbox(WATCHBOX.id).queryKey,
            });

            await waitFor(() =>
                expect(
                    screen.getByText("Could not refresh. Showing the last loaded configuration."),
                ).toBeInTheDocument(),
            );
            expect(screen.getByRole("heading", { name: "TEST2" })).toBeInTheDocument();
            expect(screen.queryByText("Watchbox unavailable")).not.toBeInTheDocument();
        });

        it("still leaves a way back while the watchbox is loading", async () => {
            const user = userEvent.setup();
            const onBack = vi.fn();
            server.use(http.get(`${API_BASE}/watchboxes/wb-slow`, () => new Promise(() => {})));

            render(
                <WatchboxDetail
                    watchboxId="wb-slow"
                    onBack={onBack}
                    onDelete={vi.fn()}
                    isMuted={false}
                    onToggleMuted={vi.fn()}
                    actionsTriggerRef={{ current: null }}
                />,
            );

            await user.click(await screen.findByRole("button", { name: "Watchbox" }));
            expect(onBack).toHaveBeenCalledOnce();
        });
    });

    it("opens the editor seeded with the saved configuration", async () => {
        const user = userEvent.setup();
        renderDetail(WATCHBOX);

        await openActionsMenu(user);
        await user.click(await screen.findByRole("menuitem", { name: "Edit watchbox" }));

        // The form, not the read-only table: the name is now an editable field.
        expect(screen.getByRole("textbox", { name: "Name" })).toHaveValue(WATCHBOX.name);
        expect(screen.getByRole("button", { name: "Save" })).toBeInTheDocument();
    });

    it("leaves the watchbox unchanged when the editor is cancelled", async () => {
        const user = userEvent.setup();
        renderDetail(WATCHBOX);

        await openActionsMenu(user);
        await user.click(await screen.findByRole("menuitem", { name: "Edit watchbox" }));
        await user.click(screen.getByRole("button", { name: "Cancel" }));

        expect(screen.getByRole("heading", { name: "TEST2" })).toBeInTheDocument();
        expect(screen.queryByRole("textbox", { name: "Name" })).not.toBeInTheDocument();
    });

    // Removing a layer resets tracking, refused with a 409 until the request
    // carries confirmTrackingReset.
    it("confirms a tracking reset before retrying the patch", async () => {
        const user = userEvent.setup();
        const patches: unknown[] = [];
        server.use(
            http.patch(`${API_BASE}/watchboxes/${WATCHBOX.id}`, async ({ request }) => {
                const body = (await request.json()) as { confirmTrackingReset?: boolean };
                patches.push(body);
                if (!body.confirmTrackingReset) {
                    return HttpResponse.json(
                        {
                            code: "WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED",
                            message: "",
                            fields: ["layerIds"],
                            reasons: ["LAYER_REMOVED"],
                        },
                        { status: 409 },
                    );
                }
                return HttpResponse.json(WATCHBOX);
            }),
            // A successful patch invalidates the detail query, which refetches.
            http.get(`${API_BASE}/watchboxes/${WATCHBOX.id}`, () => HttpResponse.json(WATCHBOX)),
        );

        renderDetail(WATCHBOX);
        await openActionsMenu(user);
        await user.click(await screen.findByRole("menuitem", { name: "Edit watchbox" }));
        await user.type(screen.getByRole("textbox", { name: "Name" }), " renamed");
        await user.click(screen.getByRole("button", { name: "Save" }));

        // The dialog explains the reset in the backend's own terms.
        await waitFor(() =>
            expect(
                screen.getByText(
                    "A layer was removed, so objects tracked through it are forgotten.",
                ),
            ).toBeInTheDocument(),
        );
        expect(patches).toHaveLength(1);

        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(patches).toHaveLength(2));
        expect(patches[1]).toMatchObject({ confirmTrackingReset: true });
    });

    it("leaves the watchbox unchanged when a tracking reset is declined", async () => {
        const user = userEvent.setup();
        const patches: unknown[] = [];
        server.use(
            http.patch(`${API_BASE}/watchboxes/${WATCHBOX.id}`, async ({ request }) => {
                patches.push(await request.json());
                return HttpResponse.json(
                    {
                        code: "WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED",
                        message: "",
                        fields: ["layerIds"],
                        reasons: ["LAYER_REMOVED"],
                    },
                    { status: 409 },
                );
            }),
            // A conflicting patch still invalidates on settle (not just on success), so the
            // detail query refetches even though the request above never succeeds.
            http.get(`${API_BASE}/watchboxes/${WATCHBOX.id}`, () => HttpResponse.json(WATCHBOX)),
        );

        renderDetail(WATCHBOX);
        await openActionsMenu(user);
        await user.click(await screen.findByRole("menuitem", { name: "Edit watchbox" }));
        await user.type(screen.getByRole("textbox", { name: "Name" }), " renamed");
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(screen.getByText(/A layer was removed/)).toBeInTheDocument());
        await user.click(screen.getByRole("button", { name: "Cancel" }));

        await waitFor(() =>
            expect(screen.queryByText(/A layer was removed/)).not.toBeInTheDocument(),
        );
        expect(patches).toHaveLength(1);
    });

    // Degrades to read-only when a capability is false, or absent on an
    // older backend.
    it("does not offer edit when the caller may not patch", async () => {
        const user = userEvent.setup();
        renderDetail({ ...WATCHBOX, capabilities: { canPatch: false, canDelete: true } });

        await openActionsMenu(user);

        expect(screen.queryByRole("menuitem", { name: "Edit watchbox" })).not.toBeInTheDocument();
    });

    it("does not offer edit when the response carries no capabilities", async () => {
        const user = userEvent.setup();
        renderDetail({ ...WATCHBOX, capabilities: undefined });

        await openActionsMenu(user);

        expect(screen.queryByRole("menuitem", { name: "Edit watchbox" })).not.toBeInTheDocument();
    });

    // Entering and leaving edit each unmount the control that was clicked, so
    // focus would fall to <body> both ways. Leaving returns focus to the
    // actions menu trigger, since the "Edit watchbox" menu item itself is
    // gone once the menu that held it closes.
    it("focuses the name field on entering edit and the actions menu trigger on leaving", async () => {
        const user = userEvent.setup();
        renderDetail(WATCHBOX);

        await openActionsMenu(user);
        await user.click(await screen.findByRole("menuitem", { name: "Edit watchbox" }));

        await waitFor(() => expect(screen.getByRole("textbox", { name: "Name" })).toHaveFocus());

        await user.click(screen.getByRole("button", { name: "Cancel" }));

        await waitFor(() => expect(moreActionsTrigger()).toHaveFocus());
    });

    // `editing` starts false, so focusing whenever it is false would grab focus
    // the moment the detail view opens.
    it("does not grab focus when the detail view merely opens", () => {
        renderDetail(WATCHBOX);

        expect(moreActionsTrigger()).not.toHaveFocus();
    });

    // onDelete opens the confirm dialog rather than deleting, so the item
    // must hand focus over rather than grabbing it back.
    it("asks to delete rather than deleting outright", async () => {
        const user = userEvent.setup();
        const { onDelete } = renderDetail(WATCHBOX);

        await openActionsMenu(user);
        await user.click(await screen.findByRole("menuitem", { name: "Delete watchbox" }));

        expect(onDelete).toHaveBeenCalledOnce();
        expect(document.activeElement).not.toBe(document.body);
    });

    it("deletes the watchbox that is open", async () => {
        const user = userEvent.setup();
        const { onDelete } = renderDetail(WATCHBOX);

        await openActionsMenu(user);
        const deleteItem = await screen.findByRole("menuitem", { name: "Delete watchbox" });
        expect(deleteItem).not.toHaveAttribute("aria-disabled", "true");
        await user.click(deleteItem);

        expect(onDelete).toHaveBeenCalledOnce();
    });

    it("hides delete when the server capability does not allow it", async () => {
        const user = userEvent.setup();
        renderDetail({ ...WATCHBOX, capabilities: { canDelete: false, canPatch: true } });

        await openActionsMenu(user);

        expect(screen.queryByRole("menuitem", { name: "Delete watchbox" })).not.toBeInTheDocument();
    });

    it("offers Mute alerts as an action when not muted", async () => {
        const user = userEvent.setup();
        renderDetail(WATCHBOX, undefined, { isMuted: false });

        await openActionsMenu(user);

        expect(await screen.findByRole("menuitem", { name: "Mute alerts" })).toBeInTheDocument();
    });

    it("offers Unmute alerts as an action when muted", async () => {
        const user = userEvent.setup();
        renderDetail(WATCHBOX, undefined, { isMuted: true });

        await openActionsMenu(user);

        expect(await screen.findByRole("menuitem", { name: "Unmute alerts" })).toBeInTheDocument();
    });

    it("toggles the mute preference without touching edit or delete", async () => {
        const user = userEvent.setup();
        const onToggleMuted = vi.fn();
        renderDetail(WATCHBOX, undefined, { isMuted: false, onToggleMuted });

        await openActionsMenu(user);
        await user.click(await screen.findByRole("menuitem", { name: "Mute alerts" }));

        expect(onToggleMuted).toHaveBeenCalledWith(true);
    });

    it("still offers mute when the caller may neither patch nor delete", async () => {
        const user = userEvent.setup();
        renderDetail({ ...WATCHBOX, capabilities: { canPatch: false, canDelete: false } });

        await openActionsMenu(user);

        expect(await screen.findByRole("menuitem", { name: "Mute alerts" })).toBeInTheDocument();
    });

    it("shows a Muted indicator beside the status badges when muted", () => {
        renderDetail(WATCHBOX, undefined, { isMuted: true });

        expect(screen.getByText("Muted")).toBeInTheDocument();
    });

    it("does not show a Muted indicator when not muted", () => {
        renderDetail(WATCHBOX, undefined, { isMuted: false });

        expect(screen.queryByText("Muted")).not.toBeInTheDocument();
    });

    it("returns to the list from the breadcrumb", async () => {
        const user = userEvent.setup();
        const { onBack } = renderDetail(WATCHBOX);

        await user.click(screen.getByRole("button", { name: "Watchbox" }));

        expect(onBack).toHaveBeenCalledOnce();
    });
});

// Plugins load after the app starts; a detail open before symbology's fields arrive reads
// its symbol rule by path, then by name once they do.
describe("WatchboxDetail — fields that arrive later", () => {
    it("re-reads the rules when a field registers", () => {
        renderDetail({
            ...WATCHBOX,
            filter: {
                combine: "ANY",
                predicates: [
                    {
                        path: "properties.opencop.element.symbol",
                        operation: "SUBSTRING_IN",
                        values: ["6"],
                        offset: 3,
                        length: 1,
                        negate: false,
                        caseInsensitive: false,
                    },
                    {
                        path: "properties.opencop.element.symbol",
                        operation: "SUBSTRING_IN",
                        values: ["H", "K"],
                        offset: 1,
                        length: 1,
                        negate: false,
                        caseInsensitive: false,
                    },
                ],
            },
        });
        expect(screen.getAllByText(/its field isn't available/)).toHaveLength(2);

        act(() => {
            onTestFinished(installPlugins(["opencop.symbology"]));
        });

        expect(screen.queryByText(/its field isn't available/)).not.toBeInTheDocument();
        expect(screen.getByText("SIDC: Standard Identity")).toBeInTheDocument();
        expect(screen.getByText("Hostile/Faker")).toBeInTheDocument();
    });
});
