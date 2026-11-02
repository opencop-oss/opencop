// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { defineGlobalStyles } from "@chakra-ui/react";

export const globalStyles = defineGlobalStyles({
    "::selection": {
        bg: "active/30",
    },
    html: {
        colorPalette: "bg",
    },
    a: {
        color: "active",
        textDecoration: "none",
        _hover: {
            textDecoration: "underline",
        },
    },
});
