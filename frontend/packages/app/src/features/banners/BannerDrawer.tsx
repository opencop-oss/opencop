// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import type { Banner } from "@opencop/api";
import { SideDrawer } from "@opencop/ui";
import { DrawerContextProvider } from "~/features/drawers/DrawerContext";
import { useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import type { DrawerState } from "~/features/drawers/drawer-state.model";
import { BannerDrawerContent } from "./detail/BannerDrawerContent";

export type BannerDrawerState = DrawerState<Banner, "banner">;

export type BannerModalState =
    | { type: "none" }
    | { type: "addBanner" }
    | { type: "deleteBanner"; banner: Banner };

interface BannerDrawerProps {
    drawerState: BannerDrawerState;
}

export function BannerDrawer({ drawerState }: BannerDrawerProps): ReactElement | null {
    const { closeDrawer, setModal } = useViewDrawerContext<BannerModalState>();
    return (
        <DrawerContextProvider onClose={closeDrawer}>
            <SideDrawer open={drawerState.type !== "closed"} onClose={closeDrawer} size="lgx">
                {drawerState.type === "banner" && (
                    <BannerDrawerContent
                        key={drawerState.entity.id}
                        banner={drawerState.entity}
                        onOpenModal={setModal}
                    />
                )}
            </SideDrawer>
        </DrawerContextProvider>
    );
}
