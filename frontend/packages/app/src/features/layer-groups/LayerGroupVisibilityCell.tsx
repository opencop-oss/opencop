// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { IconButton } from "@chakra-ui/react";
import { FaEye, FaEyeSlash } from "react-icons/fa6";
import { useTranslation } from "react-i18next";
import type { MouseEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { layerGroupQueries } from "./layer-groups.queries";
import { useFilterPanelActions } from "~/features/filters/filter-panel.actions";
import { useFilterPanelStore } from "~/features/filters/filter-panel.store";

interface LayerGroupVisibilityCellProps {
    groupId: string;
}

export function LayerGroupVisibilityCell({ groupId }: LayerGroupVisibilityCellProps) {
    const { t } = useTranslation();
    const { data: groupLayers = [] } = useQuery(layerGroupQueries.layers(groupId));
    const layerIds = groupLayers.map((l) => l.layerId);

    // Visibility is independent of individual layer states — only tracks explicit group toggles
    const isGroupHidden = useFilterPanelStore((state) => state.hiddenLayerGroups.includes(groupId));
    const visible = !isGroupHidden;

    const { hideLayerGroup, showLayerGroup } = useFilterPanelActions();

    const onToggle = (event: MouseEvent) => {
        event.stopPropagation();
        if (visible) {
            hideLayerGroup(groupId, layerIds);
        } else {
            showLayerGroup(groupId, layerIds);
        }
    };

    const label = visible ? t("filters.hideLayer") : t("filters.showLayer");

    return (
        <IconButton
            aria-label={label}
            size="xs"
            variant="ghost"
            color={visible ? "fg" : "fg.muted"}
            onClick={onToggle}
        >
            {visible ? <FaEye size={14} /> : <FaEyeSlash size={14} />}
        </IconButton>
    );
}
