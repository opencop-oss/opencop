// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ElementMessage } from "@opencop/proto";
import { Angle, type ElementMeta } from "@opencop/elements";
import type { ElementMapper } from "./ElementMapper";
import { stroke } from "./mappers.utils";

export class AngleMapper implements ElementMapper {
    fromMessage(message: ElementMessage, meta: Partial<ElementMeta>) {
        return Angle.create({
            first: [message.positions[0], message.positions[1]],
            second: [message.positions[2], message.positions[3]],
            third: [message.positions[4], message.positions[5]],
            style: stroke(message),
            meta,
        });
    }
}
