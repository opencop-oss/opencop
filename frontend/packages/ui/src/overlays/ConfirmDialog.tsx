// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Checkbox, Text } from "@chakra-ui/react";
import { type ReactNode, useState } from "react";
import { useTranslation } from "react-i18next";
import { BaseDialog } from "./BaseDialog";
import { DialogActions } from "./DialogActions";
import { DialogHeader } from "./DialogHeader";
import { Dialog } from "@chakra-ui/react";

interface ConfirmDialogProps {
    open: boolean;
    onClose: () => void;
    onConfirm: () => void;
    title: string;
    secondaryText?: string;
    message: string | ReactNode;
    confirmLabel?: string;
    cancelLabel?: string;
    isDestructive?: boolean;
    loading?: boolean;
    /** Accessible text shown by the confirm button while it is loading. */
    loadingText?: string;
    /** When false, blocks `onClose` while loading for actions with no safe cancel path. */
    dismissDuringLoading?: boolean;
    disabled?: boolean;
    /** Show a "Don't show me this again" checkbox */
    dismissible?: boolean;
    /** Called when user confirms with the checkbox checked */
    onDismiss?: () => void;
    /** Element to focus after the dialog closes. Falls back to the opening control. */
    finalFocusEl?: () => HTMLElement | null;
}

export function ConfirmDialog({
    open,
    onClose,
    onConfirm,
    title,
    secondaryText,
    message,
    confirmLabel,
    cancelLabel,
    isDestructive = true,
    loading = false,
    loadingText,
    dismissDuringLoading = true,
    disabled = false,
    dismissible = false,
    onDismiss,
    finalFocusEl,
}: ConfirmDialogProps) {
    const { t } = useTranslation();
    const [dontShowAgain, setDontShowAgain] = useState(false);

    const resolvedConfirmLabel =
        confirmLabel ?? (isDestructive ? t("common.delete") : t("common.confirm"));
    const resolvedCancelLabel = cancelLabel ?? t("common.cancel");
    const lockedByLoading = loading && !dismissDuringLoading;

    const handleClose = () => {
        if (lockedByLoading) return;
        setDontShowAgain(false);
        onClose();
    };

    const handleConfirm = () => {
        if (loading || disabled) return;
        const wasDismissed = dontShowAgain;
        setDontShowAgain(false);
        onConfirm();
        if (wasDismissed) onDismiss?.();
    };

    return (
        <BaseDialog
            open={open}
            onClose={handleClose}
            variant="destructive"
            size="sm"
            role="alertdialog"
            finalFocusEl={finalFocusEl}
        >
            <DialogHeader title={title} secondaryText={secondaryText} />

            <Dialog.Body>
                {typeof message === "string" ? <Text>{message}</Text> : message}
            </Dialog.Body>

            {dismissible && (
                <Box px="6" pb="2">
                    <Checkbox.Root
                        size="sm"
                        cursor="pointer"
                        checked={dontShowAgain}
                        disabled={loading}
                        onCheckedChange={(e) => setDontShowAgain(!!e.checked)}
                    >
                        <Checkbox.HiddenInput />
                        <Checkbox.Control>
                            <Checkbox.Indicator />
                        </Checkbox.Control>
                        <Checkbox.Label fontSize="xs" color="fg.muted">
                            {t("common.dontShowAgain")}
                        </Checkbox.Label>
                    </Checkbox.Root>
                </Box>
            )}

            <DialogActions
                onClose={handleClose}
                cancelLabel={resolvedCancelLabel}
                onAction={handleConfirm}
                actionLabel={resolvedConfirmLabel}
                destructive={isDestructive}
                loading={loading}
                loadingText={loadingText ?? resolvedConfirmLabel}
                disabled={disabled}
                closeDisabled={lockedByLoading}
            />
        </BaseDialog>
    );
}
