// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Layer } from "@deck.gl/core";
import type { Allocation } from "../../gpu/Allocator";
import type { AttributeTable } from "../../gpu/AttributeTable";
import type { PointGeometry, Primitive, AngleStyle, PartialPrimitiveOf } from "../../primitive";
import { InstancedBatch } from "../InstancedBatch";
import type { Device } from "@luma.gl/core";
import { FixedTable } from "../../gpu/FixedTable";
import { pickableOrientedArc } from "./OrientedArcLayer";
import { FILTER_COLUMN, filterValue, sharedLayerProps } from "../props.util";
import type { SceneProps } from "../../SceneProps";

export type AngleBatchProps = {
    device: Device;
    capacity?: number;
};

export type AnglePrimitive = Primitive<PointGeometry, AngleStyle>;
export const AnglePrimitive = {
    is: (primitive: Primitive): primitive is AnglePrimitive =>
        primitive.geometry.type === "point" && primitive.style.type === "angle",
};

export class AngleBatch extends InstancedBatch<AnglePrimitive> {
    public readonly geometry = "point";
    public readonly style = "angle";
    protected readonly table: AttributeTable;

    constructor(props: AngleBatchProps) {
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
                radii: {
                    type: "f32",
                    size: 1,
                },
                angles: {
                    type: "f32",
                    size: 2,
                },
                colors: {
                    type: "unorm8",
                    size: 4,
                },
                widths: {
                    type: "f32",
                    size: 1,
                },
                filters: FILTER_COLUMN,
                pickingColors: {
                    type: "unorm8",
                    size: 4,
                },
            },
        });
    }

    public render({ angle = {} }: SceneProps): Layer {
        const bindings = this.table.bindings();
        const { extensions, ...shared } = sharedLayerProps(angle, { depthWriteEnabled: false });
        return pickableOrientedArc({
            ...shared,
            id: "arcs",
            numInstances: this.table.extent,
            data: {
                length: 0,
                attributes: {
                    instancePositions: bindings.positions,
                    instanceRadius: bindings.radii,
                    instanceLineColors: bindings.colors,
                    instanceLineWidths: bindings.widths,
                    instanceAngles: bindings.angles,
                    instancePickingColors: bindings.pickingColors,
                    filterValues: bindings.filters,
                },
            },
            extensions,
            pickable: true,
            pickingIndex: this.pickingIndex!,
            stroked: true,
            filled: false,
            radiusUnits: "common",
            lineWidthUnits: "pixels",
        });
    }

    protected slots(primitive: Primitive): number {
        void primitive;
        return 1;
    }

    protected write(primitive: AnglePrimitive, allocation: Allocation): void {
        const { offset } = allocation;
        const { id, geometry, style } = primitive;
        const { positions, angles, colors, widths, radii, filters, pickingColors } =
            this.table.attributes;

        positions.write(offset, geometry.positions);
        colors.write(offset, style.strokeColor);
        widths.write(offset, [style.strokeWidth]);
        radii.write(offset, [style.radius]);
        angles.write(offset, style.angles);
        filters.write(offset, filterValue(primitive.filter));
        pickingColors.write(offset, this.pickingIndex!.encode(id));
    }

    protected patch(primitive: PartialPrimitiveOf<AnglePrimitive>, allocation: Allocation): void {
        const { offset } = allocation;
        const { geometry, style } = primitive;
        const { positions, angles, colors, widths, radii, filters } = this.table.attributes;

        if (geometry?.positions) positions.write(offset, geometry.positions);
        if (style?.strokeColor) colors.write(offset, style.strokeColor);
        if (style?.strokeWidth !== undefined) widths.write(offset, [style.strokeWidth]);
        if (style?.radius !== undefined) radii.write(offset, [style.radius]);
        if (style?.angles) angles.write(offset, style.angles);
        if (primitive.filter !== undefined) filters.write(offset, filterValue(primitive.filter));
    }

    protected paint(color: ArrayLike<number>, allocation: Allocation): void {
        const { pickingColors } = this.table.attributes;
        const { offset } = allocation;
        pickingColors.write(offset, color);
        pickingColors.flush();
    }
}
