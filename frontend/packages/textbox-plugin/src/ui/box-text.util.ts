// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { clamp } from "lodash";
import type { TextBoxElement } from "@opencop/elements";
import { FONT_FAMILY } from "@opencop/scene";
import { boxFrame, grownToHeight, INSET } from "./box-geometry.util";
import { blockHalfHeightEm, wrapText } from "./wrap-text.util";

/** The floor for text size. */
export const MIN_TEXT_SIZE_METERS = 1;

/**
 * The ceiling for text size. Roughly half the width of Colorado: far past any
 * sane use, but the box grows to hold whatever the operator types, so the only
 * thing a cap buys is a bound on how absurd a single element can get.
 */
export const MAX_TEXT_SIZE_METERS = 300_000;

export interface WrappedText {
    /** The content, broken to the box's width at the size below. */
    text: string;
    /** The box height in metres this content needs, border margin included. */
    height: number;
}

export function clampTextSize(size: number): number {
    return clamp(size, MIN_TEXT_SIZE_METERS, MAX_TEXT_SIZE_METERS);
}

/**
 * Break `text` to the box's own width and report how tall the box has to be to
 * hold the result.
 *
 * The size is the operator's and is never adjusted — the box grows instead, so
 * there is no circular "size depends on line count depends on size" to solve.
 */
export function wrapToBox(
    text: string,
    boxWidth: number,
    size: number,
    fontFamily: string,
): WrappedText {
    if (!text || boxWidth <= 0 || size <= 0) return { text, height: 0 };

    const wrapped = wrapText(text, (boxWidth * INSET) / size, fontFamily);
    const height = 2 * blockHalfHeightEm(wrapped.split("\n"), fontFamily) * size;

    return { text: wrapped, height: height / INSET };
}

/**
 * Grow `box` downwards so it holds its own content, the way a sticky note does.
 *
 * Only ever grows: the first edge stays where it is and the far edge moves away
 * from it, so a box never pulls back under text that has been deleted. Returns
 * whether the geometry changed.
 */
export function growToHoldText(box: TextBoxElement): boolean {
    const positions = box.geometry.getAllPos();
    const frame = boxFrame(positions);
    if (!frame) return false;

    const style = box.getStyle();
    const { height } = wrapToBox(style.getText(), frame.width, style.getTextSize(), FONT_FAMILY);
    const grown = grownToHeight(positions, height);
    if (!grown) return false;

    box.geometry.setAllPos(grown);
    return true;
}
