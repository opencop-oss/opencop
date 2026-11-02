// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DateTime } from "luxon";

/**
 * Format milliseconds as relative time (e.g., "T+2m 14s")
 */
export function formatRelativeTime(ms: number): string {
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;

    if (minutes > 0) {
        return `T+${minutes}m ${seconds}s`;
    }
    return `T+${seconds}s`;
}

/**
 * Format timestamp as wall clock time (e.g., "14:32:15").
 * Displays in `timezone` if provided, browser local time otherwise.
 */
export function formatWallTime(timestamp: number, timezone?: string): string {
    const dt = timezone
        ? DateTime.fromMillis(timestamp).setZone(timezone)
        : DateTime.fromMillis(timestamp);
    return dt.toFormat("HH:mm:ss");
}

/**
 * Convert milliseconds to hours/minutes/seconds breakdown
 */
export function msToHMS(ms: number) {
    const hours = Math.floor(ms / 3600000);
    const minutes = Math.floor((ms % 3600000) / 60000);
    const seconds = Math.floor((ms % 60000) / 1000);
    return { hours, minutes, seconds };
}

/**
 * Convert hours/minutes/seconds to milliseconds
 */
export function hmsToMs(hours: number, minutes: number, seconds: number): number {
    return hours * 3600000 + minutes * 60000 + seconds * 1000;
}

/**
 * Convert meters to kilometers/meters breakdown
 */
export function metersToKmM(meters: number) {
    const km = Math.floor(meters / 1000);
    const m = Math.floor(meters % 1000);
    return { km, m };
}

/**
 * Convert kilometers/meters to meters
 */
export function kmMToMeters(km: number, m: number): number {
    return km * 1000 + m;
}
