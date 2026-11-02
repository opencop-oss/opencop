// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { FONT_SETTINGS, LINE_HEIGHT } from "@opencop/scene";

/**
 * Size the measuring context runs at; widths are divided back down to em. Taken
 * from the scene's own atlas settings, because it rounds glyph boxes at that
 * size — measuring anywhere else makes these numbers disagree with the ones it
 * lays text out by.
 */
const REFERENCE_PX = FONT_SETTINGS.fontSize;

/**
 * Fallback font metrics when the canvas reports no bounding box.
 *
 * The scene falls back to 0/0 here (`TinySDFRenderer`), which would give a
 * block no height at all and a box no reason to grow. A real browser always
 * reports `fontBoundingBox`, so this only parts from the scene on canvas-less
 * paths, where erring tall is the safe direction.
 */
const DEFAULT_ASCENT = 0.9;
const DEFAULT_DESCENT = 0.3;

/** Average glyph width when no canvas is available to measure with (tests, SSR). */
const FALLBACK_EM_PER_CHAR = 0.55;

const contexts = new Map<string, CanvasRenderingContext2D | null>();

function context(fontFamily: string): CanvasRenderingContext2D | null {
    const cached = contexts.get(fontFamily);
    if (cached !== undefined) return cached;

    let ctx: CanvasRenderingContext2D | null;
    try {
        ctx = document.createElement("canvas").getContext("2d");
        if (ctx) ctx.font = `${REFERENCE_PX}px ${fontFamily}`;
    } catch {
        ctx = null;
    }
    contexts.set(fontFamily, ctx);
    return ctx;
}

interface GlyphMetrics {
    /** Pen advance, the only width deck lays a row out by. */
    advanceEm: number;
    /** How far the drawn box overhangs that advance, past the pen. */
    overhangEm: number;
    /** Height of the drawn box, straddling the row's centre. */
    frameEm: number;
}

/** Keyed by font and character, so it is bounded by the characters in use. */
const glyphs = new Map<string, GlyphMetrics>();

function glyph(char: string, fontFamily: string): GlyphMetrics {
    const key = `${fontFamily}|${char}`;
    const cached = glyphs.get(key);
    if (cached) return cached;

    const m = context(fontFamily)?.measureText(char);
    const inked = Boolean(m?.actualBoundingBoxAscent);
    const metrics: GlyphMetrics = {
        advanceEm: m ? m.width / REFERENCE_PX : FALLBACK_EM_PER_CHAR,
        overhangEm:
            inked && m
                ? Math.max(
                      0,
                      Math.ceil(m.actualBoundingBoxRight - m.actualBoundingBoxLeft) - m.width,
                  ) / REFERENCE_PX
                : 0,
        frameEm:
            inked && m
                ? (Math.ceil(m.actualBoundingBoxAscent) + Math.ceil(m.actualBoundingBoxDescent)) /
                  REFERENCE_PX
                : DEFAULT_ASCENT + DEFAULT_DESCENT,
    };
    glyphs.set(key, metrics);
    return metrics;
}

/**
 * Half the vertical span the drawn glyph boxes reach, in em.
 *
 * The scene centres each row at `baselineOffset + lineHeight / 2 + row * lineHeight`
 * and then centres the block — reported as `rows * lineHeight` — on the anchor.
 * The drawn box of each glyph straddles that centre by its own frame, so the
 * reach is per glyph, not a font-wide constant: a row near an edge with a tall
 * glyph in it is what decides the height.
 */
export function blockHalfHeightEm(rows: readonly string[], fontFamily: string): number {
    const { baselineOffsetEm } = fontMetrics(fontFamily);
    let half = 0;

    rows.forEach((row, index) => {
        const centre =
            baselineOffsetEm +
            LINE_HEIGHT / 2 +
            index * LINE_HEIGHT -
            (rows.length * LINE_HEIGHT) / 2;
        for (const char of row) {
            half = Math.max(half, Math.abs(centre) + glyph(char, fontFamily).frameEm / 2);
        }
    });

    return half;
}

/**
 * One glyph's pen advance in em, as the scene's font atlas records it. Exposed
 * for the test that holds the two measurements together.
 */
/**
 * How far the block is shifted off centre, in em, as the scene's atlas records
 * it. Exposed for the test that holds the two measurements together.
 */
export function measureBaselineOffsetEm(fontFamily: string): number {
    return fontMetrics(fontFamily).baselineOffsetEm;
}

