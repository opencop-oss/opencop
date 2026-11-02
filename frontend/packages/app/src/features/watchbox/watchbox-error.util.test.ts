// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { ApiError } from "@opencop/api";
import type { TFunction } from "i18next";
import { describe, expect, it } from "vitest";
import { i18n } from "~/i18n/config";
import { parseWatchboxError } from "./watchbox.api";
import {
    watchboxErrorMessage,
    watchboxErrorMessageKey,
    watchboxPolygonErrorMessageKey,
} from "./watchbox-error.util";

const AGGREGATE_CODE = "WATCHBOX_SOURCE_ELIGIBILITY_FAILED";
const AGGREGATE_KEY = "watchbox.errors.WATCHBOX_SOURCE_ELIGIBILITY_FAILED";

function aggregateError(
    context: unknown,
    message = "Selected sources require object ID mapping correction",
) {
    return new ApiError(409, {
        code: AGGREGATE_CODE,
        message,
        fields: ["layerIds"],
        reasons: [],
        context,
    });
}

const translatedKey = ((key: string, options?: { sourceNames?: string }) =>
    key === AGGREGATE_KEY
        ? `A mapper with stable object IDs is required for each listed data source: ${options?.sourceNames ?? ""}.`
        : `translated:${key}`) as unknown as TFunction;

const formattedNames = ((_key: string, options?: { sourceNames?: string }) =>
    options?.sourceNames ?? "") as unknown as TFunction;

describe("parseWatchboxError", () => {
    it("retains only string members from array-valued context entries", () => {
        const response = parseWatchboxError(
            aggregateError({
                sourceNames: ["Source A", 42, "Source B", null],
                empty: [false, null],
                malformed: "not an array",
                nested: [["not a string"]],
            }),
        );

        expect(response?.context).toEqual({
            sourceNames: ["Source A", "Source B"],
            empty: [],
            nested: [],
        });
        expect(response?.context).not.toHaveProperty("malformed");
    });

    it("turns absent, non-object, and array-valued context into an empty record", () => {
        expect(parseWatchboxError(aggregateError(null))?.context).toEqual({});
        expect(parseWatchboxError(aggregateError(["Source A"]))?.context).toEqual({});
        expect(parseWatchboxError(aggregateError({ sourceNames: "Source A" }))?.context).toEqual(
            {},
        );
    });

    it("rejects unknown codes and non-ApiError failures", () => {
        expect(
            parseWatchboxError(new ApiError(409, { code: "WATCHBOX_UNKNOWN", message: "detail" })),
        ).toBeUndefined();
        expect(parseWatchboxError(new Error("network"))).toBeUndefined();
        expect(watchboxErrorMessageKey(new Error("network"))).toBeUndefined();
    });
});

describe("on-missing source error", () => {
    const onMissing = new ApiError(409, {
        code: "WATCHBOX_OBJECT_ID_ON_MISSING_NOT_ERROR",
        message: "detail",
        context: { sourceNames: ["Source A", "Source B"] },
    });

    it("names the sources in the message and is not a plain-key error", () => {
        expect(watchboxErrorMessageKey(onMissing)).toBeUndefined();
        expect(watchboxErrorMessage(onMissing, formattedNames, "en")).toBe("Source A and Source B");
    });
});

describe("watchboxErrorMessageKey", () => {
    it("does not expose the contextual eligibility key to plain translation callers", () => {
        expect(
            watchboxErrorMessageKey(aggregateError({ sourceNames: ["Source A"] })),
        ).toBeUndefined();
    });
});

