// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { LineElement } from "@opencop/elements";
import type { ElementMapper } from "@opencop/contributions";
import { hexaToRgba, interleave, path, type Primitive } from "@opencop/scene";
import { nameLabel, vector } from "./mapper.util";
import * as turf from "@turf/turf";

const LINE_SEGMENT_MOD = 2;

export class LineMapper implements ElementMapper<LineElement> {
    public readonly type = "line" as const;

    map(object: LineElement): Primitive[] {
        const positions = object.geometry.getAllPos();
        const name = object.getDisplayName();

        const labels: Primitive[] = [];
        for (let i = 2; i < positions.length; i += 2) {
            if ((i / 2) % LINE_SEGMENT_MOD !== 0) continue;

            const start: [number, number] = [positions[i - 2], positions[i - 1]];
            const end: [number, number] = [positions[i], positions[i + 1]];
            const mid = vector(start[0], start[1]).add(vector(end[0], end[1])).scale(0.5);
            let angle = -turf.rhumbBearing(start, end) + 90;
            if (Math.abs(angle) > 90) angle += 180;

            labels.push(
                ...nameLabel({
                    id: "label-" + i,
                    geometry: {
                        positions: [...turf.toWgs84([mid.x, mid.y]), 0] as [number, number, number],
                    },
                    style: {
                        text: name,
                        angle,
                        billboard: false,
                    },
                }),
            );
        }

        return [
            path({
                id: "path",
                geometry: {
                    positions: interleave(positions),
                },
                style: {
                    strokeColor: hexaToRgba(object.getStyle().getColor()),
                    strokeWidth: object.getStyle().getWeight(),
                    strokeDash: object.getStyle().getPattern().dashArray,
                },
            }),
            ...labels,
        ];
    }
}
