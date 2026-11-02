// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DateTime } from "luxon";

/**
 * Converts a UTC ISO string to a value suitable for `<input type="datetime-local">`.
 * The datetime-local input has no timezone concept and displays local time, so the
 * UTC instant must first be converted to the browser's local timezone.
 */
export function toDateTimeLocal(isoString: string): string {
    if (!isoString) return "";
    return DateTime.fromISO(isoString, { zone: "utc" }).toLocal().toFormat("yyyy-MM-dd'T'HH:mm");
}

/**
 * Converts the value from `<input type="datetime-local">` back to a UTC ISO string.
 * The input value is treated as local time (no timezone), so it must be explicitly
 * interpreted in the browser's local timezone before converting to UTC.
 */
export function fromDateTimeLocal(dateTimeLocal: string): string {
    if (!dateTimeLocal) return "";
    return DateTime.fromISO(dateTimeLocal).toUTC().toISO() ?? "";
}

export function formatToGMT(dateString: string): string {
    return DateTime.fromISO(dateString, { zone: "utc" }).toHTTP() ?? "";
}

/** "2025-09-23 12:56:03 GMT" — sortable timestamp. Displays in `timezone` if provided, UTC otherwise. */
export function formatDateMilitary(dateString: string, timezone?: string): string {
    if (!dateString) return "";
    const zone = timezone ?? "utc";
    const dt = DateTime.fromISO(dateString, { zone: "utc" }).setZone(zone);
    const suffix = zone === "utc" ? "GMT" : dt.toFormat("z") || dt.toFormat("ZZ");
    return dt.toFormat("yyyy-MM-dd HH:mm:ss") + ` ${suffix}`;
}

/** "5s ago" / "5m ago" / "2h ago" / "3d ago", then falls back to formatDateMilitary. */
export function formatRelativeTime(date: Date, timezone?: string): string {
    const now = DateTime.now();
    const dt = DateTime.fromJSDate(date);
    const diffSecs = Math.floor(now.diff(dt, "seconds").seconds);
    const diffMins = Math.floor(now.diff(dt, "minutes").minutes);
    const diffHours = Math.floor(now.diff(dt, "hours").hours);
    const diffDays = Math.floor(now.diff(dt, "days").days);

    if (diffSecs < 60) return `${Math.max(0, diffSecs)}s ago`;
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;

    return formatDateMilitary(date.toISOString(), timezone).toUpperCase();
}

/** "MM/DD/YYYY" — date only. Displays in `timezone` if provided, UTC otherwise. */
export function formatDateOnly(dateString: string, timezone?: string): string {
    if (!dateString) return "";
    const zone = timezone ?? "utc";
    return DateTime.fromISO(dateString, { zone: "utc" }).setZone(zone).toFormat("MM/dd/yyyy");
}

/**
 * Format a date range for display.
 * Example: "Jan 15, 02:30 - Jan 16, 14:45"
 * Displays in `timezone` if provided, browser local time otherwise.
 */
export function formatDateRange(fromDate: string, toDate: string, timezone?: string): string {
    const zone = timezone ?? "local";
    const fmt = (iso: string) =>
        DateTime.fromISO(iso, { zone: "utc" })
            .setZone(zone)
            .toLocaleString({ month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
    return `${fmt(fromDate)} - ${fmt(toDate)}`;
}

export function toMilliseconds(options?: {
    days?: number;
    hours?: number;
    minutes?: number;
    seconds?: number;
}): number {
    if (options) {
        const { days, hours, minutes, seconds } = options;
        return (
            1000 * ((seconds ?? 0) + 60 * ((minutes ?? 0) + 60 * ((hours ?? 0) + 24 * (days ?? 0))))
        );
    }

    return 0;
}

export function fromNow(options?: {
    days?: number;
    hours?: number;
    minutes?: number;
    seconds?: number;
}): number {
    return Date.now() + toMilliseconds(options);
}

type FixedLengthArray<T, N extends number, R extends T[] = []> = R["length"] extends N
    ? R
    : FixedLengthArray<T, N, [...R, T]>;

export function isNumberArray<N extends number = 0>(
    arg: unknown,
    length?: N,
): arg is N extends 0 ? number[] : FixedLengthArray<number, N, []> {
    return (
        Array.isArray(arg) &&
        arg.every((e) => typeof e === "number") &&
        (length ? arg.length === length : true)
    );
}

export function group<K, T extends number>(array: readonly K[], size: T): FixedLengthArray<K, T>[] {
    const grouped: FixedLengthArray<K, T>[] = [];

    for (let i = 0; i < array.length; i += size) {
        const group: K[] = [];
        for (let j = 0; j < size; j++) {
            group.push(array[i + j]);
        }

        grouped.push(group as FixedLengthArray<K, T>);
    }

    return grouped;
}
