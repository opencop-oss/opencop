// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box } from "@chakra-ui/react";
import { TfiText } from "react-icons/tfi";
import { RxTextNone } from "react-icons/rx";

/** "T" glyph for text-visibility toggles; `hidden` renders the struck-through variant. */
export function TextVisibilityIcon({
    hidden = false,
    size = 16,
}: {
    hidden?: boolean;
    size?: number;
}) {
    return hidden ? (
        <Box as="span" display="inline-flex" transform="scaleX(-1)">
            <RxTextNone size={size} style={{ strokeWidth: 1.05 }} />
        </Box>
    ) : (
        <TfiText size={size} style={{ strokeWidth: 1.1 }} />
    );
}
