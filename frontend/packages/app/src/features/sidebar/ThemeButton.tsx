// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { ColorModeButton, getDefaultTransition } from "@opencop/ui";

export function ThemeButton() {
    return (
        <ColorModeButton
            w="full"
            maxW="8.5rem" // half of sidebar width
            variant="ghost"
            opacity={0}
            visibility="hidden"
            transition={
                getDefaultTransition(["width", "background", "color", "opacity"], {
                    transition: ", visibility .1s .1s",
                }).transition
            }
        />
    );
}
