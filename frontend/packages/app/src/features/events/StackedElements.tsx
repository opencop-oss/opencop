// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useCallback, type ReactNode, useRef, useMemo } from "react";
import { Box, Flex, Text, IconButton, Portal, Menu, useMenu } from "@chakra-ui/react";
import { getElementKey, type Element } from "@opencop/elements";
import { useMapManager } from "../map/MapManagerContext";
import { useStackedObjectSelectionStore } from "../map/stacked-object-selection.store";
import { MdClose } from "react-icons/md";
import { useTranslation } from "react-i18next";
import { ElementRow } from "./StackedElementRow";
import { VirtualList } from "@opencop/ui";
import { useWatchboxPanelStore } from "../watchbox/watchbox-panel.store";

const MENU_WIDTH = 300;
const MENU_MAX_HEIGHT = 300;
const DEPTH_MORE = 50;
const DEPTH_MAX = 100;

/**
 * Renders a list of elements when there are multiple stacked on top of each other.
 * Uses Chakra Menu for keyboard navigation, focus management, and positioning.
 * Supports hover highlighting and progressive depth loading via scroll-based lazy loading.
 */
export function StackedElements(): ReactNode {
    "use no memo";
    const { t } = useTranslation();
    const mapManager = useMapManager();

    const stackedElements = useStackedObjectSelectionStore((s) => s.elements);
    const position = useStackedObjectSelectionStore((s) => s.position);
    const currentDepth = useStackedObjectSelectionStore((s) => s.currentDepth);
    const hitDepthLimit = useStackedObjectSelectionStore((s) => s.hitDepthLimit);
    const advanceDepth = useStackedObjectSelectionStore((s) => s.advanceDepth);
    const dismiss = useStackedObjectSelectionStore((s) => s.dismiss);
    const wrapperRef = useRef<HTMLDivElement>(null);
    // anchor must be stable or will be caught in infinite loop.
    const anchorElement = useMemo(
        () => ({
            getBoundingClientRect: () =>
                DOMRect.fromRect({ x: position?.x ?? 0, y: position?.y ?? 0, width: 1, height: 1 }),
        }),
        [position],
    );
    // Just the store write: clearing the map hover is the surface withdrawal's
    // job, so every dismissal path gets it, not only this one.
    const dismissPicker = dismiss;

    const menu = useMenu({
        positioning: {
            strategy: "fixed",
            getAnchorElement: () => anchorElement,
        },
        onHighlightChange: (details) => {
            if (!details.highlightedValue) {
                mapManager.setHover(null);
                return;
            } // ignore transient nulls
            const el = stackedElements?.find((e) => getElementKey(e) === details.highlightedValue);
            if (el) {
                mapManager.setHover(el);
            } else {
                mapManager.setHover(null);
            }
            document.getElementById(details.highlightedValue)?.scrollIntoView({ block: "nearest" });
        },
        onOpenChange: (details) => {
            if (!details.open) dismissPicker();
        },
        onInteractOutside: (e) => {
            if (wrapperRef.current?.contains(e.target as Node)) {
                e.preventDefault();
            }
        },
    });

    // Open/close menu in sync with the store
    useEffect(() => {
        menu.api.setOpen(stackedElements !== null);
    }, [menu.api, stackedElements]);

    // Re-evaluate anchor when position changes while the menu is already open;
    // setOpen(true) is a no-op in that case, so Floating UI never re-reads the anchor.
    useEffect(() => {
        if (position) menu.api.reposition();
    }, [menu.api, position]);

    // Dismiss when the map starts moving so the picker doesn't float over a location it no longer
    // relates to. A lasso selection persists through zoom/pan because its ring is geo-anchored
    // and the operator still needs to work from the finished selection.
    useEffect(() => {
        if (!stackedElements) return;
        const sub = mapManager.onMap("movestart", dismissPicker);
        return () => sub.unsub();
    }, [stackedElements, mapManager, dismissPicker]);

    const handleLoadMore = useCallback(() => {
        if (!position) return;
        const nextDepth = currentDepth < DEPTH_MORE ? DEPTH_MORE : DEPTH_MAX;
        const more = mapManager.pickMoreAt(position.x, position.y, nextDepth);
        const existing = stackedElements ?? [];
        const seen = new Set(existing.map(getElementKey));
        const combined = [
            ...existing,
            ...more.filter((e) => {
                const key = getElementKey(e);
                if (seen.has(key)) return false;
                seen.add(key);
                return true;
            }),
        ];
        advanceDepth(nextDepth, combined, more.length >= nextDepth);
    }, [position, currentDepth, mapManager, stackedElements, advanceDepth]);

    if (!stackedElements || !position) return null;

    const handleSelect = (element: Element) => {
        dismissPicker();
        mapManager.selectElement(element);
        useWatchboxPanelStore.getState().select(null);
    };

    const atHardLimit = hitDepthLimit && currentDepth >= DEPTH_MAX;

    return (
        <Menu.RootProvider value={menu}>
            <Portal>
                <Box ref={wrapperRef}>
                    <Menu.Positioner>
                        <Box
                            data-state="open"
                            _open={{
                                animation: "expandDown 300ms ease-out",
                            }}
                            overflow="hidden"
                        >
                            <Flex
                                align="center"
                                justify="space-between"
                                pl="3"
                                border="1px solid"
                                boxShadow="0 4px 12px rgba(0,0,0,0.6)"
                                borderColor="whiteAlpha.200"
                                borderBottom="none"
                                bg="slate.bg"
                                flexShrink={0}
                            >
                                <Text
                                    fontSize="xs"
                                    fontWeight={700}
                                    letterSpacing="0.1em"
                                    py="1.5"
                                    color="whiteAlpha.600"
                                >
                                    {t("stackedElements.title")}
                                </Text>
                                <IconButton
                                    aria-label={t("stackedElements.dismiss")}
                                    size="xs"
                                    variant="ghost"
                                    color="whiteAlpha.500"
                                    outlineOffset="-2px"
                                    _hover={{ color: "white", bg: "whiteAlpha.200" }}
                                    onClick={dismissPicker}
                                >
                                    <MdClose />
                                </IconButton>
                            </Flex>
                            <Menu.Content
                                w={`${MENU_WIDTH}px`}
                                bg="slate.bg/90"
                                backdropFilter="blur(4px)"
                                borderX="1px solid"
                                borderColor="whiteAlpha.200"
                                p="0"
                                display="flex"
                                flexDirection="column"
                                css={{
                                    animation: "none !important",
                                    transition: "none !important",
                                }}
                            >
                                <VirtualList
                                    items={stackedElements}
                                    estimateSize={() => 64}
                                    getItemKey={getElementKey}
                                    renderItem={(element) => (
                                        <ElementRow element={element} onSelect={handleSelect} />
                                    )}
                                    onReachEnd={atHardLimit ? undefined : handleLoadMore}
                                    fitContent
                                    containerProps={{
                                        maxH: `${MENU_MAX_HEIGHT}px`,
                                        style: { minHeight: "1px" },
                                    }}
                                />
                                {atHardLimit && (
                                    <Text
                                        fontSize="xs"
                                        color="whiteAlpha.500"
                                        maxWidth="300px"
                                        px="3"
                                        py="1"
                                        borderTop="1px solid"
                                        borderColor="whiteAlpha.200"
                                        bg="slate.bg"
                                        flexShrink={0}
                                    >
                                        {t("stackedElements.depthWarning", {
                                            count: stackedElements.length,
                                        })}
                                    </Text>
                                )}
                            </Menu.Content>
                        </Box>
                    </Menu.Positioner>
                </Box>
            </Portal>
        </Menu.RootProvider>
    );
}
