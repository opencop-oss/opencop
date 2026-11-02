// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Element } from "@opencop/elements";
import Logger from "js-logger";
import { MinHeap } from "./MinHeap";
import EventEmitter from "eventemitter3";
import type { Store } from "./Store";

const logger = Logger.get("element_store");
const MAX_INT32 = 0x7fffffff;

type StoreEvent = "add" | "update" | "change";

type StoreListeners = {
    [K in StoreEvent]: [Element[]];
} & {
    remove: [Element[]];
    /**
     * Fired when the stale-sweep evicts elements because their `stale` time
     * passed — as opposed to a source eviction (`deleteBySource`). A source that
     * owns a backing store (e.g. `LocalSource` → IndexedDB) uses this to delete
     * an *expired* element's persisted row. It is deliberately distinct from
     * `remove` so that source eviction (a mode switch disabling a source) does
     * NOT trigger owner-side deletion of the user's data — only genuine expiry
     * does. See `IngestProvider`.
     */
    expire: [Element[]];
    /** @deprecated For compatability with existing binary compaction. Visibility will be concern for consumers, not the store. */
    sourceVisibilityChange: [string, boolean];
    /** @deprecated For compatability with existing binary compaction. Compaction won't exist in the future. */
    compactionComplete: [string];
};

type SourceId = string;
type Key = string;
type Type = string;

/** Domain store of {@link Element} objects. Notifies subscribers of CRUD operations. */
export class ElementStore extends EventEmitter<StoreListeners> implements Store<Element> {
    readonly type = "element" as const;
    /** Monotonic revision for consumers that need a cached external-store snapshot. */
    private revision = 0;
    private readonly byKey = new Map<Key, Element>();
    private readonly byType = new Map<Type, Map<Key, Element>>();
    /**
     * Source → element-key → element. Inner map is keyed by `element.key`
     * (not `objectId`) so historical sources, where multiple versions coexist
     * per objectId, don't silently dedupe. Live sources are unaffected because
     * `element.key === source + objectId` and there's at most one entry per
     * `(source, objectId)` pair.
     */
    private readonly bySource = new Map<SourceId, Map<Key, Element>>();

    /** {@link MinHeap} keeping track of the next element to evict based off {@link Element.stale stale time}. */
    private readonly staleHeap: MinHeap<Element> = new MinHeap((a, b) => a.stale - b.stale);
    private sweepTimeout?: ReturnType<typeof setTimeout>;
    /**
     * @deprecated For compatibility with existing source hiding/show functionality. Visibility will be a concern for consumers, not the store.
     */
    private readonly hiddenSources = new Set<string>();
    /** @provisional */
    private draft?: {
        element: Element;
        cleanup: () => void;
    };

    /**
     * Retrieve an {@link Element}.
     *
     * @param key {@link Element.key element key}.
     * @returns The {@link Element} or `undefined`.
     */
    get(key: string): Element | undefined {
        return this.byKey.get(key);
    }

    ref(element: Element) {
        return {
            type: this.type,
            key: element.key,
        };
    }

    getAll() {
        return this.byKey.values();
    }

    /** Return the current element-data revision. */
    getRevision(): number {
        return this.revision;
    }

    /** Get the number of {@link Element Elements} in the store. */
    getCount(): number {
        return this.byKey.size;
    }

    /**
     * Get all {@link Element Elements} of a type.
     *
     * @param type The {@link Element.type element type}.
     * @returns Iterator for the elements or `undefined`.
     */
    getByType(type: string): IterableIterator<Element> {
        return this.byType.get(type)?.values() ?? new Set<Element>().values();
    }

    /**
     * Get the number of {@link Element Elements} in the store belonging to a type.
     *
     * @param type The type of the {@link Element}.
     */
    getTypeCount(type: string): number {
        return this.byType.get(type)?.size ?? 0;
    }

    /**
     * Get all elements from a specific {@link Source}.
     *
     * @param sourceId The source id.
     * @returns All elements belonging to the source.
     */
    getBySource(sourceId: string): IterableIterator<Element> {
        return this.bySource.get(sourceId)?.values() ?? new Set<Element>().values();
    }

    /**
     * Get the number of elements in a source.
     *
     * @param sourceId The source id.
     * @returns The number of elements belonging to the source.
     */
    getSourceCount(sourceId: string): number {
        return this.bySource.get(sourceId)?.size ?? 0;
    }

    /**
     * Retrieve all source ids that elements in the store belong to.
     *
     * @returns Iterator over source ids.
     */
    getSourceIds() {
        return this.bySource.keys();
    }

