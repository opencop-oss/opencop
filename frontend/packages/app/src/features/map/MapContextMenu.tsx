// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Right-click context menu for the map canvas.
 *
 * The right-click that opens it is the interaction runtime's: ambient's
 * handler set carries the `contextmenu` trigger and hands this component
 * where it landed (`registerContextMenuOpener`), so the menu can open only
 * while nothing owns the pointer — a right-click during a draw is the draw's
 * (a vertex undo, a trace abort) and never reaches here. While open, the menu
 * is the runtime's open map surface: Escape closes it through the ladder, a
 * primary click on the canvas closes it through the surface's own pointer
 * binding, and a starting session closes it.
 *
 * Target identification: the hovered element first, then a deck pick at the
 * point, then a point-in-polygon test (which catches clicks on non-pickable
 * labels over a polygon).
 *
 * The host's own items, gated by editability (R9 permissions):
 * - Editable feature: Edit, Delete, Copy Coordinates, Zoom to Feature
 * - Read-only feature: Copy Coordinates, Zoom to Feature
 * - Empty space: Copy Coordinates
 *
 * Contributed items (`registerContextMenuItem`) follow, for what the menu was
 * opened on — empty space or an element's kind — fixed when it opens.
 *
 * Uses Chakra Menu primitives for ARIA roles, keyboard navigation,
 * and focus management.
 */
import { useCallback, useEffect, useLayoutEffect, useMemo, useState, type ReactNode } from "react";
import { Menu, Portal, useMenu } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { localEventSource, type Element } from "@opencop/elements";
import type { ContextMenuItemContribution, ContextMenuSnapshot } from "@opencop/contributions";
import { useMapManager } from "./MapManagerContext";
import { focusOnElement } from "./element-focus.util";
import { useElementActions } from "@opencop/ingest";
import { activate, startEdit } from "~/features/map/interaction-runtime/session/commands";
import {
    openSurface,
    registerContextMenuOpener,
} from "~/features/map/interaction-runtime/arbitration/surface";
import { getEditability, type Editability } from "./editability";
import { layerQueries } from "~/features/layers/layers.queries";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { useToast } from "~/features/notifications/use-toast.hook";
import { formatCoord } from "@opencop/utils";
import { useCoordFormat } from "~/features/coordinates/CoordFormatContext";
import { useMapStore } from "./core/map.store";
import { useViewActions } from "./core/slices/view/view.hook";
import { useHistoricalMode } from "~/features/historical/data-plane/historical.store";
import {
    reportMissingTool,
    useContextMenuItems,
} from "~/features/contributions/context-menu-registry";
import { getTool } from "~/features/contributions/tool-registry";

interface ContextMenuState {
    /** Viewport-relative X (from originalEvent.clientX). */
    x: number;
    /** Viewport-relative Y (from originalEvent.clientY). */
    y: number;
    /** Geographic coordinates at click point. */
    lngLat: { lng: number; lat: number };
    /** The feature under the click, if any. */
    element?: Element;
    /** What the user can do with this feature. */
    editability?: Editability;
}

