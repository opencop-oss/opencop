// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box } from "@chakra-ui/react";
import type { ReactElement } from "react";

export function MapCrosshair(): ReactElement {
    return (
        <Box
            position="absolute"
            top="50%"
            left="50%"
            transform="translate(-50%, -50%)"
            pointerEvents="none"
            zIndex={2}
        >
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                {/* Shadow outlines for contrast on any background */}
                <line
                    x1="0"
                    y1="12"
                    x2="9"
                    y2="12"
                    stroke="black"
                    strokeWidth="2.5"
                    strokeOpacity="0.3"
                />
                <line
                    x1="15"
                    y1="12"
                    x2="24"
                    y2="12"
                    stroke="black"
                    strokeWidth="2.5"
                    strokeOpacity="0.3"
                />
                <line
                    x1="12"
                    y1="0"
                    x2="12"
                    y2="9"
                    stroke="black"
                    strokeWidth="2.5"
                    strokeOpacity="0.3"
                />
                <line
                    x1="12"
                    y1="15"
                    x2="12"
                    y2="24"
                    stroke="black"
                    strokeWidth="2.5"
                    strokeOpacity="0.3"
                />
                {/* White foreground lines */}
                <line
                    x1="0"
                    y1="12"
                    x2="9"
                    y2="12"
                    stroke="white"
                    strokeWidth="1"
                    strokeOpacity="0.9"
                />
                <line
                    x1="15"
                    y1="12"
                    x2="24"
                    y2="12"
                    stroke="white"
                    strokeWidth="1"
                    strokeOpacity="0.9"
                />
                <line
                    x1="12"
                    y1="0"
                    x2="12"
                    y2="9"
                    stroke="white"
                    strokeWidth="1"
                    strokeOpacity="0.9"
                />
                <line
                    x1="12"
                    y1="15"
                    x2="12"
                    y2="24"
                    stroke="white"
                    strokeWidth="1"
                    strokeOpacity="0.9"
                />
                {/* Center dot */}
                <circle cx="12" cy="12" r="1" fill="white" fillOpacity="0.95" />
            </svg>
        </Box>
    );
}
