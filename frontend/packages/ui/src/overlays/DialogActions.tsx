// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Button, Dialog, type ButtonProps } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";

interface DialogActionsProps {
    onClose: () => void;
    cancelLabel?: string;
    onAction?: () => void;
    actionType?: ButtonProps["type"];
    actionLabel?: string;
    destructive?: boolean;
    loading?: boolean;
    loadingText?: string;
    disabled?: boolean;
    /** Prevents dismissing the dialog while an action that cannot be cancelled is running. */
    closeDisabled?: boolean;
    /** When true and no onAction provided, renders close button as primary "Done" button */
    primaryClose?: boolean;
}

export function DialogActions({
    onClose,
    cancelLabel,
    onAction,
    actionLabel,
    destructive = false,
    loading = false,
    loadingText,
    disabled = false,
    closeDisabled = false,
    primaryClose = false,
    ...rest
}: DialogActionsProps) {
    const { t } = useTranslation();
    // When primaryClose is true and no action, show primary close button
    const showPrimaryClose = primaryClose && !onAction;
    const closeLabel = cancelLabel ?? (showPrimaryClose ? t("common.done") : t("common.cancel"));

    return (
        <Dialog.Footer>
            <Button
                variant={showPrimaryClose ? "solid" : "subtle"}
                colorPalette={showPrimaryClose ? "brand" : "secondary"}
                size="sm"
                onClick={onClose}
                disabled={closeDisabled}
            >
                {closeLabel}
            </Button>
            {(onAction || rest.actionType) && (
                <Button
                    colorPalette={destructive ? "red" : "brand"}
                    size="sm"
                    onClick={onAction}
                    loading={loading}
                    loadingText={loadingText}
                    aria-busy={loading || undefined}
                    disabled={disabled}
                    type={rest.actionType}
                >
                    {actionLabel}
                </Button>
            )}
        </Dialog.Footer>
    );
}
