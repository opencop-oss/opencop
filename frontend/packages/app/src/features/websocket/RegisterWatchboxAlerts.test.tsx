// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Live Watchbox alert handling: toast delivery, the linked alert
 * description's map behavior, writing each alert into the Notifications
 * tab's detections cache (recordDetection, watchbox-detections.cache.ts) —
 * see the "write into the detections cache" suite below — and per-user
 * muting of the toast — see the "muted watchboxes" suite below.
 */
import { QueryClient } from "@tanstack/react-query";
import { Point, getElementKey, type Element } from "@opencop/elements";
import { useElementActions } from "@opencop/ingest";
import { useEffect } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createTestUser, render, screen, userEvent, waitFor } from "~/test";
import {
    attachRuntime,
    fakeDeps,
    installInteractionRuntimeHarness,
} from "~/test/interaction-runtime-harness";
import { startEdit } from "~/features/map/interaction-runtime/session/commands";
import { getInteractionState } from "~/features/map/interaction-runtime/session/interaction.store";
import { useMapStore } from "~/features/map/core/map.store";
import { dataSourceQueries } from "../data-sources/data-sources.queries";
import type { WatchboxAlertEvent } from "../watchbox/watchbox.api";
import { watchboxQueries } from "../watchbox/watchbox.queries";
import type { Watchbox } from "../watchbox/watchbox.model";
import { WATCHBOX_MUTE_STORAGE_KEY, useWatchboxMuteStore } from "../watchbox/watchbox-mute.store";
import { AlertDescription, RegisterWatchboxAlerts } from "./RegisterWatchboxAlerts";

const DEFAULT_USER_ID = createTestUser().user.id;

installInteractionRuntimeHarness();

const mockShowToast = vi.fn();
vi.mock("../notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast: mockShowToast }),
}));

type Handler = (payload: Uint8Array) => void;
let handlers: Map<string, Handler>;

// The shared OCMPClient test stub discards its subscribe handler immediately,
// so this test keeps real handlers that can receive simulated live alerts.
vi.mock("./use-ocmp-client.hook", () => ({
    useOCMPClient: () => [
        {
            subscribe: (destination: string, handler: Handler) => {
                handlers.set(destination, handler);
                return () => handlers.delete(destination);
            },
            unsubscribe: (destination: string) => handlers.delete(destination),
        },
        { status: "connected" },
    ],
}));

const SOURCE_ID = "source-1";
const OBJECT_ID = "object-1";
const KEY = getElementKey({ source: SOURCE_ID, objectId: OBJECT_ID });
const POSITION: [number, number] = [10, 20];

const ALERT: WatchboxAlertEvent = {
    watchboxId: "watchbox-1",
    configEpoch: 1,
    sourceId: SOURCE_ID,
    objectId: OBJECT_ID,
    eventId: "event-1",
    effectiveEventTime: "2026-01-01T00:00:00Z",
    kind: "ENTER",
    detectionId: "detection-1",
    objectDisplayName: "Tango One",
};

// AlertDescription reads only the Watchbox id and name.
const DESCRIPTION_WATCHBOX = { id: "watchbox-1", name: "North Gate" } as Watchbox;

const DATA_SOURCE_QUERY_DATA = {
    [JSON.stringify(dataSourceQueries.myList().queryKey)]: [
        { serverId: SOURCE_ID, serverName: "Radar Feed" },
    ],
};

