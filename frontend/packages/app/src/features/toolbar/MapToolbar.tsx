// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { HStack } from "@chakra-ui/react";
import { Divider, Panel } from "@opencop/ui";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { ErrorBoundaryWrapper } from "~/features/errors/ErrorBoundaryWrapper";
import { MapSearch } from "../location-search/MapSearch";
import { Mapbar } from "../mapbar/Mapbar";
import { DrawingTool } from "./DrawingTool";
import { PluginTools } from "./PluginTools";
export function MapToolbar(): ReactElement {
    const { t } = useTranslation();

    return (
        <Panel
            as="nav"
            aria-label={t("navigation.mapTools")}
            w="full"
            h="10"
            display="flex"
            flexDir="row"
            flexWrap="nowrap"
            alignItems="center"
            flexShrink={0}
            pointerEvents="auto"
            borderRadius="0"
            animation="slide-from-top 0.15s ease-out, fade-in 0.15s ease-out"
        >
            {/* Drawing tools yield first: flexShrink 9999 vs the right cluster's 1 lands the
                squeeze here until ToolPicker's overflow menu absorbs it. Don't normalize. */}
            <HStack gap="0" minW="0" flexGrow={1} flexShrink={9999} flexBasis="auto">
                <DrawingTool />
            </HStack>

            {/* Center column: search + any topBarCenter plugin tools */}
            <HStack gap="0" flexShrink={0}>
                <ErrorBoundaryWrapper componentName="MapSearch">
                    <MapSearch />
                </ErrorBoundaryWrapper>
                <PluginTools location="topBarCenter" />
            </HStack>

            {/* End-slot plugin tools + mapbar panels */}
            <HStack
                gap="0"
                justify="flex-end"
                minW="0"
                flexGrow={1}
                flexShrink={1}
                flexBasis="auto"
            >
                <PluginTools location="topBarEnd" />
                <Divider orientation="vertical" borderWidth="1px" height="1.5em" />
                <Mapbar />
            </HStack>
        </Panel>
    );
}
