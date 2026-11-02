// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Parameters } from "@luma.gl/core";

/** A deck.gl `DataFilterExtension` range. */
export type FilterRange = [min: number, max: number];

/** `OutlineExtension` options. */
export type OutlineProps = {
    enabled: boolean;
    /** RGB, each component in the range 0–1. */
    color: readonly [r: number, g: number, b: number];
    /** Width in pixels. */
    width: number;
};

/**
 * deck.gl props for a batch's layer. Terrain, filtering, and outlining are only
 * attached while their props are given.
 */
export type BatchRenderProps = {
    /** GPU parameters, merged over the batch's own. */
    parameters?: Parameters;
    /** `TerrainExtension` draw mode. */
    terrainDrawMode?: "drape" | "offset";
    /** `DataFilterExtension` range over each primitive's `filter`. */
    filterRange?: FilterRange;
    /** Outline of primitives flagged `outline`: on the terrain when draped, in screen space otherwise. */
    outline?: OutlineProps;
};

export type IconRenderProps = BatchRenderProps & {
    sizeScale?: number;
};

export type TextRenderProps = Omit<BatchRenderProps, "filterRange" | "outline"> & {
    sizeScale?: number;
    /** One range per channel of the text primitives' two-channel `filter`. */
    filterRange?: [FilterRange, FilterRange];
    /** `CollisionFilterExtension` toggle. */
    collisionEnabled?: boolean;
};

/**
 * deck.gl props for each batch's layer, keyed by the batch's style.
 *
 * These change how the retained primitives are drawn, never what they contain,
 * so changing them re-renders layers without touching any primitive.
 */
export type SceneProps = {
    polygon?: BatchRenderProps;
    path?: BatchRenderProps;
    point?: BatchRenderProps;
    angle?: BatchRenderProps;
    icon?: IconRenderProps;
    text?: TextRenderProps;
};
