// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { isEqual } from "lodash";
import { QueryObserver, type QueryClient } from "@tanstack/react-query";
import type { DraftObject } from "@opencop/elements";
import type { ObjectRef, Store, StoreEvent } from "@opencop/ingest";
import type { Watchbox } from "./watchbox.model";
import { watchboxQueries } from "./watchbox.queries";

/** The watchbox store's id: its objects' refs and its pick response name it. */
export const WATCHBOX_STORE_ID = "watchboxes";

/** The one type the watchbox store holds. */
export const WATCHBOX_TYPE = "watchbox";

/** A watchbox as the map draws it: a saved one, or the one being drawn. */
export interface WatchboxObject {
    /** The watchbox id; {@link WATCHBOX_DRAFT_KEY} for the one being drawn. */
    readonly key: string;
    readonly type: typeof WATCHBOX_TYPE;
    /** The saved watchbox; null while it is being drawn, before it has one. */
    readonly watchbox: Watchbox | null;
    /** The outer ring as flat `[lng, lat, …]` positions. */
    readonly polygon: number[];
}

/** The key of the watchbox being drawn — never a saved watchbox's id, and never in the store. */
export const WATCHBOX_DRAFT_KEY = "draft";

/**
 * The polygon being drawn or adjusted, as an unsaved watchbox, so the watchbox
 * pipeline draws it exactly as it will look once saved. Built fresh on each call — the draft's geometry changes in place.
 */
export function watchboxDraftOf(draft: DraftObject): WatchboxObject {
    return {
        key: WATCHBOX_DRAFT_KEY,
        type: WATCHBOX_TYPE,
        watchbox: null,
        polygon: draft.geometry.getAllPos() as number[],
    };
}

/** The saved watchboxes as a {@link Store}, plus the lifecycle its owner drives. */
export interface WatchboxStore extends Store<WatchboxObject> {
    /** Start following the watchbox list query; returns the stop. */
    connect(): () => void;
    /**
     * Hold `draft` as the watchbox being drawn, emitting `add` the first time and
     * `update` after. It is never one of the saved watchboxes `get` and `getAll` return.
     */
    setDraft(draft: WatchboxObject): void;
    /** Drop the watchbox being drawn, emitting `remove` for it. */
    clearDraft(): void;
}

function toObject(watchbox: Watchbox): WatchboxObject {
    return {
        key: watchbox.id,
        type: WATCHBOX_TYPE,
        watchbox,
        polygon: watchbox.polygon.coordinates[0].flat(),
    };
}

type Listener = (objects: WatchboxObject[]) => void;

/**
 * The saved watchboxes as a store — a projection of the React Query list cache,
 * not a second source of truth. {@link WatchboxStore.connect} starts a
 * `QueryObserver` on `watchboxQueries.watchboxes()`; each result is diffed by id
 * against what the store holds and emitted as `remove`, `add` and `update`.
 *
 * **Identity.** A row's object is reused while the query hands back an equal
 * `Watchbox` — the same object, or one equal by value — so a poll that changes
 * nothing emits nothing. Reference equality alone is not enough: React Query's
 * structural sharing compares the list by position, so a delete before the end of
 * the list hands every later row back as a new object.
 *
 * While the list loads or fails, the query has no data and the store is empty:
 * nothing is drawn, and nothing here reports an error (the panel does).
 */
export function createWatchboxStore(queryClient: QueryClient): WatchboxStore {
    const held = new Map<string, WatchboxObject>();
    let draft: WatchboxObject | null = null;
    const listeners: Record<StoreEvent, Set<Listener>> = {
        add: new Set(),
        update: new Set(),
        remove: new Set(),
    };
    const emit = (event: StoreEvent, objects: WatchboxObject[]) => {
        if (objects.length > 0) listeners[event].forEach((listener) => listener(objects));
    };

    const sync = (watchboxes: readonly Watchbox[] = []) => {
        const ids = new Set(watchboxes.map((watchbox) => watchbox.id));
        const removed: WatchboxObject[] = [];
        const added: WatchboxObject[] = [];
        const updated: WatchboxObject[] = [];

        for (const [key, object] of held) {
            if (ids.has(key)) continue;
            held.delete(key);
            removed.push(object);
        }
        for (const watchbox of watchboxes) {
            const prev = held.get(watchbox.id);
            if (prev && (prev.watchbox === watchbox || isEqual(prev.watchbox, watchbox))) continue;
            const object = toObject(watchbox);
            held.set(watchbox.id, object);
            (prev ? updated : added).push(object);
        }

        emit("remove", removed);
        emit("add", added);
        emit("update", updated);
    };

    return {
        type: WATCHBOX_STORE_ID,
        get: (key) => held.get(key),
        getAll: () => held.values(),
        ref: (object): ObjectRef => ({ type: WATCHBOX_STORE_ID, key: object.key }),
        on: (event, listener) => void listeners[event].add(listener),
        off: (event, listener) => void listeners[event].delete(listener),
        connect() {
            const observer = new QueryObserver(queryClient, watchboxQueries.watchboxes());
            sync(observer.getCurrentResult().data);
            return observer.subscribe((result) => sync(result.data));
        },
        setDraft(next) {
            const event = draft ? "update" : "add";
            draft = next;
            emit(event, [next]);
        },
        clearDraft() {
            const previous = draft;
            draft = null;
            if (previous) emit("remove", [previous]);
        },
    };
}
