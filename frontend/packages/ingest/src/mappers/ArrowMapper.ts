// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ElementMessage } from "@opencop/proto";
import { Arrow, type ElementMeta } from "@opencop/elements";
import type { ElementMapper } from "./ElementMapper";
import { arrow } from "./mappers.utils";

export class ArrowMapper implements ElementMapper {
    fromMessage(message: ElementMessage, meta: Partial<ElementMeta>) {
        return Arrow.create({
            start: [message.positions[0], message.positions[1]],
            end: [message.positions[2], message.positions[3]],
            style: arrow(message),
            meta,
        });
    }
}
