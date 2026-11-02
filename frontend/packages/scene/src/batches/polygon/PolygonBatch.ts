// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Logger from "js-logger";
import type { Layer } from "@deck.gl/core";
import { FillStyleExtension } from "@deck.gl/extensions";
import type { PartialPrimitiveOf, PolygonGeometry, PolygonStyle, Primitive } from "../../primitive";
import { VariableTable } from "../../gpu/VariableTable";
import { AtlasTexture } from "../../gpu/AtlasTexture";
import { IconAtlas, type IconPlacement } from "../icon/IconAtlas";
import { type Device } from "@luma.gl/core";
import { earcut } from "@math.gl/polygon";
import { load } from "@loaders.gl/core";
import { ImageLoader } from "@loaders.gl/images";
import { pickableBufferPolygon } from "./BufferPolygonLayer";
import { IndexedBatch, type IndexedAllocation } from "../IndexedBatch";
import { OUTLINE_COLUMN, outlineExtensions, outlineValue } from "../outline.util";
import { FILTER_COLUMN, filterValue, sharedLayerProps } from "../props.util";
import type { SceneProps } from "../../SceneProps";

const logger = Logger.get("polygon-batch");

export type PolygonPrimitive = Primitive<PolygonGeometry, PolygonStyle>;
export const PolygonPrimitive = {
    is: (primitive: Primitive): primitive is PolygonPrimitive =>
        primitive.geometry.type === "polygon" && primitive.style.type === "polygon",
};

export type PolygonBatchProps = {
    device: Device;
    capacity?: number;
};

const PATTERN_ATLAS_SIZE = 1024;
const PATTERN_CELL_SIZE = 128;

/**
 * Pattern of unpatterned fills. The fill style extension patterns the whole layer
 * or none of it, so plain fills sample an opaque tile that leaves them unchanged.
 */
const SOLID = "__solid__";
const SOLID_SIZE = 8;

// Shared instance: deck.gl compares extensions by instance.
const FILL_STYLE_EXTENSION = new FillStyleExtension({ pattern: true });

export class PolygonBatch extends IndexedBatch<PolygonPrimitive> {
    public readonly geometry = "polygon";
    public readonly style = "polygon";
    protected readonly vertexTable: VariableTable;

    private readonly patternOf = new Map<string, string>();
    private readonly usersOf = new Map<string, Set<string>>();
    private readonly atlas: IconAtlas;
    private readonly texture: AtlasTexture;

    constructor(props: PolygonBatchProps) {
        super({ ...props, pickable: true });
        const { device, capacity } = props;
        this.vertexTable = new VariableTable({
            device,
            capacity,
            attributes: {
                positions: {
                    type: "f64",
                    size: 3,
                },
                colors: {
                    type: "unorm8",
                    size: 4,
                },
                patternFrames: {
                    type: "f32",
                    size: 4,
                },
                patternScales: {
                    type: "f32",
                    size: 1,
                },
                outlines: OUTLINE_COLUMN,
                filters: FILTER_COLUMN,
                pickingColors: {
                    type: "unorm8",
                    size: 4,
                },
            },
        });

        this.atlas = new IconAtlas({
            size: Math.min(PATTERN_ATLAS_SIZE, device.limits.maxTextureDimension2D),
            cellSize: PATTERN_CELL_SIZE,
            fetch: (source) => load(source, ImageLoader),
        });
        this.texture = new AtlasTexture({
            device,
            atlas: this.atlas,
            mipLevels: 1, // No real need for mips when we scale our sample distance.
            size: PATTERN_CELL_SIZE,
        });
        this.atlas.acquire({ id: SOLID, source: solidTile });
    }

