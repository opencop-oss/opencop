// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { getLayerGroupPermissions } from "./layer-groups.util";

/**
 * These mirror `LayerGroupPolicy.assertContainerRole`, which returns early for a global admin
 * before consulting any role. Where the UI is stricter than that, an admin is shown a disabled
 * control for an operation the server would have accepted.
 */
describe("getLayerGroupPermissions", () => {
    it("grants an OWNER everything", () => {
        expect(getLayerGroupPermissions("OWNER")).toEqual({
            canAttachLayers: true,
            canManageMembers: true,
            canEditGroup: true,
            canDeleteGroup: true,
        });
    });

    it("withholds delete from an ADMIN", () => {
        expect(getLayerGroupPermissions("ADMIN")).toMatchObject({
            canManageMembers: true,
            canEditGroup: true,
            canDeleteGroup: false,
        });
    });

    it("gives a MAINTAINER layers only", () => {
        expect(getLayerGroupPermissions("MAINTAINER")).toEqual({
            canAttachLayers: true,
            canManageMembers: false,
            canEditGroup: false,
            canDeleteGroup: false,
        });
    });

    it("gives a MEMBER nothing", () => {
        expect(getLayerGroupPermissions("MEMBER")).toEqual({
            canAttachLayers: false,
            canManageMembers: false,
            canEditGroup: false,
            canDeleteGroup: false,
        });
    });

    /**
     * The two cases that motivated the flag. A global admin holds no role on a PRIVATE group they
     * do not belong to, and only the GLOBAL-scope fallback of MEMBER on one they can see, yet the
     * server accepts every operation from them.
     */
    it.each(["MEMBER", "MAINTAINER", "ADMIN", "OWNER"] as const)(
        "grants a global admin everything regardless of a %s container role",
        (containerRole) => {
            expect(getLayerGroupPermissions(containerRole, true)).toEqual({
                canAttachLayers: true,
                canManageMembers: true,
                canEditGroup: true,
                canDeleteGroup: true,
            });
        },
    );
});
