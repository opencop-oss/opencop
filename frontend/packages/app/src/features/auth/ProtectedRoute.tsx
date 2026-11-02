// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// components/ProtectedRoute.tsx
import type { PropsWithChildren } from "react";
import { Navigate } from "react-router";
import { useAuth } from "~/features/auth/AuthContext";

interface ProtectedRouteProps {
    requiredAuthority?: string;
    roles?: string[];
}

export function ProtectedRoute({
    children,
    requiredAuthority,
    roles,
}: PropsWithChildren<ProtectedRouteProps>) {
    const { user } = useAuth();

    // Auth is guaranteed by AuthProvider - only check roles
    const requiredRole = requiredAuthority ?? roles?.[0];
    if (requiredRole && !user?.authorities?.includes(requiredRole)) {
        return <Navigate to="/map" replace />;
    }

    return children;
}
