// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ElementMessage } from "@opencop/proto";
import { Graphic, type ElementMeta } from "@opencop/elements";
import type { ElementMapper } from "./ElementMapper";

export class GraphicMapper implements ElementMapper {
    fromMessage(message: ElementMessage, meta: Partial<ElementMeta>) {
        return Graphic.create({
            sidc: message.values["symbol"].string as string,
            modifiers: Object.fromEntries(
                Object.entries(message.values)
                    .filter(([key]) => key.startsWith("modifier:"))
                    .map(([key, value]) => [key.substring(9), value.string as string]),
            ),
            controls: message.positions,
            meta,
        });
    }
}
