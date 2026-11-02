// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// Raw hex by design — these are fixed categorical hues with no dark-mode variant.
export const TYPE_PALETTE = {
    red: "#EF4444",
    orange: "#F97316",
    amber: "#F59E0B",
    yellow: "#EAB308",
    lime: "#84CC16",
    green: "#22C55E",
    emerald: "#10B981",
    teal: "#14B8A6",
    cyan: "#06B6D4",
    sky: "#0EA5E9",
    blue: "#3B82F6",
    indigo: "#6366F1",
    violet: "#8B5CF6",
    purple: "#A855F7",
    pink: "#EC4899",
    rose: "#F43F5E",
} as const satisfies Record<string, string>;

export type TypePaletteKey = keyof typeof TYPE_PALETTE;