    /**
     * Iterate all elements across all types.
     *
     * This is a generator that lazily yields elements from every type bucket
     * without allocating an intermediate array.
     */
    *allTypes(): IterableIterator<Element> {
        for (const elements of this.byType.values()) {
            yield* elements.values();
        }
    }

    /**
     * Add {@link Element Element(s)} to the store.
     *
     * @param elements An iterable or single {@link Element}.
     */
    add(elements: Iterable<Element> | Element): void {
        if (import.meta.env.DEV && window.__opencop_perf) performance.mark("opencop:store-add");

        const added: Element[] = [];
        const deleted: Element[] = [];
        const updated: Element[] = [];
        const iterable = elements instanceof Element ? [elements] : elements;

        for (const element of iterable) {
            const res = this._add(element, deleted, updated);
            if (res) added.push(res);
        }

        if (import.meta.env.DEV && window.__opencop_perf)
            performance.mark("opencop:store-add-done");

        const omit = (elements: Element[]) =>
            elements.filter((e) => e.key !== this.draft?.element.key);

        const toDelete = omit(deleted);
        if (toDelete.length > 0) {
            this.revision++;
            this.emit("remove", toDelete);
        }

        const toAdd = omit(added);
        if (toAdd.length > 0) {
            this.sweep();
            logger.debug(`Added ${toAdd.length} elements.`);
            logger.debug('Firing "add" event.');
            this.revision++;
            this.emit("add", toAdd);
        }

        const toUpdate = omit(updated);
        if (toUpdate.length > 0) {
            this.sweep();
            logger.debug(`Updated ${toUpdate.length} elements.`);
            logger.debug('Firing "update" event.');
            this.revision++;
            this.emit("update", toUpdate);
        }
    }

    getDraft(): Element | undefined {
        return this.draft?.element;
    }

    setDraft(draft: Element): void {
        if (this.draft) this.clearDraft();
        this.releaseDraft();
        let unsubStyle = draft.getStyle().listen(() => this.updateDraft());
        const unsub = draft.listen((keys) => {
            if (keys.includes(Element.Signals.Style)) {
                unsubStyle();
                unsubStyle = draft.getStyle().listen(() => this.updateDraft());
            }
            this.updateDraft();
        });
        const unsubGeom = draft.geometry.listen(() => this.updateDraft());
        this.draft = {
            element: draft,
            cleanup: () => {
                unsub();
                unsubGeom();
                unsubStyle();
            },
        };
        const existing = this.get(draft.key);
        if (existing) this.emit("update", [draft]);
        else this.emit("add", [draft]);
    }

    private updateDraft(): void {
        if (!this.draft) return;
        this.emit("update", [this.draft.element]);
    }

    clearDraft(): void {
        const draft = this.releaseDraft();
        if (!draft) return;
        const existing = this.get(draft.key);
        if (!existing) this.emit("remove", [draft]);
        else if (this.isStaleExempt(existing) || existing.stale > Date.now())
            this.emit("update", [existing]);
        else {
            this._delete(existing.key);
            this.revision++;
            this.emit("remove", [existing]);
            this.emit("expire", [existing]);
        }
    }

    commitDraft(): void {
        const draft = this.releaseDraft();
        if (!draft) return;
        this._add(draft, [], []);
        this.revision++;
        this.sweep();
        const stored = this.get(draft.key);
        if (stored) this.emit("update", [stored]);
        else this.emit("remove", [draft]);
    }

    private releaseDraft(): Element | undefined {
        const draft = this.draft;
        if (!draft) return;
        draft.cleanup();
        this.draft = undefined;
        return draft.element;
    }

    /**
     * Delete {@link Element Element(s)} from the store.
     *
     * @param keys iterable or single {@link Element.key key}.
     */
    delete(keys: Iterable<string> | string) {
        const deleted: Element[] = [];
        const iterable = typeof keys === "string" ? [keys] : keys;

        for (const key of iterable) {
            const res = this._delete(key);
            if (res) deleted.push(res);
        }

        const toDelete = deleted.filter((e) => e.key !== this.draft?.element.key);
        if (toDelete.length === 0) return;

        logger.debug(`Deleted ${toDelete.length} elements.`);
        logger.debug('Firing "remove" event.');
        this.revision++;
        this.emit("remove", toDelete);
    }

    /**
     * Delete all elements from a specific {@link Source} (layer/data source).
     *
     * @param sourceId The source id.
     */
    deleteBySource(sourceId: string): void {
        logger.info(`Deleting elements from source ${sourceId}.`);
        const elements = this.bySource.get(sourceId);
        if (!elements) return;
        // Eviction, not expiry: fires `remove` but not `expire` — see the `expire` doc.
        this.delete(Array.from(elements.values(), (el) => el.key));
    }

