// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

"use client";

import { chakra, defineRecipe } from "@chakra-ui/react";

const glassButtonRecipe = defineRecipe({
    className: "button",
    base: {
        border: "1px solid",
        borderColor: "border.muted",
        cursor: "pointer",
        fontWeight: "500",
        transition:
            "background-color 0.2s ease-out, border-color 0.2s ease-out, opacity 0.2s ease-out",
        _hover: {
            borderColor: "#238fdc",
            color: "#238fdc",
        },
    },
    variants: {
        size: {
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
    defaultVariants: { size: "md" },
});

/**
 * Custom glass-style Button (renders as div).
 * For a real `<button>` element, use `Button` from `@chakra-ui/react` with `variant="glass"`.
 */
export const GlassButton = chakra("div", glassButtonRecipe);