export function MapContextMenu(): ReactNode {
    const { t } = useTranslation();
    const mapManager = useMapManager();
    const elementActions = useElementActions();
    const queryClient = useQueryClient();
    const { showToast } = useToast();
    const { format } = useCoordFormat();
    const { flyTo, fitBounds } = useViewActions();
    // Historical features are read-only: Edit becomes "View details" and
    // Delete / Drop Point Here are suppressed (no mutation of past data).
    const isHistorical = useHistoricalMode() === "historical";

    const [menu, setMenu] = useState<ContextMenuState | null>(null);
    // Before the early return below: a hook may not follow it. Closed, it resolves for no element.
    const contributed = useContextMenuItems(menu?.element, isHistorical);
    // anchor element must be stable or will be caught in infinite loop.
    const anchorElement = useMemo(
        () => ({
            getBoundingClientRect: () =>
                DOMRect.fromRect({ x: menu?.x ?? 0, y: menu?.y ?? 0, width: 1, height: 1 }),
        }),
        [menu],
    );
    const contextMenu = useMenu({
        positioning: {
            strategy: "fixed",
            getAnchorElement: () => anchorElement,
        },
        onOpenChange: (details) => {
            if (!details.open) setMenu(null);
        },
    });

    const close = useCallback(() => setMenu(null), []);

    // The runtime's right-click reaches here with where it landed. Snapshot
    // reads are intentional: this runs in a MapLibre event callback, not a
    // React render path.
    useEffect(
        () =>
            registerContextMenuOpener((at) => {
                // Target identification:
                // 1. Current hover (direct hit on pickable feature)
                // 2. Imperative pick at click coordinates (fallback)
                // 3. Point-in-polygon test (catches clicks on non-pickable labels)
                const hovered = useMapStore.getState().hovered;
                // Resolve against the shared store (it holds live or historical
                // elements depending on mode) so right-clicks target the element
                // actually on screen.
                let element = mapManager.getElement(hovered ?? "");

                if (!element) {
                    const picked = mapManager.pickAtScreen(at.screen.x, at.screen.y);
                    if (picked) element = picked;
                }

                if (!element) {
                    element = mapManager.pickAtLngLat(at.lngLat.lng, at.lngLat.lat);
                }

                // Resolve editability
                let editability: Editability | undefined;
                if (element) {
                    const myLayers = queryClient.getQueryData(layerQueries.myList().queryKey);
                    const myDataSources = queryClient.getQueryData(
                        dataSourceQueries.myList().queryKey,
                    );
                    if (myLayers && myDataSources) {
                        editability = getEditability(element.source, myLayers, myDataSources);
                    }
                }

                setMenu({
                    x: at.client.x,
                    y: at.client.y,
                    lngLat: at.lngLat,
                    element,
                    editability,
                });
            }),
        [mapManager, queryClient],
    );

    // Close on map movement — a camera event, uncontended. The primary click
    // that closes it is the surface's own pointer binding in the runtime.
    useEffect(() => {
        if (!menu) return;
        const moveSub = mapManager.onMap("movestart", close);
        return () => moveSub.unsub();
    }, [menu, mapManager, close]);

    // Keep Chakra Menu open state in sync with the map context-menu state.
    useEffect(() => {
        contextMenu.api.setOpen(!!menu);
    }, [contextMenu.api, menu]);

    // Declare the menu to the interaction runtime while it is open, so the
    // ladder owns its Escape, the surface owns the pointer, and a starting
    // session closes it. Before this the menu could stay open under a draw
    // armed by a shortcut, with its Edit and Delete items still pointing at an
    // element.
    //
    // A LAYOUT effect, not a passive one: `setMenu` runs in the runtime's
    // maplibre handler, and React flushes passive effects on a later macrotask
    // than the one that opened the menu — long enough for a keypress to land,
    // arm a draw, and find no surface to close, which is the very gap this
    // closes. Layout effects run inside the same flush, before any later input
    // event.
    useLayoutEffect(() => {
        if (!menu) return;
        return openSurface({ id: "map-context-menu", close });
    }, [menu, close]);

    // --- Actions ---

    const handleEdit = useCallback(() => {
        if (!menu?.element) return;
        close();
        startEdit(menu.element, false);
    }, [menu, close]);

    // Historical counterpart to Edit: select the element so the read-only
    // detail panel opens, without entering the (disabled) edit tool.
    const handleViewDetails = useCallback(() => {
        if (!menu?.element) return;
        close();
        mapManager.selectElement(menu.element);
    }, [menu, close, mapManager]);

    const handleDelete = useCallback(() => {
        if (!menu?.element) return;
        const element = menu.element;
        close();

        if (element.source === localEventSource) {
            // Routed through the element actions so `LocalSource` (which owns the
            // persisted row) performs the delete, rather than writing the store directly.
            elementActions.delete(element.key);
            mapManager.handleEmptyClick();
            showToast({
                type: "success",
                title: t("contextMenu.featureDeleted"),
            });
        } else {
            // Broadcast element — select it so events panel can handle deletion
            mapManager.selectElement(element);
            showToast({
                type: "info",
                title: t("contextMenu.useDeleteButton"),
            });
        }
    }, [menu, close, mapManager, elementActions, showToast, t]);

    const handleCopyCoordinates = useCallback(async () => {
        if (!menu) return;
        close();

        // Always copy the mouse click coordinates, regardless of feature
        const { lng, lat } = menu.lngLat;

        const text = formatCoord(lat, lng, format);
        try {
            await navigator.clipboard.writeText(text);
            showToast({
                type: "success",
                title: t("coordinates.copied"),
            });
        } catch {
            showToast({
                type: "error",
                title: t("coordinates.copyError"),
            });
        }
    }, [menu, close, format, showToast, t]);

    // Camera-fit computation lives in element-focus.util.ts, shared with the
    // events-list "locate this element" focus button — extracted here first
    // so that button didn't become a second copy of the same bbox/fallback
    // logic.
    const handleZoomToFeature = useCallback(() => {
        if (!menu?.element) return;
        close();
        focusOnElement(menu.element, { flyTo, fitBounds });
    }, [menu, close, flyTo, fitBounds]);

    if (!menu) return null;

    const { element, editability } = menu;
    const hasFeature = !!element;
    // Historical features are read-only regardless of source permissions (a
    // historical event from a layer you own would otherwise resolve as
    // editable), so fold the mode into the capability flags here rather than
    // repeating it on each menu item. Edit is replaced by "View details" below.
    const canEdit = !isHistorical && (editability?.canEditGeometry ?? false);
    const canDelete = !isHistorical && (editability?.canDelete ?? false);
    const snapshot: ContextMenuSnapshot = { lngLat: [menu.lngLat.lng, menu.lngLat.lat], element };

    return (
        <Menu.RootProvider value={contextMenu}>
            <Portal>
                <Menu.Positioner>
                    <Menu.Content
                        zIndex="popover"
                        bg="slate.popover"
                        borderRadius="md"
                        boxShadow="lg"
                        py="1"
                        minW="180px"
                        overflow="hidden"
                    >
                        <ContextMenuItem
                            value="view-details"
                            onSelect={handleViewDetails}
                            hidden={!hasFeature || !isHistorical}
                        >
                            {t("contextMenu.viewDetails")}
                        </ContextMenuItem>
                        <ContextMenuItem
                            value="edit"
                            onSelect={handleEdit}
                            hidden={!hasFeature || !canEdit}
                        >
                            {t("contextMenu.edit")}
                        </ContextMenuItem>
                        <ContextMenuItem
                            value="delete"
                            onSelect={handleDelete}
                            hidden={!hasFeature || !canDelete}
                        >
                            {t("contextMenu.delete")}
                        </ContextMenuItem>
                        <ContextMenuItem value="copy-coordinates" onSelect={handleCopyCoordinates}>
                            {t("contextMenu.copyCoordinates")}
                        </ContextMenuItem>
                        <ContextMenuItem
                            value="zoom-to-feature"
                            onSelect={handleZoomToFeature}
                            hidden={!hasFeature}
                        >
                            {t("contextMenu.zoomToFeature")}
                        </ContextMenuItem>
                        {contributed.map((item) =>
                            "Content" in item ? (
                                <item.Content key={item.id} target={snapshot} close={close} />
                            ) : (
                                <ContributedAction
                                    key={item.id}
                                    item={item}
                                    snapshot={snapshot}
                                    close={close}
                                />
                            ),
                        )}
                    </Menu.Content>
                </Menu.Positioner>
            </Portal>
        </Menu.RootProvider>
    );
}

