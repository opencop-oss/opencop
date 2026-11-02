// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { getErrorMessage } from "@opencop/api";
import { Button, HStack, Text, VisuallyHidden, VStack } from "@chakra-ui/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSpinDelay } from "spin-delay";
import { useAuth } from "~/features/auth/AuthContext";
import { cancel, save } from "~/features/map/interaction-runtime/session/commands";
import { useToast } from "../notifications/use-toast.hook";
import { WatchboxCard } from "./WatchboxCard";
import { WatchboxCreateButton } from "./WatchboxCreateButton";
import { WatchboxDeleteDialog } from "./WatchboxDeleteDialog";
import { WatchboxDetail } from "./WatchboxDetail";
import { WatchboxReenableDialog, type WatchboxReenablePending } from "./WatchboxReenableDialog";
import { parseWatchboxError } from "./watchbox.api";
import { watchboxErrorMessage, watchboxErrorMessageKey } from "./watchbox-error.util";
import { useWatchboxPanelStore } from "./watchbox-panel.store";
import { WatchboxForm } from "./WatchboxForm";
import type { Watchbox } from "./watchbox.model";
import { watchboxMutations, watchboxQueries } from "./watchbox.queries";
import { useWatchboxPolygon } from "./watchbox-session";
import { useWatchboxVisibilityStore } from "./watchbox-visibility.store";
import { EMPTY_MUTED_IDS, useWatchboxMuteStore } from "./watchbox-mute.store";

