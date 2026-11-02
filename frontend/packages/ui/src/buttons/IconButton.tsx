// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

"use client";

import { chakra, defineRecipe } from "@chakra-ui/react";

const iconButtonRecipe = defineRecipe({
    className: "iconButton",
    base: {
        width: "12px",
        height: "12px",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        position: "relative",
        color: "fg",
        cursor: "pointer",
        transition: "transform 0.1s linear",
        _hover: { transform: "scale(1.3)" },
        border: "none",
        background: "none",
        padding: 0,
        font: "inherit",
    },
    variants: {
        size: {
            xs: { width: "10px", height: "10px" },
            sm: { width: "12px", height: "12px" },
            md: { width: "16px", height: "16px" },
            lg: { width: "20px", height: "20px" },
        },
    },
    defaultVariants: { size: "sm" },
});

export const IconButton = chakra("button", iconButtonRecipe);
