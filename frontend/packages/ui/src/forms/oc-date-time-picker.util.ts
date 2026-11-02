// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DateTime, Interval, type DurationLikeObject, type Zone } from "luxon";

type ZoneLike = string | Zone;

export const TYPEABLE_FORMAT = "yyyy-MM-dd HH:mm";
export const TYPEABLE_FORMAT_LENGTH = TYPEABLE_FORMAT.length;

// Create an inclusive interval (Luxon's Interval.contains() is exclusive on end).
export function createInclusiveInterval(min: DateTime, max: DateTime): Interval {
    return Interval.fromDateTimes(min, max.plus({ milliseconds: 1 }));
}

/**
 * Lightweight format mask for `yyyy-MM-dd HH:mm`. Strips non-digits from
 * whatever the user typed/pasted, caps at 12 digits, then re-emits with the
 * fixed separators (`-`, `-`, space, `:`) at the canonical positions. Lets
 * paste handle loose formats (`2026/05/20T14:30`, `2026 05 20 14 30`) and
 * keeps the on-screen value visually aligned as the user types.
 */
export function maskTypeableValue(raw: string): string {
    const digits = raw.replace(/\D/g, "").slice(0, 12);
    if (digits.length <= 4) return digits;
    if (digits.length <= 6) return `${digits.slice(0, 4)}-${digits.slice(4)}`;
    if (digits.length <= 8) return `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6)}`;
    if (digits.length <= 10)
        return `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6, 8)} ${digits.slice(8)}`;
    return `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6, 8)} ${digits.slice(8, 10)}:${digits.slice(10)}`;
}

/**
 * Fill in missing pieces on blur so a partial buffer like `2026-05-21 11:`
 * auto-completes to `2026-05-21 11:00` instead of triggering a validation
 * error. Returns `null` when there's nothing to commit (empty buffer or
 * fewer than 4 digits, which can't form a valid year).
 *
 * Defaults:
 * - month / day → `01` (start of period)
 * - hour / minute → `00` (start of period)
 *
 * Single-digit slices (e.g. an odd-length digit run leaving the month slot
 * as a single character) are left-padded so `20265` becomes
 * `2026-05-01 00:00` on blur.
 */
export function padTypeableValue(buffer: string): string | null {
    const digits = buffer.replace(/\D/g, "").slice(0, 12);
    if (digits.length < 4) return null;
    const part = (slice: string, fallback: string): string =>
        slice.length === 0 ? fallback : slice.length === 1 ? `0${slice}` : slice;
    const padded =
        digits.slice(0, 4) +
        part(digits.slice(4, 6), "01") +
        part(digits.slice(6, 8), "01") +
        part(digits.slice(8, 10), "00") +
        part(digits.slice(10, 12), "00");
    return maskTypeableValue(padded);
}

/**
 * Candidate paste formats, tried in order. The first valid parse wins; if
 * none match the caller falls through to digit-extraction via
 * {@link maskTypeableValue}, which preserves the `20260520143000` raw-digit
 * paste case.
 */
const PASTE_CANDIDATE_FORMATS = [
    TYPEABLE_FORMAT, // exact
    "M/d/yyyy h:mm a", // US 12-hour
    "M/d/yyyy H:mm", // US 24-hour
    "d/M/yyyy H:mm", // EU
    "yyyy-MM-dd'T'HH:mm", // ISO-T
    "yyyy-MM-dd'T'HH:mm:ss", // ISO-T with seconds (truncate via re-format)
];

/**
 * Try the candidate paste formats against `raw` and return the canonical
 * `yyyy-MM-dd HH:mm` string on first match, or `null` if none parse. Caller
 * should `preventDefault` on success and let the default onChange handle the
 * fallback case (digit extraction by mask).
 */
export function parseTypeablePaste(raw: string, zone: ZoneLike): string | null {
    const trimmed = raw.trim();
    if (!trimmed) return null;
    for (const fmt of PASTE_CANDIDATE_FORMATS) {
        const parsed = DateTime.fromFormat(trimmed, fmt, { zone });
        if (parsed.isValid) return parsed.toFormat(TYPEABLE_FORMAT);
    }
    return null;
}

const SLOT_NAMES: (keyof DurationLikeObject)[] = ["years", "months", "days", "hours", "minutes"];

/**
 * Identify which `yyyy-MM-dd HH:mm` slot the cursor is in. Boundaries match
 * the literal character positions of the canonical format:
 *
 * ```
 * y y y y - M M - d d   H H : m m
 * 0 1 2 3 4 5 6 7 8 9   …
 * ```
 *
 * Cursor 0–4 → years, 5–7 → months, 8–10 → days, 11–13 → hours, else minutes.
 */
