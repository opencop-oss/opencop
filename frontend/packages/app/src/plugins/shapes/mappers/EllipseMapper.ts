// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { hexaToRgba, interleave, path, polygon, type Primitive } from "@opencop/scene";
import type { ElementMapper } from "@opencop/contributions";
import { nameLabel } from "./mapper.util";
import { BBOX_STEPS, type EllipseElement } from "@opencop/elements";
import * as turf from "@turf/turf";

export class EllipseMapper implements ElementMapper<EllipseElement> {
    public readonly type = "ellipse" as const;

    public map(object: EllipseElement): Primitive[] {
        const { geometry } = object;
        // turf.ellipse rejects a zero axis.
        if (geometry.getMajor() === 0 || geometry.getMinor() === 0) return [];

        const [lng, lat] = geometry.getOrigin();
        // Same rasterization and bearing conversion the geometry measures its bbox against.
        const positions = interleave(
            turf
                .ellipse([lng, lat], geometry.getMajor(), geometry.getMinor(), {
                    steps: BBOX_STEPS,
                    units: "meters",
                    angle: geometry.getBearing() + 90,
                })
                .geometry.coordinates.flat(2),
        );

        return [
            path({
                id: "path",
                geometry: {
                    positions,
                },
                style: {
                    strokeColor: hexaToRgba(object.getStyle().getStrokeColor()),
                    strokeWidth: object.getStyle().getStrokeWeight(),
                    strokeDash: object.getStyle().getStrokePattern().dashArray,
                },
            }),
            polygon({
                id: "polygon",
                geometry: {
                    positions,
                },
                style: {
                    fillColor: hexaToRgba(object.getStyle().getFillColor()),
                },
            }),
            ...nameLabel({
                id: "label",
                geometry: {
                    positions: [...object.geometry.getOrigin(), 0] as [number, number, number],
                },
                style: {
                    text: object.getDisplayName(),
                },
            }),
        ];
    }
}