export function measureAdvanceEm(char: string, fontFamily: string): number {
    return glyph(char, fontFamily).advanceEm;
}

/**
 * Width of a row in em, summed per character the way the scene's text layout
 * does. Measuring the whole string instead would apply the font's kerning,
 * which the atlas never sees — it builds rows from single glyphs — so a kerning
 * font would make us read every row narrower than it is drawn.
 */
function rowAdvanceEm(text: string, fontFamily: string): number {
    let total = 0;
    for (const char of text) total += glyph(char, fontFamily).advanceEm;
    return total;
}

/** Vertical metrics of the font, in em, derived the way deck derives them. */
interface FontMetrics {
    /** How far deck shifts the block off centre: `(ascent - descent) / 2`. */
    baselineOffsetEm: number;
}

const allMetrics = new Map<string, FontMetrics>();

/** Font-wide metrics, read the way deck's font atlas reads them. */
function fontMetrics(fontFamily: string): FontMetrics {
    const cached = allMetrics.get(fontFamily);
    if (cached) return cached;

    // deck reads both off the font box, or neither — the same test it falls back on.
    const box = context(fontFamily)?.measureText("A");
    const [ascent, descent] = box?.fontBoundingBoxAscent
        ? [Math.ceil(box.fontBoundingBoxAscent), Math.ceil(box.fontBoundingBoxDescent)]
        : [REFERENCE_PX * DEFAULT_ASCENT, REFERENCE_PX * DEFAULT_DESCENT];

    const metrics: FontMetrics = {
        baselineOffsetEm: (ascent - descent) / 2 / REFERENCE_PX,
    };
    allMetrics.set(fontFamily, metrics);
    return metrics;
}

/**
 * Box width the row needs. The row is centred on the anchor, so an overhang on
 * the trailing side alone still costs twice its width: the box has to grow at
 * both edges to keep its centre under the row's.
 */
function rowBoxEm(text: string, fontFamily: string): number {
    const last = text.at(-1);
    return rowAdvanceEm(text, fontFamily) + (last ? 2 * glyph(last, fontFamily).overhangEm : 0);
}

/** Split a word too long for a whole line into line-sized pieces. */
function breakWord(word: string, maxEm: number, fontFamily: string): string[] {
    const pieces: string[] = [];
    let piece = "";
    for (const char of word) {
        if (piece && rowBoxEm(piece + char, fontFamily) > maxEm) {
            pieces.push(piece);
            piece = char;
        } else {
            piece += char;
        }
    }
    if (piece) pieces.push(piece);
    return pieces;
}

/**
 * Wrap `text` to `maxEm` em per line.
 *
 * deck.gl's own `maxWidth` is a layer-level multiple of the glyph size, so it
 * cannot follow each box's width — this does, and because both the box and the
 * glyphs are sized in metres the result is zoom-invariant: it only changes when
 * the box is resized or the text edited, never as the camera moves.
 */
export function wrapText(text: string, maxEm: number, fontFamily: string): string {
    if (!text || !Number.isFinite(maxEm) || maxEm <= 0) return text;

    return text
        .split("\n")
        .map((paragraph) => {
            const lines: string[] = [];
            let line = "";
            // The committed line's advance, carried forward. Re-summing the
            // whole line per word makes the pass quadratic in its length, which
            // a wide box or pasted text turns into seconds on the UI thread —
            // and this runs on every keystroke, drag and move.
            let lineEm = 0;
            const spaceEm = glyph(" ", fontFamily).advanceEm;

            for (const word of paragraph.split(/ +/)) {
                if (!word) continue;
                const wordEm = rowAdvanceEm(word, fontFamily);
                const joinedEm = line ? lineEm + spaceEm + wordEm : wordEm;
                // The overhang is the trailing glyph's, so it is the candidate's
                // last character either way.
                const overhangEm = 2 * glyph(word.at(-1)!, fontFamily).overhangEm;
                if (!line || joinedEm + overhangEm <= maxEm) {
                    line = line ? `${line} ${word}` : word;
                    lineEm = joinedEm;
                    continue;
                }
                lines.push(line);
                line = word;
                lineEm = wordEm;
            }
            if (line) lines.push(line);

            return lines
                .flatMap((entry) =>
                    rowBoxEm(entry, fontFamily) > maxEm
                        ? breakWord(entry, maxEm, fontFamily)
                        : [entry],
                )
                .join("\n");
        })
        .join("\n");
}
