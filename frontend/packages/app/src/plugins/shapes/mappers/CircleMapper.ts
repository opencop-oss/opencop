// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { BBOX_STEPS, type CircleElement } from "@opencop/elements";
import type { ElementMapper } from "@opencop/contributions";
import { formatLength, type MeasurementSystem } from "@opencop/layers";
import { hexaToRgba, interleave, measurement, path, polygon, type Primitive } from "@opencop/scene";
import * as turf from "@turf/turf";
import { nameLabel } from "./mapper.util";
import type { MapperSelector, MapperState } from "@opencop/contributions";

export type CircleState = MeasurementSystem;

export class CircleSelector implements MapperSelector<CircleState> {
    select(state: MapperState): CircleState {
        return state.units.measurement;
    }
}

const degPerStep = 360 / BBOX_STEPS;
const stepsPer90 = 90 / degPerStep;
const stepsPer45 = 45 / degPerStep;

export class CircleMapper implements ElementMapper<CircleElement, CircleState> {
    public readonly type = "circle" as const;
    public readonly selector = new CircleSelector();

    map(object: CircleElement, state: CircleState): Primitive[] {
        const [lng, lat] = object.geometry.getOrigin();
        const radius = object.geometry.getRadius();
        // Same rasterization the geometry measures its bbox against.
        const ring = turf.circle([lng, lat], radius, {
            steps: BBOX_STEPS,
            units: "meters",
        }).geometry.coordinates[0];
        const positions = interleave(ring.flat());

        // Radius readouts on the diagonals (45°, 135°, …).
        const text = formatLength(radius, state);
        const measurements = [0, 1, 2, 3].map((i) =>
            measurement({
                id: `measurement-${i}`,
                geometry: {
                    positions: [...ring[Math.floor(stepsPer45 + stepsPer90 * i)], 0] as [
                        number,
                        number,
                        number,
                    ],
                },
                style: {
                    text,
                    angle: 45 + 90 * i,
                },
            }),
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
            ...measurements,
        ];
    }
}
