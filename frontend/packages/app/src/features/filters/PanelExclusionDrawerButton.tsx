// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useTranslation } from "react-i18next";
import { DrawerToolbarButton } from "~/features/drawers/DrawerToolbarButton";
import { PanelExclusionIcon } from "./PanelExclusionIcon";
import { usePanelExclusionActions } from "./panel-exclusion.actions";
import { useFilterPanelStore } from "./filter-panel.store";

const LABEL_KEYS = {
    layer: {
        hide: "filters.panelExclusion.hideLayer",
        show: "filters.panelExclusion.showLayer",
    },
    dataSource: {
        hide: "filters.panelExclusion.hideDataSource",
        show: "filters.panelExclusion.showDataSource",
    },
} as const;

interface PanelExclusionDrawerButtonProps {
    id: string;
    kind: keyof typeof LABEL_KEYS;
}

/** Drawer header toggle for removing a layer or data source from the map panel. */
export function PanelExclusionDrawerButton({ id, kind }: PanelExclusionDrawerButtonProps) {
    const { t } = useTranslation();
    const excluded = useFilterPanelStore((s) => s.excludedIds.includes(id));
    const { setExcluded } = usePanelExclusionActions();

    return (
        <DrawerToolbarButton
            label={t(LABEL_KEYS[kind][excluded ? "show" : "hide"])}
            icon={<PanelExclusionIcon excluded={excluded} />}
            onClick={() => setExcluded([id], !excluded)}
        />
    );
}
