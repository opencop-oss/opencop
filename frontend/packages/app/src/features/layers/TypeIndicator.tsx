// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { TbFolder, TbStack2 } from "react-icons/tb";

interface TypeIndicatorProps {
    type: "layer" | "layer-group";
}

/**
 * Type glyph for a layer / layer group. Renders icon-only (no label) and inherits
 * its size and color from the parent — sized for the drawer header's icon slot.
 */
export function TypeIndicator({ type }: TypeIndicatorProps): ReactElement {
    return type === "layer-group" ? <TbFolder /> : <TbStack2 />;
}
