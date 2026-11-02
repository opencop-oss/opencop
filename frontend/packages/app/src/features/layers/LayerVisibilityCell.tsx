// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { IconButton } from "@chakra-ui/react";
import { FaEye, FaEyeSlash } from "react-icons/fa6";
import { useTranslation } from "react-i18next";
import type { MouseEvent } from "react";
import { useSourceState } from "@opencop/ingest";
import { useFilterPanelActions } from "~/features/filters/filter-panel.actions";
import { useFilterPanelStore } from "~/features/filters/filter-panel.store";

interface LayerVisibilityCellProps {
    layerId: string;
}

export function LayerVisibilityCell({ layerId }: LayerVisibilityCellProps) {
    const { t } = useTranslation();
    const visible = useFilterPanelStore((state) =>
        state.hiddenSources.every((id) => id !== layerId),
    );
    const { hideSource, showSource } = useFilterPanelActions();
    const syncing = useSourceState(layerId) === "syncing";

    const onToggle = (event: MouseEvent) => {
        event.stopPropagation();
        if (syncing) return;
        if (visible) {
            hideSource(layerId);
        } else {
            showSource(layerId);
        }
    };

    const label = syncing
        ? t("filters.syncing")
        : visible
          ? t("filters.hideLayer")
          : t("filters.showLayer");

    return (
        <IconButton
            aria-label={label}
            size="xs"
            variant="ghost"
            color={syncing ? "fg.muted" : visible ? "fg" : "fg.muted"}
            onClick={onToggle}
            disabled={syncing}
        >
            {visible ? <FaEye size={14} /> : <FaEyeSlash size={14} />}
        </IconButton>
    );
}
