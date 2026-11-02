// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import type { User } from "@opencop/api";
import { SideDrawer } from "@opencop/ui";
import { DrawerContextProvider } from "~/features/drawers/DrawerContext";
import { useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import type { DrawerState } from "~/features/drawers/drawer-state.model";
import { UserDrawerContent } from "./detail/UserDrawerContent";

export type UserDrawerState = DrawerState<User, "user">;

interface UserDrawerProps {
    drawerState: UserDrawerState;
}

export function UserDrawer({ drawerState }: UserDrawerProps): ReactElement | null {
    const { closeDrawer } = useViewDrawerContext<{ type: string }>();
    return (
        <DrawerContextProvider onClose={closeDrawer}>
            <SideDrawer open={drawerState.type !== "closed"} onClose={closeDrawer} size="lgx">
                {drawerState.type === "user" && (
                    <UserDrawerContent key={drawerState.entity.id} user={drawerState.entity} />
                )}
            </SideDrawer>
        </DrawerContextProvider>
    );
}
