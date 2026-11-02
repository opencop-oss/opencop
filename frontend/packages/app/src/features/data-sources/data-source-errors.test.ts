// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { ApiError } from "@opencop/api";
import { createInstance, type TFunction } from "i18next";
import { beforeAll, describe, expect, it } from "vitest";
import en from "~/locales/en/translation.json";
import fr from "~/locales/fr/translation.json";
import tw from "~/locales/tw/translation.json";
import {
    getConnectionInputErrorMessage,
    getCreateDataSourceErrorMessage,
    getDataSourceMetadataErrorMessage,
} from "./data-source-errors";

const translations: Record<string, string> = {
    "admin.failedToCreateServer": "Failed to create server",
    "dataSources.createAccessInvalid": "Access mode must be Read, Write, or Read & Write.",
    "dataSources.createAccessUnsupported":
        "Access mode must be supported by the selected transport and mapping profile.",
    "dataSources.updateEmpty": "No metadata changes were provided.",
    "dataSources.updateForbidden": "Data source updates are not permitted.",
    "dataSources.updateNameConflict":
        "A data source with this name already exists. Choose a different name.",
    "dataSources.updateNotFound": "This data source no longer exists.",
    "dataSources.updateFailed": "Failed to update data source.",
    "adminDialogs.archivedDataSourceViewOnly": "Archived data sources are read-only.",
};
const t = ((key: string) => translations[key] ?? key) as unknown as TFunction;

describe("getConnectionInputErrorMessage", () => {
    const i18n = createInstance();

    beforeAll(async () => {
        await i18n.init({
            lng: "en",
            fallbackLng: "en",
            resources: {
                en: { translation: en },
                fr: { translation: fr },
                tw: { translation: tw },
            },
        });
    });

    describe.each(["en", "fr", "tw"] as const)("%s messages", (language) => {
        it.each([
            {
                detailCode: "UNSUPPORTED_RTSP_SCHEME",
                expected: {
                    en: "RTSP URL must use rtsp:// or rtsps:// scheme",
                    fr: "L'URL RTSP doit utiliser le protocole rtsp:// ou rtsps://",
                    tw: "RTSP URL 必須使用 rtsp:// 或 rtsps:// 通訊協定",
                },
            },
            {
                detailCode: "RTSP_URL_CREDENTIALS",
                expected: {
                    en: "RTSP URL must not contain credentials — use Vault instead",
                    fr: "L'URL RTSP ne doit pas contenir d'identifiants — utilisez Vault à la place",
                    tw: "RTSP URL 不得包含認證資訊，請改用 Vault",
                },
            },
            {
                detailCode: "RTSP_URL_QUERY",
                expected: {
                    en: "RTSP URL must not contain query parameters",
                    fr: "L'URL RTSP ne doit pas contenir de paramètres de requête",
                    tw: "RTSP URL 不得包含查詢參數",
                },
            },
        ])("localizes $detailCode", ({ detailCode, expected }) => {
            const error = new ApiError(400, {
                code: "CONNECTION_INPUT_INVALID",
                detailCode,
                message: "Core's English rejection",
            });

            expect(getConnectionInputErrorMessage(error, i18n.getFixedT(language))).toBe(
                expected[language],
            );
        });
    });

    it.each([
        { status: 400, code: "CONNECTION_INPUT_INVALID", detailCode: "FUTURE_RTSP_RULE" },
        { status: 400, code: "CONNECTION_INPUT_INVALID", detailCode: undefined },
        { status: 400, code: "OTHER_FAILURE", detailCode: "RTSP_URL_QUERY" },
        { status: 500, code: "CONNECTION_INPUT_INVALID", detailCode: "RTSP_URL_QUERY" },
    ])("preserves Core's text for $status/$code/$detailCode", ({ status, code, detailCode }) => {
        const error = new ApiError(status, {
            code,
            detailCode,
            message: "Core's original message",
        });

        expect(getConnectionInputErrorMessage(error, i18n.getFixedT("fr"))).toBe(
            "Core's original message",
        );
    });

    it("leaves an unknown rejection without text to the generic error fallback", () => {
        const error = new ApiError(400, { code: "CONNECTION_INPUT_INVALID" });

        expect(getConnectionInputErrorMessage(error, i18n.getFixedT("fr"))).toBeUndefined();
    });
});

