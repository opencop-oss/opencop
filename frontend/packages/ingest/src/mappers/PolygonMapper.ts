// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ElementMessage } from "@opencop/proto";
import { Polygon, type ElementMeta } from "@opencop/elements";
import type { ElementMapper } from "./ElementMapper";
import { shape } from "./mappers.utils";

export class PolygonMapper implements ElementMapper {
    fromMessage(message: ElementMessage, meta: Partial<ElementMeta>) {
        return Polygon.create({
            positions: message.positions as number[],
            style: shape(message),
            meta,
        });
    }
}
