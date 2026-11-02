// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ElementMessage } from "@opencop/proto";
import { Circle, type ElementMeta } from "@opencop/elements";
import type { Position2D } from "@opencop/elements";
import type { ElementMapper } from "./ElementMapper";
import { shape } from "./mappers.utils";

export class CircleMapper implements ElementMapper {
    fromMessage(message: ElementMessage, meta: Partial<ElementMeta>) {
        return Circle.create({
            origin: message.positions as Position2D,
            radius: message.values["radius"].double as number,
            style: shape(message),
            meta,
        });
    }
}
