// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ElementMessage } from "@opencop/proto";
import { MediaIcon, type ElementMeta } from "@opencop/elements";
import type { Position2D } from "@opencop/elements";
import type { ElementMapper } from "./ElementMapper";

export class MediaIconMapper implements ElementMapper {
    fromMessage(message: ElementMessage, meta: Partial<ElementMeta>) {
        return MediaIcon.create({
            position: message.positions as Position2D,
            href: message.values["href"]?.string ?? "",
            size: (message.values["size"]?.double as number) ?? 15,
            meta,
        });
    }
}
