// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ElementMapper } from "@opencop/contributions";
import type { AngleUnit } from "@opencop/utils";
import * as turf from "@turf/turf";
import type { AngleElement } from "@opencop/elements";
import { shallow } from "zustand/shallow";
import { angleBetween, nameLabel, vector } from "./mapper.util";
import type { MapperSelector, MapperState } from "@opencop/contributions";
import { angle, hexaToRgba, interleave, measurement, path, type Primitive } from "@opencop/scene";

const MILS_PER_DEGREE = 6400 / 360;
const ARC_SCALE = 0.25;
/** Web mercator metres to deck.gl common units, which span the world in 512. */
const COMMON_PER_METRE = 512 / (2 * Math.PI * 6378137);

export type AngleState = {
    unit: AngleUnit;
    orientation: "ccw" | "cw";
};

const angleSelector: MapperSelector<AngleState> = {
    select: (state: MapperState): AngleState => ({
        unit: state.units.angle,
        orientation: state.units.angleOrientation,
    }),
    equalityFn: shallow,
};

export class AngleMapper implements ElementMapper<AngleElement, AngleState> {
    public readonly type = "angle" as const;
    public readonly selector = angleSelector;

    public map(object: AngleElement, state: AngleState): Primitive[] {
        const { orientation, unit } = state;

        const positions = object.geometry.getAllPos();
        const next = interleave(positions);
        const strokeColor = hexaToRgba(object.getStyle().getColor());

        const origin = [positions[2], positions[3]] as const;
        const arm0 = [positions[0], positions[1]] as const;
        const arm1 = [positions[4], positions[5]] as const;

        // Calculate sweep
        const o = vector(origin[0], origin[1]);
        const a0 = vector(arm0[0], arm0[1]).subtract(o);
        const a1 = vector(arm1[0], arm1[1]).subtract(o);
        const m0 = a0.magnitude();
        const m1 = a1.magnitude();
        const radius = Math.min(m0, m1) * ARC_SCALE * COMMON_PER_METRE;
        const rad0 = a0.horizontalAngle();
        const rad1 = a1.horizontalAngle();
        const TWO_PI = 2 * Math.PI;
        const forward = (rad1 - rad0 + TWO_PI) % TWO_PI; // [0, 2π) counter-clockwise angle.
        const sweep = orientation === "ccw" ? forward : -((TWO_PI - forward) % TWO_PI);

        const a = angleBetween(origin, arm0, arm1, orientation);
        const formatted = formatAngle(a, unit);
        const [mid0, mid1] = [a0, a1].map((v) => v.clone().scale(0.5).add(o));
        const [textPos0, textPos1] = [mid0, mid1].map((vector) =>
            turf.toWgs84([vector.x, vector.y]),
        );
        const [textAngle0, textAngle1] = [a0, a1].map((v) => {
            const a = turf.radiansToDegrees(v.horizontalAngle());
            return Math.abs(a) > 90 ? a + 180 : a;
        });

        return [
            path({
                id: "arms",
                geometry: {
                    positions: next,
                },
                style: {
                    strokeColor,
                    strokeWidth: object.getStyle().getWeight(),
                    strokeDash: object.getStyle().getPattern().dashArray,
                },
            }),
            angle({
                id: "angle",
                geometry: {
                    positions: [next[3], next[4], next[5]],
                },
                style: {
                    strokeColor,
                    strokeWidth: object.getStyle().getWeight(),
                    radius,
                    angles: [rad0, rad0 + sweep],
                },
            }),
            measurement({
                id: "text-0",
                geometry: {
                    positions: interleave(textPos0) as [number, number, number],
                },
                style: {
                    text: formatted,
                    angle: textAngle0,
                },
            }),
            measurement({
                id: "text-1",
                geometry: {
                    positions: interleave(textPos1) as [number, number, number],
                },
                style: {
                    text: formatted,
                    angle: textAngle1,
                },
            }),
            ...nameLabel({
                id: "label",
                geometry: {
                    positions: interleave(origin) as [number, number, number],
                },
                style: {
                    text: object.getDisplayName(),
                },
            }),
        ];
    }
}

export const formatAngle = (angle: number, unit: AngleUnit) => {
    const formattedDegrees = angle.toFixed(2) + "°";
    const formattedMils = (angle * MILS_PER_DEGREE).toFixed(2) + "mils";
    switch (unit) {
        case "degrees":
            return formattedDegrees;
        case "mils":
            return formattedMils;
        case "both":
            return formattedDegrees + " | " + formattedMils;
    }
};
