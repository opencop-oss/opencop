// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { RectangleElement } from "@opencop/elements";
import { hexaToRgba, interleave, path, polygon, type Primitive } from "@opencop/scene";
import type { ElementMapper } from "@opencop/contributions";
import { nameLabel } from "./mapper.util";

export class RectangleMapper implements ElementMapper<RectangleElement> {
    public readonly type = "rectangle" as const;
    public map(object: RectangleElement): Primitive[] {
        const positions = interleave(object.geometry.getClosed());
        const centroid = object.geometry.getCentroid();

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
            ...nameLabel({
                id: "label",
                geometry: {
                    positions: [...centroid, 0] as [number, number, number],
                },
                style: {
                    text: object.getDisplayName(),
                },
            }),
            // Only draw the polygon if there are enough vertices.
            ...(positions.length / 3 > 3
                ? [
                      polygon({
                          id: "polygon",
                          geometry: {
                              positions,
                          },
                          style: {
                              fillColor: hexaToRgba(object.getStyle().getFillColor()),
                          },
                      }),
                  ]
                : []),
        ];
    }
}
