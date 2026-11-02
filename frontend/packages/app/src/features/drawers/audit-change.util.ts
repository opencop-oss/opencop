// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { AuditChange, AuditDelta, AuditFieldChange } from "@opencop/api";
import type { TFunction } from "i18next";
import { DateTime } from "luxon";

/** Keys the Change cell renders structurally; everything else is an operation's own detail. */
const STRUCTURAL_KEYS = new Set(["changed", "added", "removed", "roleChanged"]);

/** Shown for a `before` the server recorded as absent, so "nothing → something" reads as such. */
const ABSENT = "—";

function isDelta(value: unknown): value is AuditDelta {
    return (
        typeof value === "object" &&
        value !== null &&
        "count" in value &&
        typeof value.count === "number"
    );
}

/**
 * Booleans are translated; everything else is the server's own vocabulary.
 *
 * Access modes, transports and integration names are stored as the enum constant the backend
 * recorded, and that is what an administrator reading a ledger should see — translating them would
 * put a word in the history that appears nowhere else in the product or the API.
 */
function valueLabel(t: TFunction, value: AuditFieldChange["before"]): string {
    if (value == null) return ABSENT;
    if (typeof value === "boolean") return t(value ? "common.yes" : "common.no");
    return String(value);
}

/**
 * One line per field the action moved.
 *
 * A field with neither `before` nor `after` was recorded **by name only** — a secret, a
 * certificate or a plugin-declared setting whose value must never reach this ledger — so it reads
 * as "changed" with no values rather than as a change to nothing.
 */
export function fieldChangeLabels(t: TFunction, change: AuditChange): string[] {
    return (
        (change.changed ?? [])
            // Both sides absent is a field that was always empty — a creation's optional description,
            // say. "— → —" is noise that reads as though something moved.
            .filter((field) => !(field.before === null && field.after === null))
            .map((field) => {
                const name = t(`audit.fields.${field.field}`, { defaultValue: field.field });
                if (field.before === undefined && field.after === undefined) {
                    return t("audit.fieldChangedWithoutValues", { field: name });
                }
                return t("audit.fieldChange", {
                    field: name,
                    before: valueLabel(t, field.before),
                    after: valueLabel(t, field.after),
                });
            })
    );
}

/**
 * One line per non-empty delta list.
 *
 * The count is exact even when the ids were capped, so the label leads with it and marks a capped
 * list rather than implying the ids are the whole story.
 */
export function deltaLabels(t: TFunction, change: AuditChange): string[] {
    const labels: string[] = [];
    for (const kind of ["added", "removed", "roleChanged"] as const) {
        const delta = change[kind];
        if (delta == null || delta.count === 0) continue;
        labels.push(
            delta.truncated
                ? t(`audit.deltas.${kind}Truncated`, { count: delta.count })
                : t(`audit.deltas.${kind}`, { count: delta.count }),
        );
    }
    return labels;
}

/**
 * The remaining keys an operation recorded, as `label: value` lines.
 *
 * Driven by what is in the payload rather than by a per-operation switch, so a new backend
 * operation displays its details without a web change. A nested object that is not a delta is
 * skipped: the shapes this ledger stores are flat by design, and guessing at an unknown one would
 * print `[object Object]` into an admin's history.
 */
export function detailLabels(t: TFunction, change: AuditChange): string[] {
    const labels: string[] = [];
    for (const [key, value] of Object.entries(change)) {
        if (STRUCTURAL_KEYS.has(key) || value == null) continue;
        const label = t(`audit.details.${key}`, { defaultValue: key });
        if (Array.isArray(value)) {
            // A plain array is a list of names the operation recorded (`mergedFromNames`).
            // Each is looked up as a field label first, so a list that does carry field ids
            // reads the same way the `changed` list would show them.
            if (value.length > 0) {
                const names = value
                    .map((entry) =>
                        typeof entry === "string"
                            ? t(`audit.fields.${entry}`, { defaultValue: entry })
                            : String(entry),
                    )
                    .join(", ");
                labels.push(t("audit.detailLine", { label, value: names }));
            }
        } else if (isDelta(value)) {
            labels.push(t("audit.detailLine", { label, value: value.count }));
        } else if (typeof value === "boolean") {
            // `valuesOmitted: true` and friends: a flag reads as a word, not as `true`.
            const flag = t(value ? "common.yes" : "common.no");
            labels.push(t("audit.detailLine", { label, value: flag }));
        } else if (typeof value !== "object") {
            labels.push(t("audit.detailLine", { label, value: String(value) }));
        }
    }
    return labels;
}

/** The occurrence time in the reader's own zone, the one format every history column uses. */
export function occurredAtLabel(occurredAt: string, timezone: string): string {
    return DateTime.fromISO(occurredAt).setZone(timezone).toLocaleString(DateTime.DATETIME_MED);
}

/** `fullName -> email -> userId`, the fallback the server deliberately leaves to the client. */
export function actorLabel(actor: { fullName?: string; email?: string; userId: string }): string {
    return actor.fullName ?? actor.email ?? actor.userId;
}
