// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { RouterProvider } from "react-router/dom";
import { OcThemeProvider } from "@opencop/ui";
import { router } from "./router";

export function App() {
    return (
        <OcThemeProvider>
            <RouterProvider router={router} />
        </OcThemeProvider>
    );
}
