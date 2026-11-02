// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Box,
    Button,
    Flex,
    HStack,
    Icon,
    IconButton,
    Menu,
    Portal,
    Spinner,
    Text,
    VStack,
} from "@chakra-ui/react";
import { Breadcrumbs, DetailTable, RefreshButton, Tooltip, type DetailTableRow } from "@opencop/ui";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSpinDelay } from "spin-delay";
import { useEffect, useMemo, useRef, useState, type ReactNode, type RefObject } from "react";
import { useTranslation } from "react-i18next";
import { FaBellSlash } from "react-icons/fa";
import { FaEllipsisVertical } from "react-icons/fa6";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { layerQueries } from "~/features/layers/layers.queries";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { formatDateMilitary } from "~/date.util";
import { useSelectAndFocusElement } from "~/features/map/use-select-and-focus-element.hook";
import { WatchboxDetailTabs, type WatchboxDetailTab } from "./WatchboxDetailTabs";
import { WatchboxForm } from "./WatchboxForm";
import { WatchboxSection } from "./WatchboxSection";
import { WatchboxStatusBadges } from "./WatchboxStatusBadges";
import { WatchboxTitle } from "./WatchboxTitle";
import { PredicateSummary, resolveFilter, summarizePredicate, useFilterFields } from "@opencop/ui";
import { watchboxQueries } from "./watchbox.queries";
import { watchboxErrorMessageKey } from "./watchbox-error.util";
import { parseWatchboxError } from "./watchbox.api";
import type { Watchbox, WatchboxDirection } from "./watchbox.model";
import type { WatchboxAlertEventKind, WatchboxDetection } from "./watchbox.api";

/** Spelled out rather than interpolated: a template-literal key is invisible to the extractor. */
const directionKey = {
    ENTER: "watchbox.directionEnter",
    LEAVE: "watchbox.directionLeave",
    BOTH: "watchbox.directionBoth",
} as const satisfies Record<WatchboxDirection, string>;

/** Spelled out rather than interpolated: a template-literal key is invisible to the extractor. */
const notificationKindKey = {
    ENTER: "watchbox.detail.notificationEnter",
    LEAVE: "watchbox.detail.notificationLeave",
    FILTER_MATCH_GAINED: "watchbox.detail.notificationFilterMatchGained",
    FILTER_MATCH_LOST: "watchbox.detail.notificationFilterMatchLost",
} as const satisfies Record<WatchboxAlertEventKind, string>;

export type WatchboxDetailProps = {
    watchboxId: string;
    onBack: () => void;
    /** Deletes this watchbox and leaves the view when it succeeds. */
    onDelete: () => Promise<void>;
    isDeleting?: boolean;
    /** Whether this user has muted alert toasts for this watchbox — a personal
     *  preference, independent of server-side capabilities. */
    isMuted: boolean;
    onToggleMuted: (muted: boolean) => void;
    /** Delete lives in this menu, which unmounts on select, so the delete
     *  dialog's finalFocusEl points back at this trigger rather than a
     *  control that no longer exists. */
    actionsTriggerRef: RefObject<HTMLButtonElement | null>;
};

/**
 * One saved watchbox: its configuration read-only, with edit, delete, and
 * alert-toast mute, plus its recent Notifications.
 */
