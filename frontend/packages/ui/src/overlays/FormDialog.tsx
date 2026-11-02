// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Dialog } from "@chakra-ui/react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { BaseDialog } from "./BaseDialog";
import { DialogActions } from "./DialogActions";
import { DialogHeader } from "./DialogHeader";

interface FormDialogProps {
    open: boolean;
    onClose: () => void;
    title?: string;
    secondaryText?: string;
    onSubmit?: () => void;
    submitLabel?: string;
    cancelLabel?: string;
    isSubmitting?: boolean;
    submitDisabled?: boolean;
    children: ReactNode;
    size?: "sm" | "md" | "lg" | "xl" | "2xl";
    contentWidth?: string;
    disableFooter?: boolean;
    /** When true and no onSubmit provided, renders close button as primary "Done" button */
    primaryClose?: boolean;
    /** Element to focus when dialog opens. Return null from function to disable auto-focus. */
    initialFocusEl?: () => HTMLElement | null;
}

export function FormDialog({
    open,
    onClose,
    title,
    secondaryText,
    onSubmit,
    submitLabel,
    cancelLabel,
    isSubmitting = false,
    submitDisabled = false,
    children,
    size = "md",
    contentWidth,
    disableFooter = false,
    primaryClose = false,
    initialFocusEl,
}: FormDialogProps) {
    const { t } = useTranslation();
    const resolvedSubmitLabel = submitLabel ?? t("common.save");

    return (
        <BaseDialog
            open={open}
            onClose={onClose}
            variant="admin"
            size={size}
            contentWidth={contentWidth}
            initialFocusEl={initialFocusEl}
        >
            {title && <DialogHeader title={title} secondaryText={secondaryText} />}

            <Dialog.Body>{children}</Dialog.Body>

            {!disableFooter && (
                <DialogActions
                    onClose={onClose}
                    cancelLabel={cancelLabel}
                    onAction={onSubmit}
                    actionLabel={resolvedSubmitLabel}
                    loading={isSubmitting}
                    loadingText={t("common.saving")}
                    disabled={submitDisabled}
                    primaryClose={primaryClose}
                />
            )}
        </BaseDialog>
    );
}
