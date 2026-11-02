// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { SortableLayerEntry, type SortableLayerEntryProps } from "@opencop/ui";
import { useTranslation } from "react-i18next";

type SortableWeatherEntryProps = Omit<
    SortableLayerEntryProps,
    "dragAriaLabel" | "moveUpAriaLabel" | "moveDownAriaLabel"
>;

export function SortableWeatherEntry(props: SortableWeatherEntryProps) {
    const { t } = useTranslation();
    return (
        <SortableLayerEntry
            {...props}
            dragAriaLabel={t("weather.dragToReorder")}
            moveUpAriaLabel={t("weather.moveLayerUp")}
            moveDownAriaLabel={t("weather.moveLayerDown")}
        />
    );
}
