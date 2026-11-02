// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, VisuallyHidden } from "@chakra-ui/react";
import { Tooltip } from "@opencop/ui";
import type { ReactElement } from "react";
import { LuInfo } from "react-icons/lu";

interface DataSourceAccessHintProps {
    id?: string;
    message: string;
}

/** Explanation marker shown beside an access mode choice, describing what that mode does.
 * The message is rendered as real (visually hidden) text rather than an `aria-label`, so a
 * radio pointing here with `aria-describedby` resolves an accessible description. */
export function DataSourceAccessHint({ id, message }: DataSourceAccessHintProps): ReactElement {
    return (
        <Tooltip content={message} uppercase={false}>
            <Box
                as="span"
                id={id}
                tabIndex={0}
                color="fg.muted"
                display="inline-flex"
                flexShrink={0}
            >
                <LuInfo aria-hidden focusable={false} />
                <VisuallyHidden>{message}</VisuallyHidden>
            </Box>
        </Tooltip>
    );
}
