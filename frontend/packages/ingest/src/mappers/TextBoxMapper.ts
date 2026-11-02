// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ElementMessage } from "@opencop/proto";
import { TextBox, type ElementMeta } from "@opencop/elements";
import type { ElementMapper } from "./ElementMapper";
import { textBox } from "./mappers.utils";

export class TextBoxMapper implements ElementMapper {
    fromMessage(message: ElementMessage, meta: Partial<ElementMeta>) {
        return TextBox.create({
            positions: message.positions as number[],
            style: textBox(message),
            meta,
        });
    }
}
