// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createContext, use, useMemo, type ReactNode } from "react";
import { useLocation } from "react-router";
import { TbMap, TbServer, TbShield, TbStack2 } from "react-icons/tb";
import { useIsAdmin } from "~/features/auth/use-is-admin.hook";

export type SideBarContextType = {
    modules: { name: string; icon: ReactNode }[];
    moduleOpen: string;
    width: number;
};

const SidebarContext = createContext<SideBarContextType | null>(null);

export function useSidebar(): SideBarContextType {
    const ctx = use(SidebarContext);
    if (!ctx) throw new Error("useSidebar must be used within a SidebarProvider.");
    return ctx;
}

const SIDEBAR_WIDTH = 16;

function moduleFromPath(pathname: string): string {
    if (pathname === "/" || pathname === "/map") return "map";
    if (pathname === "/layers") return "layers";
    if (pathname === "/data-sources") return "data-sources";
    if (pathname.startsWith("/admin")) return "admin";
    return "";
}

export function SidebarProvider({ children }: { children?: React.ReactNode }) {
    const location = useLocation();
    const moduleOpen = moduleFromPath(location.pathname);
    const isAdmin = useIsAdmin();
    const modules = useMemo(() => {
        const m: { name: string; icon: ReactNode }[] = [
            { name: "map", icon: <TbMap /> },
            { name: "layers", icon: <TbStack2 /> },
            { name: "data-sources", icon: <TbServer /> },
        ];
        if (isAdmin) m.push({ name: "admin", icon: <TbShield /> });
        return m;
    }, [isAdmin]);

    const value = useMemo(
        () => ({ modules, moduleOpen, width: SIDEBAR_WIDTH }),
        [modules, moduleOpen],
    );

    return <SidebarContext value={value}>{children}</SidebarContext>;
}
