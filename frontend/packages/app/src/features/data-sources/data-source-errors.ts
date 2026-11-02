// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { ApiError } from "@opencop/api";
import type { TFunction } from "i18next";

/** Localized text for known connection-input rejections; other failures keep Core's message. */
export function getConnectionInputErrorMessage(error: ApiError, t: TFunction): string | undefined {
    if (error.status !== 400 || error.body.code !== "CONNECTION_INPUT_INVALID") {
        return error.body.message;
    }
    switch (error.body.detailCode) {
        case "UNSUPPORTED_RTSP_SCHEME":
            return t("connectionSetup.rtspUrlScheme");
        case "RTSP_URL_CREDENTIALS":
            return t("connectionSetup.rtspUrlCredentials");
        case "RTSP_URL_QUERY":
            return t("connectionSetup.rtspUrlQuery");
        default:
            return error.body.message;
    }
}

function getConnectionErrorMessage(
    error: unknown,
    t: TFunction,
    operation: "create" | "metadata",
): string | undefined {
    const fallbackKey =
        operation === "create" ? "admin.failedToCreateServer" : "dataSources.updateFailed";
    if (!(error instanceof ApiError)) return t(fallbackKey);

    switch (error.status) {
        case 401:
            return undefined;
        case 403:
            return operation === "metadata" ? t("dataSources.updateForbidden") : t(fallbackKey);
        case 404:
            return operation === "metadata" ? t("dataSources.updateNotFound") : t(fallbackKey);
        case 409: {
            if (operation !== "metadata") return t(fallbackKey);
            if (error.body.code === "CORE-CONNECTION-1003") {
                return t("dataSources.updateNameConflict");
            }
            return error.body.code === undefined || error.body.code === null
                ? t("adminDialogs.archivedDataSourceViewOnly")
                : t(fallbackKey);
        }
    }

    switch (error.body.code) {
        case "CORE-CONNECTION-1004":
            return operation === "metadata" ? t("dataSources.updateEmpty") : t(fallbackKey);
        case "CORE-CONNECTION-1005":
            return t("dataSources.createAccessInvalid");
        case "CORE-CONNECTION-1006":
            return t("dataSources.createAccessUnsupported");
        default:
            return t(fallbackKey);
    }
}

export function getCreateDataSourceErrorMessage(error: unknown, t: TFunction): string {
    return getConnectionErrorMessage(error, t, "create") ?? "";
}

export function getDataSourceMetadataErrorMessage(
    error: unknown,
    t: TFunction,
): string | undefined {
    return getConnectionErrorMessage(error, t, "metadata");
}
