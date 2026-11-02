// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/** BCP 47 for Traditional Chinese, as returned by {@link toBcp47Locale}. */
export const TRADITIONAL_CHINESE_TAG = "zh-TW";

/** The app's `tw` locale means Traditional Chinese; many libraries (Intl, BCP 47 APIs) read `tw` as Twi. */
export function toBcp47Locale(locale: string): string;
export function toBcp47Locale(locale: string | undefined): string | undefined;
export function toBcp47Locale(locale: string | undefined): string | undefined {
    return locale === "tw" ? TRADITIONAL_CHINESE_TAG : locale;
}
