// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { SideDrawer } from "@opencop/ui";
import { useState, type ReactElement } from "react";
import { DrawerContextProvider } from "~/features/drawers/DrawerContext";
import { useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import { RuntimeFeatureDrawerContent } from "./detail/RuntimeFeatureDrawerContent";
import type { RuntimeFeatureRow } from "./runtime-features.rows";

export type RuntimeFeatureDrawerState =
    | { type: "closed" }
    | { type: "runtimeFeature"; entity: RuntimeFeatureRow };

interface RuntimeFeatureDrawerProps {
    drawerState: RuntimeFeatureDrawerState;
}

export function RuntimeFeatureDrawer({
    drawerState,
}: RuntimeFeatureDrawerProps): ReactElement | null {
    const { closeDrawer } = useViewDrawerContext();
    // An audience PATCH reports its result through callbacks the content owns, so dismissing the
    // drawer mid-flight would drop the success notice, the exact Undo, or a conflict's reread.
    const [busy, setBusy] = useState(false);
    return (
        <DrawerContextProvider onClose={closeDrawer} setBusy={setBusy}>
            <SideDrawer
                open={drawerState.type !== "closed"}
                onClose={closeDrawer}
                size="lgx"
                closeDisabled={busy}
            >
                {drawerState.type === "runtimeFeature" && (
                    <RuntimeFeatureDrawerContent
                        key={drawerState.entity.name}
                        row={drawerState.entity}
                    />
                )}
            </SideDrawer>
        </DrawerContextProvider>
    );
}
