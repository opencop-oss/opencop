// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Logger from "js-logger";

const logger = Logger.get("scene");

/** deck.gl's null picking color — a fragment with this color is not pickable. */
export const NULL_PICKING_COLOR: ArrayLike<number> = new Uint8Array([0, 0, 0, 0]);
/** 24 bits of picking color, minus the +1 reserved for "nothing picked". */
const MAX_HANDLE = 0xffffff - 1;

export type Paint = (id: string, color: ArrayLike<number>) => void;
type PrimitiveId = string;
type Handle = number;

/**
 * Assigns each primitive a small integer that survives a round trip through the
 * picking buffer.
 *
 * Handles are dense and recycled: the 24 bits deck.gl gives a picking color are
 * not enough for a monotonic counter in a long-lived scene.
 */
export class PickingIndex {
    /** Handles to Ids */
    private readonly ids: (PrimitiveId | undefined)[] = [];
    private readonly handles = new Map<PrimitiveId, Handle>();
    private readonly free: Handle[] = [];
    private readonly masked = new Set<PrimitiveId>();
    private readonly scratch = new Uint8Array(4);
    private paint: Paint;

    constructor(paint: Paint) {
        this.paint = paint;
    }

    /** Returns the primitive's handle, assigning one if it has none. */
    public acquire(id: PrimitiveId): number {
        const existing = this.handles.get(id);
        if (existing !== undefined) return existing;

        const handle = this.free.pop() ?? this.ids.length;
        if (handle > MAX_HANDLE) logger.warn("Picking handle space exhausted");
        this.ids[handle] = id;
        this.handles.set(id, handle);
        return handle;
    }

    public release(id: PrimitiveId): void {
        const handle = this.handles.get(id);
        if (handle === undefined) return;
        this.handles.delete(id);
        this.masked.delete(id);
        this.ids[handle] = undefined;
        this.free.push(handle);
    }

    /** Resolves a handle decoded from a picking color back to its primitive. */
    public resolve(handle: Handle): string | undefined {
        return this.ids[handle];
    }

    /**
     * The picking color for a primitive, in deck.gl's encoding: `handle + 1`
     * packed little-endian into RGB, so that zero means "nothing".
     *
     * The returned array is reused — write it before calling again.
     */
    public encode(id: PrimitiveId): ArrayLike<number> {
        const v = (this.handles.get(id) ?? -1) + 1;
        this.scratch[0] = v & 255;
        this.scratch[1] = (v >>> 8) & 255;
        this.scratch[2] = (v >>> 16) & 255;
        this.scratch[3] = 0;
        return this.scratch;
    }

    public decode(color: ArrayLike<number>): number | undefined {
        const v = color[0] + (color[1] << 8) + (color[2] << 16);
        return v === 0 ? undefined : v - 1;
    }

    /** Blanks a primitive's picking color so deck.gl can see what is behind it. */
    public mask(handle: Handle): void {
        const id = this.resolve(handle);
        if (id === undefined) return;
        this.masked.add(id);
        this.paint(id, NULL_PICKING_COLOR);
    }

    /** Repaints every primitive blanked since the last restore. */
    public unmask(): void {
        for (const id of this.masked) this.paint(id, this.encode(id));
        this.masked.clear();
    }
}
