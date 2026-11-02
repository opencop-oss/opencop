// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, type BoxProps } from "@chakra-ui/react";
import type { ComponentProps } from "react";

export const selectionCheckboxControlProps = {
    bg: "bg.elevatedLight",
    borderColor: "slate.icon.primary",
    borderWidth: "1.5px",
    borderRadius: "0",
    _checked: {
        bg: "bg.elevatedLight",
        borderColor: "slate.icon.primary",
        color: "active",
    },
} satisfies ComponentProps<typeof Box> & BoxProps;
