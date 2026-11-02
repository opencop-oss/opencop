// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ElementMessage } from "@opencop/proto";
import { Ellipse, type ElementMeta } from "@opencop/elements";
import type { Position2D } from "@opencop/elements";
import type { ElementMapper } from "./ElementMapper";
import { shape } from "./mappers.utils";

export class EllipseMapper implements ElementMapper {
    fromMessage(message: ElementMessage, meta: Partial<ElementMeta>) {
        return Ellipse.create({
            origin: message.positions as Position2D,
            major: message.values["major"].double as number,
            minor: message.values["minor"].double as number,
            bearing: message.values["bearing"].double as number,
            style: shape(message),
            meta,
        });
    }
}
