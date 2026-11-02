// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Dialog, Portal } from "@chakra-ui/react";
import { createContext, useRef, type ReactNode, type RefObject } from "react";

export const DialogContainerContext = createContext<RefObject<HTMLElement | null> | null>(null);

export interface BaseDialogProps {
    open: boolean;
    onClose: () => void;
    variant?: "admin" | "view" | "destructive" | "content";
    size?: "sm" | "md" | "lg" | "xl" | "2xl";
    role?: "dialog" | "alertdialog";
    children: ReactNode;
    contentWidth?: string;
    /** Element to focus when dialog opens. Defaults to null (no auto-focus). */
    initialFocusEl?: () => HTMLElement | null;
    /** Element to focus after the dialog closes. Falls back to the opening control. */
    finalFocusEl?: () => HTMLElement | null;
}

export function BaseDialog({
    open,
    onClose,
    variant,
    size,
    role = "dialog",
    children,
    contentWidth,
    initialFocusEl,
    finalFocusEl,
}: BaseDialogProps) {
    const contentRef = useRef<HTMLDivElement>(null);

    return (
        <Dialog.Root
            open={open}
            onOpenChange={(e) => !e.open && onClose()}
            placement="center"
            role={role}
            size={size}
            variant={variant}
            initialFocusEl={initialFocusEl ?? (() => contentRef.current)}
            finalFocusEl={finalFocusEl}
        >
            <Portal>
                <Dialog.Backdrop />
                <Dialog.Positioner>
                    <Dialog.Content
                        margin={4}
                        ref={contentRef}
                        {...(contentWidth ? { width: contentWidth } : {})}
                    >
                        <DialogContainerContext value={contentRef}>
                            {children}
                        </DialogContainerContext>
                    </Dialog.Content>
                </Dialog.Positioner>
            </Portal>
        </Dialog.Root>
    );
}
