// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

"use client";

import { chakra, defineRecipe } from "@chakra-ui/react";

const panelRecipe = defineRecipe({
    base: {
        display: "flex",
        flexDir: "column",
        bg: "bg.glass",
        backdropFilter: "blur(4px)",
        boxShadow: "0 4px 12px rgba(0, 0, 0, 0.15)",
        borderRadius: "md",
        borderWidth: "1px",
        borderColor: "border",
    },
});

export const Panel = chakra("div", panelRecipe);