export function slotFromCursor(cursor: number): keyof DurationLikeObject {
    if (cursor <= 4) return SLOT_NAMES[0];
    if (cursor <= 7) return SLOT_NAMES[1];
    if (cursor <= 10) return SLOT_NAMES[2];
    if (cursor <= 13) return SLOT_NAMES[3];
    return SLOT_NAMES[4];
}

/**
 * Apply a single ArrowUp/ArrowDown nudge to the slot under `cursor` in
 * `buffer`. Returns the new canonical-formatted string, clamped silently to
 * `[min, max]`, or `null` when the buffer isn't a complete parseable value
 * (caller leaves the user's in-progress typing alone).
 */
export function incrementTypeableSlot(
    buffer: string,
    cursor: number,
    delta: 1 | -1,
    min: DateTime,
    max: DateTime,
    zone: ZoneLike,
): string | null {
    const parsed = DateTime.fromFormat(buffer, TYPEABLE_FORMAT, { zone });
    if (!parsed.isValid) return null;
    const slot = slotFromCursor(cursor);
    const next = parsed.plus({ [slot]: delta });
    const clamped = next < min ? min : next > max ? max : next;
    return clamped.toFormat(TYPEABLE_FORMAT);
}

// Separator positions in the canonical "yyyy-MM-dd HH:mm" layout.
function isSeparatorPosition(pos: number): boolean {
    return pos === 4 || pos === 7 || pos === 10 || pos === 13;
}

/**
 * Action to apply after a typeable-input onChange. Decouples the slot/cursor
 * logic from the JSX side-effects (setBuffer, input.value restoration, rAF
 * caret positioning) so it can be unit-tested directly.
 *
 * - `reject`: discard the edit. Caller restores `input.value` to the prior
 *   buffer and parks the caret at `cursor`.
 * - `commit`: accept the edit. Caller calls `setBuffer(buffer)`; if `cursor`
 *   is non-null, queues a rAF to set the caret to that index after re-render.
 */
export type OvertypeAction =
    | { kind: "reject"; cursor: number }
    | { kind: "commit"; buffer: string; cursor: number | null };

/**
 * Mid-string editing of a completed 16-character buffer is handled in
 * overtype mode — the fixed-position mask doesn't have slot semantics, so
 * insert + shift would mangle every later digit. Overtype keeps each digit
 * position stable: Backspace/Delete on a digit clears it to "0", typing
 * replaces the digit at the cursor.
 *
 * Falls through to {@link maskTypeableValue} for partial buffers, paste,
 * multi-character edits, and trailing-edge Backspace where the standard
 * mask grow/shrink behavior is correct.
 */
export function computeOvertypeAction(buffer: string, raw: string, cursor: number): OvertypeAction {
    if (buffer.length === TYPEABLE_FORMAT_LENGTH) {
        // Single-char deletion (Backspace or Delete) of an interior
        // character. Trailing-edge Backspace (cursor at raw.length) falls
        // through so the operator can shorten the buffer to re-enter values.
        if (raw.length === buffer.length - 1 && cursor < raw.length) {
            if (isSeparatorPosition(cursor)) {
                // Tried to delete a separator — no-op.
                return { kind: "reject", cursor };
            }
            // Replace the deleted digit with "0" to preserve slot structure.
            // The operator can then type a digit to overtype it.
            const next = buffer.slice(0, cursor) + "0" + buffer.slice(cursor + 1);
            return { kind: "commit", buffer: next, cursor };
        }

        // Single-char insertion (digit typed) mid-string: overtype at the
        // cursor instead of insert + shift.
        if (raw.length === buffer.length + 1) {
            const inserted = raw[cursor - 1] ?? "";
            if (!/\d/.test(inserted)) {
                return { kind: "reject", cursor: cursor - 1 };
            }
            // If the cursor landed on a separator, advance to the next digit
            // slot before overtyping.
            let pos = cursor - 1;
            while (isSeparatorPosition(pos) && pos < buffer.length) {
                pos++;
            }
            if (pos >= buffer.length) {
                return { kind: "commit", buffer: maskTypeableValue(raw), cursor: null };
            }
            const next = buffer.slice(0, pos) + inserted + buffer.slice(pos + 1);
            // Advance the cursor past the slot (and any subsequent separator)
            // so the next keystroke lands on the next slot.
            let nextCursor = pos + 1;
            while (isSeparatorPosition(nextCursor) && nextCursor < next.length) {
                nextCursor++;
            }
            return { kind: "commit", buffer: next, cursor: nextCursor };
        }
    }

    return { kind: "commit", buffer: maskTypeableValue(raw), cursor: null };
}
