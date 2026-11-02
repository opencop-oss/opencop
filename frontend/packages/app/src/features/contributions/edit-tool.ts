// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Element } from "@opencop/elements";
import type { ToolDescriptor } from "@opencop/tools";
import { toolForElementType } from "~/features/contributions/tool-registry";

/**
 * The tool that edits `element`: the tool that produces its type, or
 * `undefined` for the host's generic edit when no tool does.
 */
export function editToolFor(element: Element): ToolDescriptor | undefined {
    return toolForElementType(element.type);
}
