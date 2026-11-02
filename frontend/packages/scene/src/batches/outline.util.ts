// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { LayerExtension } from "@deck.gl/core";
import { OutlineExtension, TerrainOutlineExtension } from "@opencop/layers";
import type { ColumnSpec } from "../gpu/AttributeTable";
import type { BatchRenderProps } from "../SceneProps";

/**
 * Column backing the `outlines` attribute the {@link OutlineExtension} adds.
 *
 * The extension declares the attribute as `float32` and the shader reads it as a
 * float, so the column must be `f32`: an external buffer is read with the
 * attribute's declared type.
 */
export const OUTLINE_COLUMN: ColumnSpec = { type: "f32", size: 1 };

const extensions = new Map<string, OutlineExtension>();
const terrainExtensions = new Map<string, LayerExtension>();

const DEFAULT_COLOR = [1, 1, 1] as const;

/**
 * Every terrain layer takes the outline-capable terrain extension: deck.gl keeps
 * whichever terrain effect is installed first, and only this one outlines draped layers.
 */
const TERRAIN_EXTENSION = new TerrainOutlineExtension({ outline: false }) as LayerExtension;

/**
 * The screen-space outline of a layer, if any. Draped layers are drawn into the
 * terrain instead, so their outline comes from {@link terrainExtension}.
 *
 * deck.gl compares extensions by instance, so one instance is kept per distinct
 * setting: layers only rebuild when the setting actually changes.
 */
export function outlineExtensions({
    outline,
    terrainDrawMode,
}: BatchRenderProps): OutlineExtension[] {
    if (!outline || terrainDrawMode === "drape") return [];
    const { enabled, color, width } = outline;
    const key = `${enabled}|${color.join(",")}|${width}`;
    let extension = extensions.get(key);
    if (!extension) {
        extension = new OutlineExtension({ enabled, color, width });
        extensions.set(key, extension);
    }
    return [extension];
}

/**
 * The terrain extension of a layer. Draped layers always outline: they draw into
 * the terrain cover, whose outline mask only their outlining shader writes.
 */
export function terrainExtension({ outline, terrainDrawMode }: BatchRenderProps): LayerExtension {
    if (terrainDrawMode !== "drape") return TERRAIN_EXTENSION;
    const color = outline?.color ?? DEFAULT_COLOR;
    const key = color.join(",");
    let extension = terrainExtensions.get(key);
    if (!extension) {
        extension = new TerrainOutlineExtension({ outline: true, color }) as LayerExtension;
        terrainExtensions.set(key, extension);
    }
    return extension;
}

/** Value written to the `outlines` column for a primitive. */
export function outlineValue(outline: boolean | undefined): [number] {
    return [outline ? 1 : 0];
}
