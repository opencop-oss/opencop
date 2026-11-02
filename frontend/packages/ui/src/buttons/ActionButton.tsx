// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

"use client";

import { chakra, defineRecipe } from "@chakra-ui/react";

const actionButtonRecipe = defineRecipe({
    className: "actionButton",
    base: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        color: "fg",
        border: "1px solid",
        borderColor: "border",
        cursor: "pointer",
        fontWeight: "500",
        transition: "background-color 0.2s, border-color 0.2s, opacity 0.2s",
        _hover: {
            borderColor: "active",
            color: "active",
        },
    },
    variants: {
        size: {
            xs: { px: "8px", py: "4px", borderRadius: "sm", fontSize: "10px" },
            sm: { px: "12px", py: "6px", borderRadius: "md", fontSize: "12px" },
            md: { px: "16px", py: "8px", borderRadius: "lg", fontSize: "14px" },
            lg: {
                px: "20px",
                py: "10px",
                borderRadius: "xl",
                fontSize: "16px",
                fontWeight: "600",
                alignItems: "center",
            },
        },
    },
    defaultVariants: { size: "sm" },
});

export const ActionButton = chakra("div", actionButtonRecipe);
