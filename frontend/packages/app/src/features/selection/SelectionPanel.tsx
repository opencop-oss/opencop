// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Badge, Box, Checkbox, Flex, Tabs, Text } from "@chakra-ui/react";
import { PiLasso } from "react-icons/pi";
import { useMemo, useRef, useState, type ReactNode } from "react";
import type { ParseKeys } from "i18next";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { getElementKey, localEventSource, type Element } from "@opencop/elements";
import { getErrorMessage, downloadExport, type MyLayerInfo } from "@opencop/api";
import {
    clearAreaSelection,
    useAreaSelectionStore,
} from "~/features/selection/area-selection.store";
import { useMapManager } from "~/features/map/MapManagerContext";
import { useViewActions } from "~/features/map/core/slices/view/view.hook";
import { exportMutations } from "~/features/exports/exports.queries";
import { buildElementExportRequest } from "~/features/exports/exports.util";
import { useToast } from "~/features/notifications/use-toast.hook";
import { EventListItem, type EventListItemContainer } from "~/features/events/EventListItem";
import { EventsListLayout } from "~/features/events/EventsListLayout";
import { focusElementFromList } from "~/features/events/events-navigation.store";
import { formatTimestamp } from "~/features/events/helpers.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { selectionCheckboxControlProps } from "./selection-checkbox-control.const";
import { TemporarySelectionPanelToolbar } from "./TemporarySelectionPanelToolbar";
import { useBulkCopyToLayer } from "./use-bulk-copy-to-layer.hook";
import { useBulkEventDelete } from "./use-bulk-event-delete.hook";
import { ConfirmDialog, Tooltip, VirtualList } from "@opencop/ui";
import { DELETE_DISABLED_REASON_KEYS } from "~/features/events/delete-disabled-reason.const";
import { CopyToLayerDialog } from "./CopyToLayerDialog";
import { ReadOnlyTabNotice } from "./ReadOnlyTabNotice";
import { getSelectionContainer } from "./selection-access.util";
import {
    useSelectionEditability,
    type SelectionPermissionStatus,
} from "./use-selection-editability.hook";
import { getCopyTargetLayers } from "./copy-to-layer.util";

//~32px icon + 2x20px text lines + 16px vertical padding.
const ROW_HEIGHT = 88;
const ROW_GAP = 6;

// Stable reference so `?? EMPTY_ELEMENTS` doesn't invalidate memoization every
// render while there's no selection (a fresh `[]` literal would).
const EMPTY_ELEMENTS: Element[] = [];

const ACCESS_TABS = ["all", "editable", "readOnly"] as const;
/** Which slice of the selection the list shows; every toolbar action applies only to that slice. */
type AccessTab = (typeof ACCESS_TABS)[number];

function isAccessTab(value: string): value is AccessTab {
    return (ACCESS_TABS as readonly string[]).includes(value);
}

interface EventCardProps {
    element: Element;
    index: number;
    isChecked: boolean;
    /** Layer or data source shown after the Copy ID button. */
    container?: EventListItemContainer;
    /** Set when the user can't delete this element: the i18n key explaining why. */
    readOnlyReasonKey?: ParseKeys;
    onToggleChecked: (key: string) => void;
    /** Row click — selects and slides Events over to the Detail view (via onFocus prop, below). */
    onFocus: (element: Element) => void;
    /** Location-pin button click — selects + pans the camera, but stays on this list (no Detail navigation). */
    onFocusOnMap: (element: Element) => void;
}

