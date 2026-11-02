// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback, useMemo, useSyncExternalStore } from "react";
import { useIngestContext } from "./IngestContext";
import { Element, localEventSource } from "@opencop/elements";
import type { ElementStore } from "../ElementStore";
import type { LocalSource } from "../sources/LocalSource";

function matchField<K extends keyof Element>(key: K) {
    return (value: Element[K]) => (element: Element) => element[key] === value;
}
const matchSource = matchField("source");
const matchType = matchField("type");
const matchKey = matchField("key");
const matchAll = () => true;

interface UseElementsOptions<T> {
    enabled?: boolean;
    disabledSnapshot: T;
}

class ElementsSnapshot<T> {
    private readonly store: ElementStore;
    private readonly actions: ElementActions;
    private readonly select: (actions: ElementActions) => T;
    private revision: number;
    private value: T;

    constructor(
        store: ElementStore,
        actions: ElementActions,
        select: (actions: ElementActions) => T,
        initialValue: T,
    ) {
        this.store = store;
        this.actions = actions;
        this.select = select;
        this.revision = store.getRevision();
        this.value = initialValue;
    }

    getRevision = () => this.revision;
    getValue = () => this.value;
    rebuild = () => {
        this.revision = this.store.getRevision();
        this.value = this.select(this.actions);
    };
}

function useElements<T>(
    matches: (el: Element) => boolean,
    select: (actions: ElementActions) => T,
    options?: UseElementsOptions<T>,
): T {
    const { store } = useIngestContext();
    const actions = useElementActions();
    const enabled = options?.enabled ?? true;
    // Only `undefined` when the caller omits `options` entirely — and every
    // such caller also leaves `enabled` at its `true` default, so the branch
    // below that would read this value never executes in that case. Callers
    // that pass `enabled: false` are required (by `UseElementsOptions<T>`) to
    // also supply `disabledSnapshot`, so the cast holds at every call site.
    const disabledSnapshot = options?.disabledSnapshot;
    const snapshot = useMemo(
        () =>
            new ElementsSnapshot(
                store,
                actions,
                select,
                enabled ? select(actions) : (disabledSnapshot as T),
            ),
        [store, actions, select, enabled, disabledSnapshot],
    );

    return useSyncExternalStore(
        useCallback(
            (cb) => {
                if (!enabled) return () => {};

                const listener = (elements: Element[]) => {
                    for (const el of elements) {
                        if (matches(el)) {
                            snapshot.rebuild();
                            cb();
                            return;
                        }
                    }
                };

                store.on("add", listener);
                store.on("remove", listener);
                store.on("update", listener);

                // Catch a mutation between render and subscription without
                // replacing an otherwise-stable mount snapshot.
                if (snapshot.getRevision() !== store.getRevision()) snapshot.rebuild();

                return () => {
                    store.off("add", listener);
                    store.off("remove", listener);
                    store.off("update", listener);
                };
            },
            [store, matches, enabled, snapshot],
        ),
        snapshot.getValue,
    );
}

export function useElementsBySource(id: string) {
    const matches = useMemo(() => matchSource(id), [id]);
    const selector = useCallback((actions: ElementActions) => () => actions.getBySource(id), [id]);
    return useElements(matches, selector);
}

export function useElementsByType(type: string) {
    const matches = useMemo(() => matchType(type), [type]);
    const selector = useCallback((actions: ElementActions) => actions.getByType(type), [type]);
    return useElements(matches, selector);
}

export function useElementCount() {
    const selector = useCallback((actions: ElementActions) => actions.getCount(), []);
    return useElements(matchAll, selector);
}

export interface UseAllElementsOptions {
    enabled?: boolean;
}

const EMPTY_ELEMENTS: readonly Element[] = Object.freeze([]);

/**
 * Reactive snapshot of every element currently held by the live ingest store.
 * The array is rebuilt when WebSocket-backed elements are added, updated, or
 * deleted, so consumers can derive views without maintaining a second cache.
 * When disabled, the hook returns a stable empty array and does not subscribe
 * to store mutations.
 *
 * NOTE: This re-runs on every store mutation for any source. At high ingest
 * rates (>10 events/s), profile consumers for visible rendering jank.
 */
