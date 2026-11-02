// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Accessor, DefaultProps, LayerExtension } from "@deck.gl/core";
import { DataFilterExtension } from "@deck.gl/extensions";

/**
 * Props for layers that support GPU data filtering via deck.gl's
 * {@link https://deck.gl/docs/api-reference/extensions/data-filter-extension DataFilterExtension}.
 *
 * Historical View attaches the extension to a composite and sets
 * `getFilterValue`/`filterRange` to discard, on the GPU, every element whose
 * value falls outside the playhead window. deck.gl forwards both to the
 * rendering sublayers **natively**: `LayerExtension.getSubLayerProps` copies the
 * extension's accessor props down and wraps `getFilterValue` with
 * `getSubLayerAccessor`, so it resolves the originating element even for
 * tessellated sublayer data (e.g. a circle's polygon rows via `__source`). The
 * live path passes no extension, so nothing is forwarded and there is zero GPU
 * cost. {@link isFiltering} remains the signal for layers that must pack a
 * per-instance filter attribute themselves (the culled text/icon layers).
 */
export type FilterProps<DataT = unknown> = {
    /**
     * Per-datum value tested by `DataFilterExtension({ filterSize: 1 })`.
     * @default 0
     */
    getFilterValue?: Accessor<DataT, number>;
    /**
     * Inclusive `[min, max]` window; data whose `getFilterValue` falls outside
     * is discarded on the GPU. Consumed only when a `DataFilterExtension` is
     * attached to the sublayer.
     * @default [0, 0]
     */
    filterRange?: Readonly<[number, number]>;
};

export const defaultFilterProps = {
    getFilterValue: { type: "accessor", value: 0 },
    filterRange: [0, 0],
} as const satisfies DefaultProps<FilterProps>;

/**
 * True when a `DataFilterExtension` is present on the layer. Layers that pack a
 * per-instance filter attribute themselves (the culled text/icon layers) read
 * this to decide whether to do that extra work. The geometry composites need no
 * such gate — deck.gl forwards the extension's `getFilterValue`/`filterRange` to
 * their sublayers natively. When false (the live path), nothing filter-related
 * happens and the sublayer renders identically to before.
 */
export function isFiltering(extensions: readonly LayerExtension[] = []): boolean {
    return extensions.some((e) => e instanceof DataFilterExtension);
}
