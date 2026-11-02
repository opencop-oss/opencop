// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Circle, IconButton } from "@chakra-ui/react";
import { Tooltip } from "@opencop/ui";
import { type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import type { PanelDefinition } from "~/features/mapbar/mapbar.config";
import { useMapbar } from "./mapbar.hook";
import { useTerrainState } from "../map/core/slices/terrain/terrain.hook";
import { useIsEditing } from "~/features/map/interaction-runtime/session/selectors";

interface PanelToggleProps {
    panel: PanelDefinition;
    onAfterToggle?: () => void;
}

export function PanelToggle({ panel, onAfterToggle }: PanelToggleProps): ReactElement {
    const { t } = useTranslation();
    const { open, toggleOpen } = useMapbar();
    const isEditing = useIsEditing();
    // Subscribe to terrain state so isActive() re-evaluates when terrain toggles.
    useTerrainState((s) => s.terrainConfig);
    const active = panel.isActive?.() ?? false;
    const isOpen = panel.id === open;
    const label = t(panel.translationKey);
    const tooltip = active ? `${label} (On)` : label;

    function handleClick() {
        toggleOpen(panel.id);
        onAfterToggle?.();
    }

    return (
        <IconButton
            size={{ base: "compact", lg: "md" }}
            onClick={handleClick}
            h="full"
            p="0"
            border="none"
            aria-label={tooltip}
            active="iconToggle"
            data-active={isOpen ? "" : undefined}
            data-panel-toggle={panel.id}
            disabled={isEditing}
        >
            <Tooltip content={tooltip}>
                <Box position="relative">
                    <panel.icon />
                    {active && (
                        <Circle
                            size="6px"
                            bg="active"
                            position="absolute"
                            top="-2px"
                            right="-2px"
                        />
                    )}
                    {panel.badge}
                </Box>
            </Tooltip>
        </IconButton>
    );
}
