// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Layer } from "@deck.gl/core";
import type { PartialPrimitiveOf, PathGeometry, PathStyle, Primitive } from "../../primitive";
import { VariableTable } from "../../gpu/VariableTable";
import type { Device } from "@luma.gl/core";
import type { Allocation } from "../../gpu/Allocator";
import { InstancedBatch } from "../InstancedBatch";
import { pickablePath } from "../../picking/picking.util";
import { PathStyleExtension } from "@deck.gl/extensions";
import { OUTLINE_COLUMN, outlineExtensions, outlineValue } from "../outline.util";
import { FILTER_COLUMN, filterValue, sharedLayerProps } from "../props.util";
import type { SceneProps } from "../../SceneProps";

export type PathPrimitive = Primitive<PathGeometry, PathStyle>;
export const PathPrimitive = {
    is: (primitive: Primitive): primitive is PathPrimitive =>
        primitive.geometry.type === "path" && primitive.style.type === "path",
};

export type PathBatchProps = {
    device: Device;
    capacity?: number;
};

export const PATH_HEADROOM = 1;
export const PATH_TAIL_PADDING = 3;
export const SEGMENT = {
    START_CAP: 1,
    END_CAP: 2,
    INVALID: 4,
};
export const CLOSED_WRAP = 2;

const EXTENSION = new PathStyleExtension({ dash: true });

export class PathBatch extends InstancedBatch<PathPrimitive> {
    public readonly geometry = "path";
    public readonly style = "path";
    protected readonly table: VariableTable;

    constructor(props: PathBatchProps) {
        super(true);
        const { device, capacity } = props;
        this.table = new VariableTable({
            device,
            capacity,
            attributes: {
                positions: {
                    type: "f64",
                    size: 3,
                    bufferConfig: {
                        offset: PATH_HEADROOM,
                        padding: PATH_TAIL_PADDING,
                    },
                },
                segments: {
                    type: "u8",
                    size: 1,
                    tombstoneValue: [SEGMENT.INVALID], // Segments need to be tombstoned.
                },
                widths: {
                    type: "f32",
                    size: 1,
                },
                colors: {
                    type: "unorm8",
                    size: 4,
                },
                dashes: {
                    type: "f32",
                    size: 2,
                },
                outlines: OUTLINE_COLUMN,
                filters: FILTER_COLUMN,
                pickingColors: {
                    type: "unorm8",
                    size: 4,
                },
            },
        });
    }

    public render({ path = {} }: SceneProps): Layer {
        const bindings = this.table.bindings();
        const { extensions, ...shared } = sharedLayerProps(path, { depthWriteEnabled: false });
        return pickablePath({
            ...shared,
            id: "path-render-store-layer",
            numInstances: this.table.extent,
            data: {
                length: 0,
                attributes: {
                    vertexPositions: bindings.positions,
                    instanceTypes: bindings.segments,
                    instanceStrokeWidths: bindings.widths,
                    instanceColors: bindings.colors,
                    instanceDashArrays: bindings.dashes,
                    instancePickingColors: bindings.pickingColors,
                    outlines: bindings.outlines,
                    filterValues: bindings.filters,
                },
            },
            extensions: [EXTENSION, ...outlineExtensions(path), ...extensions],
            pickingIndex: this.pickingIndex!,
            pickable: true,
            widthUnits: "pixels",
            widthMinPixels: 1,
            jointRounded: false,
            capRounded: false,
            miterLimit: 4,
        });
    }

    protected slots(primitive: PathPrimitive): number | undefined {
        const {
            geometry: { positions },
        } = primitive;
        if (!isValidPath(primitive)) return;
        const closed = isClosed(positions);
        return positions.length / 3 + (closed ? CLOSED_WRAP : 0);
    }

    protected write(primitive: PathPrimitive, allocation: Allocation) {
        const { offset, requested } = allocation;
        const { id, geometry, style } = primitive;
        const { positions, segments, widths, colors, dashes, outlines, filters, pickingColors } =
            this.table.attributes;
        const closed = isClosed(geometry.positions);

        const range = { start: offset, end: offset + requested };
        positions.write(
            offset,
            closed
                ? // Closed paths must be formatted: v0 -> v1 -> ... -> vN -> v0 -> v1
                  // We expect the user to supply: v0 -> v1 -> ... vN -> v0
                  [...geometry.positions, ...geometry.positions.slice(3, 9)]
                : geometry.positions,
        );
        segments.write(offset, closed ? closedPath(requested) : openPath(requested));
        widths.fill([style.strokeWidth], range);
        colors.fill(style.strokeColor, range);
        dashes.fill(style.strokeDash ?? [0, 0], range);
        outlines.fill(outlineValue(primitive.outline), range);
        filters.fill(filterValue(primitive.filter), range);
        pickingColors.fill(this.pickingIndex!.encode(id), range);
    }

    protected patch(primitive: PartialPrimitiveOf<PathPrimitive>, allocation: Allocation): void {
        const { offset, requested } = allocation;
        const { geometry, style } = primitive;
        const { positions, segments, widths, colors, dashes, outlines, filters } =
            this.table.attributes;
        const range = { start: offset, end: offset + requested };

        if (geometry?.positions) {
            const closed = isClosed(geometry.positions);
            positions.write(
                offset,
                closed
                    ? [...geometry.positions, ...geometry.positions.slice(3, 9)]
                    : geometry.positions,
            );
            segments.write(offset, closed ? closedPath(requested) : openPath(requested));
        }
        if (style?.strokeWidth !== undefined) widths.fill([style.strokeWidth], range);
        if (style?.strokeColor) colors.fill(style.strokeColor, range);
        if (style && "strokeDash" in style) dashes.fill(style.strokeDash ?? [0, 0], range);
        if (primitive.outline !== undefined) outlines.fill(outlineValue(primitive.outline), range);
        if (primitive.filter !== undefined) filters.fill(filterValue(primitive.filter), range);
    }

    protected paint(color: ArrayLike<number>, allocation: Allocation): void {
        const { pickingColors } = this.table.attributes;
        const { offset, requested } = allocation;
        pickingColors.fill(color, { start: offset, end: offset + requested });
        pickingColors.flush();
    }
}

export function isValidPath<G extends PathGeometry>(primitive: Primitive<G>): boolean {
    return (
        primitive.geometry.positions.length % 3 === 0 &&
        primitive.geometry.positions.length / 3 >= 2
    );
}

export function isClosed(positions: number[]): boolean {
    return (
        positions.length / 3 >= 3 &&
        positions.at(0) === positions.at(-3) &&
        positions.at(1) === positions.at(-2) &&
        positions.at(2) === positions.at(-1)
    );
}

export function openPath(slots: number): ArrayLike<number> {
    const open = new Uint8Array(slots);
    open[0] += SEGMENT.START_CAP;
    open[slots - 2] += SEGMENT.END_CAP;
    open[slots - 1] = SEGMENT.INVALID;

    return open;
}

export function closedPath(slots: number): ArrayLike<number> {
    const closed = new Uint8Array(slots);
    closed[0] += SEGMENT.INVALID;
    closed[slots - 2] += SEGMENT.INVALID;
    closed[slots - 1] = SEGMENT.INVALID;

    return closed;
}
