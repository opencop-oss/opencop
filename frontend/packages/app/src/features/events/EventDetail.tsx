// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Flex, Spinner, Text } from "@chakra-ui/react";
import { Breadcrumbs } from "@opencop/ui";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState, type ReactElement, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { ErrorBoundaryWrapper } from "~/features/errors/ErrorBoundaryWrapper";
import { useHistoricalMode } from "~/features/historical/data-plane/historical.store";
import { useEditability } from "~/features/map/use-editability.hook";
import { useClearPickedElement } from "~/features/map/use-picked-element.hook";
import { useIsEditing } from "~/features/map/interaction-runtime/session/selectors";
import { EventsHeader } from "./EventsHeader";
import { EventsTabs, type EventsTab } from "./EventsTabs";
import { PushMenu } from "./PushMenu";
import { DeleteDialog } from "./detail/DeleteDialog";
import { EditButton } from "./detail/EditButton";
import { EventEditForm } from "./sections/EventEditForm";
import { HistorySection } from "./sections/HistorySection";
import { InformationSection } from "./sections/InformationSection";
import { MediaSection } from "./sections/MediaSection";
import { RemarksSection } from "./sections/RemarksSection";
import { useEventDetails } from "./use-event-details.hook";
import type { Editability, EditabilityReason } from "~/features/map/editability";
import { eventQueries, selectUrlAttachments } from "./events.queries";
import { useEventsNavigationStore } from "./events-navigation.store";
import { DELETE_DISABLED_REASON_KEYS } from "./delete-disabled-reason.const";

const EDIT_TOOLTIP_BY_REASON: Partial<Record<EditabilityReason, string>> = {
    dataSourceReadOnly: "events.editDisabledDataSource",
    insufficientLayerRole: "events.editDisabledInsufficientRole",
    noPermission: "events.editDisabledNoPermission",
};

function getEditTooltip(editability: Editability, fallback: string): string {
    if (editability.canEditProperties) return fallback;
    return editability.reason ? (EDIT_TOOLTIP_BY_REASON[editability.reason] ?? fallback) : fallback;
}

function getDeleteTooltip(editability: Editability, fallback: string): string {
    if (editability.canDelete) return fallback;
    return editability.reason
        ? (DELETE_DISABLED_REASON_KEYS[editability.reason] ?? fallback)
        : fallback;
}

/** Renders the selected event's detail view. Callers must only mount this when
 *  an element is actually selected (see EventsPanel). */
