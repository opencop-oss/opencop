// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { SystemStyleObject } from "@chakra-ui/react";

/**
 * Style props for section headings inside grouped menus (select, combobox).
 *
 * The first heading gets less top padding so the menu isn't over-padded at the top.
 * `_first` can't express this because the label is always the first child of its own
 * ItemGroup, so it'd match every heading; key off the section index instead.
 */
export function groupLabelStyleProps(sectionIndex: number) {
    return {
        color: "fg.muted",
        fontWeight: "bold",
        fontSize: "2xs",
        textTransform: "uppercase",
        letterSpacing: "wider",
        ps: "2",
        pe: "3",
        pt: sectionIndex === 0 ? "1.5" : "3",
        pb: "1",
    } satisfies SystemStyleObject;
}

/**
 * Inline styles that visually hide an element while keeping it accessible to screen readers.
 * Use on group label elements where aria-labelledby must resolve but no visible text is wanted.
 */
export const SR_ONLY = {
    position: "absolute",
    width: "1px",
    height: "1px",
    padding: "0",
    margin: "-1px",
    overflow: "hidden",
    clip: "rect(0,0,0,0)",
    whiteSpace: "nowrap",
    border: "0",
} as const;