function makeElement(displayName = "Tango One"): Element {
    return Point.create({
        position: POSITION,
        meta: { source: SOURCE_ID, objectId: OBJECT_ID, displayName },
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

function renderAlert({
    element,
    alert = ALERT,
}: { element?: Element; alert?: WatchboxAlertEvent } = {}) {
    return render(
        <>
            {element && <Seed element={element} />}
            <AlertDescription alert={alert} watchbox={DESCRIPTION_WATCHBOX} />
        </>,
        { queryData: DATA_SOURCE_QUERY_DATA },
    );
}

const WATCHBOX_TOPIC = "/topic/watchbox/";

/** Counts watchbox subscriptions only; the harness's providers hold subscriptions of their own. */
function watchboxSubscriptionCount(): number {
    return [...handlers.keys()].filter((destination) => destination.startsWith(WATCHBOX_TOPIC))
        .length;
}

function publish(watchboxId: string, alert: WatchboxAlertEvent) {
    const handler = handlers.get(`${WATCHBOX_TOPIC}${watchboxId}`);
    if (!handler) throw new Error(`No subscription for watchbox ${watchboxId}`);
    handler(new TextEncoder().encode(JSON.stringify(alert)));
}

function makeWatchbox(overrides: Partial<Watchbox> & { id: string }): Watchbox {
    return {
        name: overrides.id,
        description: null,
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
        filter: { combine: "ALL", predicates: [] },
        layerIds: [],
        configEpoch: 1,
        alertingStatus: "ACTIVE",
        alertingReasons: [],
        sourceStatuses: [],
        capabilities: { canDelete: true, canPatch: true },
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
        ...overrides,
    };
}

// watchForAlerts dedupes against module-level seen-alert state, so each test
// alert needs a unique event id.
let nextEventId = 0;
function makeAlert(
    overrides: Partial<WatchboxAlertEvent> & { watchboxId: string },
): WatchboxAlertEvent {
    return {
        configEpoch: 1,
        sourceId: "src-1",
        objectId: "obj-1",
        eventId: `evt-${++nextEventId}`,
        effectiveEventTime: "2026-01-01T00:00:00Z",
        kind: "ENTER",
        detectionId: null,
        objectDisplayName: null,
        ...overrides,
    };
}

const EMPTY_DETECTIONS_PAGE = { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 };
const WATCHBOX_A = makeWatchbox({ id: "wb-a", name: "Watchbox A" });
const WATCHBOX_B = makeWatchbox({ id: "wb-b", name: "Watchbox B" });

/** Prevents seeded Watchbox data from auto-refetching in these WS-only tests. */
function makeQueryClient(): QueryClient {
    return new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
}

function renderRegistered(queryClient: QueryClient) {
    return render(<RegisterWatchboxAlerts />, {
        queryClient,
        queryData: {
            [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [WATCHBOX_A, WATCHBOX_B],
        },
    });
}

function resetMuteStore() {
    useWatchboxMuteStore.setState({ mutedIdsByUser: {} });
    localStorage.removeItem(WATCHBOX_MUTE_STORAGE_KEY);
}

beforeEach(() => {
    handlers = new Map();
    useMapStore.setState({ selected: null, flyToTarget: undefined });
    resetMuteStore();
});

afterEach(() => {
    mockShowToast.mockClear();
    resetMuteStore();
});

describe("AlertDescription", () => {
    it("prefers the alert's display name when the element is not held", () => {
        renderAlert();

        expect(screen.getByText(/Tango One/)).toBeInTheDocument();
        expect(screen.queryByText(new RegExp(OBJECT_ID))).not.toBeInTheDocument();
        expect(screen.getByText(/data source may be hidden/i)).toBeInTheDocument();
        expect(screen.queryByRole("button")).not.toBeInTheDocument();
    });

    it("falls back to the raw object id when the alert has no display name", () => {
        renderAlert({ alert: { ...ALERT, objectDisplayName: null } });

        expect(screen.getByText(new RegExp(OBJECT_ID))).toBeInTheDocument();
    });

    it("prefers the alert's captured display name over the local element's current name", () => {
        renderAlert({ element: makeElement("Renamed Locally") });

        expect(screen.getByRole("button", { name: "Tango One" })).toBeInTheDocument();
        expect(screen.queryByText("Renamed Locally")).not.toBeInTheDocument();
    });

    it("selects the element and moves the camera when its name is clicked", async () => {
        const element = makeElement();
        renderAlert({ element });

        await userEvent.click(screen.getByRole("button", { name: "Tango One" }));

        expect(useMapStore.getState().selected).toBe(KEY);
        expect(useMapStore.getState().flyToTarget).toMatchObject({ center: POSITION });
    });

    it("does nothing on click while an interaction session holds the map", async () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        const element = makeElement();
        renderAlert({ element });

        startEdit(makeElement(), false);
        expect(getInteractionState().session.kind).toBe("edit");

        await userEvent.click(screen.getByRole("button", { name: "Tango One" }));

        expect(useMapStore.getState().selected).toBeNull();
        expect(useMapStore.getState().flyToTarget).toBeUndefined();

        detach();
    });
});

describe("RegisterWatchboxAlerts — live alerts write into the detections cache", () => {
    // The Notifications tab used to be kept fresh by invalidating its
    // detections query on every alert; a burst of alerts (many objects
    // crossing at once) turned into a burst of refetches. It now writes each
    // alert straight into that query's cached page instead (recordDetection,
    // watchbox-detections.cache.ts) — zero requests in the normal case.
    it("writes an ENTER/LEAVE alert into only the alerting watchbox's cached page", async () => {
        const queryClient = makeQueryClient();
        queryClient.setQueryData(
            watchboxQueries.detections("wb-a").queryKey,
            EMPTY_DETECTIONS_PAGE,
        );
        queryClient.setQueryData(
            watchboxQueries.detections("wb-b").queryKey,
            EMPTY_DETECTIONS_PAGE,
        );
        renderRegistered(queryClient);
        await waitFor(() => expect(watchboxSubscriptionCount()).toBe(2));

        publish("wb-a", makeAlert({ watchboxId: "wb-a", detectionId: "det-1" }));

        await waitFor(() => expect(mockShowToast).toHaveBeenCalled());
        expect(queryClient.getQueryData(watchboxQueries.detections("wb-a").queryKey)).toMatchObject(
            {
                content: [expect.objectContaining({ id: "det-1" })],
                totalElements: 1,
            },
        );
        // Untouched: no invalidation, no request, no row.
        expect(
            queryClient.getQueryState(watchboxQueries.detections("wb-b").queryKey)?.isInvalidated,
        ).toBeFalsy();
        expect(queryClient.getQueryData(watchboxQueries.detections("wb-b").queryKey)).toEqual(
            EMPTY_DETECTIONS_PAGE,
        );
    });

    it("writes filter-match alerts into the cache too, even though they don't toast", async () => {
        const queryClient = makeQueryClient();
        queryClient.setQueryData(
            watchboxQueries.detections("wb-a").queryKey,
            EMPTY_DETECTIONS_PAGE,
        );
        renderRegistered(queryClient);
        await waitFor(() => expect(watchboxSubscriptionCount()).toBe(2));

        publish(
            "wb-a",
            makeAlert({ watchboxId: "wb-a", kind: "FILTER_MATCH_GAINED", detectionId: "det-1" }),
        );

        await waitFor(() =>
            expect(
                queryClient.getQueryData(watchboxQueries.detections("wb-a").queryKey),
            ).toMatchObject({ content: [expect.objectContaining({ id: "det-1" })] }),
        );
        expect(mockShowToast).not.toHaveBeenCalled();
    });

    it("falls back to invalidating when the alert has no stable detection id", async () => {
        const queryClient = makeQueryClient();
        queryClient.setQueryData(
            watchboxQueries.detections("wb-a").queryKey,
            EMPTY_DETECTIONS_PAGE,
        );
        renderRegistered(queryClient);
        await waitFor(() => expect(watchboxSubscriptionCount()).toBe(2));

        // detectionId: null (makeAlert's default) — an older backend, or a row
        // that predates this field.
        publish("wb-a", makeAlert({ watchboxId: "wb-a" }));

        await waitFor(() =>
            expect(
                queryClient.getQueryState(watchboxQueries.detections("wb-a").queryKey)
                    ?.isInvalidated,
            ).toBe(true),
        );
    });

    it("falls back to invalidating when nothing is cached yet (tab never opened)", async () => {
        const queryClient = makeQueryClient();
        renderRegistered(queryClient);
        await waitFor(() => expect(watchboxSubscriptionCount()).toBe(2));

        publish("wb-a", makeAlert({ watchboxId: "wb-a", detectionId: "det-1" }));
        await waitFor(() => expect(mockShowToast).toHaveBeenCalled());

        // No page existed to write into, and no observer to invalidate for —
        // the query never gets created at all, let alone fetched.
        expect(
            queryClient.getQueryState(watchboxQueries.detections("wb-a").queryKey),
        ).toBeUndefined();
    });

    it("still toasts ENTER and LEAVE alerts with linked content", async () => {
        const queryClient = makeQueryClient();
        renderRegistered(queryClient);
        await waitFor(() => expect(watchboxSubscriptionCount()).toBe(2));

        publish("wb-a", makeAlert({ watchboxId: "wb-a", kind: "LEAVE" }));

        await waitFor(() =>
            expect(mockShowToast).toHaveBeenCalledWith(
                expect.objectContaining({ type: "important", content: expect.anything() }),
            ),
        );
    });
});

describe("RegisterWatchboxAlerts — muted watchboxes", () => {
    it("skips the toast for a muted watchbox's ENTER/LEAVE alert but still records the detection", async () => {
        useWatchboxMuteStore.getState().actions.setMuted(DEFAULT_USER_ID, "wb-a", true);
        const queryClient = makeQueryClient();
        queryClient.setQueryData(
            watchboxQueries.detections("wb-a").queryKey,
            EMPTY_DETECTIONS_PAGE,
        );
        renderRegistered(queryClient);
        await waitFor(() => expect(watchboxSubscriptionCount()).toBe(2));

        publish("wb-a", makeAlert({ watchboxId: "wb-a", detectionId: "det-1" }));

        await waitFor(() =>
            expect(
                queryClient.getQueryData(watchboxQueries.detections("wb-a").queryKey),
            ).toMatchObject({ content: [expect.objectContaining({ id: "det-1" })] }),
        );
        expect(mockShowToast).not.toHaveBeenCalled();
    });

    it("still toasts an unmuted watchbox's alert while a different watchbox is muted", async () => {
        useWatchboxMuteStore.getState().actions.setMuted(DEFAULT_USER_ID, "wb-a", true);
        const queryClient = makeQueryClient();
        renderRegistered(queryClient);
        await waitFor(() => expect(watchboxSubscriptionCount()).toBe(2));

        publish("wb-b", makeAlert({ watchboxId: "wb-b" }));

        await waitFor(() => expect(mockShowToast).toHaveBeenCalled());
    });

    it("observes a mute toggled after subscriptions are already registered, with no new subscription", async () => {
        const queryClient = makeQueryClient();
        renderRegistered(queryClient);
        await waitFor(() => expect(watchboxSubscriptionCount()).toBe(2));

        publish("wb-a", makeAlert({ watchboxId: "wb-a" }));
        await waitFor(() => expect(mockShowToast).toHaveBeenCalledTimes(1));

        useWatchboxMuteStore.getState().actions.setMuted(DEFAULT_USER_ID, "wb-a", true);
        publish("wb-a", makeAlert({ watchboxId: "wb-a" }));

        // No new toast for the muted alert, and no re-subscription happened —
        // the same two handlers registered at mount are still the ones in use.
        expect(watchboxSubscriptionCount()).toBe(2);
        expect(mockShowToast).toHaveBeenCalledTimes(1);
    });
});
