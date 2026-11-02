// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Text } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { ConfirmDialog } from "@opencop/ui";

interface ConfirmDeleteDialogProps {
    isOpen: boolean;
    onClose: () => void;
    onDelete: () => void;
    userName: string;
}

export function ConfirmDeleteDialog({
    isOpen,
    onClose,
    onDelete,
    userName,
}: ConfirmDeleteDialogProps) {
    const { t } = useTranslation();

    const handleDelete = async () => {
        await onDelete();
        onClose();
    };

    return (
        <ConfirmDialog
            open={isOpen}
            onClose={onClose}
            onConfirm={handleDelete}
            title={t("adminConfirmDelete.title")}
            secondaryText={t("adminConfirmDelete.action")}
            message={<Text>{t("adminConfirmDelete.message", { userName })}</Text>}
        />
    );
}
