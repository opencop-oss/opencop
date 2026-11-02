// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { SideDrawer } from "@opencop/ui";
import { DrawerContextProvider } from "~/features/drawers/DrawerContext";
import { useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import type { DrawerState } from "~/features/drawers/drawer-state.model";
import type { Group } from "./groups.queries";
import { AdminGroupDrawerContent } from "./detail/AdminGroupDrawerContent";

export type AdminGroupDrawerState = DrawerState<Group, "group">;

export type AdminGroupModalState =
    | { type: "none" }
    | { type: "addGroup" }
    | { type: "deleteGroup"; group: Group };

interface AdminGroupDrawerProps {
    drawerState: AdminGroupDrawerState;
}

export function AdminGroupDrawer({ drawerState }: AdminGroupDrawerProps): ReactElement | null {
    const { closeDrawer, setModal } = useViewDrawerContext<AdminGroupModalState>();
    return (
        <DrawerContextProvider onClose={closeDrawer}>
            <SideDrawer open={drawerState.type !== "closed"} onClose={closeDrawer} size="lgx">
                {drawerState.type === "group" && (
                    <AdminGroupDrawerContent
                        key={drawerState.entity.id}
                        group={drawerState.entity}
                        onOpenModal={setModal}
                    />
                )}
            </SideDrawer>
        </DrawerContextProvider>
    );
}
