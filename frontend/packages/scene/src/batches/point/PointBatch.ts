// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Layer } from "@deck.gl/core";
import type { PartialPrimitiveOf, PointGeometry, PointStyle, Primitive } from "../../primitive";
import { FixedTable } from "../../gpu/FixedTable";
import type { Device } from "@luma.gl/core";
import type { Allocation } from "../../gpu/Allocator";
import { InstancedBatch } from "../InstancedBatch";
import { pickableScatterplot } from "../../picking/picking.util";
import { OUTLINE_COLUMN, outlineExtensions, outlineValue } from "../outline.util";
import { FILTER_COLUMN, filterValue, sharedLayerProps } from "../props.util";
import type { SceneProps } from "../../SceneProps";

export type PointPrimitive = Primitive<PointGeometry, PointStyle>;
export const PointPrimitive = {
    is: (primitive: Primitive): primitive is PointPrimitive =>
        primitive.geometry.type === "point" && primitive.style.type === "point",
};

export type PointBatchProps = {
    device: Device;
    capacity?: number;
};

export class PointBatch extends InstancedBatch<PointPrimitive> {
    public readonly geometry = "point";
    public readonly style = "point";
    protected readonly table: FixedTable;

    constructor(props: PointBatchProps) {
        super(true);
        const { device, capacity } = props;
        this.table = new FixedTable({
            device,
            capacity,
            attributes: {
                positions: {
                    type: "f64",
                    size: 3,
                },
                fillColors: {
                    type: "unorm8",
                    size: 4,
                },
                strokeColors: {
                    type: "unorm8",
                    size: 4,
                },
                widths: {
                    type: "f32",
                    size: 1,
                },
                radii: {
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
    }

    public render({ point = {} }: SceneProps): Layer {
        const bindings = this.table.bindings();
        const { extensions, ...shared } = sharedLayerProps(point, { depthWriteEnabled: false });
        return pickableScatterplot({
            ...shared,
            id: "point-render-store-layer",
            numInstances: this.table.extent,
            data: {
                length: 0,
                attributes: {
                    instancePositions: bindings.positions,
                    instanceRadius: bindings.radii,
                    instanceFillColors: bindings.fillColors,
                    instanceLineColors: bindings.strokeColors,
                    instanceLineWidths: bindings.widths,
                    instancePickingColors: bindings.pickingColors,
                    outlines: bindings.outlines,
                    filterValues: bindings.filters,
                },
            },
            extensions: [...outlineExtensions(point), ...extensions],
            pickable: true,
            pickingIndex: this.pickingIndex!,
            radiusUnits: "pixels",
            lineWidthUnits: "pixels",
            stroked: true,
            filled: true,
        });
    }

    protected slots(primitive: PointPrimitive): number {
        void primitive;
        return 1;
    }

    protected write(primitive: PointPrimitive, allocation: Allocation) {
        const { offset } = allocation;
        const { id, geometry, style } = primitive;
        const {
            positions,
            fillColors,
            strokeColors,
            widths,
            radii,
            outlines,
            filters,
            pickingColors,
        } = this.table.attributes;

        positions.write(offset, geometry.positions);
        fillColors.write(offset, style.fillColor);
        strokeColors.write(offset, style.strokeColor);
        widths.write(offset, [style.strokeWidth]);
        radii.write(offset, [style.radius]);
        outlines.write(offset, outlineValue(primitive.outline));
        filters.write(offset, filterValue(primitive.filter));
        pickingColors.write(offset, this.pickingIndex!.encode(id));
    }

    protected patch(primitive: PartialPrimitiveOf<PointPrimitive>, allocation: Allocation): void {
        const { offset } = allocation;
        const { geometry, style } = primitive;
        const { positions, fillColors, strokeColors, widths, radii, outlines, filters } =
            this.table.attributes;

        if (geometry?.positions) positions.write(offset, geometry.positions);
        if (style?.fillColor) fillColors.write(offset, style.fillColor);
        if (style?.strokeColor) strokeColors.write(offset, style.strokeColor);
        if (style?.strokeWidth !== undefined) widths.write(offset, [style.strokeWidth]);
        if (style?.radius !== undefined) radii.write(offset, [style.radius]);
        if (primitive.outline !== undefined)
            outlines.write(offset, outlineValue(primitive.outline));
        if (primitive.filter !== undefined) filters.write(offset, filterValue(primitive.filter));
    }

    protected paint(color: ArrayLike<number>, allocation: Allocation): void {
        const { pickingColors } = this.table.attributes;
        const { offset } = allocation;
        pickingColors.write(offset, color);
        pickingColors.flush();
    }
}