export function useAllElements(options?: UseAllElementsOptions): ReadonlyArray<Element> {
    const selector = useCallback(
        (actions: ElementActions): readonly Element[] => Array.from(actions.allTypes() ?? []),
        [],
    );
    return useElements(matchAll, selector, {
        enabled: options?.enabled,
        disabledSnapshot: EMPTY_ELEMENTS,
    });
}

export function useSourceCount(id: string) {
    const matches = useMemo(() => matchSource(id), [id]);
    const selector = useCallback((actions: ElementActions) => actions.getSourceCount(id), [id]);
    return useElements(matches, selector);
}

export function useElement(key: string) {
    const matches = useMemo(() => matchKey(key), [key]);
    const selector = useCallback((actions: ElementActions) => actions.get(key), [key]);
    return useElements(matches, selector);
}

export function useDraftElement() {
    const selector = useCallback((actions: ElementActions) => actions.getDraft(), []);
    return useElements(matchAll, selector);
}

export interface ElementActions {
    // Reads
    get(key: string): Element | undefined;
    getByType(type: string): IterableIterator<Element> | undefined;
    getBySource(sourceId: string): IterableIterator<Element> | undefined;
    getCount(): number;
    getSourceCount(sourceId: string): number;
    getSourceIds(): IterableIterator<string> | undefined;
    allTypes(): IterableIterator<Element> | undefined;

    // Mutations
    add: (elements: Iterable<Element> | Element) => void;
    delete: (elements: Iterable<string> | string) => void;
    deleteBySource: (sourceId: string) => void;

    setDraft: (element: Element) => void;
    getDraft: () => Element | undefined;
    clearDraft: () => void;
    commitDraft: () => void;

    /** @deprecated */
    hideSource: (sourceId: string) => void;
    /** @deprecated */
    showSource: (sourceId: string) => void;
}

/**
 * Build the app-facing mutation/read API over the store.
 *
 * Mutations are routed to the source that *owns* the data: locally-drawn
 * elements go through {@link LocalSource} (which owns IndexedDB and projects
 * into the store), everything else is written to the store directly — elements
 * from a `WebSocketSource` are already source-driven, since that source pushes
 * them in via its bound handler. This keeps the store a projection of its
 * sources and keeps one gate instead of one per call site.
 */
export function createActions(store: ElementStore, localSource?: LocalSource): ElementActions {
    const isLocal = (element: Element) => element.source === localEventSource;

    const add = (elements: Iterable<Element> | Element) => {
        if (!localSource) return store.add(elements);

        const list = elements instanceof Element ? [elements] : [...elements];
        // Single pass split into "owned by LocalSource" vs "everything else".
        const local: Element[] = [];
        const remote: Element[] = [];
        for (const element of list) (isLocal(element) ? local : remote).push(element);

        if (remote.length > 0) store.add(remote);
        if (local.length > 0) localSource.add(local);
    };

    const remove = (keys: Iterable<string> | string) => {
        if (!localSource) return store.delete(keys);

        const list = typeof keys === "string" ? [keys] : [...keys];
        const local: Element[] = [];
        const remote: string[] = [];

        for (const key of list) {
            const element = store.get(key);
            if (element && isLocal(element)) local.push(element);
            else remote.push(key);
        }

        if (remote.length > 0) store.delete(remote);
        if (local.length > 0) localSource.delete(local);
    };

    return {
        // Reads
        get: store.get.bind(store),
        getByType: store.getByType.bind(store),
        getBySource: store.getBySource.bind(store),
        getCount: store.getCount.bind(store),
        getSourceCount: store.getSourceCount.bind(store),
        getSourceIds: store.getSourceIds.bind(store),
        allTypes: store.allTypes.bind(store),

        // Mutations
        add,
        delete: remove,
        deleteBySource: store.deleteBySource.bind(store),

        setDraft: store.setDraft.bind(store),
        getDraft: store.getDraft.bind(store),
        clearDraft: store.clearDraft.bind(store),
        commitDraft: store.commitDraft.bind(store),

        // Deprecated
        hideSource: store.hideSource.bind(store),
        showSource: store.showSource.bind(store),
    };
}

export function useElementActions() {
    const { store, localSource } = useIngestContext();
    return useMemo(() => createActions(store, localSource), [store, localSource]);
}