    public override flush(): boolean {
        const changed = this.atlas.consumePlacements();
        if (changed.kind === "keys") {
            for (const key of changed.keys) {
                const frame = this.frameOf(key);
                for (const user of this.usersOf.get(key) ?? []) {
                    const allocation = this.allocations.get(user);
                    if (allocation) this.writeFrame(frame, allocation);
                }
            }
        }

        return [changed.kind !== "none", super.flush(), this.texture.flush()].reduce(
            (acc, v) => acc || v,
            false,
        );
    }

    public override destroy(): void {
        super.destroy();
        this.texture.destroy();
    }

    public render({ polygon = {} }: SceneProps): Layer {
        const indices = this.indexTable.bindings().indices;
        const bindings = this.vertexTable.bindings();
        const { extensions, ...shared } = sharedLayerProps(polygon, { depthWriteEnabled: false });
        return pickableBufferPolygon({
            ...shared,
            id: "polygon-render-store-layer",
            vertexCount: this.indexTable.extent * 3,
            data: {
                length: 0,
                attributes: {
                    indices: indices,
                    vertexPositions: bindings.positions,
                    fillColors: bindings.colors,
                    fillPatternFrames: bindings.patternFrames,
                    fillPatternScales: bindings.patternScales,
                    pickingColors: bindings.pickingColors,
                    outlines: bindings.outlines,
                    filterValues: bindings.filters,
                },
            },
            extensions: [...outlineExtensions(polygon), FILL_STYLE_EXTENSION, ...extensions],
            fillPatternAtlas: this.texture.gpu,
            fillPatternMask: true,
            getFillPatternOffset: [0, 0],
            pickable: true,
            pickingIndex: this.pickingIndex!,
            filled: true,
            extruded: false,
            wireframe: false,
        });
    }

    protected vertices(primitive: PolygonPrimitive): number | undefined {
        if (!isValidPolygon(primitive)) return;
        return primitive.geometry.positions.length / 3;
    }

    protected indices(vertices: number, primitive: PolygonPrimitive): number {
        void primitive;
        return vertices - 2;
    }

    protected write(primitive: PolygonPrimitive, allocation: IndexedAllocation) {
        const {
            vertex: { offset: vOffset, requested: vReq },
        } = allocation;
        const { geometry, style } = primitive;
        const { positions, colors, patternScales, outlines, filters, pickingColors } =
            this.vertexTable.attributes;

        const range = { start: vOffset, end: vOffset + vReq };
        positions.write(vOffset, geometry.positions);
        colors.fill(style.fillColor, range);
        patternScales.fill([style.pattern?.scale ?? 1], range);
        outlines.fill(outlineValue(primitive.outline), range);
        filters.fill(filterValue(primitive.filter), range);
        pickingColors.fill(this.pickingIndex!.encode(primitive.id), range);
        this.writeFrame(this.frameOf(this.patternOf.get(primitive.id) ?? SOLID), allocation);
        this.writeIndices(geometry.positions, allocation);
    }

    protected patch(
        primitive: PartialPrimitiveOf<PolygonPrimitive>,
        allocation: IndexedAllocation,
    ): void {
        const {
            vertex: { offset: vOffset, requested: vReq },
        } = allocation;
        const { geometry, style } = primitive;
        const { positions, colors, patternScales, outlines, filters } = this.vertexTable.attributes;

        const range = { start: vOffset, end: vOffset + vReq };
        if (geometry?.positions) positions.write(vOffset, geometry.positions);
        if (style?.fillColor) colors.fill(style.fillColor, range);
        if (style && "pattern" in style) patternScales.fill([style.pattern?.scale ?? 1], range);
        if (primitive.outline !== undefined) outlines.fill(outlineValue(primitive.outline), range);
        if (primitive.filter !== undefined) filters.fill(filterValue(primitive.filter), range);
        if (style)
            this.writeFrame(this.frameOf(this.patternOf.get(primitive.id) ?? SOLID), allocation);

        if (geometry?.positions) this.writeIndices(geometry.positions, allocation);
    }