export function WatchboxDetail({
    watchboxId,
    onBack,
    onDelete,
    isDeleting,
    isMuted,
    onToggleMuted,
    actionsTriggerRef,
}: WatchboxDetailProps) {
    const { t } = useTranslation();
    const [activeTab, setActiveTab] = useState<WatchboxDetailTab>("information");
    const [editing, setEditing] = useState(false);

    // Leaving edit unmounts the control that was clicked (Cancel or Save), so
    // focus would fall to <body>. Keyed on the transition rather than on
    // `editing` alone: focusing whenever it is false would steal focus the
    // moment the detail view opens.
    const wasEditingRef = useRef(false);
    useEffect(() => {
        const leftEditing = wasEditingRef.current && !editing;
        wasEditingRef.current = editing;
        if (leftEditing) actionsTriggerRef.current?.focus();
    }, [editing, actionsTriggerRef]);
    const queryClient = useQueryClient();
    const {
        data: watchbox,
        isLoading,
        error,
    } = useQuery({
        ...watchboxQueries.watchbox(watchboxId),
        // The list carries full rows, so opening a card already on screen has
        // its configuration in hand and needs no fetch to render.
        placeholderData: () =>
            queryClient
                .getQueryData(watchboxQueries.watchboxes().queryKey)
                ?.find((row) => row.id === watchboxId),
    });

    // The query polls, and a failed poll keeps its data. Only "never loaded"
    // or a server-confirmed deletion may replace a configuration already on
    // screen; any other error leaves the last good one rendered.
    const notFound = parseWatchboxError(error)?.code === "WATCHBOX_NOT_FOUND";

    // Only reached when the list held nothing for this id. Below the delay the
    // shell still renders, so the breadcrumb is there to leave by.
    const showSpinner = useSpinDelay(isLoading, { delay: 500, minDuration: 200, ssr: false });

    if (isLoading) {
        return (
            <DetailShell onBack={onBack} label={t("common.loading")}>
                {showSpinner && (
                    <Flex
                        direction="column"
                        align="center"
                        justify="center"
                        gap="3"
                        h="full"
                        w="full"
                    >
                        <Spinner size="lg" />
                        <Text fontSize="sm" opacity={0.6}>
                            {t("common.loading")}
                        </Text>
                    </Flex>
                )}
            </DetailShell>
        );
    }

    if (!watchbox || notFound) {
        const key = error ? watchboxErrorMessageKey(error) : undefined;
        return (
            <DetailShell onBack={onBack} label={t("watchbox.detail.unknownWatchbox")}>
                <Flex direction="column" align="center" justify="center" gap="3" h="full" w="full">
                    <Text fontSize="lg" fontWeight={600} color="red.400">
                        {t("watchbox.detail.unavailable")}
                    </Text>
                    <Text fontSize="sm" opacity={0.6} textAlign="center">
                        {key ? t(key) : t("watchbox.detail.unavailableDescription")}
                    </Text>
                </Flex>
            </DetailShell>
        );
    }

    const canPatch = watchbox.capabilities?.canPatch ?? false;
    const muteLabel = t(isMuted ? "watchbox.unmute" : "watchbox.mute");

    // Editing replaces the read-only body in place, as the event detail panel
    // does. Gated here as well as on the button: losing the capability mid-edit
    // drops back to read-only rather than offering a save that cannot succeed.
    if (editing && canPatch) {
        return (
            <DetailShell onBack={onBack} label={watchbox.name}>
                {/* The form sizes to 100% of its parent, so it needs a box
                    holding only the space left under the breadcrumb. Given the
                    shell directly, the shell clips its action row. */}
                <Flex direction="column" flex="1" minH="0" w="full">
                    <WatchboxForm
                        watchbox={watchbox}
                        onSaved={() => setEditing(false)}
                        onCancel={() => setEditing(false)}
                    />
                </Flex>
            </DetailShell>
        );
    }

    return (
        <DetailShell onBack={onBack} label={watchbox.name}>
            {error && (
                <Text role="alert" fontSize="xs" color="orange.400" pb="2">
                    {t("watchbox.detail.refreshFailed")}
                </Text>
            )}
            <Box mb="6" pos="relative">
                <Flex gap={1} pos="absolute" top="0" right="0" zIndex="1">
                    {/* Mute is never gated on capabilities (a personal, client-only
                        preference), so this menu is always offered even when Edit
                        and Delete — server-mutating actions — are hidden. */}
                    <Menu.Root>
                        <Menu.Trigger asChild>
                            <IconButton
                                ref={actionsTriggerRef}
                                size="md"
                                variant="ghost"
                                color="white"
                                aria-label={t("watchbox.moreActions", { name: watchbox.name })}
                            >
                                <Tooltip
                                    content={t("watchbox.moreActions", { name: watchbox.name })}
                                    uppercase={false}
                                >
                                    <FaEllipsisVertical />
                                </Tooltip>
                            </IconButton>
                        </Menu.Trigger>
                        <Portal>
                            <Menu.Positioner>
                                <Menu.Content>
                                    {canPatch && (
                                        <Menu.Item value="edit" onClick={() => setEditing(true)}>
                                            {t("watchbox.edit")}
                                        </Menu.Item>
                                    )}
                                    {/* A plain Menu.Item with a swapping label ("Mute
                                        alerts"/"Unmute alerts"), not Menu.CheckboxItem: the
                                        label already conveys the current state to a screen
                                        reader, and this matches WatchboxCard.tsx's menu. */}
                                    <Menu.Item value="mute" onClick={() => onToggleMuted(!isMuted)}>
                                        {muteLabel}
                                    </Menu.Item>
                                    {watchbox.capabilities?.canDelete && (
                                        <Menu.Item
                                            value="delete"
                                            color="type.error"
                                            disabled={isDeleting}
                                            // Opens the confirm dialog rather than deleting,
                                            // so focus is the dialog's to manage: it restores
                                            // to this trigger on cancel, and a confirmed
                                            // delete returns to the list, which takes focus
                                            // there.
                                            onClick={onDelete}
                                        >
                                            {t("watchbox.delete")}
                                        </Menu.Item>
                                    )}
                                </Menu.Content>
                            </Menu.Positioner>
                        </Portal>
                    </Menu.Root>
                </Flex>
                <Box pr="12">
                    <WatchboxTitle title={watchbox.name}>
                        <HStack gap="2">
                            <WatchboxStatusBadges
                                enabled={watchbox.enabled}
                                alertingStatus={watchbox.alertingStatus}
                            />
                            {isMuted && (
                                <Text as="span" fontSize="xs" color="fg.muted">
                                    <Icon
                                        as={FaBellSlash}
                                        aria-hidden
                                        focusable={false}
                                        boxSize="3"
                                        verticalAlign="-0.125em"
                                        mr="1"
                                    />
                                    {t("watchbox.muted")}
                                </Text>
                            )}
                        </HStack>
                    </WatchboxTitle>
                </Box>
                <WatchboxDetailTabs activeTab={activeTab} onTabChange={setActiveTab} />
            </Box>

            <Flex direction="column" w="full" flex={1} px="2" overflowY="auto" borderRadius="sm">
                {activeTab === "information" ? (
                    <InformationTab watchbox={watchbox} />
                ) : (
                    <NotificationsTab watchbox={watchbox} />
                )}
            </Flex>
        </DetailShell>
    );
}

