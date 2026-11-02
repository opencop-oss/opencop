// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { formatLength, type MeasurementSystem } from "@opencop/layers";
import type { AngleUnit } from "@opencop/utils";
import { hexaToRgba, icon, interleave, measurement, path, type Primitive } from "@opencop/scene";
import * as turf from "@turf/turf";
import type { ArrowElement } from "@opencop/elements";
import { formatAngle } from "./AngleMapper";
import { shallow } from "zustand/shallow";
import type { ElementMapper } from "@opencop/contributions";
import { nameLabel, vector } from "./mapper.util";
import type { MapperSelector, MapperState } from "@opencop/contributions";

export type ArrowState = {
    unit: AngleUnit;
    system: MeasurementSystem;
};

export class ArrowSelector implements MapperSelector<ArrowState> {
    select(state: MapperState): ArrowState {
        return { unit: state.units.angle, system: state.units.measurement };
    }
    equalityFn = shallow<ArrowState>;
}

export class ArrowMapper implements ElementMapper<ArrowElement, ArrowState> {
    public readonly type = "arrow" as const;
    public readonly selector = new ArrowSelector();

    public map(object: ArrowElement, state: ArrowState): Primitive[] {
        const style = object.getStyle();
        const color = style.getColor();
        const strokeColor = hexaToRgba(color);

        // Measurements
        const start = object.geometry.getPos(0) as [number, number];
        const end = object.geometry.getPos(1) as [number, number];
        const rhumb = turf.rhumbBearing(start, end);
        const bearing = turf.bearing(start, end);
        const length = turf.distance(start, end, { units: "meters" });

        // Text placement
        const u = vector(start[0], start[1]);
        const v = vector(end[0], end[1]);
        const to = v.subtract(u);
        const [lengthPos, bearingPos, labelPos] = [1 / 6, 5 / 6, 1 / 2].map((t) => {
            const v = to.clone().scale(t).add(u);
            return turf.toWgs84([v.x, v.y]);
        });

        const angle = Math.abs(-rhumb + 90) >= 90 ? -rhumb + 270 : -rhumb + 90;

        return [
            path({
                id: "shaft",
                geometry: {
                    positions: [...start, 0, ...end, 0],
                },
                style: {
                    strokeColor,
                    strokeWidth: style.getWeight(),
                    strokeDash: style.getPattern().dashArray,
                },
            }),
            icon({
                id: "head",
                geometry: {
                    positions: [...end, 0],
                },
                style: {
                    id: "arrow-head",
                    source: ARROW_HEAD_SOURCE,
                    width: 32,
                    height: 32,

                    size: ARROW_HEAD_SIZE,
                    angle: -rhumb,
                    billboard: false,

                    mask: true,
                    color: strokeColor,
                },
            }),
            measurement({
                id: "length",
                geometry: {
                    positions: interleave(lengthPos) as [number, number, number],
                },
                style: {
                    text: formatLength(length, state.system),
                    angle,
                    anchor: "start",
                },
            }),
            measurement({
                id: "bearing",
                geometry: {
                    positions: interleave(bearingPos) as [number, number, number],
                },
                style: {
                    text: formatAngle(bearing, state.unit),
                    angle,
                    anchor: "end",
                },
            }),
            ...nameLabel({
                id: "label",
                geometry: {
                    positions: interleave(labelPos) as [number, number, number],
                },
                style: {
                    text: object.getDisplayName(),
                    angle,
                    billboard: false,
                },
            }),
        ];
    }
}

const ARROW_HEAD_SIZE = 25;
/**
 * Chevron head, masked and tinted per arrow. The icon batch anchors icons at
 * their centre, so the tip is drawn at the centre of the image.
 */
const ARROW_HEAD_SOURCE = `data:image/svg+xml;utf8,${encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32">` +
        `<path d="M4 28 L16 16 L28 28" stroke="white" stroke-width="3" ` +
        `stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>`,
)}`;
