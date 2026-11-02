// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ElementMessage } from "@opencop/proto";
import { Symbol, type ElementMeta } from "@opencop/elements";
import type { Position2D } from "@opencop/elements";
import type { ElementMapper } from "./ElementMapper";

export class SymbolMapper implements ElementMapper {
    fromMessage(message: ElementMessage, meta: Partial<ElementMeta>) {
        return Symbol.create({
            position: message.positions as Position2D,
            sidc: message.values["symbol"].string as string,
            size: (message.values["size"]?.double as number) ?? 15,
            meta,
        });
    }
}
