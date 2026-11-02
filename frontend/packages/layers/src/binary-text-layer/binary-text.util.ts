// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { attr, type BinaryAttribute } from "../binary.util";

export type TextAttributes = {
    [K in
        | "getPosition"
        | "getColor"
        | "getSize"
        | "getAngle"
        | "getPixelOffset"
        | "getContentBox"
        | "getIcon"
        | "getBillboard"
        | "instancePickingColors"]: BinaryAttribute;
} & {
    /**
     * Per-glyph filter value consumed by `DataFilterExtension({ filterSize: 1 })`
     * (deck maps the `getFilterValue` accessor key to its `filterValues`
     * attribute). Present only when the text layer is GPU-filtering — see
     * {@link textAttributes}'s `includeFilter` flag — so the live path packs and
     * uploads nothing extra.
     */
    getFilterValue?: BinaryAttribute;
};

/**
 * @param includeFilter When true, include the `getFilterValue` attribute so the
 *   `DataFilterExtension` has a per-glyph value to test (Historical View's GPU
 *   playhead filter). Omitted on the live path.
 */
export function textAttributes(includeFilter = false): TextAttributes {
    return {
        getPosition: attr("float64", 3),
        getColor: attr("uint8", 4),
        getSize: attr("float32"),
        getAngle: attr("float32"),
        getPixelOffset: attr("float32", 2),
        getContentBox: attr("float32", 4),
        getIcon: attr("float32", 7),
        getBillboard: attr("uint8", 1),
        instancePickingColors: attr("uint8", 3),
        ...(includeFilter ? { getFilterValue: attr("float32", 1) } : {}),
    };
}