/** Breadcrumb and column shared by every detail state. */
function DetailShell({
    onBack,
    label,
    children,
}: {
    onBack: () => void;
    /** Trailing crumb. Always present: the root crumb is only rendered as a
     *  link while another crumb follows it. */
    label: string;
    children: ReactNode;
}) {
    const { t } = useTranslation();

    return (
        <Flex direction="column" h="full" w="full" overflowY="hidden">
            {/* px=0 because the mapbar panel is already padded. */}
            <Breadcrumbs
                px={0}
                items={[{ label: t("mapbar.watchbox"), href: "#" }, { label }]}
                // A button, not a link: returning to the list is in-panel
                // state, so there is no route to navigate to.
                renderLink={({ children: linkChildren }: { href: string; children: ReactNode }) => (
                    <Button
                        type="button"
                        variant="plain"
                        fontSize="sm"
                        color="fg"
                        fontWeight="semibold"
                        cursor="pointer"
                        textDecoration="underline"
                        textUnderlineOffset="3px"
                        _hover={{ color: "active" }}
                        display="inline-flex"
                        alignItems="center"
                        gap={1}
                        p={0}
                        minW="auto"
                        h="auto"
                        onClick={onBack}
                    >
                        {linkChildren}
                    </Button>
                )}
            />
            {children}
        </Flex>
    );
}

