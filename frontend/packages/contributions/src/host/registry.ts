// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * A mutable, subscribable registry backing `useSyncExternalStore`. `getAll`
 * returns a stable reference between mutations so `useSyncExternalStore` does not
 * loop.
 *
 *
 * Deliberately hand-rolled rather than a Zustand store, despite `state-management.md`'s
 * "new pure store → Zustand" default: these registries are host-internal infra
 * (not part of the plugin contract or the shared-singleton set), consumers read
 * the whole array (no selector benefit), and this module stays dependency-free.
 */
export class Registry<T> {
    private snapshot: readonly T[] = [];
    private readonly listeners = new Set<() => void>();

    getAll = (): readonly T[] => this.snapshot;

    add(item: T): void {
        this.snapshot = [...this.snapshot, item];
        this.emit();
    }

    remove(predicate: (item: T) => boolean): void {
        const next = this.snapshot.filter((item) => !predicate(item));
        if (next.length === this.snapshot.length) return;
        this.snapshot = next;
        this.emit();
    }

    /** Replace the entire contents (used for snapshot-style stores). */
    replace(items: readonly T[]): void {
        this.snapshot = [...items];
        this.emit();
    }

    subscribe = (cb: () => void): (() => void) => {
        this.listeners.add(cb);
        return () => {
            this.listeners.delete(cb);
        };
    };

    private emit(): void {
        for (const listener of this.listeners) listener();
    }
}
