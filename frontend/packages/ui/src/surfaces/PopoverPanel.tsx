// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Popover } from "@chakra-ui/react";
import type { ComponentProps } from "react";

type PopoverContentProps = ComponentProps<typeof Popover.Content>;

/**
 * Pre-styled `Popover.Content` with the project's glass surface tokens.
 *
 * Defaults to `w="fit-content"` and `minW="0"` — override via props.
 * All Chakra style props are accepted and override the defaults.
 */
export function PopoverPanel(props: PopoverContentProps) {
    return (
        <Popover.Content
            bg="bg.glass"
            backdropFilter="blur(4px)"
            borderColor="border"
            borderWidth="1px"
            borderRadius="md"
            boxShadow="md"
            w="fit-content"
            minW="0"
            {...props}
        />
    );
}
