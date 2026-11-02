// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type Ref, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Stack, Text } from "@chakra-ui/react";
import type { UserPreferences } from "@opencop/api";
import { PreferenceSelect } from "./PreferenceSelect";
import {
    LANGUAGE_CODES,
    MEASUREMENT_SYSTEMS,
    TEMPERATURE_UNITS,
    TIMEZONE_OPTIONS,
} from "./preferences.constants";
import { preferencesMutations } from "./preferences.queries";
import { detectTimezone } from "./timezone.util";

interface LocalizationSectionProps {
    preferences: UserPreferences | undefined;
    ref?: Ref<HTMLDivElement>;
}

export function LocalizationSection({ preferences, ref }: LocalizationSectionProps) {
    const { t, i18n } = useTranslation();
    const queryClient = useQueryClient();
    const { mutate: updatePreferencesMutate, isPending: isUpdatingPreferences } = useMutation(
        preferencesMutations.update(queryClient),
    );

    const detectedTimezone = useMemo(() => detectTimezone(), []);
    const languageItems = useMemo(
        () =>
            LANGUAGE_CODES.map((code) => ({
                value: code,
                label:
                    code === "en"
                        ? t("language.english", { defaultValue: "English" })
                        : code === "fr"
                          ? t("language.french", { defaultValue: "Français" })
                          : t("language.mandarin", { defaultValue: "中文" }),
            })),
        [t],
    );
    const measurementSystemItems = useMemo(
        () =>
            MEASUREMENT_SYSTEMS.map((value) => ({
                value,
                label:
                    value === "IMPERIAL"
                        ? t("userPreferences.imperial", { defaultValue: "Imperial" })
                        : t("userPreferences.metric", { defaultValue: "Metric" }),
            })),
        [t],
    );
    const temperatureUnitItems = useMemo(
        () =>
            TEMPERATURE_UNITS.map((value) => ({
                value,
                label:
                    value === "FAHRENHEIT"
                        ? t("userPreferences.fahrenheit", { defaultValue: "Fahrenheit" })
                        : t("userPreferences.celsius", { defaultValue: "Celsius" }),
            })),
        [t],
    );
    const handleLanguageChange = (code: string) => {
        if (code === i18n.language) return;

        const previousLanguage = i18n.language;

        // Optimistically update UI immediately
        i18n.changeLanguage(code);

        // Fire mutation without awaiting - React Query handles the state
        updatePreferencesMutate(
            { locale: code },
            {
                onError: () => {
                    // Rollback on error
                    i18n.changeLanguage(previousLanguage);
                },
            },
        );
    };

    return (
        <Stack gap={4} ref={ref} id="localization">
            <Text fontSize="lg" fontWeight="semibold" color="fg">
                {t("userPreferences.localizationSection", { defaultValue: "Localization" })}
            </Text>
            <PreferenceSelect
                label={t("userPreferences.language", { defaultValue: "Language" })}
                items={languageItems}
                value={i18n.language}
                onValueChange={handleLanguageChange}
                disabled={isUpdatingPreferences}
            />

            <PreferenceSelect
                label={t("userPreferences.timezone", { defaultValue: "Timezone" })}
                items={TIMEZONE_OPTIONS}
                value={preferences?.timezone ?? detectedTimezone}
                onValueChange={(value) => {
                    updatePreferencesMutate({ timezone: value });
                }}
                disabled={isUpdatingPreferences}
            />

            <PreferenceSelect
                label={t("userPreferences.measurementSystem", {
                    defaultValue: "Measurement System",
                })}
                items={measurementSystemItems}
                value={preferences?.measurementSystem ?? "IMPERIAL"}
                onValueChange={(value) => {
                    updatePreferencesMutate({
                        measurementSystem: value as "IMPERIAL" | "METRIC",
                    });
                }}
                disabled={isUpdatingPreferences}
            />

            <PreferenceSelect
                label={t("userPreferences.temperatureUnit", {
                    defaultValue: "Temperature Unit",
                })}
                items={temperatureUnitItems}
                value={preferences?.temperatureUnit ?? "FAHRENHEIT"}
                onValueChange={(value) => {
                    updatePreferencesMutate({
                        temperatureUnit: value as "FAHRENHEIT" | "CELSIUS",
                    });
                }}
                disabled={isUpdatingPreferences}
            />
        </Stack>
    );
}
