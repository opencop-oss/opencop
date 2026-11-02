// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { IconButton } from "@chakra-ui/react";
import type { ReactNode } from "react";
import { PiBroadcastBold } from "react-icons/pi";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { layerQueries } from "~/features/layers/layers.queries";
import { Tooltip } from "@opencop/ui";
import { useEventDetails } from "../use-event-details.hook";

export function LayerIndicator(): ReactNode {
    const { t } = useTranslation();
    const { details } = useEventDetails();
    const { data: layers } = useQuery(layerQueries.myList());

    if (!details) return null;

    const source = details.source;
    const isLocal = !source || source === "local";

    // Helper to get layer name from layer ID
    const getLayerName = (layerId: string): string => {
        const layer = layers?.find((l) => l.layerId === layerId);
        return layer?.layerName ?? layerId;
    };

    if (isLocal) {
        return (
            <Tooltip content={t("events.notSavedToLayer")}>
                <IconButton
                    size="md"
                    color="fg.muted"
                    disabled
                    aria-label={t("events.notSavedToLayer")}
                >
                    <PiBroadcastBold />
                </IconButton>
            </Tooltip>
        );
    }

    return (
        <Tooltip content={t("events.savedToLayer", { layerName: getLayerName(source) })}>
            <IconButton
                size="md"
                color="green.400"
                variant="plain"
                filter="drop-shadow(1px 1px 4px currentColor)"
                aria-label={t("events.savedToLayer", { layerName: getLayerName(source) })}
            >
                <PiBroadcastBold />
            </IconButton>
        </Tooltip>
    );
}