describe("getCreateDataSourceErrorMessage", () => {
    it.each([
        {
            code: "CORE-CONNECTION-1005",
            expected: "Access mode must be Read, Write, or Read & Write.",
        },
        {
            code: "CORE-CONNECTION-1006",
            expected:
                "Access mode must be supported by the selected transport and mapping profile.",
        },
    ])("maps $code without exposing backend prose", ({ code, expected }) => {
        const error = new ApiError(400, { code, message: "protected backend detail" });

        expect(getCreateDataSourceErrorMessage(error, t)).toBe(expected);
    });

    it("uses the stable generic fallback for an unknown failure", () => {
        const error = new ApiError(500, { message: "internal backend detail" });

        expect(getCreateDataSourceErrorMessage(error, t)).toBe("Failed to create server");
    });

    it.each([
        new ApiError(400, {
            code: "CORE-CONNECTION-1004",
            message: "metadata-only backend detail",
        }),
        new ApiError(403, {
            code: "CORE-CONNECTION-1006",
            message: "protected backend detail",
        }),
        new ApiError(404, { message: "protected backend detail" }),
        new ApiError(409, { message: "protected backend detail" }),
    ])("does not apply metadata-specific copy to creation failures", (error) => {
        expect(getCreateDataSourceErrorMessage(error, t)).toBe("Failed to create server");
    });

    it("delegates creation 401 to the established authentication flow", () => {
        expect(
            getCreateDataSourceErrorMessage(
                new ApiError(401, { message: "protected backend detail" }),
                t,
            ),
        ).toBe("");
    });
});

describe("getDataSourceMetadataErrorMessage", () => {
    it.each([
        {
            error: new ApiError(400, {
                code: "CORE-CONNECTION-1004",
                message: "protected backend detail",
            }),
            expected: "No metadata changes were provided.",
        },
        {
            error: new ApiError(400, {
                code: "CORE-CONNECTION-1005",
                message: "protected backend detail",
            }),
            expected: "Access mode must be Read, Write, or Read & Write.",
        },
        {
            error: new ApiError(400, {
                code: "CORE-CONNECTION-1006",
                message: "protected backend detail",
            }),
            expected:
                "Access mode must be supported by the selected transport and mapping profile.",
        },
        {
            error: new ApiError(403, {
                code: "CORE-CONNECTION-1006",
                message: "protected backend detail",
            }),
            expected: "Data source updates are not permitted.",
        },
        {
            error: new ApiError(404, { message: "protected backend detail" }),
            expected: "This data source no longer exists.",
        },
        {
            error: new ApiError(409, {
                code: "CORE-CONNECTION-1003",
                message: "protected backend detail",
            }),
            expected: "A data source with this name already exists. Choose a different name.",
        },
        {
            error: new ApiError(409, { message: "protected backend detail" }),
            expected: "Archived data sources are read-only.",
        },
        {
            error: new ApiError(409, {
                code: "CORE-CONNECTION-9999",
                message: "protected backend detail",
            }),
            expected: "Failed to update data source.",
        },
        {
            error: new ApiError(500, { message: "protected backend detail" }),
            expected: "Failed to update data source.",
        },
    ])("selects localized metadata failures without backend prose", ({ error, expected }) => {
        expect(getDataSourceMetadataErrorMessage(error, t)).toBe(expected);
    });

    it("delegates 401 to the established authentication flow", () => {
        expect(
            getDataSourceMetadataErrorMessage(
                new ApiError(401, { message: "protected backend detail" }),
                t,
            ),
        ).toBeUndefined();
    });
});
