// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { TFunction } from "i18next";
import { toBcp47Locale } from "~/i18n/locale.util";
import type { WatchboxPolygonError } from "./polygon.model";
import { parseWatchboxError } from "./watchbox.api";
import type { WatchboxError } from "./watchbox.model";

/** Codes whose message names the data sources at fault, so it needs the response context. */
const SOURCE_NAMED_ERROR_CODES: ReadonlySet<WatchboxError> = new Set([
    "WATCHBOX_SOURCE_ELIGIBILITY_FAILED",
    "WATCHBOX_OBJECT_ID_ON_MISSING_NOT_ERROR",
]);
const MAX_VISIBLE_SOURCE_NAMES = 3;

const messageKey = {
    WATCHBOX_INVALID_REQUEST: "watchbox.errors.WATCHBOX_INVALID_REQUEST",
    // The server's detailed self-intersection text (WatchboxValidationService) is log-only:
    // this lookup is by code only, so a server-side rejection always renders the generic message.
    WATCHBOX_INVALID_POLYGON: "watchbox.errors.WATCHBOX_INVALID_POLYGON",
    WATCHBOX_INVALID_FILTER: "watchbox.errors.WATCHBOX_INVALID_FILTER",
    WATCHBOX_INVALID_NAME: "watchbox.errors.WATCHBOX_INVALID_NAME",
    WATCHBOX_LAYER_REQUIRED: "watchbox.errors.WATCHBOX_LAYER_REQUIRED",
    WATCHBOX_LAYER_LIMIT_EXCEEDED: "watchbox.errors.WATCHBOX_LAYER_LIMIT_EXCEEDED",
    WATCHBOX_FILTER_LIMIT_EXCEEDED: "watchbox.errors.WATCHBOX_FILTER_LIMIT_EXCEEDED",
    WATCHBOX_MANAGEMENT_ROLE_REQUIRED: "watchbox.errors.WATCHBOX_MANAGEMENT_ROLE_REQUIRED",
    WATCHBOX_NO_ELIGIBLE_SOURCES: "watchbox.errors.WATCHBOX_NO_ELIGIBLE_SOURCES",
    // Contextual messages: render with watchboxErrorMessage, never plain t(key).
    WATCHBOX_SOURCE_ELIGIBILITY_FAILED: "watchbox.errors.WATCHBOX_SOURCE_ELIGIBILITY_FAILED",
    WATCHBOX_OBJECT_ID_ON_MISSING_NOT_ERROR:
        "watchbox.errors.WATCHBOX_OBJECT_ID_ON_MISSING_NOT_ERROR",
    WATCHBOX_MAPPER_OBJECT_ID_NOT_CONFIGURED:
        "watchbox.errors.WATCHBOX_MAPPER_OBJECT_ID_NOT_CONFIGURED",
    WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN:
        "watchbox.errors.WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN",
    WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED:
        "watchbox.errors.WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED",
    WATCHBOX_FEATURE_DISABLED: "watchbox.errors.WATCHBOX_FEATURE_DISABLED",
    WATCHBOX_NOT_FOUND: "watchbox.errors.WATCHBOX_NOT_FOUND",
} as const satisfies Record<WatchboxError, string>;

export type WatchboxErrorMessageKey = (typeof messageKey)[WatchboxError];

/**
 * Translation key for a non-contextual watchbox action failure. Returns undefined when the error
 * is outside the watchbox error contract or requires interpolation by watchboxErrorMessage.
 */
export function watchboxErrorMessageKey(error: unknown): WatchboxErrorMessageKey | undefined {
    const response = parseWatchboxError(error);
    if (!response || SOURCE_NAMED_ERROR_CODES.has(response.code)) return undefined;
    return messageKey[response.code];
}

/**
 * Localized description for a Watchbox failure, including Watchbox-local source-name context when the
 * backend reports a source-level eligibility failure. Returns undefined for non-Watchbox
 * errors or a malformed aggregate response without source names.
 */
export function watchboxErrorMessage(
    error: unknown,
    t: TFunction,
    locale: string | undefined,
): string | undefined {
    const response = parseWatchboxError(error);
    if (!response) return undefined;

    if (!SOURCE_NAMED_ERROR_CODES.has(response.code)) {
        return t(messageKey[response.code]);
    }

    const sourceNames = response.context.sourceNames ?? [];
    if (sourceNames.length === 0) return undefined;

    const visibleSourceNames = sourceNames.slice(0, MAX_VISIBLE_SOURCE_NAMES);
    const remainingSourceCount = sourceNames.length - visibleSourceNames.length;
    const sourceLabels =
        remainingSourceCount > 0
            ? [
                  ...visibleSourceNames,
                  t("watchbox.sourceEligibilityAdditionalSources", {
                      count: remainingSourceCount,
                  }),
              ]
            : visibleSourceNames;
    const formattedSourceNames = new Intl.ListFormat(toBcp47Locale(locale), {
        style: "long",
        type: "conjunction",
    }).format(sourceLabels);
    return t(messageKey[response.code], { sourceNames: formattedSourceNames });
}

const polygonMessageKey = {
    empty: "watchbox.errors.polygon.empty",
    "has-holes": "watchbox.errors.polygon.hasHoles",
    "not-closed": "watchbox.errors.polygon.notClosed",
    "crosses-antimeridian": "watchbox.errors.polygon.crossesAntimeridian",
    "too-few-vertices": "watchbox.errors.polygon.tooFewVertices",
    "self-intersects": "watchbox.errors.polygon.selfIntersects",
} as const satisfies Record<WatchboxPolygonError, string>;

type WatchboxPolygonErrorMessageKey =
    | (typeof polygonMessageKey)[WatchboxPolygonError]
    | "watchbox.errors.WATCHBOX_INVALID_POLYGON";

/**
 * Translation key naming why a drawn area failed client-side validation.
 * Falls back to the generic invalid-area message for anything that is not a
 * recognised {@link WatchboxPolygonError}.
 */
export function watchboxPolygonErrorMessageKey(
    message: string | undefined,
): WatchboxPolygonErrorMessageKey {
    return message !== undefined && Object.hasOwn(polygonMessageKey, message)
        ? polygonMessageKey[message as WatchboxPolygonError]
        : "watchbox.errors.WATCHBOX_INVALID_POLYGON";
}
