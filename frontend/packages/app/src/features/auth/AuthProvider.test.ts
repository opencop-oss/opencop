// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { getIsAdmin } from "./use-is-admin.hook";
import type { AuthUser } from "@opencop/api";

const makeUser = (authorities: string[]): AuthUser => ({
    authenticated: true,
    user: {
        id: "1",
        subject: "s",
        username: "u",
        displayName: "d",
        email: "e",
    },
    idp: { issuer: "https://example.com" },
    authorities,
    claims: {},
});

describe("getIsAdmin", () => {
    it.each`
        authorities                                        | expected
        // User with admin flag in database - backend adds ROLE_admin
        ${["ROLE_admin"]}                                  | ${true}
        ${["SCOPE_openid", "ROLE_admin", "SCOPE_profile"]} | ${true}
        ${["SCOPE_openid", "SCOPE_profile"]}               | ${false}
        ${[]}                                              | ${false}
        ${["ROLE_Admin", "ROLE_ADMIN"]}                    | ${false}
        ${["admin"]}                                       | ${false}
    `("$authorities returns $expected", ({ authorities, expected }) => {
        expect(getIsAdmin(makeUser(authorities))).toBe(expected);
    });

    it.each`
        user
        ${null}
        ${undefined}
    `("returns false for $user user", ({ user }) => {
        expect(getIsAdmin(user)).toBe(false);
    });
});
