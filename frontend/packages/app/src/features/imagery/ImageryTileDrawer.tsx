// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { SideDrawer } from "@opencop/ui";
import { DrawerContextProvider } from "~/features/drawers/DrawerContext";
import { useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import type { DrawerState } from "~/features/drawers/drawer-state.model";
import { TileDetailContent } from "./detail/TileDetailContent";

export type ImageryTileDrawerState = DrawerState<string, "tile">;

interface ImageryTileDrawerProps {
    drawerState: ImageryTileDrawerState;
}

export function ImageryTileDrawer({ drawerState }: ImageryTileDrawerProps): ReactElement {
    const { closeDrawer } = useViewDrawerContext<{ type: "none" }>();
    return (
        <DrawerContextProvider onClose={closeDrawer}>
            <SideDrawer open={drawerState.type !== "closed"} onClose={closeDrawer} size="lgx">
                {drawerState.type === "tile" && (
                    <TileDetailContent key={drawerState.entity} tileId={drawerState.entity} />
                )}
            </SideDrawer>
        </DrawerContextProvider>
    );
}