describe("watchboxErrorMessage", () => {
    it("retains localized-key behavior for recognized non-aggregate errors", () => {
        const error = new ApiError(409, {
            code: "WATCHBOX_NO_ELIGIBLE_SOURCES",
            message: "backend detail",
        });

        expect(watchboxErrorMessage(error, translatedKey, "en")).toBe(
            "translated:watchbox.errors.WATCHBOX_NO_ELIGIBLE_SOURCES",
        );
    });

    it("formats three names with English conjunction grammar", () => {
        expect(
            watchboxErrorMessage(
                aggregateError({ sourceNames: ["Source A", "Source B", "Source C"] }),
                translatedKey,
                "en",
            ),
        ).toBe(
            "A mapper with stable object IDs is required for each listed data source: Source A, Source B, and Source C.",
        );
    });

    it("keeps one and two source names unchanged", () => {
        expect(
            watchboxErrorMessage(
                aggregateError({ sourceNames: ["Source A"] }),
                formattedNames,
                "en",
            ),
        ).toBe("Source A");
        expect(
            watchboxErrorMessage(
                aggregateError({ sourceNames: ["Source A", "Source B"] }),
                formattedNames,
                "en",
            ),
        ).toBe("Source A and Source B");
    });

    it("limits a large English source list to three names and a localized remainder", () => {
        const sourceNames = Array.from({ length: 100 }, (_, index) => `Source ${index + 1}`);
        const error = aggregateError({ sourceNames });

        expect(
            watchboxErrorMessage(
                aggregateError({ sourceNames: sourceNames.slice(0, 4) }),
                i18n.getFixedT("en"),
                "en",
            ),
        ).toBe(
            "A mapper with stable object IDs is required for each listed data source: Source 1, Source 2, Source 3, and 1 more data source.",
        );
        expect(
            parseWatchboxError(error)?.context.sourceNames,
            "the presentation cap must not discard API context",
        ).toEqual(sourceNames);
        expect(watchboxErrorMessage(error, i18n.getFixedT("en"), "en")).toBe(
            "A mapper with stable object IDs is required for each listed data source: Source 1, Source 2, Source 3, and 97 more data sources.",
        );
        expect(sourceNames).toHaveLength(100);
        expect(sourceNames.slice(0, 4)).toEqual(["Source 1", "Source 2", "Source 3", "Source 4"]);
    });

    it("normalizes the app's tw locale to zh-TW before formatting names", () => {
        expect(
            watchboxErrorMessage(
                aggregateError({ sourceNames: ["來源甲", "來源乙", "來源丙"] }),
                formattedNames,
                "tw",
            ),
        ).toBe("來源甲、來源乙和來源丙");
    });

    it("renders the full Traditional Chinese message with localized name joining", () => {
        expect(
            watchboxErrorMessage(
                aggregateError({ sourceNames: ["來源甲", "來源乙"] }),
                i18n.getFixedT("tw"),
                "tw",
            ),
        ).toBe("以下每個資料來源都需要使用能提供穩定物件識別碼的映射器：來源甲和來源乙。");
    });

    it("localizes the Traditional Chinese remainder within the source list", () => {
        expect(
            watchboxErrorMessage(
                aggregateError({ sourceNames: ["來源甲", "來源乙", "來源丙", "來源丁", "來源戊"] }),
                i18n.getFixedT("tw"),
                "tw",
            ),
        ).toBe(
            "以下每個資料來源都需要使用能提供穩定物件識別碼的映射器：來源甲、來源乙、來源丙和另外 2 個資料來源。",
        );
    });

    it("renders the full French message with localized name joining", () => {
        expect(
            watchboxErrorMessage(
                aggregateError({ sourceNames: ["Source A", "Source B", "Source C"] }),
                i18n.getFixedT("fr"),
                "fr",
            ),
        ).toBe(
            "Chaque source de données répertoriée doit utiliser un mappeur fournissant des identifiants d’objet stables : Source A, Source B et Source C.",
        );
    });

    it("localizes the French remainder within the source list", () => {
        expect(
            watchboxErrorMessage(
                aggregateError({
                    sourceNames: ["Source A", "Source B", "Source C", "Source D"],
                }),
                i18n.getFixedT("fr"),
                "fr",
            ),
        ).toBe(
            "Chaque source de données répertoriée doit utiliser un mappeur fournissant des identifiants d’objet stables : Source A, Source B, Source C et 1 source de données supplémentaire.",
        );
    });

    it("does not expose the backend message when no valid source names are present", () => {
        expect(
            watchboxErrorMessage(aggregateError({ sourceNames: [] }), translatedKey, "en"),
        ).toBeUndefined();
        expect(
            watchboxErrorMessage(aggregateError({ sourceNames: [] }, ""), translatedKey, "en"),
        ).toBeUndefined();
    });
});

describe("watchboxPolygonErrorMessageKey", () => {
    it("names a recognised polygon error", () => {
        expect(watchboxPolygonErrorMessageKey("self-intersects")).toBe(
            "watchbox.errors.polygon.selfIntersects",
        );
    });

    it("falls back to the generic message for anything else, prototype names included", () => {
        expect(watchboxPolygonErrorMessageKey(undefined)).toBe(
            "watchbox.errors.WATCHBOX_INVALID_POLYGON",
        );
        expect(watchboxPolygonErrorMessageKey("nonsense")).toBe(
            "watchbox.errors.WATCHBOX_INVALID_POLYGON",
        );
        expect(watchboxPolygonErrorMessageKey("constructor")).toBe(
            "watchbox.errors.WATCHBOX_INVALID_POLYGON",
        );
    });
});
