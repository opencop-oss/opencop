// Derived from deck.gl v9.3.1 modules/layers/src/text-layer/utils.ts
// SPDX-License-Identifier: MIT
// Copyright (c) vis.gl contributors
// OpenCOP modifications: Changed function signatures.
import Logger from "js-logger";
import type { CharacterPlacement } from "./FontAtlas";

const MISSING_CHAR_WIDTH = 32;
const SINGLE_LINE: number[] = [];
const logger = Logger.get("text-render-store");

function widthOf({
    text,
    startIndex,
    endIndex,
    placements,
}: {
    text: string[];
    startIndex: number;
    endIndex: number;
    placements: Record<string, CharacterPlacement>;
}): number {
    let width = 0;
    for (let i = startIndex; i < endIndex; i++) {
        const character = text[i];
        width += placements[character]?.advance || 0;
    }

    return width;
}

function breakAll({
    text,
    startIndex,
    endIndex,
    maxWidth,
    placements,
    target,
}: {
    text: string[];
    startIndex: number;
    endIndex: number;
    maxWidth: number;
    placements: Record<string, CharacterPlacement>;
    target: number[];
}): number {
    let rowStartCharIndex = startIndex;
    let rowOffsetLeft = 0;

    for (let i = startIndex; i < endIndex; i++) {
        // Figure out where to break lines
        const textWidth = widthOf({
            text,
            startIndex: i,
            endIndex: i + 1,
            placements,
        });

        // Character exceeds width
        if (rowOffsetLeft + textWidth > maxWidth) {
            // Only after first index, prevents empty row.
            if (rowStartCharIndex < i) target.push(i);
            rowStartCharIndex = i;
            rowOffsetLeft = 0;
        }
        rowOffsetLeft += textWidth;
    }

    return rowOffsetLeft;
}

function breakWord({
    text,
    startIndex,
    endIndex,
    maxWidth,
    placements,
    target,
}: {
    text: string[];
    startIndex: number;
    endIndex: number;
    maxWidth: number;
    placements: Record<string, CharacterPlacement>;
    target: number[];
}): number {
    let rowStartCharIndex = startIndex;
    let rowOffsetLeft = 0;

    // Group characters into word groups.
    let groupStartCharIndex = startIndex;
    let groupEndCharIndex = startIndex;

    for (let i = startIndex; i < endIndex; i++) {
        // Break text into word groups if any of the following are true
        // - Current character is white space
        // - Next character is white space
        // - Reached last character
        if (text[i] === " " || text[i + 1] === " " || i + 1 === endIndex) groupEndCharIndex = i + 1;

        // Only if group is pending.
        if (groupEndCharIndex > groupStartCharIndex) {
            // Break text into next row at maxWidth
            let groupWidth = widthOf({
                text,
                startIndex: groupStartCharIndex,
                endIndex: groupEndCharIndex,
                placements,
            });

            // Word exceeds max width.
            if (rowOffsetLeft + groupWidth > maxWidth) {
                // If row is not empty
                if (rowStartCharIndex < groupStartCharIndex) {
                    target.push(groupStartCharIndex);
                    rowStartCharIndex = groupStartCharIndex;
                    rowOffsetLeft = 0;
                }

                // If a single text group is bigger than maxWidth, then `break-all`
                if (groupWidth > maxWidth) {
                    groupWidth = breakAll({
                        text,
                        startIndex: groupStartCharIndex,
                        endIndex: groupEndCharIndex,
                        maxWidth,
                        placements,
                        target,
                    });
                    // move reference to last row
                    rowStartCharIndex = target[target.length - 1];
                }
            }
            groupStartCharIndex = groupEndCharIndex;
            rowOffsetLeft += groupWidth;
        }
    }

    return rowOffsetLeft;
}

/**
 * Wrap the given text so that each line does not exceed the given max width.
 * Returns a list of indices where line breaks should be inserted.
 */
export function autoWrapping(args: {
    text: string[];
    wordBreak: "break-all" | "break-word";
    startIndex: number;
    endIndex: number;
    maxWidth: number;
    placements: Record<string, CharacterPlacement>;
}): number[] {
    const target: number[] = [];
    if (args.wordBreak === "break-all") {
        breakAll({ ...args, target });
    } else {
        breakWord({ ...args, target });
    }
    return target;
}

