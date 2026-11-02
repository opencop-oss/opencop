// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

"use client";

import { chakra, defineRecipe } from "@chakra-ui/react";

const squareRecipe = defineRecipe({
    base: {},
    variants: {
        size: {
            xs: { width: "14px", height: "14px" },
            sm: { width: "24px", height: "24px" },
        },
    },
});

const circleRecipe = defineRecipe({
    base: {
        borderRadius: "50%",
        display: "flex",
    },
    variants: {
        size: {
            xs: { width: "14px", height: "14px" },
            sm: { width: "24px", height: "24px" },
        },
    },
});

export const Square = chakra("div", squareRecipe);
export const Circle = chakra("div", circleRecipe);
