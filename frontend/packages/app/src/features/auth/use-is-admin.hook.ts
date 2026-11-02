// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useAuth } from "./AuthContext";
import type { AuthUser } from "@opencop/api";

const ADMIN_AUTHORITY = "ROLE_admin";

export const useIsAdmin = () => {
    const { user } = useAuth();
    return getIsAdmin(user);
};

/**
 * Checks if the user has admin authority.
 * Exported for testing. You probably want to use the hook `useIsAdmin()` instead.
 */
export function getIsAdmin(user: AuthUser | null | undefined): boolean {
    if (!user?.authorities) return false;
    return user.authorities.includes(ADMIN_AUTHORITY);
}
