// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, HStack } from "@chakra-ui/react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { FaTimes } from "react-icons/fa";
import { IconButton } from "@opencop/ui";
import { useMapbarStore } from "../mapbar/mapbar.store";
import { MAP_FILTERS_PANEL_ID, useMapFilterStore } from "./map-filter.store";

/**
 * Sends focus to the Map Filter toggle so keyboard users aren't dropped onto <body>
 * when the pill unmounts. A folded toggle sits in the closed overflow popover, which
 * stays mounted under `hidden`, so focus() would no-op there; fall back to the More trigger.
 */
function focusPanelToggle() {
    const toggle = document.querySelector<HTMLElement>(
        `[data-panel-toggle="${MAP_FILTERS_PANEL_ID}"]`,
    );
    const target = toggle?.closest("[hidden]")
        ? document.querySelector<HTMLElement>("[data-panel-overflow]")
        : toggle;
    target?.focus();
}

/**
 * Floating pill shown above the map when the Map Filter panel is closed but
 * filters are still active — otherwise a user could toggle filters on, close
 * the panel, and forget the map is no longer showing everything. Styled after
 * `BasemapToggleBar`'s collapsed pill.
 */
export function MapFilterActivePopup() {
    const { t } = useTranslation();
    const activeCount = useMapFilterStore((s) => s.activeNames.length);
    const { clearActive } = useMapFilterStore((s) => s.actions);
    const isPanelOpen = useMapbarStore((s) => s.sidebarPanel === MAP_FILTERS_PANEL_ID);
    const [dismissed, setDismissed] = useState(false);
    const [prevIsPanelOpen, setPrevIsPanelOpen] = useState(isPanelOpen);
    const [prevActiveCount, setPrevActiveCount] = useState(activeCount);

    // Bring the popup back the next time the panel closes, or the moment the
    // active set changes — a stale dismissal shouldn't hide a filter change
    // the user hasn't seen yet. Adjusted during render (React's documented
    // pattern for deriving state from a prop change) rather than in an
    // effect, since it only reacts to values this render already has.
    if (isPanelOpen !== prevIsPanelOpen || activeCount !== prevActiveCount) {
        if ((prevIsPanelOpen && !isPanelOpen) || activeCount !== prevActiveCount) {
            setDismissed(false);
        }
        setPrevIsPanelOpen(isPanelOpen);
        setPrevActiveCount(activeCount);
    }

    if (isPanelOpen || activeCount === 0 || dismissed) return null;

    const openPanel = () => useMapbarStore.getState().openPanel(MAP_FILTERS_PANEL_ID);

    return (
        <HStack
            role="status"
            aria-live="polite"
            gap="0"
            align="center"
            bg="bg.glass"
            backdropFilter="blur(4px)"
            borderRadius="sm"
            borderWidth="1px"
            borderColor="border"
            w="fit-content"
            overflow="hidden"
        >
            <Button
                type="button"
                variant="ghost"
                size="2xs"
                px="2"
                py="1.5"
                minW="0"
                h="auto"
                cursor="pointer"
                fontSize="xs"
                fontWeight="medium"
                lineHeight="1"
                color="fg.muted"
                borderRadius="0"
                whiteSpace="nowrap"
                aria-expanded={false}
                _hover={{ bg: "active/5" }}
                // Inset, not the outward default — the container clips overflow for its
                // rounded corners, which would crop a ring drawn outside this leaf's edge.
                _focusVisible={{
                    outline: "2px solid",
                    outlineColor: "brand.focusRing",
                    outlineOffset: "-2px",
                }}
                transition="background 0.25s"
                onClick={openPanel}
            >
                {t("filter.filtersActive", { count: activeCount })}
            </Button>
            <Box w="1px" h="18px" bg="border" flexShrink={0} />
            <Button
                type="button"
                variant="ghost"
                size="2xs"
                px="2"
                py="1.5"
                minW="0"
                h="auto"
                cursor="pointer"
                fontSize="xs"
                fontWeight="medium"
                lineHeight="1"
                color="fg.muted"
                borderRadius="0"
                _hover={{ bg: "active/5" }}
                // Same clipping container as the button above.
                _focusVisible={{
                    outline: "2px solid",
                    outlineColor: "brand.focusRing",
                    outlineOffset: "-2px",
                }}
                transition="background 0.25s"
                onClick={clearActive}
            >
                {t("filter.clearFilters")}
            </Button>
            <Box w="1px" h="18px" bg="border" flexShrink={0} />
            <Box px="2">
                <IconButton
                    size="xs"
                    aria-label={t("filter.dismiss")}
                    color="fg.muted"
                    onClick={() => {
                        setDismissed(true);
                        focusPanelToggle();
                    }}
                >
                    <FaTimes size={10} />
                </IconButton>
            </Box>
        </HStack>
    );
}