    /** Writes the polygon's triangles, blanking any slots of its index block they leave over. */
    private writeIndices(positions: number[], allocation: IndexedAllocation): void {
        const {
            vertex: { offset: vOffset },
            index: { offset: iOffset, requested: iReq },
        } = allocation;
        const { indices } = this.indexTable.attributes;

        const local = earcut(positions, undefined, 3);
        indices.write(
            iOffset,
            local.map((triangle) => triangle + vOffset),
        );
        indices.tombstone({ start: iOffset + local.length / 3, end: iOffset + iReq });
    }

    protected paint(color: ArrayLike<number>, allocation: IndexedAllocation): void {
        const {
            vertex: { offset, requested },
        } = allocation;
        const { pickingColors } = this.vertexTable.attributes;
        pickingColors.fill(color, { start: offset, end: offset + requested });
        pickingColors.flush();
    }

    protected override onAdd(primitive: PolygonPrimitive): void {
        this.acquirePattern(primitive.id, primitive.style);
    }

    protected override onUpdate(primitive: PolygonPrimitive): void {
        if (this.patternOf.get(primitive.id) === patternId(primitive.style)) return;
        this.releasePattern(primitive.id);
        this.acquirePattern(primitive.id, primitive.style);
    }

    protected override onRemove(id: string): void {
        this.releasePattern(id);
    }

    private acquirePattern(id: string, style: PolygonStyle): void {
        const pattern = style.pattern;
        // The solid tile is held by the batch itself.
        let key = pattern?.id ?? SOLID;
        if (pattern && !this.atlas.acquire(pattern)) {
            logger.warn(`Pattern atlas is full; "${pattern.id}" will fill solid`);
            key = SOLID;
        }

        this.patternOf.set(id, key);
        const users = this.usersOf.get(key);
        if (users) users.add(id);
        else this.usersOf.set(key, new Set([id]));
    }

    private releasePattern(id: string): void {
        const key = this.patternOf.get(id);
        if (!key) return;
        this.patternOf.delete(id);

        const users = this.usersOf.get(key);
        users?.delete(id);
        if (users?.size === 0) this.usersOf.delete(key);

        if (key !== SOLID) this.atlas.release(key);
    }

    /** The atlas rect a pattern tiles from, as `[x, y, width, height]` in pixels. */
    private frameOf(key: string): [number, number, number, number] {
        const { x, y, width, height }: IconPlacement = this.atlas.placement(key);
        // Keep linear filtering from sampling the transparent padding around the solid tile.
        if (key === SOLID && width > 2 && height > 2) return [x + 1, y + 1, width - 2, height - 2];
        return [x, y, width, height];
    }

    private writeFrame(frame: [number, number, number, number], allocation: IndexedAllocation) {
        const { offset, requested } = allocation.vertex;
        this.vertexTable.attributes.patternFrames.fill(frame, {
            start: offset,
            end: offset + requested,
        });
    }
}

/** The atlas key of a style's pattern; unpatterned fills use the solid tile. */
function patternId(style: PolygonStyle): string {
    return style.pattern?.id ?? SOLID;
}

async function solidTile(): Promise<OffscreenCanvas | HTMLCanvasElement> {
    const canvas =
        typeof OffscreenCanvas !== "undefined"
            ? new OffscreenCanvas(SOLID_SIZE, SOLID_SIZE)
            : Object.assign(document.createElement("canvas"), {
                  width: SOLID_SIZE,
                  height: SOLID_SIZE,
              });
    const ctx = canvas.getContext("2d") as CanvasRenderingContext2D;
    ctx.fillStyle = "#fff";
    ctx.fillRect(0, 0, SOLID_SIZE, SOLID_SIZE);
    return canvas;
}

function isValidPolygon<G extends PolygonGeometry>(primitive: Primitive<G>): boolean {
    return (
        primitive.geometry.positions.length % 3 === 0 &&
        primitive.geometry.positions.length / 3 >= 4
    );
}
