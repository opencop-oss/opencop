// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

"use client";

import { chakra, defineRecipe } from "@chakra-ui/react";

const statusBoxRecipe = defineRecipe({
    base: {
        position: "fixed",
        bottom: "20px",
        right: "50%",
        width: "175px",
        height: "15px",
        display: "flex",
        flexDirection: "column",
        bg: "glass",
        borderRadius: "lg",
        boxShadow: {
            _light: "0px 0px 5px 1px rgba(0, 0, 0, 0.3)",
            _dark: "0px 0px 5px 1px rgba(0, 0, 0, 0.7)",
        },
        overflow: "hidden",
        backdropFilter: "blur(3px)",
        alignItems: "center",
        justifyContent: "center",
    },
});

export const StatusBox = chakra("div", statusBoxRecipe);
