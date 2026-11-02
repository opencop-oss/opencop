// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { ChakraProvider } from "@chakra-ui/react";
import type { ReactNode } from "react";
import { ColorModeProvider } from "./ColorMode.tsx";
import { system } from "./theme.ts";

/** Mounts the OpenCOP theme and the light/dark color mode around `children`. */
export function OcThemeProvider({ children }: { children: ReactNode }) {
    return (
        <ChakraProvider value={system}>
            <ColorModeProvider>{children}</ColorModeProvider>
        </ChakraProvider>
    );
}
