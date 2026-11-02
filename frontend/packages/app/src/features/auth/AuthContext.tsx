// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
    createContext,
    use,
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
    type ReactNode,
} from "react";
import { Navigate } from "react-router";
import {
    ApiError,
    AUTH_QUERY_KEY,
    fetchMe,
    globalSignOut,
    clearCsrfToken,
    getCachedUser,
    clearCachedUser,
    wasRecentlyActive,
    clearLastActivity,
} from "@opencop/api";
import type { AuthUser } from "@opencop/api";
import { AppLoadingScreen } from "~/features/errors/AppLoadingScreen";
import { redirectToLogin, getCurrentUrl } from "./redirect-to-login";

export type AuthContextType = {
    user: AuthUser | null;
    loading: boolean;
    login: () => void;
    logout: () => Promise<void>;
    handleSessionExpiry: () => void;
};

const AuthContext = createContext<AuthContextType | null>(null);

export function useAuth(): AuthContextType {
    const ctx = use(AuthContext);
    if (!ctx) throw new Error("useAuth must be used within an AuthProvider.");
    return ctx;
}

const LOGOUT_URL = "/logged-out";

type TerminalOutcome = "global-sign-out" | "session-expiry";

export const AuthProvider = ({ children }: { children: ReactNode }) => {
    const queryClient = useQueryClient();
    const [cachedUser] = useState(getCachedUser);
    const [recentlyActive] = useState(wasRecentlyActive);
    const terminalOutcomeRef = useRef<TerminalOutcome | null>(null);
    const [sessionExpiry, setSessionExpiry] = useState<{ returnUrl: string | null } | null>(null);

    const claimTerminalOutcome = useCallback((outcome: TerminalOutcome): boolean => {
        if (terminalOutcomeRef.current !== null) return false;
        terminalOutcomeRef.current = outcome;
        return true;
    }, []);

    const handleSessionExpiry = useCallback(() => {
        if (!claimTerminalOutcome("session-expiry")) return;

        clearCsrfToken();
        void queryClient.cancelQueries();
        const returnUrl = getCurrentUrl();
        clearCachedUser();
        clearLastActivity();
        setSessionExpiry({ returnUrl });
    }, [claimTerminalOutcome, queryClient]);

    const fetchAuthSession = useCallback(async () => {
        try {
            return await fetchMe();
        } catch (error) {
            if (recentlyActive) handleSessionExpiry();
            throw error;
        }
    }, [handleSessionExpiry, recentlyActive]);

    const {
        data: user,
        isLoading,
        error,
    } = useQuery({
        queryKey: AUTH_QUERY_KEY,
        queryFn: fetchAuthSession,
        enabled: sessionExpiry === null,
        retry: false,
        // if a session expires server-side, it could take up to 5 minutes for the client
        // to notice (unless another API call triggers the 401).
        staleTime: 5 * 60 * 1000,
        // Use cached user for instant render on new tabs while revalidating
        placeholderData: cachedUser,
    });

    useEffect(() => {
        if (sessionExpiry === null) return;
        // Evict only after the auth query is disabled so removal cannot trigger revalidation.
        queryClient.removeQueries();
    }, [queryClient, sessionExpiry]);

    // Check if user was recently active (within 30 min).
    // Used to distinguish "session expired" (show message) vs "session expired while away" (go to OAuth).
    // Activity is tracked on successful API calls in queryClient.ts.
    // Redirect new visitors (or returning after long absence) to OAuth
    const isUnauthenticated = !isLoading && (error || !user);
    const isNewVisitor = isUnauthenticated && !recentlyActive;
    useEffect(() => {
        if (isNewVisitor) {
            redirectToLogin();
        }
    }, [isNewVisitor]);

    const login = useCallback(() => {
        redirectToLogin();
    }, []);

    const logout = useCallback(async () => {
        try {
            await globalSignOut();
        } catch (error) {
            if (error instanceof ApiError && error.status === 401) {
                handleSessionExpiry();
                return;
            }
            throw error;
        }

        if (!claimTerminalOutcome("global-sign-out")) return;

        clearCsrfToken();
        void queryClient.cancelQueries();
        clearCachedUser();
        clearLastActivity();
        sessionStorage.clear();
        // Hard navigation to avoid race condition with AuthProvider's redirect
        window.location.assign(`${LOGOUT_URL}?reason=logout`);
    }, [claimTerminalOutcome, handleSessionExpiry, queryClient]);

    // Session expired while user was active - show friendly message and clear cached data
    const isSessionExpired = isUnauthenticated && recentlyActive;

    const value = useMemo(
        () => ({ user: user!, loading: isLoading, login, logout, handleSessionExpiry }),
        [user, isLoading, login, logout, handleSessionExpiry],
    );

    // Skip loading screen if we have cached user data
    const hasCachedData = !!cachedUser;

    return (
        <AppLoadingScreen
            loading={isLoading || isNewVisitor}
            skip={hasCachedData}
            message="Loading your session..."
        >
            {sessionExpiry ? (
                <Navigate
                    to={`${LOGOUT_URL}?${new URLSearchParams({ reason: "expired", returnUrl: sessionExpiry.returnUrl ?? "" })}`}
                    replace
                />
            ) : isSessionExpired ? null : (
                <AuthContext value={value}>{children}</AuthContext>
            )}
        </AppLoadingScreen>
    );
};
