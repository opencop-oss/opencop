// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, HStack } from "@chakra-ui/react";
import { OverflowContainer, OverflowPopover } from "@opencop/ui";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { HistoricalViewToggle } from "../historical/controls/HistoricalViewToggle";
import { useMapbar, useMapbarSync } from "./mapbar.hook";
import { PanelToggle } from "./PanelToggle";
import { useVisiblePanels } from "./use-visible-panels.hook";

export function Mapbar(): ReactElement {
    useMapbarSync();
    const { t } = useTranslation();
    const { open } = useMapbar();
    const panels = useVisiblePanels().filter((panel) => !panel.hidden);

    return (
        <HStack gap="0" minW="0" flex="1">
            <OverflowContainer
                items={panels}
                pinned={panels.find((panel) => panel.id === open)}
                renderItem={(panel) => <PanelToggle key={panel.id} panel={panel} />}
                renderOverflow={(hidden) => (
                    <OverflowPopover
                        label={t("mapbar.morePanels")}
                        triggerProps={{ "data-panel-overflow": "" }}
                        renderItems={(close) =>
                            hidden.map((panel) => (
                                <PanelToggle key={panel.id} panel={panel} onAfterToggle={close} />
                            ))
                        }
                    />
                )}
                gap="0"
                alignItems="center"
                justifyContent="flex-end"
                flex="1"
                // The notifications panel's badge (Float placement="top-end") pokes slightly
                // above its icon by design — the default overflow:hidden (a guard against
                // transient overflow mid-resize, not how items get hidden; those just aren't
                // rendered) was clipping it.
                overflow="visible"
            />
            {/* Not part of the overflow pool: a differently-shaped segmented control,
                not a uniform-width icon button, and always needs to stay reachable. */}
            <Box flexShrink={0}>
                <HistoricalViewToggle />
            </Box>
        </HStack>
    );
}
