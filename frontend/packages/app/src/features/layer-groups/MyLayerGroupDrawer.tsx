// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { SideDrawer } from "@opencop/ui";
import { DrawerContextProvider } from "~/features/drawers/DrawerContext";
import { useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import type { DrawerState } from "~/features/drawers/drawer-state.model";
import type { MyLayerGroup } from "./layer-groups.queries";
import { MyLayerGroupDrawerContent } from "./detail/MyLayerGroupDrawerContent";

export type MyLayerGroupDrawerState = DrawerState<MyLayerGroup, "layer-group">;

export type MyLayerGroupModalState =
    | { type: "none" }
    | { type: "deleteGroup"; group: MyLayerGroup };

interface MyLayerGroupDrawerProps {
    drawerState: MyLayerGroupDrawerState;
}

export function MyLayerGroupDrawer({ drawerState }: MyLayerGroupDrawerProps): ReactElement | null {
    const { closeDrawer, setModal } = useViewDrawerContext<MyLayerGroupModalState>();
    return (
        <DrawerContextProvider onClose={closeDrawer}>
            <SideDrawer open={drawerState.type !== "closed"} onClose={closeDrawer} size="lgx">
                {drawerState.type === "layer-group" && (
                    <MyLayerGroupDrawerContent
                        key={drawerState.entity.id}
                        group={drawerState.entity}
                        onOpenModal={setModal}
                    />
                )}
            </SideDrawer>
        </DrawerContextProvider>
    );
}
