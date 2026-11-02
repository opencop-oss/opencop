// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { PointElement } from "@opencop/elements";
import type { ElementMapper } from "@opencop/contributions";
import { hexaToRgba, point, type Primitive } from "@opencop/scene";
import { nameLabel } from "./mapper.util";

export class PointMapper implements ElementMapper<PointElement> {
    public readonly type = "point" as const;
    map(element: PointElement): Primitive[] {
        return [
            point({
                id: "point",
                geometry: {
                    positions: [...element.geometry.getPos(0), 0] as [number, number, number],
                },
                style: {
                    fillColor: hexaToRgba(element.getStyle().getColor()),
                    radius: element.getStyle().getRadius(),
                    strokeColor: [0, 0, 0, 0],
                    strokeWidth: 0,
                },
            }),
            ...nameLabel({
                id: "label",
                geometry: {
                    positions: [...element.geometry.getPos(0), 0] as [number, number, number],
                },
                style: {
                    text: element.getDisplayName(),
                    offset: [0, element.getStyle().getRadius() + 5],
                },
            }),
        ];
    }
}
