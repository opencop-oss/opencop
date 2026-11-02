// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex } from "@chakra-ui/react";
import { getDefaultTransition, Panel } from "@opencop/ui";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { ErrorBoundaryWrapper } from "~/features/errors/ErrorBoundaryWrapper";
import { useMapbar } from "./mapbar.hook";
import { useVisiblePanels } from "./use-visible-panels.hook";

export function InfoPanel(): ReactElement | null {
    const { t } = useTranslation();
    const { open } = useMapbar();
    const visiblePanels = useVisiblePanels();

    if (!open) return null;

    // Resolves against the flag-filtered list, not the raw config — on the
    // render where a runtime flag flips a panel off, `open` (from the
    // store) is still stale for one paint; useMapbarSync's own correction
    // effect only runs after that paint. Filtering here means that stale
    // render finds no panel and returns null instead of flashing it mounted.
    const activePanel = visiblePanels.find((p) => p.id === open);
    if (!activePanel) return null;

    const Content = activePanel.content;

    return (
        <Flex
            as="aside"
            aria-label={t("navigation.detailsPanel")}
            w="lg"
            flexDir="column"
            pointerEvents="none"
        >
            <Panel
                w="full"
                h="full"
                maxH="full"
                transition={getDefaultTransition(["all"]).transition}
                p="0"
                pointerEvents="auto"
                pos="relative"
                zIndex="docked"
                overflow="hidden"
            >
                <Flex key={open} p="4" pos="relative" w="full" h="full" minH="0">
                    <ErrorBoundaryWrapper componentName={open}>
                        <Content />
                    </ErrorBoundaryWrapper>
                </Flex>
            </Panel>
        </Flex>
    );
}
