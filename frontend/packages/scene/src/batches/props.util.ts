// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { LayerExtension } from "@deck.gl/core";
import { DataFilterExtension } from "@deck.gl/extensions";
import type { Parameters } from "@luma.gl/core";
import type { ColumnSpec } from "../gpu/AttributeTable";
import type { BatchRenderProps, FilterRange } from "../SceneProps";
import { terrainExtension } from "./outline.util";

/** Column backing the `filterValues` attribute of the data filter extension. */
export const FILTER_COLUMN: ColumnSpec = { type: "f32", size: 1 };

/** Filter value for primitives without one; inside the live range `[0, 1]`. */
const UNFILTERED = 1;

// Shared instance: deck.gl compares extensions by instance.
const FILTER_EXTENSION = new DataFilterExtension({ filterSize: 1 });

/** Value written to a `filters` column of `size` channels for a primitive. */
export function filterValue(filter: number[] | undefined, size = 1): number[] {
    return Array.from({ length: size }, (_, i) => filter?.[i] ?? UNFILTERED);
}

export type SharedLayerProps = {
    extensions: LayerExtension[];
    parameters: Parameters;
    terrainDrawMode?: "drape" | "offset";
    filterRange?: FilterRange;
};

/**
 * Terrain, filtering, and GPU parameter props every batch layer shares.
 *
 * @param parameters The layer's own GPU parameters, which `props.parameters`
 * override.
 */
export function sharedLayerProps(
    props: BatchRenderProps,
    parameters: Parameters,
): SharedLayerProps {
    const { terrainDrawMode, filterRange } = props;
    const shared: SharedLayerProps = {
        extensions: [],
        parameters: { ...parameters, ...props.parameters },
    };

    if (terrainDrawMode) {
        shared.extensions.push(terrainExtension(props));
        shared.terrainDrawMode = terrainDrawMode;
    }

    if (filterRange) {
        shared.extensions.push(FILTER_EXTENSION);
        shared.filterRange = filterRange;
    }

    return shared;
}
