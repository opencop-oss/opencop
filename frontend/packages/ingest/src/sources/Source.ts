// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Element } from "@opencop/elements";

export type SourceListeners = {
    state: [SourceState];
};

export type SourceState = "enabled" | "syncing" | "disabled" | "error";

/** Common contract for a stream of {@link Element elements}. */
export abstract class Source {
    readonly id: string;
    /** State of the source, whether it is started or stopped */
    protected _state: SourceState = "disabled";
    protected handler?: (elements: Element[]) => void;
    private onStateChange?: (state: SourceState) => void;

    constructor(id: string) {
        this.id = id;
    }

    get state(): SourceState {
        return this._state;
    }

    /**
     * The `element.source` values this source's elements are stored under.
     *
     * Purging a source means deleting its rows from the `ElementStore`, which is
     * keyed by `element.source` — usually the source's own id. That assumption
     * does not hold universally: a historical source is identified by its
     * session, while its elements carry the id of the *subject* (data source or
     * layer) they came from. Declaring ownership here keeps the controller's
     * single purge path correct for every source rather than special-casing it.
     */
    get ownedSourceIds(): readonly string[] {
        return [this.id];
    }

    protected setState(state: SourceState): void {
        this._state = state;
        this.onStateChange?.(state);
    }

    /** Initiate source, typically a live subscription. */
    enable(onStateChange?: typeof this.onStateChange): void {
        // Idempotent while already active, mirroring `disable`'s guard so the
        // invariant lives with the class rather than only in the controller
        // (which is currently the sole caller and applies the same guard). A
        // re-enable from `disabled`/`error` still runs — only `enabled`/`syncing`
        // are skipped.
        if (this._state === "enabled" || this._state === "syncing") return;
        this.onStateChange = onStateChange;
        this.setState("syncing");
        this.onEnable();
    }

    /**
     * Disable the source, typically unsubscribing. Note: this stops the stream
     * but does NOT remove already-delivered elements from any `ElementStore` —
     * callers that need that data gone must delete it themselves (e.g. by source).
     */
    disable(): void {
        // Idempotent, mirroring `enable`'s already-active guard: a second disable
        // (e.g. a redundant controller eviction) must not re-run `onDisable`.
        // `HistoricalSource.onDisable` releases a shared session refcount,
        // so a double-disable there would over-decrement and could stop a
        // transport other subjects still need.
        if (this._state === "disabled") return;
        this.onDisable();
        this.setState("disabled");
    }

    /**
     * Bind handler function.
     *
     * @param handle Function to handle streamed elements.
     */
    bind(handler: (elements: Element[]) => void): this {
        this.handler = handler;
        return this;
    }

    protected abstract onEnable(): void;

    protected abstract onDisable(): void;
}