    /** Delete all {@link Element Elements} from the store. */
    deleteAll(): void {
        logger.info(`Deleting all elements: ${this.byKey.size}`);
        this.delete(this.byKey.keys());
        this.dispose();
    }

    /** Clear out the store. */
    dispose(): void {
        logger.info("Disposing store");

        if (this.sweepTimeout) {
            clearTimeout(this.sweepTimeout);
            this.sweepTimeout = undefined;
        }

        this.byType.clear();
        this.bySource.clear();
        this.byKey.clear();
        this.staleHeap.clear();
    }

    // TODO: Remove method.
    /**
     * Mark a source as hidden from rendering.
     *
     * @deprecated For compatibility with existing source hiding/show functionality. Visibility will be a concern for consumers, not the store.
     */
    hideSource(sourceId: string): void {
        if (this.hiddenSources.has(sourceId)) return;
        this.hiddenSources.add(sourceId);
        this.emit("sourceVisibilityChange", sourceId, true);
    }

    // TODO: Remove method.
    /**
     * Mark a source as visible again.
     *
     * @deprecated For compatibility with existing source hiding/show functionality. Visibility will be a concern for consumers, not the store.
     */
    showSource(sourceId: string): void {
        if (!this.hiddenSources.has(sourceId)) return;
        this.hiddenSources.delete(sourceId);
        this.emit("sourceVisibilityChange", sourceId, false);
    }

    /**
     * Internal addition logic, does not fire event.
     *
     * @param element An {@link Element}.
     * @returns If added, the {@link Element}. `undefined` if not added.
     */
    private _add(element: Element, deleted: Element[], updated: Element[]): Element | undefined {
        if (!this.isStaleExempt(element)) {
            const now = Date.now();
            if (element.stale <= now) {
                // A stale arrival for an existing key acts as a delete broadcast.
                const removed = this._delete(element.key);
                if (removed && deleted) deleted.push(removed);
                return;
            }
        }

        const key = element.key;

        const existing = this.byKey.get(key);
        if (existing) {
            // Clean up old type bucket if the element type changed.
            if (existing.type !== element.type) {
                this.byType.get(existing.type)?.delete(key);
            }
            // Remove old heap entry so ghosts don't accumulate.
            if (!this.isStaleExempt(existing)) this.staleHeap.remove(existing);
        }

        this.byKey.set(key, element);

        const sourceElements = this.bySource.get(element.source);
        if (sourceElements) sourceElements.set(element.key, element);
        else this.bySource.set(element.source, new Map([[element.key, element]]));

        const typeElements = this.byType.get(element.type);
        if (typeElements) typeElements.set(element.key, element);
        else this.byType.set(element.type, new Map([[element.key, element]]));

        if (!this.isStaleExempt(element)) this.staleHeap.push(element);

        if (existing) {
            updated.push(element);
            return;
        }

        return element;
    }

    /**
     * Whether an element is exempt from stale eviction — it is never swept and
     * never enters the stale heap. The store does not decide this; the element
     * declares it via {@link Element.exemptFromStale}. That is what lets one
     * store hold exempt and expiring elements at once (historical snapshots
     * alongside live rows) without the store knowing what either kind is.
     */
    private isStaleExempt(element: Element): boolean {
        return element.exemptFromStale;
    }

    /**
     * Internal deletion logic, does not fire event.
     *
     * @param key {@link Element.key element key}.
     * @returns If deleted, the {@link Element}. `undefined` if not deleted.
     */
    private _delete(key: string): Element | undefined {
        const element = this.byKey.get(key);
        if (!element) return;

        this.byKey.delete(key);
        this.bySource.get(element.source)?.delete(element.key);
        this.byType.get(element.type)?.delete(key);
        if (!this.isStaleExempt(element)) this.staleHeap.remove(element);

        return element;
    }

    private sweep() {
        const now = Date.now();

        const expired: Element[] = [];
        while (now >= (this.staleHeap.peek()?.stale ?? Infinity)) {
            const element = this.staleHeap.pop()!;
            if (element.key === this.draft?.element.key) continue;
            this.delete(element.key);
            expired.push(element);
        }
        // Signal owner-side cleanup for genuinely expired elements (see the
        // `expire` event doc). Separate from the per-element `delete` above so a
        // source's backing store is pruned on expiry but NOT on eviction.
        if (expired.length > 0) this.emit("expire", expired);

        const nextSweep = this.staleHeap.peek()?.stale;
        if (nextSweep) {
            if (this.sweepTimeout) clearTimeout(this.sweepTimeout);
            this.sweepTimeout = setTimeout(
                () => this.sweep(),
                Math.min(nextSweep - now, MAX_INT32),
            );
        }
    }
}
