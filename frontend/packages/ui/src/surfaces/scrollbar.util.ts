// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

export const scrollbarStyles = {
    "&::-webkit-scrollbar": { width: "8px" },
    "&::-webkit-scrollbar-track": { background: "transparent" },
    "&::-webkit-scrollbar-thumb": {
        background: "var(--chakra-colors-scrollbar-thumb)",
        borderRadius: "sm",
    },
    "&::-webkit-scrollbar-thumb:hover": {
        background: "var(--chakra-colors-scrollbar-thumb-hover)",
    },
} as const;