export function EventDetail(): ReactElement {
    const { t } = useTranslation();
    const { details, geometry, element, hasElement, loading, error } = useEventDetails();
    const isCustodyEdit = useIsEditing();
    const [activeTab, setActiveTab] = useState<EventsTab>("information");
    // null for a raw map-feature pick — there's no list to go back to, so no
    // breadcrumb. See MapManager.selectElement.
    const listOrigin = useEventsNavigationStore((s) => s.listOrigin);
    const clearSelection = useClearPickedElement();

    const handleBackToList = () => {
        // Keep the appropriate list surface explicit while dropping the
        // drilled-in selection. Search is the default; lasso is the alternate.
        useEventsNavigationStore
            .getState()
            .showList(listOrigin === "selection" ? "selection" : "search");
        clearSelection();
    };

    const editability = useEditability(details?.source);

    // Historical events are read-only snapshots: no editing, no media/remarks/
    // history tabs (those REST-fetch the *latest* version, which would
    // contradict the historical version shown). Show core details only.
    const isHistorical = useHistoricalMode() === "historical";

    // Editing state derived directly from tool mode.
    // Transitions are atomic — the interaction commands update both the session and
    // eventsEditingStore synchronously, so no effect sync is needed.
    const isEditing = isCustodyEdit && !isHistorical;

    // Edit actions (Edit/Push/Delete) and the tab bar only show for live,
    // non-editing views. Historical views are read-only Information only.
    const showEditActions = !isEditing && !isHistorical;

    const isLocalEvent = details?.source === "local";

    const { data: urlAttachments = [] } = useQuery({
        ...eventQueries.allAttachments(details?.source ?? "", details?.objectId ?? ""),
        select: selectUrlAttachments,
        enabled: !!details && !isLocalEvent && !isHistorical && activeTab === "information",
        refetchInterval: false,
    });

    // Error state — REST fetch failed and no live element to fall back on.
    // When we have a live element (e.g. right after broadcast, before the
    // server indexes the event), skip the error gate so the header and core
    // details still render from the WS element.
    if (error && !hasElement) {
        return (
            <Flex
                direction="column"
                align="center"
                justify="center"
                gap="3"
                height="100%"
                width="100%"
            >
                <Text fontSize="lg" fontWeight={600} color="red.400">
                    {t("eventsContext.failedToLoad")}
                </Text>
                <Text fontSize="sm" opacity={0.6} textAlign="center">
                    {error.message || t("eventsContext.failedToLoadDescription")}
                </Text>
            </Flex>
        );
    }

    // Loading: waiting for initial data (REST or throttled WS details).
    // When `details` is already populated from the WS element, skip the
    // loading gate so the header and action buttons (Edit/Delete) render
    // immediately — they don't depend on REST metadata.
    if (!details && (loading || hasElement)) {
        return (
            <Flex
                direction="column"
                align="center"
                justify="center"
                gap="3"
                height="100%"
                width="100%"
            >
                <Spinner size="lg" />
                <Text fontSize="sm" opacity={0.6} mt={2}>
                    {t("eventsContext.loadingDetails")}
                </Text>
            </Flex>
        );
    }

    // No live element: either URL-restore (WS hasn't delivered yet) or the
    // element was evicted from the store (expired). Show spinner briefly,
    // then fall through to the "not available" message after a timeout.
    if (!hasElement || !details || !element) {
        return <WaitingForElement />;
    }

    // Push sends a remote point event that carries a code (a symbol) to another
    // system; the rule names no element type.
    const canPush = details.source !== "local" && geometry?.type === "Point" && !!element.getCode();

    return (
        <Flex direction="column" height="100%" width="100%" overflowY="hidden" overflow="visible">
            {listOrigin !== null && (
                <Breadcrumbs
                    px={0}
                    items={[
                        { label: t("selectionPanel.backToList"), href: "#" },
                        { label: details.displayName || details.objectId.slice(0, 8) },
                    ]}
                    renderLink={({ children }: { href: string; children: ReactNode }) => (
                        <Button
                            type="button"
                            variant="plain"
                            alignItems="center"
                            gap={1}
                            p={0}
                            minW="auto"
                            h="auto"
                            fontSize="sm"
                            color="fg"
                            fontWeight="semibold"
                            cursor="pointer"
                            textDecoration="underline"
                            textUnderlineOffset="3px"
                            _hover={{ color: "active" }}
                            display="inline-flex"
                            onClick={handleBackToList}
                        >
                            {children}
                        </Button>
                    )}
                />
            )}
            {/* Action Buttons */}
            <Box mb="6" pos="relative">
                <Flex gap={1} pos="absolute" top="0" right="0">
                    {showEditActions && (
                        <>
                            <EditButton
                                disabled={!editability.canEditProperties}
                                disabledReason={
                                    !editability.canEditProperties
                                        ? t(getEditTooltip(editability, "events.edit") as never)
                                        : undefined
                                }
                                showDataSourceWarning={editability.reason === "dataSourceOwner"}
                            />
                            {canPush ? <PushMenu /> : null}
                            <DeleteDialog
                                disabled={!editability.canDelete}
                                disabledReason={
                                    !editability.canDelete
                                        ? t(getDeleteTooltip(editability, "common.delete") as never)
                                        : undefined
                                }
                                showDataSourceWarning={editability.reason === "dataSourceOwner"}
                                mutationTarget={editability.mutationTarget}
                            />
                        </>
                    )}
                </Flex>
                <EventsHeader details={details} element={element} />
                {showEditActions && <EventsTabs activeTab={activeTab} onTabChange={setActiveTab} />}
            </Box>

            {isEditing ? (
                <Flex
                    direction="column"
                    w="full"
                    flex={1}
                    px="2"
                    overflow="visible"
                    overflowY="auto"
                    borderRadius="sm"
                >
                    <EventEditForm details={details} editability={editability} />
                </Flex>
            ) : (
                <Flex
                    direction="column"
                    w="full"
                    flex={1}
                    px="2"
                    overflow="visible"
                    overflowY="auto"
                    borderRadius="sm"
                >
                    {(isHistorical || activeTab === "information") && (
                        <ErrorBoundaryWrapper componentName="InformationSection">
                            <InformationSection
                                details={details}
                                geometry={geometry}
                                element={element}
                                urlAttachments={urlAttachments}
                            />
                        </ErrorBoundaryWrapper>
                    )}
                    {!isHistorical && activeTab === "media" && (
                        <ErrorBoundaryWrapper componentName="MediaSection">
                            <MediaSection details={details} />
                        </ErrorBoundaryWrapper>
                    )}
                    {!isHistorical && activeTab === "remarks" && (
                        <RemarksSection key={details.objectId} details={details} />
                    )}
                    {!isHistorical && activeTab === "history" && (
                        <ErrorBoundaryWrapper componentName="HistorySection">
                            <HistorySection />
                        </ErrorBoundaryWrapper>
                    )}
                </Flex>
            )}
        </Flex>
    );
}

/** Shows a spinner while waiting for the WS element; falls back to an
 *  "event not available" message after 10 seconds. */
const WAIT_TIMEOUT_MS = 10_000;

function WaitingForElement() {
    const { t } = useTranslation();
    const [timedOut, setTimedOut] = useState(false);

    useEffect(() => {
        const id = setTimeout(() => setTimedOut(true), WAIT_TIMEOUT_MS);
        return () => clearTimeout(id);
    }, []);

    return (
        <Flex direction="column" align="center" justify="center" gap="3" height="100%" width="100%">
            {timedOut ? (
                <>
                    <Text fontSize="lg" fontWeight={600} opacity={0.6}>
                        {t("eventsContext.eventExpired")}
                    </Text>
                    <Text fontSize="sm" opacity={0.4} textAlign="center">
                        {t("eventsContext.eventExpiredDescription")}
                    </Text>
                </>
            ) : (
                <>
                    <Spinner size="lg" />
                    <Text fontSize="sm" opacity={0.6} mt={2}>
                        {t("eventsContext.loadingDetails")}
                    </Text>
                </>
            )}
        </Flex>
    );
}