/** Menu item row for the map context menu. */
function ContextMenuItem({
    children,
    onSelect,
    hidden,
    value,
}: {
    children: ReactNode;
    onSelect: () => void | Promise<void>;
    hidden?: boolean;
    value: string;
}) {
    if (hidden) return null;
    return (
        <Menu.Item
            value={value}
            px="3"
            py="1.5"
            fontSize="sm"
            color="fg.muted"
            _highlighted={{ bg: "whiteAlpha.200" }}
            onSelect={onSelect}
        >
            {children}
        </Menu.Item>
    );
}

/**
 * A contributed item that starts a tool: hidden when the tool is not registered;
 * selecting it closes the menu and activates the tool, at the right-click when
 * the item asks.
 */
function ContributedAction({
    item,
    snapshot,
    close,
}: {
    item: Extract<ContextMenuItemContribution, { activate: unknown }>;
    snapshot: ContextMenuSnapshot;
    close: () => void;
}) {
    const { t } = useTranslation();
    const { toolId, placeAt } = item.activate;
    if (!getTool(toolId)) {
        reportMissingTool(item, toolId);
        return null;
    }
    return (
        <ContextMenuItem
            value={item.id}
            onSelect={() => {
                close();
                const [lng, lat] = snapshot.lngLat;
                activate(toolId, placeAt ? { placeAt: [lng, lat] } : {});
            }}
        >
            {t(item.labelKey as never)}
        </ContextMenuItem>
    );
}