export function WatchboxPanel() {
    const { t, i18n } = useTranslation();
    const polygon = useWatchboxPolygon();
    const hideDisabled = useWatchboxVisibilityStore((s) => s.hideDisabled);
    const { setHideDisabled } = useWatchboxVisibilityStore((s) => s.actions);
    const { user } = useAuth();
    const userId = user?.user.id ?? "";
    const mutedIds = useWatchboxMuteStore((s) => s.mutedIdsByUser[userId] ?? EMPTY_MUTED_IDS);
    const { setMuted } = useWatchboxMuteStore((s) => s.actions);
    const client = useQueryClient();
    const { showToast } = useToast();

    const selectedId = useWatchboxPanelStore((s) => s.selected);
    const select = useWatchboxPanelStore((s) => s.select);
    const deleteOptions = watchboxMutations.delete(client);
    const {
        mutate: deleteMutate,
        isPending: isDeleting,
        variables: deletingId,
    } = useMutation({
        ...deleteOptions,
        onSuccess: (...args) => {
            deleteOptions.onSuccess?.(...args);
            showToast({ type: "success", title: t("watchbox.deleted") });
        },
        onError: (error, ...rest) => {
            deleteOptions.onError?.(error, ...rest);
            const key = watchboxErrorMessageKey(error);
            showToast({
                type: "error",
                title: t("watchbox.deleteError"),
                description: key ? t(key) : getErrorMessage(error),
            });
        },
    });
    const { mutateAsync: patchAsync } = useMutation(watchboxMutations.patch(client));
    const { data, isPending, isError, isFetching, refetch } = useQuery(
        watchboxQueries.watchboxes(),
    );
    const watchboxes = data ?? [];
    const visibleWatchboxes = hideDisabled
        ? watchboxes.filter((watchbox) => watchbox.enabled)
        : watchboxes;
    // `data` stays defined after the first successful fetch even if a later
    // background refetch fails, so this — not an empty `watchboxes` array —
    // distinguishes "never loaded" from "loaded, then refresh failed."
    const hasLoadedOnce = data !== undefined;
    const showLoading = useSpinDelay(isPending, { delay: 500, minDuration: 200, ssr: false });
    const [pendingDelete, setPendingDelete] = useState<Watchbox | null>(null);
    const [pendingReenable, setPendingReenable] = useState<WatchboxReenablePending | null>(null);
    const [patchErrorAnnouncement, setPatchErrorAnnouncement] = useState("");
    const pendingPatchIdsRef = useRef(new Set<string>());
    const [pendingPatchIds, setPendingPatchIds] = useState<ReadonlySet<string>>(() => new Set());
    const listErrorMessage = isError
        ? hasLoadedOnce
            ? t("watchbox.listRefreshFailed")
            : t("watchbox.listUnavailable")
        : "";

    const deleteWatchbox = (watchbox: Watchbox) =>
        deleteMutate(watchbox.id, {
            // Scope completion to the submitted watchbox even if panel state is
            // changed by something other than the guarded dialog controls.
            onSuccess: () => {
                const { selected, select } = useWatchboxPanelStore.getState();
                if (selected === watchbox.id) select(null);
                setMuted(userId, watchbox.id, false);
            },
            onSettled: () =>
                setPendingDelete((current) => (current?.id === watchbox.id ? null : current)),
        });

    const closeDeleteDialog = () => {
        // Keep the active request visible and prevent another row from replacing
        // its mutation observer before its completion callbacks have run.
        if (!isDeleting) setPendingDelete(null);
    };

    const showPatchError = (error: unknown) => {
        const title = t("watchbox.patchError");
        const description =
            watchboxErrorMessage(error, t, i18n.resolvedLanguage) ?? getErrorMessage(error);
        setPatchErrorAnnouncement(description || title);
        showToast({
            type: "error",
            title,
            description,
        });
    };

    const beginPatch = (id: string) => {
        if (pendingPatchIdsRef.current.has(id)) return false;
        pendingPatchIdsRef.current.add(id);
        setPendingPatchIds(new Set(pendingPatchIdsRef.current));
        return true;
    };

    const endPatch = (id: string) => {
        pendingPatchIdsRef.current.delete(id);
        setPendingPatchIds(new Set(pendingPatchIdsRef.current));
    };

    // Disabling never needs confirmation. Re-enabling may require a second,
    // explicitly confirmed request when the server reports a tracking reset.
    const toggleEnabled = async (watchbox: Watchbox, enabled: boolean) => {
        if (!beginPatch(watchbox.id)) return;
        // Clearing first makes an identical failure on a retry a new live-region update.
        setPatchErrorAnnouncement("");
        try {
            await patchAsync({ id: watchbox.id, enabled });
            showToast({
                type: "success",
                title: t(enabled ? "watchbox.enableSuccess" : "watchbox.disableSuccess"),
            });
        } catch (error) {
            const response = parseWatchboxError(error);
            if (enabled && response?.code === "WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED") {
                setPendingReenable((current) =>
                    current && current.watchbox.id !== watchbox.id
                        ? current
                        : { watchbox, reasons: response.reasons },
                );
            } else {
                showPatchError(error);
            }
        } finally {
            endPatch(watchbox.id);
        }
    };

    // The confirmed retry owns closing its dialog after the request settles.
    const confirmReenable = async () => {
        if (!pendingReenable) return;
        const { watchbox } = pendingReenable;
        if (!beginPatch(watchbox.id)) return;
        setPatchErrorAnnouncement("");
        try {
            await patchAsync({ id: watchbox.id, enabled: true, confirmTrackingReset: true });
            showToast({ type: "success", title: t("watchbox.enableSuccess") });
            setPendingReenable((current) =>
                current?.watchbox.id === watchbox.id ? null : current,
            );
        } catch (error) {
            const response = parseWatchboxError(error);
            if (response?.code === "WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED") {
                setPendingReenable((current) =>
                    current?.watchbox.id === watchbox.id
                        ? { watchbox, reasons: response.reasons }
                        : current,
                );
                return;
            }
            showPatchError(error);
            setPendingReenable((current) =>
                current?.watchbox.id === watchbox.id ? null : current,
            );
        } finally {
            endPatch(watchbox.id);
        }
    };

    // Leaving a detail unmounts the control that had focus, whether by delete
    // or by the breadcrumb, so focus would fall to <body>. Focus the list instead.
    const listRef = useRef<HTMLDivElement>(null);
    const detailActionsRef = useRef<HTMLButtonElement>(null);
    const previousSelectedRef = useRef<string | null>(null);
    useEffect(() => {
        const leftDetail = previousSelectedRef.current !== null && selectedId === null;
        previousSelectedRef.current = selectedId;
        if (leftDetail) listRef.current?.focus();
    }, [selectedId]);

    // Starting a draw drops the inspected watchbox so a later save returns to the list.
    const [drawing, setDrawing] = useState(false);
    if (drawing !== !!polygon) {
        setDrawing(!!polygon);
    }

    useEffect(() => {
        if (drawing) select(null);
    }, [drawing, select]);

    const retryButton = (
        <Button
            size="xs"
            variant="outline"
            colorPalette="secondary"
            loading={isFetching}
            loadingText={t("common.retry")}
            onClick={() => void refetch()}
        >
            {t("common.retry")}
        </Button>
    );

    const refreshWarning = isError && (
        <HStack justify="space-between" align="center" w="full">
            <Text fontSize="xs" color="fg.error">
                {listErrorMessage}
            </Text>
            {retryButton}
        </HStack>
    );

    if (polygon) {
        return (
            <VStack w="full" h="full">
                <WatchboxForm
                    polygon={polygon}
                    // The polygon lives on the server now. The runtime holds no
                    // custody over it, so save ends the session successfully.
                    onSaved={() => save()}
                    onCancel={() => cancel()}
                />
            </VStack>
        );
    }

    if (selectedId) {
        return (
            <VStack w="full" h="full">
                <WatchboxDetail
                    watchboxId={selectedId}
                    onBack={() => select(null)}
                    isDeleting={isDeleting && deletingId === selectedId}
                    onDelete={async () => {
                        const selectedWatchbox =
                            client.getQueryData<Watchbox>(
                                watchboxQueries.watchbox(selectedId).queryKey,
                            ) ?? watchboxes.find((watchbox) => watchbox.id === selectedId);
                        if (selectedWatchbox) setPendingDelete(selectedWatchbox);
                    }}
                    isMuted={mutedIds.includes(selectedId)}
                    onToggleMuted={(muted) => setMuted(userId, selectedId, muted)}
                    actionsTriggerRef={detailActionsRef}
                />
                {/* Delete lives in the ⋮ menu, which unmounts on select, so
                    finalFocusEl points back at its trigger instead of the
                    menu item itself, which no longer exists once the menu
                    closes. A confirmed delete leaves detailActionsRef null;
                    zag's setReturnFocus falls back when finalFocusEl returns
                    a falsy value, so the leftDetail effect above still moves
                    focus to the list. */}
                <WatchboxDeleteDialog
                    watchbox={pendingDelete}
                    onClose={closeDeleteDialog}
                    onConfirm={deleteWatchbox}
                    loading={isDeleting && deletingId === pendingDelete?.id}
                    disabled={isDeleting}
                    finalFocusEl={() => detailActionsRef.current}
                />
            </VStack>
        );
    }

    return (
        <VStack
            ref={listRef}
            tabIndex={-1}
            outline="none"
            w="full"
            h="full"
            gap={2}
            align="stretch"
        >
            {/* These live regions stay mounted so screen readers observe text changes
                instead of nodes inserted with their messages already populated. */}
            <VisuallyHidden role="alert" aria-atomic="true">
                {patchErrorAnnouncement}
            </VisuallyHidden>
            <VisuallyHidden role="alert">{hasLoadedOnce ? "" : listErrorMessage}</VisuallyHidden>
            <VisuallyHidden aria-live="polite">
                {hasLoadedOnce ? listErrorMessage : ""}
            </VisuallyHidden>
            {isPending ? (
                showLoading && (
                    <Text fontSize="sm" role="status">
                        {t("common.loading")}
                    </Text>
                )
            ) : isError && !hasLoadedOnce ? (
                <VStack align="start" gap={2}>
                    <Text fontSize="sm" color="fg.error">
                        {t("watchbox.listUnavailable")}
                    </Text>
                    {retryButton}
                </VStack>
            ) : watchboxes.length === 0 ? (
                <VStack
                    w="full"
                    h="full"
                    flex="1"
                    justify="center"
                    align="center"
                    gap="4"
                    px="4"
                    textAlign="center"
                >
                    {refreshWarning}
                    <Text fontWeight="600" color="bg.contrast">
                        {t("watchbox.emptyStateTitle")}
                    </Text>
                    <Text color="fg.muted" fontSize="sm">
                        {t("watchbox.emptyStateDescription")}
                    </Text>
                    <WatchboxCreateButton size="sm" variant="solid" colorPalette="brand" />
                </VStack>
            ) : (
                <>
                    <HStack justify="space-between" w="full">
                        <WatchboxCreateButton
                            size="2xs"
                            variant="plain"
                            borderRadius="sm"
                            px="2"
                            fontSize="xs"
                            color="active"
                        />
                        <Button
                            size="2xs"
                            variant="plain"
                            borderRadius="sm"
                            px="2"
                            fontSize="xs"
                            bg={hideDisabled ? "transparent" : "bg.subtle"}
                            color={hideDisabled ? "fg.muted" : "active"}
                            onClick={() => setHideDisabled(!hideDisabled)}
                            aria-pressed={hideDisabled}
                        >
                            {t(hideDisabled ? "watchbox.showDisabled" : "watchbox.hideDisabled")}
                        </Button>
                    </HStack>
                    {refreshWarning}
                    {visibleWatchboxes.map((watchbox) => (
                        <WatchboxCard
                            key={watchbox.id}
                            watchbox={watchbox}
                            open={() => select(watchbox.id)}
                            onDeleteClick={() => setPendingDelete(watchbox)}
                            onToggleEnabled={(enabled) => void toggleEnabled(watchbox, enabled)}
                            isMuted={mutedIds.includes(watchbox.id)}
                            onToggleMuted={(muted) => setMuted(userId, watchbox.id, muted)}
                            togglePending={pendingPatchIds.has(watchbox.id)}
                        />
                    ))}
                </>
            )}
            <WatchboxDeleteDialog
                watchbox={pendingDelete}
                onClose={closeDeleteDialog}
                onConfirm={deleteWatchbox}
                loading={isDeleting && deletingId === pendingDelete?.id}
                disabled={isDeleting}
                finalFocusEl={() => listRef.current}
            />
            <WatchboxReenableDialog
                pending={pendingReenable}
                onClose={() => setPendingReenable(null)}
                onConfirm={confirmReenable}
                loading={
                    pendingReenable !== null && pendingPatchIds.has(pendingReenable.watchbox.id)
                }
            />
        </VStack>
    );
}
