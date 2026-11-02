// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

"use client";

import { chakra, defineRecipe } from "@chakra-ui/react";

const treeViewRecipe = defineRecipe({
    className: "treeView",
    base: {},
    variants: {
        variant: {
            trunk: {
                paddingLeft: "16px",
                borderLeft: "2px solid",
                borderColor: "border.muted",
                marginLeft: "12px",
            },
            branch: {
                position: "absolute",
                left: "-16px",
                top: "14px",
                width: "18px",
                height: "2px",
                bg: "border.muted",
            },
        },
    },
    defaultVariants: { variant: "trunk" },
});

export const TreeView = chakra("div", treeViewRecipe);