function EventCard({
    element,
    index,
    isChecked,
    container,
    readOnlyReasonKey,
    onToggleChecked,
    onFocus,
    onFocusOnMap,
}: EventCardProps): ReactNode {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const key = getElementKey(element);
    const timestamp = element.time != null ? formatTimestamp(element.time, timezone) : undefined;

    const selectionCheckbox = (
        <Checkbox.Root
            size="sm"
            checked={isChecked}
            aria-label={t("selectionPanel.selectItem", { name: element.getDisplayName() })}
            onCheckedChange={() => onToggleChecked(key)}
        >
            <Checkbox.HiddenInput />
            <Checkbox.Control {...selectionCheckboxControlProps}>
                <Checkbox.Indicator />
            </Checkbox.Control>
        </Checkbox.Root>
    );

    const readOnlyBadge = readOnlyReasonKey && (
        <Tooltip content={t(readOnlyReasonKey)} uppercase={false}>
            {/* The span is the focusable trigger, so Tab reaches it and the tooltip renders from
                the keyboard. Its aria-label keeps the visible "Read-only" text (label-in-name)
                and adds the reason, since Ark only sets aria-describedby once focused. */}
            <Box
                as="span"
                tabIndex={0}
                display="inline-flex"
                aria-label={`${t("selectionPanel.readOnlyBadge")}: ${t(readOnlyReasonKey)}`}
            >
                <Badge size="xs" h="5" variant="outline" colorPalette="orange" flexShrink={0}>
                    {t("selectionPanel.readOnlyBadge")}
                </Badge>
            </Box>
        </Tooltip>
    );

    return (
        <EventListItem
            element={element}
            index={index}
            onClick={() => onFocus(element)}
            onFocusOnMap={() => onFocusOnMap(element)}
            detail={timestamp}
            container={container}
            leading={selectionCheckbox}
            actions={readOnlyBadge}
        />
    );
}

interface PermissionDisabledMessages {
    /** Shown while the permission lookup is still running. */
    checking: string;
    /** Shown when the permission lookup failed. */
    unavailable: string;
    /** Shown when `blockedCount` checked items can't take the action. */
    blocked: string;
    blockedCount: number;
}

/**
 * Why a bulk action over the checked items can't run yet, or `undefined` when permissions allow
 * it. Nothing is wrong until something is checked.
 */
function getPermissionDisabledReason(
    hasChecked: boolean,
    status: SelectionPermissionStatus,
    messages: PermissionDisabledMessages,
): string | undefined {
    if (!hasChecked) return undefined;
    if (status === "pending") return messages.checking;
    if (status === "error") return messages.unavailable;
    return messages.blockedCount > 0 ? messages.blocked : undefined;
}

interface SelectionPanelProps {
    /** Called with the picked element when a row is clicked/focused. The
     *  caller owns recording where the pick came from (see selectFromList in
     *  events-navigation.store.ts) and delegating to MapManager. */
    onFocus: (element: Element) => void;
    /** Called when the user cancels the area selection. The caller owns
     *  whatever container-level panel state needs to close. */
    onClose: () => void;
    /** Element to focus after a delete removes the whole selection. That
     *  unmounts SelectionPanel (and its confirm dialog) rather than closing
     *  the dialog first, so focus can't return to a control inside the panel —
     *  the caller must supply one that survives, such as the panel's own
     *  toggle button. Not used when the selection outlives the dialog. */
    finalFocusEl?: () => HTMLElement | null;
}

