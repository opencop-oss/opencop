// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Drawer } from "@chakra-ui/react";
import type { ReactNode } from "react";

/**
 * Built-in Chakra drawer sizes plus `"lgx"`, a custom size registered on the
 * drawer slot recipe (maxW 3xl, between the built-in `lg` and `xl`). The custom
 * token isn't in Chakra's static size union, so it's cast at the `Drawer.Root`
 * boundary — the recipe resolves it at runtime.
 */
type SideDrawerSize = Drawer.RootProps["size"] | "lgx";

interface SideDrawerProps {
    open: boolean;
    onClose: () => void;
    children?: ReactNode;
    /** Chakra drawer size token (incl. custom "lgx"). Defaults to "md". */
    size?: SideDrawerSize;
    /**
     * Refuses every dismissal route — close trigger, Escape, and outside interaction. Use while the
     * drawer owns an in-flight request whose result has nowhere else to land.
     */
    closeDisabled?: boolean;
}

export function SideDrawer({
    open,
    onClose,
    children,
    size = "md",
    closeDisabled = false,
}: SideDrawerProps) {
    return (
        <Drawer.Root
            open={open}
            onOpenChange={(e) => {
                if (!e.open && !closeDisabled) onClose();
            }}
            closeOnEscape={!closeDisabled}
            closeOnInteractOutside={!closeDisabled}
            placement="end"
            size={size as Drawer.RootProps["size"]}
        >
            <Drawer.Backdrop h="100%" w="100%" />
            <Drawer.Positioner h="100%" w="100%">
                <Drawer.Content maxH="100%">
                    <Drawer.CloseTrigger disabled={closeDisabled} />
                    {children}
                </Drawer.Content>
            </Drawer.Positioner>
        </Drawer.Root>
    );
}
