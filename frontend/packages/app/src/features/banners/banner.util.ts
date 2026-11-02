// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { BANNER_NO_EXPIRY_DATE } from "@opencop/api";
import type { BannerFormat } from "@opencop/api";
import { DateTime } from "luxon";

export function formatInstant(
    value: unknown,
    t: (key: never) => string,
    timezone?: string,
): string {
    if (!value || typeof value !== "string") return "-";
    if (value === BANNER_NO_EXPIRY_DATE) return t("adminBanners.never" as never);
    const zone = timezone ?? "local";
    return DateTime.fromISO(value, { zone: "utc" })
        .setZone(zone)
        .toLocaleString(DateTime.DATETIME_SHORT);
}

export type ValidationKey =
    | "adminBanners.validation.plainNoHtml"
    | "adminBanners.validation.plainNoMarkdown"
    | "adminBanners.validation.dangerousContent";

export function validateContent(
    content: string,
    format: BannerFormat | "",
): ValidationKey | undefined {
    const trimmed = content.trim();
    if (!trimmed || !format) return undefined;

    if (format === "PLAIN") {
        if (/<[a-zA-Z][^>]*>/.test(trimmed)) {
            return "adminBanners.validation.plainNoHtml";
        }
        if (/\*\*|__|\*[^*\s]|_[^_\s]|\[.+\]\(.+\)|^#{1,6}\s|^[-*+]\s/m.test(trimmed)) {
            return "adminBanners.validation.plainNoMarkdown";
        }
    }

    if (format === "MARKDOWN" || format === "HTML") {
        if (
            /<script[\s\S]*?>/i.test(trimmed) ||
            /\bon\w+\s*=/i.test(trimmed) ||
            /javascript:/i.test(trimmed)
        ) {
            return "adminBanners.validation.dangerousContent";
        }
    }

    return undefined;
}

/** Strip alpha channel from HEXA (#RRGGBBAA) → hex (#RRGGBB) */
export function toHex(hexa: string): string {
    return hexa.startsWith("#") && hexa.length === 9 ? hexa.slice(0, 7) : hexa;
}

/** Chakra size token for banner bar height — matches BannerBar's h={BANNER_BAR_HEIGHT} */
export const BANNER_BAR_HEIGHT = 6;
