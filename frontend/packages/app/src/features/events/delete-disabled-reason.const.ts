// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ParseKeys } from "i18next";
import type { EditabilityReason } from "~/features/map/editability";

/** Translation key explaining why Delete is unavailable, for each reason that blocks it. */
export const DELETE_DISABLED_REASON_KEYS: Partial<Record<EditabilityReason, ParseKeys>> = {
    dataSourceReadOnly: "events.deleteDisabledDataSource",
    insufficientLayerRole: "events.deleteDisabledInsufficientRole",
    noPermission: "events.deleteDisabledNoPermission",
};
