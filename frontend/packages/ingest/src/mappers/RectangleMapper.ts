// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ElementMessage } from "@opencop/proto";
import { Rectangle, type ElementMeta } from "@opencop/elements";
import type { ElementMapper } from "./ElementMapper";
import { shape } from "./mappers.utils";

export class RectangleMapper implements ElementMapper {
    fromMessage(message: ElementMessage, meta: Partial<ElementMeta>) {
        return Rectangle.create({
            positions: message.positions as number[],
            style: shape(message),
            meta,
        });
    }
}
