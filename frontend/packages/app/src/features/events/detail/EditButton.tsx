// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useRef } from "react";
import { IconButton, useDisclosure } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { Tooltip } from "@opencop/ui";
import type { ReactElement } from "react";
import { MdEdit } from "react-icons/md";
import { DataSourceWarningDialog } from "./DataSourceWarningDialog";
import { usePickedElement } from "~/features/map/use-picked-element.hook";
import { startEdit } from "~/features/map/interaction-runtime/session/commands";

export function EditButton({
    disabled,
    disabledReason,
    showDataSourceWarning,
}: {
    disabled?: boolean;
    disabledReason?: string;
    showDataSourceWarning?: boolean;
}): ReactElement {
    const { t } = useTranslation();
    const { open, onOpen, onClose } = useDisclosure();
    const pickedElement = usePickedElement();
    // Capture pickedElement when the dialog opens so a WS eviction between
    // open and confirm doesn't silently swallow the action.
    const capturedElementRef = useRef(pickedElement);

    const handleClick = () => {
        if (showDataSourceWarning) {
            capturedElementRef.current = pickedElement;
            onOpen();
        } else if (pickedElement) {
            startEdit(pickedElement, false);
        }
    };

    const handleConfirm = () => {
        onClose();
        if (capturedElementRef.current) startEdit(capturedElementRef.current, false);
    };

    return (
        <>
            <Tooltip content={disabled && disabledReason ? disabledReason : t("events.edit")}>
                <IconButton
                    disabled={disabled}
                    size="md"
                    color="active"
                    onClick={handleClick}
                    aria-label={t("events.edit")}
                >
                    <MdEdit />
                </IconButton>
            </Tooltip>

            <DataSourceWarningDialog
                open={open}
                onClose={onClose}
                onConfirm={handleConfirm}
                titleKey="events.dataSourceEditWarningTitle"
                messageKey="events.dataSourceEditWarningMessage"
                confirmLabel={t("events.edit")}
                isDestructive={false}
            />
        </>
    );
}
