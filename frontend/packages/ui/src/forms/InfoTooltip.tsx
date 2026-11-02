// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box } from "@chakra-ui/react";
import { FaInfoCircle } from "react-icons/fa";
import type { ReactNode } from "react";
import { Tooltip } from "../overlays/Tooltip.tsx";

export interface InfoTooltipProps {
    content: ReactNode;
    /**
     * Read by assistive tech in place of visible text — the trigger carries only an icon. Pass
     * the label this icon is paired with (e.g. a column header) so it self-identifies when
     * several info icons share a page; if there's no separate label to reuse, pass the full
     * disclosed text instead.
     */
    "aria-label": string;
}

/**
 * An info-circle icon that discloses `content` on hover or keyboard focus. Clicks and Enter/Space
 * are swallowed so the icon never activates whatever it's sitting next to (a sortable column
 * header, a checkbox row).
 */
export function InfoTooltip({ content, "aria-label": ariaLabel }: InfoTooltipProps) {
    return (
        <Tooltip content={content} uppercase={false}>
            <Box
                as="span"
                role="button"
                tabIndex={0}
                aria-label={ariaLabel}
                display="inline-flex"
                alignItems="center"
                color="fg.muted"
                cursor="default"
                _hover={{ color: "fg" }}
                _focusVisible={{
                    outline: "2px solid",
                    outlineColor: "brand.solid",
                    borderRadius: "sm",
                }}
                onClick={(e: React.MouseEvent) => e.stopPropagation()}
                onKeyDown={(e: React.KeyboardEvent) => {
                    if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        e.stopPropagation();
                    }
                }}
            >
                <FaInfoCircle size={12} aria-hidden="true" />
            </Box>
        </Tooltip>
    );
}
