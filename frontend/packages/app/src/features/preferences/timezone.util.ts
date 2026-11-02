// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DateTime } from "luxon";
import Logger from "js-logger";

const logger = Logger.get("preferences");

/**
 * Curated list of common timezones grouped by region
 * Reduces from 400+ IANA zones to ~40 commonly used ones
 * Format: IANA identifier that Luxon can use
 */
export const COMMON_TIMEZONES = [
    // Americas
    "America/New_York", // Eastern Time
    "America/Chicago", // Central Time
    "America/Denver", // Mountain Time
    "America/Phoenix", // Arizona (no DST)
    "America/Los_Angeles", // Pacific Time
    "America/Anchorage", // Alaska
    "Pacific/Honolulu", // Hawaii
    "America/Toronto", // Eastern Time (Canada)
    "America/Halifax", // Atlantic Time
    "America/St_Johns", // Newfoundland
    "America/Mexico_City",
    "America/Bogota",
    "America/Lima",
    "America/Santiago",
    "America/Buenos_Aires",
    "America/Sao_Paulo",

    // Europe
    "Europe/London",
    "Europe/Dublin",
    "Europe/Paris",
    "Europe/Berlin",
    "Europe/Rome",
    "Europe/Madrid",
    "Europe/Amsterdam",
    "Europe/Brussels",
    "Europe/Vienna",
    "Europe/Warsaw",
    "Europe/Athens",
    "Europe/Istanbul",
    "Europe/Moscow",

    // Asia
    "Asia/Dubai",
    "Asia/Karachi",
    "Asia/Kolkata",
    "Asia/Dhaka",
    "Asia/Bangkok",
    "Asia/Singapore",
    "Asia/Hong_Kong",
    "Asia/Shanghai",
    "Asia/Taipei",
    "Asia/Tokyo",
    "Asia/Seoul",

    // Pacific
    "Australia/Perth",
    "Australia/Adelaide",
    "Australia/Brisbane",
    "Australia/Sydney",
    "Australia/Melbourne",
    "Pacific/Auckland",
    "Pacific/Fiji",

    // Africa & Middle East
    "Africa/Cairo",
    "Africa/Johannesburg",
    "Africa/Lagos",
    "Africa/Nairobi",

    // UTC
    "UTC",
] as const;

/**
 * Type-safe timezone identifier from the curated list
 * Provides autocomplete for valid timezones
 */
export type Timezone = (typeof COMMON_TIMEZONES)[number];

export interface TimezoneOption {
    label: string;
    value: string;
    offset: string;
}

/**
 * Format a timezone for display with UTC offset and human-readable name
 * Example: "(UTC-05:00) Eastern Time - New York"
 */
function formatTimezone(ianaZone: Timezone): TimezoneOption {
    const dt = DateTime.now().setZone(ianaZone);
    const offset = dt.toFormat("ZZ"); // e.g., "-05:00"

    // Convert IANA identifier to readable name
    // "America/New_York" -> "Eastern Time - New York"
    const parts = ianaZone.split("/");
    let displayName = parts[parts.length - 1].replace(/_/g, " ");

    // Special cases for better readability
    if (ianaZone === "UTC") {
        displayName = "UTC";
    }

    const label = `(UTC${offset}) ${displayName}`;

    return {
        label,
        value: ianaZone,
        offset,
    };
}

/**
 * Parse timezone offset string (e.g., "+05:30", "-03:30") to decimal hours
 * Correctly handles half-hour and quarter-hour offsets
 */
function parseOffset(offset: string): number {
    const [h, m] = offset.split(":").map(Number);
    return h + (Math.sign(h) || 1) * (m / 60);
}

/**
 * Get list of common timezone options formatted for dropdown
 * Sorted by UTC offset for easier scanning
 */
export function getTimezoneOptions(): TimezoneOption[] {
    const options = COMMON_TIMEZONES.map(formatTimezone);

    // Sort by offset, then alphabetically
    return options.sort((a, b) => {
        // Compare offsets numerically (correctly handles :30 and :45 offsets)
        const offsetA = parseOffset(a.offset);
        const offsetB = parseOffset(b.offset);

        if (offsetA !== offsetB) {
            return offsetA - offsetB;
        }

        // If same offset, sort alphabetically
        return a.label.localeCompare(b.label);
    });
}

/**
 * Detect user's timezone from browser
 * Returns IANA identifier or "UTC" as fallback
 */
export function detectTimezone(): string {
    try {
        const detected = Intl.DateTimeFormat().resolvedOptions().timeZone;

        // Validate that it's a known IANA zone
        DateTime.now().setZone(detected);

        return detected;
    } catch (err) {
        logger.warn("Failed to detect timezone:", err);
        return "UTC";
    }
}
