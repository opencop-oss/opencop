// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    IconButton,
    Menu,
    Portal,
    Spinner,
    useDisclosure,
    type MenuSelectionDetails,
} from "@chakra-ui/react";
import { useQuery } from "@tanstack/react-query";
import { dataSourceQueries } from "../data-sources/data-sources.queries";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useEventPush } from "./use-event-push.hook";
import { useToast } from "../notifications/use-toast.hook";
import { MdOutlineFileUpload } from "react-icons/md";
import { useEventDetails } from "./use-event-details.hook";
import { ConfirmDialog, Tooltip } from "@opencop/ui";
import { isOffered } from "~/features/filters/panel-exclusion.util";
import { usePanelExcludedIds } from "~/features/filters/use-panel-excluded-ids.hook";

export function PushMenu() {
    const { t } = useTranslation();
    const { showToast } = useToast();
    const { details } = useEventDetails();
    const { open: menuOpen, onOpen: menuOnOpen, onClose: menuOnClose } = useDisclosure();
    const { open: dialogOpen, onOpen: dialogOnOpen, onClose: dialogOnClose } = useDisclosure();
    const { data: sources, isLoading: sourcesLoading } = useQuery(dataSourceQueries.myList());
    const [source, setSource] = useState(details?.source);
    const push = useEventPush();
    const excludedIds = usePanelExcludedIds();

    const items = useMemo(
        () =>
            sources
                ?.filter(
                    (source) =>
                        isOffered(excludedIds, source.serverId) &&
                        (source.access === "RW" || source.access === "W") &&
                        (source.role === "OWNER" || source.role === "ADMIN"),
                )
                .map((source) => ({
                    label: source.serverName,
                    value: source.serverId,
                })) ?? [],
        [sources, excludedIds],
    );

    const onSelect = useCallback(
        (details: MenuSelectionDetails) => {
            setSource(details.value);
            menuOnClose();
            dialogOnOpen();
        },
        [setSource, menuOnClose, dialogOnOpen],
    );

    const onConfirm = useCallback(async () => {
        try {
            dialogOnClose();
            // Safe assertion as onConfirm is only called following onSelect setting the source.
            await push(source!);
            showToast({
                type: "success",
                title: t("pushMenu.pushedEvent"),
            });
        } catch {
            showToast({
                type: "error",
                title: t("pushMenu.failedToPush"),
            });
        }
    }, [dialogOnClose, push, showToast, source, t]);

    return items.length > 0 ? (
        <>
            <ConfirmDialog
                open={dialogOpen}
                title={t("pushMenu.areYouSure")}
                message={t("pushMenu.pushConfirmMessage", {
                    displayName: details?.displayName,
                    serverName: items.find((i) => i.value === source)?.label,
                })}
                onClose={dialogOnClose}
                onConfirm={onConfirm}
                isDestructive={false}
            />

            <Menu.Root
                open={menuOpen}
                onOpenChange={(e) => (e.open ? menuOnOpen() : menuOnClose())}
                onSelect={onSelect}
            >
                <Menu.Trigger asChild>
                    <IconButton size="md" color="orange">
                        <Tooltip content={t("pushMenu.pushToDataSource")}>
                            <MdOutlineFileUpload />
                        </Tooltip>
                    </IconButton>
                </Menu.Trigger>
                <Portal>
                    <Menu.Positioner>
                        <Menu.Content>
                            {sourcesLoading ? (
                                <Spinner />
                            ) : (
                                items.map((item) => (
                                    <Menu.Item key={item.value} value={item.value}>
                                        {item.label}
                                    </Menu.Item>
                                ))
                            )}
                        </Menu.Content>
                    </Menu.Positioner>
                </Portal>
            </Menu.Root>
        </>
    ) : null;
}
