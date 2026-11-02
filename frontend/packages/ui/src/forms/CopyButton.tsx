// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { IconButton, VisuallyHidden, type IconButtonProps } from "@chakra-ui/react";
import { useEffect, useRef, useState, type MouseEvent } from "react";
import { useTranslation } from "react-i18next";
import { MdCheck, MdContentCopy } from "react-icons/md";

/** How long the button shows the copied icon after a copy. */
const COPIED_MS = 2000;

export interface CopyButtonProps extends Omit<
    IconButtonProps,
    "aria-label" | "onClick" | "children" | "color"
> {
    /** The text written to the clipboard. */
    readonly value: string;
    /** The accessible name. Defaults to "Copy to clipboard"; name what is copied when a row has several. */
    readonly label?: string;
    /** The icon's size in pixels, for dense rows. Defaults to the icon's own size. */
    readonly iconSize?: number;
}

/**
 * An icon button that copies `value` to the clipboard and shows a check for two
 * seconds, counted from the latest copy. A screen reader hears "Copied" through a
 * polite live region. The click does not reach its container, so it can sit
 * inside a clickable row. A clipboard that refuses (an insecure context, a denied
 * permission, an unfocused tab) leaves the button unchanged and announces
 * nothing.
 */
export function CopyButton({ value, label, iconSize, ...buttonProps }: CopyButtonProps) {
    const { t } = useTranslation();
    const [copied, setCopied] = useState(false);
    const timerRef = useRef<ReturnType<typeof setTimeout>>(undefined);
    const mountedRef = useRef(false);

    useEffect(() => {
        mountedRef.current = true;
        return () => {
            mountedRef.current = false;
            clearTimeout(timerRef.current);
        };
    }, []);

    const handleCopy = async (event: MouseEvent) => {
        event.stopPropagation();
        try {
            await navigator.clipboard.writeText(value);
        } catch {
            // The clipboard is optional; the button simply does not confirm.
            return;
        }
        // The copy can finish after the button has gone; nothing may start then.
        if (!mountedRef.current) return;
        // Restarted on every copy: `setCopied(true)` while already copied changes
        // nothing, so an effect keyed on `copied` would keep the first copy's timer.
        clearTimeout(timerRef.current);
        setCopied(true);
        timerRef.current = setTimeout(() => setCopied(false), COPIED_MS);
    };

    return (
        <>
            <IconButton
                size="2xs"
                variant="ghost"
                {...buttonProps}
                aria-label={label ?? t("common.copyToClipboard")}
                color={copied ? "type.success" : "fg.icon"}
                onClick={handleCopy}
            >
                {copied ? <MdCheck size={iconSize} /> : <MdContentCopy size={iconSize} />}
            </IconButton>
            {/* Present before the copy, so the change is announced. */}
            <VisuallyHidden aria-live="polite">{copied ? t("common.copied") : ""}</VisuallyHidden>
        </>
    );
}
