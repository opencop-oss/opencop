// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

"use client";

import { chakra, defineRecipe } from "@chakra-ui/react";

const glowRecipe = defineRecipe({
    className: "glow",
    base: {},
    variants: {
        intensity: {
            subtle: { filter: "drop-shadow(0px 0px 2px currentColor)" },
            medium: { filter: "drop-shadow(0px 0px 4px currentColor)" },
            strong: { filter: "drop-shadow(0px 0px 8px currentColor)" },
        },
        type: {
            indicator: { filter: "drop-shadow(0px 0px 4px currentColor)" },
            box: { boxShadow: "0 0 8px 2px currentColor" },
        },
    },
    defaultVariants: {
        intensity: "medium",
        type: "indicator",
    },
});

export const Glow = chakra("div", glowRecipe);
