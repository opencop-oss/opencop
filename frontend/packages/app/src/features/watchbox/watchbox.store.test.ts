// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { MutationObserver, QueryClient } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { API_BASE, http, HttpResponse, server } from "~/test";
import type { CreateWatchboxRequest } from "./watchbox.api";
import type { Watchbox } from "./watchbox.model";
import { watchboxMutations, watchboxQueries } from "./watchbox.queries";
import {
    WATCHBOX_DRAFT_KEY,
    createWatchboxStore,
    WATCHBOX_STORE_ID,
    WATCHBOX_TYPE,
    type WatchboxObject,
    type WatchboxStore,
} from "./watchbox.store";

const WATCHBOXES_URL = `${API_BASE}/watchboxes`;

const POLYGON = {
    type: "Polygon" as const,
    coordinates: [
        [
            [0, 0],
            [1, 0],
            [1, 1],
            [0, 0],
        ],
    ],
};

function makeWatchbox(overrides: Partial<Watchbox> = {}): Watchbox {
    return {
        id: "wb-1",
        name: "My Watchbox",
        description: null,
        enabled: true,
        direction: "BOTH",
        polygon: POLYGON,
        filter: { combine: "ALL", predicates: [] },
        layerIds: ["layer-1"],
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

function testClient(): QueryClient {
    return new QueryClient({
        defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
}

/** Records every batch the store emits, by event. */
function recordEvents(store: WatchboxStore) {
    const events: { event: string; keys: string[] }[] = [];
    for (const event of ["add", "update", "remove"] as const) {
        store.on(event, (items: WatchboxObject[]) =>
            events.push({ event, keys: items.map((object) => object.key) }),
        );
    }
    return events;
}

const count = (store: WatchboxStore) => [...store.getAll()].length;

const disconnects: (() => void)[] = [];

function connected(queryClient: QueryClient) {
    const store = createWatchboxStore(queryClient);
    const events = recordEvents(store);
    disconnects.push(store.connect());
    return { store, events };
}

afterEach(() => {
    disconnects.splice(0).forEach((disconnect) => disconnect());
});

describe("createWatchboxStore", () => {
    it("holds what the list query already has when it connects", () => {
        const queryClient = testClient();
        queryClient.setQueryData(watchboxQueries.watchboxes().queryKey, [makeWatchbox()]);
        server.use(http.get(WATCHBOXES_URL, () => HttpResponse.json([makeWatchbox()])));

        const { store, events } = connected(queryClient);

        expect(events).toEqual([{ event: "add", keys: ["wb-1"] }]);
        const object = store.get("wb-1");
        expect(object?.type).toBe(WATCHBOX_TYPE);
        expect(object?.polygon).toEqual([0, 0, 1, 0, 1, 1, 0, 0]);
        expect([...store.getAll()]).toEqual([object]);
        expect(store.ref(object!)).toEqual({ type: WATCHBOX_STORE_ID, key: "wb-1" });
    });

    it("is empty and emits nothing while the list fails", async () => {
        const queryClient = testClient();
        server.use(http.get(WATCHBOXES_URL, () => new HttpResponse(null, { status: 500 })));

        const { store, events } = connected(queryClient);

        await vi.waitFor(() =>
            expect(queryClient.getQueryState(watchboxQueries.watchboxes().queryKey)?.status).toBe(
                "error",
            ),
        );
        expect(count(store)).toBe(0);
        expect(events).toEqual([]);
    });

    // React Query's structural sharing hands back the same objects for a
    // refetch that changed nothing, so the store has nothing to emit.
    it("emits nothing for a refetch that changes nothing, and keeps each object", async () => {
        const queryClient = testClient();
        server.use(
            http.get(WATCHBOXES_URL, () =>
                HttpResponse.json([makeWatchbox(), makeWatchbox({ id: "wb-2" })]),
            ),
        );
        const { store, events } = connected(queryClient);
        await vi.waitFor(() => expect(count(store)).toBe(2));
        const before = store.get("wb-1");

        await queryClient.refetchQueries({ queryKey: watchboxQueries.watchboxes().queryKey });

        expect(events).toEqual([{ event: "add", keys: ["wb-1", "wb-2"] }]);
        expect(store.get("wb-1")).toBe(before);
    });

    it("emits an update with a new object for a changed row only", async () => {
        const queryClient = testClient();
        let second = makeWatchbox({ id: "wb-2" });
        server.use(http.get(WATCHBOXES_URL, () => HttpResponse.json([makeWatchbox(), second])));
        const { store, events } = connected(queryClient);
        await vi.waitFor(() => expect(count(store)).toBe(2));
        const unchanged = store.get("wb-1");
        const changed = store.get("wb-2");

        second = makeWatchbox({ id: "wb-2", enabled: false });
        await queryClient.refetchQueries({ queryKey: watchboxQueries.watchboxes().queryKey });

        expect(events.at(-1)).toEqual({ event: "update", keys: ["wb-2"] });
        expect(store.get("wb-1")).toBe(unchanged);
        expect(store.get("wb-2")).not.toBe(changed);
        expect(store.get("wb-2")?.watchbox?.enabled).toBe(false);
    });

    it("emits a remove for a row the list no longer has", async () => {
        const queryClient = testClient();
        let list = [makeWatchbox(), makeWatchbox({ id: "wb-2" })];
        server.use(http.get(WATCHBOXES_URL, () => HttpResponse.json(list)));
        const { store, events } = connected(queryClient);
        await vi.waitFor(() => expect(count(store)).toBe(2));

        list = [makeWatchbox()];
        await queryClient.refetchQueries({ queryKey: watchboxQueries.watchboxes().queryKey });

        expect(events.at(-1)).toEqual({ event: "remove", keys: ["wb-2"] });
        expect(store.get("wb-2")).toBeUndefined();
    });

    it("keeps every later row when a row before the end of the list is deleted", async () => {
        const queryClient = testClient();
        let list = [makeWatchbox(), makeWatchbox({ id: "wb-2" }), makeWatchbox({ id: "wb-3" })];
        server.use(http.get(WATCHBOXES_URL, () => HttpResponse.json(list)));
        const { store, events } = connected(queryClient);
        await vi.waitFor(() => expect(count(store)).toBe(3));
        const last = store.get("wb-3");
        const before = events.length;

        list = [makeWatchbox(), makeWatchbox({ id: "wb-3" })];
        await queryClient.refetchQueries({ queryKey: watchboxQueries.watchboxes().queryKey });

        // Structural sharing compares the list by position, so wb-3 comes back as a
        // new object; the store must still see it as unchanged.
        expect(events.slice(before)).toEqual([{ event: "remove", keys: ["wb-2"] }]);
        expect(store.get("wb-3")).toBe(last);
    });

    it("stops following the list once disconnected", async () => {
        const queryClient = testClient();
        server.use(http.get(WATCHBOXES_URL, () => HttpResponse.json([makeWatchbox()])));
        const store = createWatchboxStore(queryClient);
        const disconnect = store.connect();
        await vi.waitFor(() => expect(count(store)).toBe(1));

        disconnect();
        queryClient.setQueryData(watchboxQueries.watchboxes().queryKey, []);

        expect(count(store)).toBe(1);
    });

    // The form ends the draw session — removing the draft — only after
    // `mutateAsync` resolves. The saved row must already be in the store then,
    // or the watchbox would vanish from the map until the next poll.
    it("holds a created watchbox by the time the create mutation resolves", async () => {
        const queryClient = testClient();
        const created = makeWatchbox({ id: "wb-new" });
        let list: Watchbox[] = [];
        server.use(
            http.get(WATCHBOXES_URL, () => HttpResponse.json(list)),
            http.post(WATCHBOXES_URL, () => {
                list = [created];
                return HttpResponse.json(created);
            }),
        );
        const { store } = connected(queryClient);
        await vi.waitFor(() =>
            expect(queryClient.getQueryState(watchboxQueries.watchboxes().queryKey)?.status).toBe(
                "success",
            ),
        );
        const request: CreateWatchboxRequest = {
            name: created.name,
            polygon: POLYGON,
            direction: "BOTH",
            layerIds: ["layer-1"],
        };

        await new MutationObserver(queryClient, watchboxMutations.create(queryClient)).mutate(
            request,
        );

        expect(store.get("wb-new")?.watchbox).toEqual(created);
    });
});

describe("the watchbox being drawn", () => {
    const draft = (polygon: number[]): WatchboxObject => ({
        key: WATCHBOX_DRAFT_KEY,
        type: WATCHBOX_TYPE,
        watchbox: null,
        polygon,
    });

    it("is added, then updated, then removed, and is never one of the saved watchboxes", () => {
        const store = createWatchboxStore(testClient());
        const events = recordEvents(store);

        store.setDraft(draft([0, 0, 1, 0, 1, 1]));
        store.setDraft(draft([0, 0, 2, 0, 2, 2]));
        expect([...store.getAll()]).toEqual([]);
        expect(store.get(WATCHBOX_DRAFT_KEY)).toBeUndefined();
        store.clearDraft();
        store.clearDraft();

        expect(events).toEqual([
            { event: "add", keys: [WATCHBOX_DRAFT_KEY] },
            { event: "update", keys: [WATCHBOX_DRAFT_KEY] },
            { event: "remove", keys: [WATCHBOX_DRAFT_KEY] },
        ]);
    });

    it("is not reported to a listener that has been removed", () => {
        const store = createWatchboxStore(testClient());
        const listener = vi.fn();
        store.on("add", listener);
        store.off("add", listener);

        store.setDraft(draft([0, 0, 1, 0, 1, 1]));

        expect(listener).not.toHaveBeenCalled();
    });
});
