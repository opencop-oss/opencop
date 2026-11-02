// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { SystemStyleObject } from "@chakra-ui/react";

/**
 * Shared style props for interactive list-item cards used across
 * imagery tiles, map captures, and terrain tiles.
 *
 * Matches the SourceFilter row style: consistent bg, selection border, subtle hover.
 */

const BASE = {
    position: "relative",
    w: "full",
    bg: "bg.elevated",
    transition: "background-color 0.2s ease-out",
    borderRadius: "sm",
    borderWidth: "1px",
    // The card itself isn't focusable; its row button is (see ListPanelItem). Ring the whole
    // card while that button has keyboard focus.
    "&:has([data-panel-item-button]:focus-visible)": {
        outline: "2px solid var(--chakra-colors-active)",
        outlineOffset: "-2px",
    },
    _hover: { bg: "bg.interactive" },
} as const;

const STYLES = {
    selected: { ...BASE, borderColor: "active" },
    unselected: { ...BASE, borderColor: "border" },
} as const satisfies Record<string, SystemStyleObject>;

export function interactiveCardStyles(_index: number, isSelected: boolean): SystemStyleObject {
    return STYLES[isSelected ? "selected" : "unselected"];
}
