// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Shared icon-result shape produced by both library wrappers
 * (`milstd/wrapper.ts` and `milsymbol/wrapper.ts`). Kept in one place so
 * the cross-library dispatch in `render.ts` consumes a single type
 * rather than reconciling parallel `PreviewResult` / `IconResult`
 * shapes that drift over time.
 *
 * Anchor fields are populated by mil-sym-ts (`getSymbolCenterX/Y`) and
 * left undefined by milsymbol (which has no equivalent on its public
 * `Symbol` API). Consumers default-to-center when absent.
 *
 * Private to the package — not re-exported from the public barrel. The
 * public surface is `IconSource` in `render.ts`, which adds an opaque
 * atlas `id` and is intentionally distinct from `@opencop/elements`'s
 * wider `IconSource` union.
 */
export interface WrapperIconResult {
    /** SVG or raster data: URI suitable for any `<img src>` or icon-atlas consumer. */
    url: string;
    /** Intrinsic width in pixels. */
    width: number;
    /** Intrinsic height in pixels. */
    height: number;
    /** Optional anchor point in source pixels for precise placement. milstd populates; milsymbol leaves undefined. */
    anchorX?: number;
    /** Optional anchor point in source pixels for precise placement. milstd populates; milsymbol leaves undefined. */
    anchorY?: number;
}
