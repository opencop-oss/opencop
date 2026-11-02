// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { IconButton, useDisclosure } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { Tooltip, ConfirmDialog } from "@opencop/ui";
import type { ReactElement } from "react";
import { useEventsEditingStore } from "../events-editing.store";
import { useEventDelete } from "../use-event-delete.hook";
import { IoMdTrash } from "react-icons/io";
import { DataSourceWarningDialog } from "./DataSourceWarningDialog";
import type { EventMutationTarget } from "~/features/map/editability";

export function DeleteDialog({
    disabled,
    disabledReason,
    showDataSourceWarning,
    mutationTarget,
}: {
    disabled?: boolean;
    disabledReason?: string;
    showDataSourceWarning?: boolean;
    mutationTarget?: EventMutationTarget;
}): ReactElement {
    const { t } = useTranslation();
    const { open, onOpen, onClose } = useDisclosure();
    const { open: warnOpen, onOpen: onWarnOpen, onClose: onWarnClose } = useDisclosure();
    const overlay = useEventsEditingStore((s) => s.overlay);
    const del = useEventDelete(mutationTarget);
    const isDisabled = !!overlay || disabled;

    const handleConfirm = () => {
        onClose();
        del();
    };

    const handleWarnConfirm = () => {
        onWarnClose();
        del();
    };

    const handleClick = () => {
        if (showDataSourceWarning) {
            onWarnOpen();
        } else {
            onOpen();
        }
    };

    return (
        <>
            <Tooltip content={isDisabled && disabledReason ? disabledReason : t("common.delete")}>
                <IconButton
                    size="md"
                    color="red.400"
                    disabled={isDisabled}
                    onClick={handleClick}
                    aria-label={t("common.delete")}
                >
                    <IoMdTrash />
                </IconButton>
            </Tooltip>

            <ConfirmDialog
                open={open}
                onClose={onClose}
                onConfirm={handleConfirm}
                title={t("eventDelete.title")}
                secondaryText={t("eventDelete.action")}
                message={t("eventDelete.message")}
            />

            <DataSourceWarningDialog
                open={warnOpen}
                onClose={onWarnClose}
                onConfirm={handleWarnConfirm}
            />
        </>
    );
}
