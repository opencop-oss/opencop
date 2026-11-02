// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Text } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { ConfirmDialog } from "@opencop/ui";

interface ConfirmArchiveDialogProps {
    isOpen: boolean;
    onClose: () => void;
    onConfirm: () => void;
    name: string;
    /**
     * Element to focus once the dialog closes. Needed when the control that opened it lives
     * inside a drawer the confirm handler also closes: both unmount in the same commit, so
     * the default restore-to-trigger has no target and focus falls to `<body>`.
     */
    finalFocusEl?: () => HTMLElement | null;
}

/**
 * Confirmation for archiving a soft-archived entity (data source, layer).
 * Mirrors `ConfirmDeleteDialog` but with reversible, archive-appropriate copy —
 * archiving is non-destructive, so no destructive styling.
 */
export function ConfirmArchiveDialog({
    isOpen,
    onClose,
    onConfirm,
    name,
    finalFocusEl,
}: ConfirmArchiveDialogProps) {
    const { t } = useTranslation();

    const handleConfirm = async () => {
        await onConfirm();
        onClose();
    };

    return (
        <ConfirmDialog
            open={isOpen}
            onClose={onClose}
            onConfirm={handleConfirm}
            title={t("adminConfirmArchive.title")}
            secondaryText={t("common.archive")}
            message={<Text>{t("adminConfirmArchive.message", { name })}</Text>}
            confirmLabel={t("common.archive")}
            isDestructive={false}
            finalFocusEl={finalFocusEl}
        />
    );
}
