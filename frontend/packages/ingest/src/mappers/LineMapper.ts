// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ElementMessage } from "@opencop/proto";
import { Line, type ElementMeta } from "@opencop/elements";
import type { ElementMapper } from "./ElementMapper";
import { stroke } from "./mappers.utils";

export class LineMapper implements ElementMapper {
    fromMessage(message: ElementMessage, meta: Partial<ElementMeta>) {
        return Line.create({
            positions: message.positions as number[],
            style: stroke(message),
            meta,
        });
    }
}
