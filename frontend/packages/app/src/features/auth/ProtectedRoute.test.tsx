// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { useLocation } from "react-router";
import { render, screen, createTestUser } from "~/test";
import { ProtectedRoute } from "./ProtectedRoute";

function LocationDisplay() {
    const location = useLocation();
    return <div data-testid="location">{location.pathname}</div>;
}

describe("ProtectedRoute", () => {
    it("renders children when no role is required", () => {
        render(
            <ProtectedRoute>
                <div data-testid="guarded-content" />
            </ProtectedRoute>,
            { user: createTestUser({ authorities: [] }) },
        );

        expect(screen.getByTestId("guarded-content")).toBeInTheDocument();
    });

    it("renders children when the user has the required role", () => {
        render(
            <>
                <ProtectedRoute roles={["ROLE_admin"]}>
                    <div data-testid="guarded-content" />
                </ProtectedRoute>
                <LocationDisplay />
            </>,
            { user: createTestUser({ authorities: ["ROLE_admin"] }) },
        );

        expect(screen.getByTestId("guarded-content")).toBeInTheDocument();
        expect(screen.getByTestId("location")).toHaveTextContent("/");
    });

    it("redirects to /map when the authenticated user lacks the required role", () => {
        render(
            <>
                <ProtectedRoute roles={["ROLE_admin"]}>
                    <div data-testid="guarded-content" />
                </ProtectedRoute>
                <LocationDisplay />
            </>,
            { user: createTestUser({ authorities: [] }) },
        );

        expect(screen.queryByTestId("guarded-content")).not.toBeInTheDocument();
        expect(screen.getByTestId("location")).toHaveTextContent("/map");
    });

    it("checks requiredAuthority when roles is not provided", () => {
        render(
            <>
                <ProtectedRoute requiredAuthority="ROLE_admin">
                    <div data-testid="guarded-content" />
                </ProtectedRoute>
                <LocationDisplay />
            </>,
            { user: createTestUser({ authorities: [] }) },
        );

        expect(screen.queryByTestId("guarded-content")).not.toBeInTheDocument();
        expect(screen.getByTestId("location")).toHaveTextContent("/map");
    });
});
