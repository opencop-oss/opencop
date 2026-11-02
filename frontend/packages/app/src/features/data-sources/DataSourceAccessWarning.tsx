// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box } from "@chakra-ui/react";
import { Tooltip } from "@opencop/ui";
import type { ReactElement } from "react";
import { LuTriangleAlert } from "react-icons/lu";

interface DataSourceAccessWarningProps {
    message: string;
}

/** Warning marker shown beside an access mode. The message states what is wrong
 * and, when the caller can act on it, the action needed. */
export function DataSourceAccessWarning({ message }: DataSourceAccessWarningProps): ReactElement {
    return (
        <Tooltip content={message} uppercase={false}>
            <Box
                as="span"
                role="img"
                aria-label={message}
                tabIndex={0}
                color="fg.warning"
                display="inline-flex"
                flexShrink={0}
            >
                <LuTriangleAlert />
            </Box>
        </Tooltip>
    );
}
