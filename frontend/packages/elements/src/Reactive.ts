// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Base class for objects that signal when properties change.
 *
 * Subclasses call {@link dirty} to notify listeners that a named property
 * has been mutated. When multiple mutations need to fire as a single event,
 * wrap them in {@link batch}.
 */
export abstract class Reactive {
    private _listeners: Set<(keys: string[]) => void> = new Set();
    private _pending: string[] | null = null;

    /**
     * Mark a property key as dirty. Fires all listeners immediately
     * unless inside a {@link batch} call, in which case the key is
     * queued and delivered when the batch completes.
     * @param key The signal key identifying the changed property.
     */
    protected dirty(key: string): void {
        if (this._pending) {
            this._pending.push(key);
            return;
        }
        this._listeners.forEach((fn) => fn([key]));
    }

    /**
     * Register a change listener. Returns an unsubscribe function that MUST
     * be called to clean up.
     *
     * @remarks
     * Any number of listeners may be registered. Each one receives every
     * notification independently of the others.
     *
     * @param fn Callback invoked with an array of dirty signal keys.
     * @returns A function to unsubscribe this listener.
     */
    listen(fn: (keys: string[]) => void): () => void {
        this._listeners.add(fn);
        return () => this._listeners.delete(fn);
    }

    /**
     * Batch multiple mutations so listeners fire only once with all
     * accumulated dirty keys after {@link fn} completes. Supports nesting.
     * @param fn A function that performs mutations on this object.
     * @returns `this` for chaining.
     */
    batch(fn: (self: this) => void): this {
        const prev = this._pending;
        this._pending = [];
        fn(this);
        const keys = this._pending;
        this._pending = prev;
        if (keys.length) this._listeners.forEach((fn) => fn(keys));
        return this;
    }
}
