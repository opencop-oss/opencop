// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { TFunction } from "i18next";
import { MILS_PER_DEGREE, type AngleUnit } from "@opencop/utils";

/** An angle in degrees as display text in `unit`; translated text comes from `t`. */
export function formatAngleValue(degrees: number, unit: AngleUnit, t: TFunction): string {
    const formattedDegrees = t("mapSettings.angleFormats.degrees", {
        value: degrees.toFixed(2),
        unit: t("mapSettings.angleUnitsShort.degrees", { defaultValue: "deg" }),
        defaultValue: "{{value}} {{unit}}",
    });
    const formattedMils = t("mapSettings.angleFormats.mils", {
        value: (degrees * MILS_PER_DEGREE).toFixed(1),
        unit: t("mapSettings.angleUnitsShort.mils", { defaultValue: "mils" }),
        defaultValue: "{{value}} {{unit}}",
    });

    switch (unit) {
        case "degrees":
            return formattedDegrees;
        case "mils":
            return formattedMils;
        case "both":
            return t("mapSettings.angleFormats.both", {
                degrees: formattedDegrees,
                mils: formattedMils,
                defaultValue: "{{degrees}} | {{mils}}",
            });
    }
}