function transformRow({
    line,
    startIndex,
    endIndex,
    placements,
    leftOffsets,
    rowSize,
}: {
    line: string[];
    startIndex: number;
    endIndex: number;
    placements: Record<string, CharacterPlacement>;
    leftOffsets: number[];
    rowSize: [width: number, height: number];
}) {
    let x = 0;
    let rowHeight = 0;

    // Find row height.
    for (let i = startIndex; i < endIndex; i++) {
        const character = line[i];
        const placement = placements[character];
        if (placement) rowHeight = Math.max(rowHeight, placement.height);
    }

    for (let i = startIndex; i < endIndex; i++) {
        const character = line[i];
        const frame = placements[character];
        if (frame) {
            leftOffsets[i] = x + frame.anchorX;
            x += frame.advance;
        } else {
            logger.warn(`Missing character: ${character} (${character.codePointAt(0)})`);
            leftOffsets[i] = x;
            x += MISSING_CHAR_WIDTH;
        }
    }

    rowSize[0] = x;
    rowSize[1] = rowHeight;
}

/**
 * Transform a text paragraph to an array of characters, each character contains
 */
export function transformParagraph({
    paragraph,
    baselineOffset,
    lineHeight,
    wordBreak,
    maxWidth,
    placements,
}: {
    paragraph: string;
    /** font property - distance from baseline to vertical center */
    baselineOffset: number;
    /** line-height in pixels */
    lineHeight: number;
    /** CSS word-break option */
    wordBreak: "break-word" | "break-all";
    /** CSS max-width */
    maxWidth: number;
    /** character mapping table for retrieving a character from font atlas */
    placements: Record<string, CharacterPlacement>;
}): {
    /** x position of each character */
    x: number[];
    /** y position of each character */
    y: number[];
    /** the current row width of each character */
    rowWidth: number[];
    /** the width and height of the paragraph */
    size: [number, number];
} {
    // Break into an array of characters
    // When dealing with double-length unicode characters, `str.length` or `str[i]` do not work
    const characters = Array.from(paragraph);
    const numCharacters = characters.length;
    const x: number[] = new Array(numCharacters);
    const y: number[] = new Array(numCharacters);
    const rowWidth: number[] = new Array(numCharacters);
    const autoWrappingEnabled =
        (wordBreak === "break-word" || wordBreak === "break-all") &&
        isFinite(maxWidth) &&
        maxWidth > 0;

    // maxWidth and height of the paragraph
    const size: [number, number] = [0, 0];
    const rowSize: [number, number] = [0, 0];
    let rowCount = 0;
    let rowOffsetTop = baselineOffset + lineHeight / 2; // this places the top of the first row at 0
    let lineStartIndex = 0;
    let lineEndIndex = 0;

    for (let i = 0; i <= numCharacters; i++) {
        const char = characters[i];
        if (char === "\n" || i === numCharacters) lineEndIndex = i;

        // Is there a pending line
        if (lineEndIndex > lineStartIndex) {
            const cuts = autoWrappingEnabled
                ? autoWrapping({
                      text: characters,
                      wordBreak,
                      maxWidth,
                      placements,
                      startIndex: lineStartIndex,
                      endIndex: lineEndIndex,
                  })
                : SINGLE_LINE;

            for (let row = 0; row <= cuts.length; row++) {
                // N cuts -> N + 1 rows.
                const start = row === 0 ? lineStartIndex : cuts[row - 1];
                const end = row < cuts.length ? cuts[row] : lineEndIndex;

                transformRow({
                    line: characters,
                    startIndex: start,
                    endIndex: end,
                    placements,
                    leftOffsets: x,
                    rowSize,
                });

                for (let j = start; j < end; j++) {
                    y[j] = rowOffsetTop;
                    rowWidth[j] = rowSize[0];
                }

                rowCount++;
                rowOffsetTop += lineHeight;
                size[0] = Math.max(size[0], rowSize[0]);
            }

            lineStartIndex = lineEndIndex;
        }

        if (char === "\n") {
            // Make sure result.length matches paragraph.length
            x[lineStartIndex] = 0;
            y[lineStartIndex] = 0;
            rowWidth[lineStartIndex] = 0;
            lineStartIndex++;
        }
    }

    // last row
    size[1] = rowCount * lineHeight;
    return { x, y, rowWidth, size };
}
