// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, useToken } from "@chakra-ui/react";
import { buttonHeight } from "./ModuleButton";
import { useSidebar } from "./SidebarContext";
import { getDefaultTransition } from "@opencop/ui";

export function ModuleIndicator() {
    const { moduleOpen, modules } = useSidebar();
    const index: number = modules.findIndex((m) => m.name === moduleOpen);

    // Get the actual pixel/rem value for the button height token
    const [heightValue] = useToken("sizes", [buttonHeight.toString()]);

    // Calculate top position in JavaScript
    const topPosition = `calc(${index} * ${heightValue})`;

    return index !== -1 ? (
        <Box
            pos="absolute"
            left="0"
            zIndex="1"
            top={topPosition}
            w="1"
            h={buttonHeight}
            bg="icon.active"
            color="icon.active"
            filter="drop-shadow(0px 0px 4px currentColor)"
            transition={getDefaultTransition(["opacity", "top"]).transition}
        />
    ) : null;
}
