// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ElementMessage } from "@opencop/proto";
import { Point, PointStyle, type ElementMeta } from "@opencop/elements";
import type { Position2D } from "@opencop/elements";
import type { ElementMapper } from "./ElementMapper";

export class PointMapper implements ElementMapper {
    fromMessage(message: ElementMessage, meta: Partial<ElementMeta>) {
        return Point.create({
            position: message.positions as Position2D,
            style: new PointStyle({
                color: message.values["fillColor"].string as string,
                radius: (message.values["radius"]?.double as number) ?? 6,
            }),
            meta,
        });
    }
}
