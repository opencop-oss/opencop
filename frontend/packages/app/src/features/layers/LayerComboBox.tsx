// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import type { MyLayerInfo } from "@opencop/api";
import { OcRecentsComboBox, type OcRecentsComboboxItem } from "@opencop/ui";
import { isOffered } from "~/features/filters/panel-exclusion.util";
import { usePanelExcludedIds } from "~/features/filters/use-panel-excluded-ids.hook";
import { layerQueries } from "./layers.queries";

export interface LayerComboBoxProps {
    value: string;
    onValueChange: (value: string) => void;
    /** localStorage key for the recents list. Each call site must supply its own to keep histories isolated. */
    storageKey: string;
    disabled?: boolean;
    renderItem?: (item: OcRecentsComboboxItem) => ReactNode;
    /** Only show layers that pass this predicate. */
    filter?: (layer: MyLayerInfo) => boolean;
}

/**
 * Layer picker with a "Recent Layers" group showing the last 5 selected layers.
 * Fetches its own layer list and persists recents to localStorage under `storageKey`.
 * Layers the user removed from the map panel are not offered, unless one is the current value.
 */
export function LayerComboBox({
    value,
    onValueChange,
    disabled,
    renderItem,
    filter,
    storageKey,
}: LayerComboBoxProps) {
    const { t } = useTranslation();
    const { data: layers = [], isLoading } = useQuery(layerQueries.myList());

    const excludedIds = usePanelExcludedIds();

    const items = useMemo(() => {
        const offered = layers.filter((l) => isOffered(excludedIds, l.layerId, [value]));
        const visible = filter ? offered.filter(filter) : offered;
        return visible.map((l) => ({ label: l.layerName, value: l.layerId }));
    }, [layers, filter, excludedIds, value]);

    return (
        <OcRecentsComboBox
            items={items}
            value={value}
            onValueChange={onValueChange}
            storageKey={storageKey}
            recentsLabel={t("events.recentLayers")}
            ungroupedLabel={t("events.allLayers")}
            placeholder={isLoading ? t("events.loadingLayers") : t("events.searchLayers")}
            disabled={disabled || isLoading}
            emptyMessage={isLoading ? t("common.loading") : t("events.noLayersAvailable")}
            renderItem={renderItem}
        />
    );
}