function InformationTab({ watchbox }: { watchbox: Watchbox }) {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const { fields, fieldFor } = useFilterFields();
    const { data: layers } = useQuery(layerQueries.myList());

    const detailRows = useMemo<DetailTableRow[]>(
        () => [
            {
                key: "name",
                label: t("watchbox.name").toUpperCase(),
                value: <DetailValue>{watchbox.name}</DetailValue>,
            },
            {
                key: "description",
                label: t("watchbox.description").toUpperCase(),
                value: watchbox.description ? (
                    <DetailValue>{watchbox.description}</DetailValue>
                ) : (
                    <EmptyValue>{t("watchbox.detail.noDescription")}</EmptyValue>
                ),
            },
            {
                key: "direction",
                label: t("watchbox.direction").toUpperCase(),
                value: <DetailValue>{t(directionKey[watchbox.direction])}</DetailValue>,
            },
        ],
        [t, watchbox.name, watchbox.description, watchbox.direction],
    );

    const timestampRows = useMemo<DetailTableRow[]>(
        () => [
            {
                key: "createdAt",
                label: t("watchbox.detail.created").toUpperCase(),
                value: (
                    <DetailValue>{formatDateMilitary(watchbox.createdAt, timezone)}</DetailValue>
                ),
            },
            {
                key: "updatedAt",
                label: t("watchbox.detail.updated").toUpperCase(),
                value: (
                    <DetailValue>{formatDateMilitary(watchbox.updatedAt, timezone)}</DetailValue>
                ),
            },
        ],
        [t, watchbox.createdAt, watchbox.updatedAt, timezone],
    );

    // Resolved through the caller's own layer list, so a layer they have
    // lost access to falls back to its id rather than disappearing.
    const layerNames = useMemo(() => {
        const byId = new Map((layers ?? []).map((layer) => [layer.layerId, layer.layerName]));
        return watchbox.layerIds.map((id) => ({ id, name: byId.get(id) ?? id }));
    }, [layers, watchbox.layerIds]);

    const rules = useMemo(() => {
        if (watchbox.filter.predicates.length === 0) return null;
        // A custom attribute this browser never saw still reads back (`fieldFor`);
        // a condition whose field is unavailable reads by its path.
        const { combinator, rows } = resolveFilter(watchbox.filter, fields, fieldFor);
        return {
            combinator,
            predicates: rows.map(({ draft, field }) => ({
                id: draft.id,
                summary: summarizePredicate(draft, field, t),
            })),
        };
    }, [watchbox.filter, fields, fieldFor, t]);

    return (
        <VStack align="stretch" gap="6" w="full" pb="4">
            <WatchboxSection title={t("watchbox.detail.detailsHeading")} variant="detail">
                <DetailTable rows={detailRows} />
            </WatchboxSection>

            <WatchboxSection title={t("watchbox.layers")} variant="detail">
                <VStack align="stretch" gap="2" px="3">
                    {layerNames.map((layer) => (
                        <Text key={layer.id} color="bg.contrast" textStyle="xs">
                            {layer.name}
                        </Text>
                    ))}
                </VStack>
            </WatchboxSection>

            <WatchboxSection title={t("filter.rulesTitle")} variant="detail">
                <Box px="3">
                    {rules ? (
                        <VStack align="stretch" gap="2">
                            {/* A sentence, not the form's segmented control:
                                a control the reader cannot move still takes
                                keyboard focus. */}
                            <Text color="fg.muted" textStyle="xs">
                                {rules.combinator === "ALL"
                                    ? t("watchbox.detail.rulesAll")
                                    : t("watchbox.detail.rulesAny")}
                            </Text>
                            {rules.predicates.map((predicate) => (
                                <Box
                                    key={predicate.id}
                                    borderWidth="1px"
                                    borderColor="border"
                                    borderRadius="sm"
                                    bg="bg.elevated"
                                    px="3"
                                    py="2"
                                    fontSize="sm"
                                >
                                    {predicate.summary ? (
                                        <PredicateSummary summary={predicate.summary} />
                                    ) : (
                                        <EmptyValue>
                                            {t("watchbox.detail.ruleUnavailable")}
                                        </EmptyValue>
                                    )}
                                </Box>
                            ))}
                        </VStack>
                    ) : (
                        <EmptyValue>{t("filter.noConditions")}</EmptyValue>
                    )}
                </Box>
            </WatchboxSection>

            <WatchboxSection title={t("watchbox.detail.timestampsHeading")} variant="detail">
                <DetailTable rows={timestampRows} />
            </WatchboxSection>
        </VStack>
    );
}

/**
 * Kept live by RegisterWatchboxAlerts writing each alert straight into this
 * query's cache (watchbox-detections.cache.ts), not by polling or by
 * refetching per-alert — a burst of alerts used to invalidate this query per
 * alert, turning into a burst of refetches. Opening this tab still refetches
 * once to reconcile with the server (refetchOnMount: "always"; see
 * watchboxQueries.detections), and the refresh button above the list forces
 * an out-of-band fetch on demand.
 */
