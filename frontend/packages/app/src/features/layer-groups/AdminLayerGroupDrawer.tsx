// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { SideDrawer } from "@opencop/ui";
import { DrawerContextProvider } from "~/features/drawers/DrawerContext";
import { useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import type { DrawerState } from "~/features/drawers/drawer-state.model";
import type { LayerGroup } from "./layer-groups.queries";
import { AdminLayerGroupDrawerContent } from "./detail/AdminLayerGroupDrawerContent";

export type AdminLayerGroupDrawerState = DrawerState<LayerGroup, "layer-group">;

export type AdminLayerGroupModalState =
    | { type: "none" }
    | { type: "deleteGroup"; group: LayerGroup };

interface AdminLayerGroupDrawerProps {
    drawerState: AdminLayerGroupDrawerState;
}

export function AdminLayerGroupDrawer({
    drawerState,
}: AdminLayerGroupDrawerProps): ReactElement | null {
    const { closeDrawer, setModal } = useViewDrawerContext<AdminLayerGroupModalState>();
    return (
        <DrawerContextProvider onClose={closeDrawer}>
            <SideDrawer open={drawerState.type !== "closed"} onClose={closeDrawer} size="lgx">
                {drawerState.type === "layer-group" && (
                    <AdminLayerGroupDrawerContent
                        key={drawerState.entity.id}
                        group={drawerState.entity}
                        onOpenModal={setModal}
                    />
                )}
            </SideDrawer>
        </DrawerContextProvider>
    );
}