export function SelectionPanel({ onFocus, onClose, finalFocusEl }: SelectionPanelProps): ReactNode {
    "use no memo";
    const { t } = useTranslation();
    const { showToast } = useToast();
    const mapManager = useMapManager();
    const viewActions = useViewActions();

    const selectedElements = useAreaSelectionStore((s) => s.elements) ?? EMPTY_ELEMENTS;
    const boundary = useAreaSelectionStore((s) => s.boundary);
    const checkedKeys = useAreaSelectionStore((s) => s.checkedKeys);
    const toggleChecked = useAreaSelectionStore((s) => s.toggleChecked);
    const setChecked = useAreaSelectionStore((s) => s.setChecked);

    const {
        editability,
        permissionStatus: accessStatus,
        myLayers,
        myDataSources,
    } = useSelectionEditability(selectedElements);
    const accessReady = accessStatus === "ready";

    const [tab, setTab] = useState<AccessTab>("all");
    // A new lasso starts over on the All tab. Keyed on the boundary rather than the elements,
    // which also change when deletes prune them. Adjusted during render rather than by a `key`
    // on the panel, which would also remount the confirm dialog and the export mutation.
    const [lassoBoundary, setLassoBoundary] = useState(boundary);
    if (lassoBoundary !== boundary) {
        setLassoBoundary(boundary);
        setTab("all");
    }
    // Until permissions resolve nothing can be sorted into Can edit / Read-only.
    const activeTab: AccessTab = accessReady ? tab : "all";

    const { editableElements, readOnlyElements } = useMemo(() => {
        const editable: Element[] = [];
        const readOnly: Element[] = [];
        for (const element of selectedElements) {
            if (editability.get(getElementKey(element))?.canDelete) editable.push(element);
            else readOnly.push(element);
        }
        return { editableElements: editable, readOnlyElements: readOnly };
    }, [editability, selectedElements]);

    const visibleElements =
        activeTab === "editable"
            ? editableElements
            : activeTab === "readOnly"
              ? readOnlyElements
              : selectedElements;

    const checkedElements = useMemo(
        () => visibleElements.filter((e) => checkedKeys.has(getElementKey(e))),
        [visibleElements, checkedKeys],
    );

    const { blockedElements, dataSourceDeleteCount, isDeleting, deleteSelected } =
        useBulkEventDelete(checkedElements, { editability, permissionStatus: accessStatus });
    const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);
    const {
        blockedElements: copyBlockedElements,
        countStale,
        countFreshByLayer,
        allStale: copyAllStale,
        isCopying,
        copySelected,
    } = useBulkCopyToLayer(checkedElements, { editability, permissionStatus: accessStatus });
    const copyTargetLayers = useMemo(() => getCopyTargetLayers(myLayers), [myLayers]);
    const [copyDialogOpen, setCopyDialogOpen] = useState(false);
    const [copyDialogKey, setCopyDialogKey] = useState(0);
    // Counted when the dialog opens, by calling the hook's count functions, so render doesn't read
    // the clock and the copy unticking what it copied doesn't shrink the count mid-copy.
    const [copyCounts, setCopyCounts] = useState<{
        total: number;
        stale: number;
        freshByLayer: Record<string, number>;
    }>({ total: 0, stale: 0, freshByLayer: {} });
    // The confirm message is a snapshot taken when the dialog opens: each wave's delete echo
    // prunes the selection while the delete runs, so reading it live would shrink the count.
    const [confirmCounts, setConfirmCounts] = useState({ total: 0, dataSource: 0 });
    const rootRef = useRef<HTMLDivElement>(null);
    // Set when a confirmed delete closes the dialog, so the close can tell it apart from Cancel.
    const deleteRanRef = useRef(false);
    const queryClient = useQueryClient();
    const { mutate: exportCsv, isPending } = useMutation({
        ...exportMutations.create(queryClient),
        onSuccess: (response) => {
            try {
                downloadExport(response);
            } catch (err) {
                showToast({
                    title: t("selectionPanel.exportError"),
                    description: getErrorMessage(err),
                    type: "error",
                });
            }
        },
        onError: (err) => {
            showToast({
                title: t("selectionPanel.exportError"),
                description: getErrorMessage(err),
                type: "error",
            });
        },
    });

    if (selectedElements.length === 0) {
        return null;
    }

    const handleCancel = () => {
        mapManager.clearSelection();
        clearAreaSelection();
        onClose();
    };

    const handleConfirmDelete = () => {
        void (async () => {
            await deleteSelected();
            deleteRanRef.current = true;
            // A delete that empties the active tab moves the panel to All, so the user isn't
            // left on a blank list. Read the store: this render's lists predate the delete.
            const remaining = useAreaSelectionStore.getState().elements ?? [];
            const tabHasRows = remaining.some((element) => {
                if (activeTab === "all") return true;
                const canDelete = editability.get(getElementKey(element))?.canDelete ?? false;
                return activeTab === "editable" ? canDelete : !canDelete;
            });
            if (!tabHasRows) setTab("all");
            setConfirmDeleteOpen(false);
        })();
    };

    // Where focus goes when the confirm dialog closes. Cancel returns it to Delete Selected
    // (the dialog's default); a delete leaves Delete Selected disabled, so focus goes to the
    // selected tab; a delete that removed the whole selection unmounts this panel, so focus
    // goes to the caller's element.
    const getFinalFocusEl = (): HTMLElement | null => {
        const deleteRan = deleteRanRef.current;
        deleteRanRef.current = false;
        if (!useAreaSelectionStore.getState().elements) return finalFocusEl?.() ?? null;
        if (!deleteRan) return null;
        return (
            rootRef.current?.querySelector<HTMLElement>('[role="tab"][aria-selected="true"]') ??
            null
        );
    };

    // e.id is the per-version UUID (not objectId). The /exports endpoint expects
    // these exact version IDs, and intentionally accepts the narrow race where a
    // concurrent WS update supersedes a version between selection and export.
    const checkedIds = checkedElements.map((e) => e.id);
    const allSelected = visibleElements.length > 0 && checkedIds.length === visibleElements.length;
    const hasChecked = checkedElements.length > 0;
    const deleteDisabledReason = getPermissionDisabledReason(hasChecked, accessStatus, {
        checking: t("selectionPanel.checkingDeletePermissions"),
        unavailable: t("selectionPanel.deletePermissionsUnavailable"),
        blocked: t("selectionPanel.deleteBlocked", { count: blockedElements.length }),
        blockedCount: blockedElements.length,
    });
    const copyDisabledReason =
        getPermissionDisabledReason(hasChecked, accessStatus, {
            checking: t("selectionPanel.checkingCopyPermissions"),
            unavailable: t("selectionPanel.copyPermissionsUnavailable"),
            blocked: t("selectionPanel.copyBlocked", { count: copyBlockedElements.length }),
            blockedCount: copyBlockedElements.length,
        }) ??
        (hasChecked && copyTargetLayers.length === 0
            ? t("selectionPanel.copyNoTargetLayers")
            : copyAllStale
              ? t("selectionPanel.copyAllStale", { count: checkedElements.length })
              : undefined);

    const handleOpenCopyDialog = () => {
        setCopyCounts({
            total: checkedElements.length,
            stale: countStale(),
            freshByLayer: countFreshByLayer(),
        });
        // A new key remounts the dialog, so it opens with no destination chosen.
        setCopyDialogKey((key) => key + 1);
        setCopyDialogOpen(true);
    };

    const handleConfirmCopy = (target: MyLayerInfo) => {
        void (async () => {
            const result = await copySelected(target);
            // Another copy is already running and will close the dialog when it finishes.
            if (result === "busy") return;
            // A guard stopped the copy (the selection or permissions changed under the dialog):
            // say so rather than closing as if it had worked.
            if (result === "skipped") {
                showToast({
                    type: "warning",
                    title: t("selectionPanel.copyNothingTitle"),
                    description: t("selectionPanel.copyNotRunDescription"),
                });
            }
            setCopyDialogOpen(false);
        })();
    };

    // Copying unchecks what it copied, so a copy that covered every checked item leaves Copy
    // disabled; focus then goes to the selected tab rather than to a disabled button. Scoped to
    // the visible tab: ticks on the other tab don't keep Copy enabled here.
    const getCopyFinalFocusEl = (): HTMLElement | null => {
        const { checkedKeys: liveCheckedKeys } = useAreaSelectionStore.getState();
        const copyStillAvailable = visibleElements.some((e) =>
            liveCheckedKeys.has(getElementKey(e)),
        );
        return copyStillAvailable
            ? null
            : (rootRef.current?.querySelector<HTMLElement>('[role="tab"][aria-selected="true"]') ??
                  null);
    };

    const handleToggleSelectAll = () => {
        setChecked(visibleElements.map(getElementKey), !allSelected);
    };

    const tabs: { value: AccessTab; label: string; count: number }[] = [
        {
            value: "editable",
            label: t("selectionPanel.tabCanEdit"),
            count: editableElements.length,
        },
        {
            value: "readOnly",
            label: t("selectionPanel.tabReadOnly"),
            count: readOnlyElements.length,
        },
        { value: "all", label: t("selectionPanel.tabAll"), count: selectedElements.length },
    ];

    const list = (
        <VirtualList
            items={visibleElements}
            estimateSize={() => ROW_HEIGHT + ROW_GAP}
            gap={ROW_GAP}
            getItemKey={getElementKey}
            renderItem={(element, index) => {
                const key = getElementKey(element);
                const container =
                    accessReady && element.source !== localEventSource
                        ? getSelectionContainer(element.source, myLayers, myDataSources)
                        : undefined;
                const access = editability.get(key);
                const readOnlyReasonKey =
                    access && !access.canDelete
                        ? ((access.reason && DELETE_DISABLED_REASON_KEYS[access.reason]) ??
                          "events.deleteDisabledNoPermission")
                        : undefined;
                return (
                    <EventCard
                        element={element}
                        index={index}
                        isChecked={checkedKeys.has(key)}
                        container={container}
                        readOnlyReasonKey={readOnlyReasonKey}
                        onToggleChecked={toggleChecked}
                        onFocus={onFocus}
                        onFocusOnMap={(el) => focusElementFromList(mapManager, el, viewActions)}
                    />
                );
            }}
            containerProps={{ flex: 1, minH: "0" }}
        />
    );

    const layout = (
        <EventsListLayout
            controls={
                <>
                    <Flex align="center" gap="2" color="fg.muted" fontSize="xs">
                        <PiLasso aria-hidden />
                        <Text>{t("selectionPanel.lassoSelection")}</Text>
                    </Flex>
                    {accessReady && (
                        <Tabs.List
                            aria-label={t("selectionPanel.accessTabs")}
                            borderBottomColor="border.muted"
                        >
                            {tabs.map(({ value, label, count }) => (
                                <Tabs.Trigger
                                    key={value}
                                    value={value}
                                    gap="2"
                                    fontSize="xs"
                                    px="3"
                                >
                                    {label}
                                    <Badge size="xs" variant="subtle" fontFamily="mono">
                                        {count}
                                    </Badge>
                                </Tabs.Trigger>
                            ))}
                        </Tabs.List>
                    )}
                    <TemporarySelectionPanelToolbar
                        selectedCount={checkedIds.length}
                        totalCount={visibleElements.length}
                        allSelected={allSelected}
                        isExporting={isPending}
                        deleteDisabled={
                            checkedElements.length === 0 || deleteDisabledReason !== undefined
                        }
                        deleteDisabledReason={deleteDisabledReason}
                        isDeleting={isDeleting}
                        copyDisabled={
                            checkedElements.length === 0 || copyDisabledReason !== undefined
                        }
                        copyDisabledReason={copyDisabledReason}
                        isCopying={isCopying}
                        onToggleSelectAll={handleToggleSelectAll}
                        onExport={() => exportCsv(buildElementExportRequest(checkedIds))}
                        onDeleteSelected={() => {
                            setConfirmCounts({
                                total: checkedElements.length,
                                dataSource: dataSourceDeleteCount,
                            });
                            setConfirmDeleteOpen(true);
                        }}
                        onCopySelected={handleOpenCopyDialog}
                        onCancel={handleCancel}
                    />
                    {activeTab === "readOnly" && readOnlyElements.length > 0 && (
                        <ReadOnlyTabNotice />
                    )}
                </>
            }
        >
            {accessReady ? (
                // The list is the tab panel: one Content for whichever tab is active, so
                // the selected tab's aria-controls always resolves to a rendered panel.
                <Tabs.Content
                    value={activeTab}
                    display="flex"
                    flexDirection="column"
                    flex="1"
                    minH="0"
                    p="0"
                >
                    {list}
                </Tabs.Content>
            ) : (
                list
            )}

            <CopyToLayerDialog
                key={copyDialogKey}
                open={copyDialogOpen}
                onClose={() => setCopyDialogOpen(false)}
                targetLayers={copyTargetLayers}
                count={copyCounts.total}
                staleCount={copyCounts.stale}
                freshCountByLayer={copyCounts.freshByLayer}
                disabledReason={copyDisabledReason}
                isCopying={isCopying}
                onConfirm={handleConfirmCopy}
                finalFocusEl={getCopyFinalFocusEl}
            />

            <ConfirmDialog
                open={confirmDeleteOpen}
                onClose={() => setConfirmDeleteOpen(false)}
                onConfirm={handleConfirmDelete}
                // Permissions can change while the dialog is open (membership refetch); a
                // blocked delete would otherwise close the dialog without deleting anything.
                disabled={deleteDisabledReason !== undefined}
                title={t("selectionPanel.confirmDeleteTitle")}
                message={
                    <>
                        <Text>
                            {t("selectionPanel.confirmDeleteMessage", {
                                count: confirmCounts.total,
                            })}
                        </Text>
                        {deleteDisabledReason !== undefined && (
                            <Text mt={2} fontSize="sm" color="fg.error">
                                {deleteDisabledReason}
                            </Text>
                        )}
                        {confirmCounts.dataSource > 0 && (
                            <Text mt={2} fontSize="sm" color="fg.warning">
                                {t("selectionPanel.confirmDeleteDataSourceNote", {
                                    count: confirmCounts.dataSource,
                                })}
                            </Text>
                        )}
                    </>
                }
                loading={isDeleting}
                dismissDuringLoading={false}
                finalFocusEl={getFinalFocusEl}
            />
        </EventsListLayout>
    );

    return (
        <Tabs.Root
            ref={rootRef}
            size="sm"
            variant="line"
            value={activeTab}
            onValueChange={(e) => {
                if (isAccessTab(e.value)) setTab(e.value);
            }}
            display="flex"
            flexDirection="column"
            width="100%"
            height="100%"
            minH="0"
        >
            {layout}
        </Tabs.Root>
    );
}