function NotificationsTab({ watchbox }: { watchbox: Watchbox }) {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const { data, isLoading, isFetching, error, refetch } = useQuery(
        watchboxQueries.detections(watchbox.id),
    );
    const showSpinner = useSpinDelay(isLoading, { delay: 500, minDuration: 200, ssr: false });
    const detections = data?.content ?? [];

    return (
        <VStack align="stretch" gap="2" w="full" pb="4">
            <Flex justify="flex-end">
                <RefreshButton
                    onRefresh={() => refetch()}
                    isRefreshing={isFetching}
                    size="sm"
                    variant="ghost"
                />
            </Flex>
            {isLoading ? (
                showSpinner && (
                    <Flex
                        direction="column"
                        align="center"
                        justify="center"
                        h="full"
                        w="full"
                        py="10"
                    >
                        <Spinner size="lg" />
                    </Flex>
                )
            ) : error && !data ? (
                <Flex
                    direction="column"
                    align="center"
                    justify="center"
                    gap="2"
                    h="full"
                    w="full"
                    py="10"
                >
                    <Text role="alert" fontSize="sm" fontWeight={600} color="red.400">
                        {t("watchbox.detail.notificationsUnavailable")}
                    </Text>
                </Flex>
            ) : (
                <>
                    {error && (
                        <Text role="alert" fontSize="xs" color="orange.400">
                            {t("watchbox.detail.notificationsRefreshFailed")}
                        </Text>
                    )}
                    {detections.length === 0 ? (
                        <EmptyValue>{t("watchbox.detail.noNotifications")}</EmptyValue>
                    ) : (
                        // Newest-first, as the server returns it — the endpoint rejects a
                        // sort param, so this must not re-sort.
                        detections.map((detection) => (
                            <NotificationRow
                                key={detection.id}
                                detection={detection}
                                timezone={timezone}
                            />
                        ))
                    )}
                </>
            )}
        </VStack>
    );
}

function NotificationRow({
    detection,
    timezone,
}: {
    detection: WatchboxDetection;
    timezone: string;
}) {
    const { t } = useTranslation();
    // Same resolution RegisterWatchboxAlerts.tsx's toast uses, and for the same
    // reason: NOT `watchbox.sourceStatuses`, which the API leaves `[]` unless
    // the caller manages every layer the watchbox covers, so it resolves to
    // nothing for most viewers of this tab. Falls back to the raw id for a
    // source the caller can no longer see at all.
    const { data: ds } = useQuery(dataSourceQueries.myList());
    const sourceName =
        ds?.find((dataSource) => dataSource.serverId === detection.sourceId)?.serverName ??
        detection.sourceId;

    // Same hook RegisterWatchboxAlerts.tsx's toast uses, so a retained row and
    // the live toast agree on whether the object is locally held and what
    // clicking its name does.
    const { element, selectAndFocus } = useSelectAndFocusElement(detection);
    const objectName = detection.objectDisplayName ?? detection.objectId;

    return (
        <Box
            borderWidth="1px"
            borderColor="border"
            borderRadius="sm"
            bg="bg.elevated"
            px="3"
            py="2"
        >
            <Flex justify="space-between" align="baseline" gap="2">
                {element ? (
                    <Tooltip content={objectName} uppercase={false}>
                        <Box
                            as="button"
                            cursor="pointer"
                            maxW="full"
                            minW="0"
                            flexShrink={1}
                            onClick={selectAndFocus}
                        >
                            <Text
                                fontSize="sm"
                                fontWeight={600}
                                fontFamily="mono"
                                color="active"
                                textDecoration="underline"
                                textDecorationColor="currentColor"
                                textUnderlineOffset="2px"
                                truncate
                            >
                                {objectName}
                            </Text>
                        </Box>
                    </Tooltip>
                ) : (
                    <Tooltip content={objectName} uppercase={false}>
                        <Text
                            fontSize="sm"
                            fontWeight={600}
                            color="bg.contrast"
                            minW="0"
                            flexShrink={1}
                            truncate
                        >
                            {objectName}
                        </Text>
                    </Tooltip>
                )}
                <Text fontSize="xs" color="fg.muted" whiteSpace="nowrap">
                    {formatDateMilitary(detection.effectiveEventTime, timezone)}
                </Text>
            </Flex>
            <Text fontSize="xs" color="fg.muted">
                {t(notificationKindKey[detection.kind], { sourceName })}
            </Text>
        </Box>
    );
}

function DetailValue({ children }: { children: ReactNode }) {
    return (
        <Text color="bg.contrast" textStyle="xs">
            {children}
        </Text>
    );
}

function EmptyValue({ children }: { children: ReactNode }) {
    return (
        <Text color="fg.muted" textStyle="xs" fontStyle="italic">
            {children}
        </Text>
    );
}
