// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { Group } from "~/features/groups/groups.queries";
import {
    audienceUserGroupIds,
    buildAudience,
    confirmationReason,
    isUnresolved,
    type PendingAudienceChange,
    resolveAudienceTargets,
    shortId,
    audienceGroupLabel,
} from "./audience.util";

function group(id: string, name: string): Group {
    return { id, name, description: null, createdAt: "2026-08-01T00:00:00Z", source: "OPENCOP" };
}

describe("audienceUserGroupIds", () => {
    it("reads the targets of a USER_GROUPS audience", () => {
        expect(audienceUserGroupIds({ mode: "USER_GROUPS", userGroupIds: ["a", "b"] })).toEqual([
            "a",
            "b",
        ]);
    });

    it("reports no targets for the audiences that carry none", () => {
        expect(audienceUserGroupIds({ mode: "NONE" })).toEqual([]);
        expect(audienceUserGroupIds({ mode: "ALL" })).toEqual([]);
    });
});

describe("buildAudience", () => {
    it("drops the targets for the modes that carry none", () => {
        expect(buildAudience("NONE", ["a"])).toEqual({ mode: "NONE" });
        expect(buildAudience("ALL", ["a"])).toEqual({ mode: "ALL" });
    });

    it("copies the ids rather than aliasing the caller's array", () => {
        // The caller's array is the one inside the cached record; the built audience is handed to
        // a mutation body, so the two must not be the same object.
        const ids = ["a", "b"];
        const audience = buildAudience("USER_GROUPS", ids);

        expect(audience).toEqual({ mode: "USER_GROUPS", userGroupIds: ["a", "b"] });
        expect(audience.mode === "USER_GROUPS" && audience.userGroupIds).not.toBe(ids);
    });
});

describe("resolveAudienceTargets", () => {
    it("names each id the directory resolves", () => {
        const targets = resolveAudienceTargets(
            ["g1", "g2"],
            [group("g1", "Operations"), group("g2", "Analysts"), group("g3", "Unused")],
        );

        expect(targets).toEqual([
            { id: "g1", name: "Operations" },
            { id: "g2", name: "Analysts" },
        ]);
    });

    /**
     * The invariant the picker exists to protect: dropping an id the directory no longer returns
     * would let an unrelated edit silently revoke access for whoever that group still covers
     * server-side.
     */
    it("keeps an id the directory no longer resolves, with no name", () => {
        const targets = resolveAudienceTargets(["g1", "gone"], [group("g1", "Operations")]);

        expect(targets).toHaveLength(2);
        expect(targets[1]).toEqual({ id: "gone", name: undefined });
        expect(isUnresolved(targets[1]!)).toBe(true);
        expect(isUnresolved(targets[0]!)).toBe(false);
    });

    it("keeps every id when the directory has not loaded", () => {
        const targets = resolveAudienceTargets(["g1", "g2"], undefined);

        expect(targets.map((target) => target.id)).toEqual(["g1", "g2"]);
        expect(targets.every(isUnresolved)).toBe(true);
    });
});

/**
 * Disambiguation moved here from an `ambiguous` flag on the target: that flag only saw the
 * *selected* ids, while the label sees the whole pool, and only the label is ever rendered.
 */
describe("audienceGroupLabel", () => {
    const local = (id: string, name: string) => ({ id, name, source: "OPENCOP" as const });
    const scim = (id: string, name: string) => ({ id, name, source: "SCIM" as const });
    const labels = { scimLabel: "IdP-managed" };

    it("shows the bare name when nothing else shares it", () => {
        expect(
            audienceGroupLabel(local("g1", "Operations"), { total: 1, scim: 0, ...labels }),
        ).toBe("Operations");
    });

    it("marks the provisioned side of a local/SCIM collision", () => {
        const counts = { total: 2, scim: 1, ...labels };
        expect(audienceGroupLabel(scim("g2", "Operations"), counts)).toBe(
            "Operations — IdP-managed",
        );
        // Its local twin needs no marker; one badge separates the pair.
        expect(audienceGroupLabel(local("g1", "Operations"), counts)).toBe("Operations");
    });

    it("falls back to the id when the source cannot separate the collision", () => {
        // Two SCIM groups sharing a name — reachable only after an IdP repoint leaves stale rows.
        expect(audienceGroupLabel(scim("g2", "Operations"), { total: 2, scim: 2, ...labels })).toBe(
            "Operations — IdP-managed (g2)",
        );
    });
});

describe("shortId", () => {
    it("takes the leading eight characters of a UUID", () => {
        expect(shortId("3f2a1b4c-5d6e-7f80-9a1b-2c3d4e5f6071")).toBe("3f2a1b4c");
    });

    it("returns a shorter id unchanged", () => {
        expect(shortId("abc")).toBe("abc");
    });
});

describe("confirmationReason", () => {
    function change(overrides: Partial<PendingAudienceChange> = {}): PendingAudienceChange {
        return {
            consumerClass: "FRONTEND_ONLY",
            storedMode: "NONE",
            storedGroupIds: [],
            nextMode: "ALL",
            nextGroupIds: [],
            ...overrides,
        };
    }

    it("does not confirm enabling a feature that reached nobody", () => {
        // The common development action. Confirming it would train an admin to click through the
        // dialog, which costs more than it buys on the changes that matter.
        expect(confirmationReason(change({ storedMode: "NONE", nextMode: "ALL" }))).toBeNull();
        expect(
            confirmationReason(
                change({ storedMode: "NONE", nextMode: "USER_GROUPS", nextGroupIds: ["g1"] }),
            ),
        ).toBeNull();
    });

    it("confirms turning a live feature off", () => {
        expect(confirmationReason(change({ storedMode: "ALL", nextMode: "NONE" }))).toBe(
            "turningOff",
        );
        expect(
            confirmationReason(
                change({
                    storedMode: "USER_GROUPS",
                    storedGroupIds: ["g1"],
                    nextMode: "NONE",
                }),
            ),
        ).toBe("turningOff");
        // An audience with no targets reaches nobody, so arriving there is still turning it off.
        expect(
            confirmationReason(
                change({ storedMode: "ALL", nextMode: "USER_GROUPS", nextGroupIds: [] }),
            ),
        ).toBe("turningOff");
    });

    /** A stored audience that already reaches nobody has no access left to take away. */
    it("does not confirm turning off a feature that already reached nobody", () => {
        expect(
            confirmationReason(
                change({ storedMode: "USER_GROUPS", storedGroupIds: [], nextMode: "NONE" }),
            ),
        ).toBeNull();
    });

    /**
     * The transition the guardrail exists for: `rtsp` is `ALL` in production, and scoping it to a
     * group list revokes it from everyone outside those groups. It reaches someone afterwards, so
     * it is not "turning off" — but somebody loses access, which is the part worth confirming.
     */
    it("confirms narrowing from everyone to a group list", () => {
        expect(
            confirmationReason(
                change({ storedMode: "ALL", nextMode: "USER_GROUPS", nextGroupIds: ["g1"] }),
            ),
        ).toBe("narrowingAccess");
    });

    it("confirms dropping a group from a group audience", () => {
        expect(
            confirmationReason(
                change({
                    storedMode: "USER_GROUPS",
                    storedGroupIds: ["g1", "g2"],
                    nextMode: "USER_GROUPS",
                    nextGroupIds: ["g1"],
                }),
            ),
        ).toBe("narrowingAccess");
    });

    /** Exchanging one group for another still revokes access from the group that left. */
    it("confirms exchanging one group for another", () => {
        expect(
            confirmationReason(
                change({
                    storedMode: "USER_GROUPS",
                    storedGroupIds: ["g1"],
                    nextMode: "USER_GROUPS",
                    nextGroupIds: ["g2"],
                }),
            ),
        ).toBe("narrowingAccess");
    });

    it("does not confirm widening a group audience", () => {
        expect(
            confirmationReason(
                change({
                    storedMode: "USER_GROUPS",
                    storedGroupIds: ["g1"],
                    nextMode: "USER_GROUPS",
                    nextGroupIds: ["g1", "g2"],
                }),
            ),
        ).toBeNull();
        // Widening to everyone takes access from nobody.
        expect(
            confirmationReason(
                change({ storedMode: "USER_GROUPS", storedGroupIds: ["g1"], nextMode: "ALL" }),
            ),
        ).toBeNull();
    });

    it("confirms any change to a backend-enforced feature", () => {
        expect(
            confirmationReason(
                change({ consumerClass: "BACKEND_ENFORCED", storedMode: "NONE", nextMode: "ALL" }),
            ),
        ).toBe("backendEnforced");
    });

    /**
     * The consequence is what the dialog has to name, and "everyone loses access" outranks "the
     * server enforces this" — so the specific reason wins over the class-based one.
     */
    it("reports the more specific reason when a backend-enforced feature loses reach", () => {
        expect(
            confirmationReason(
                change({ consumerClass: "BACKEND_ENFORCED", storedMode: "ALL", nextMode: "NONE" }),
            ),
        ).toBe("turningOff");
        expect(
            confirmationReason(
                change({
                    consumerClass: "BACKEND_ENFORCED",
                    storedMode: "ALL",
                    nextMode: "USER_GROUPS",
                    nextGroupIds: ["g1"],
                }),
            ),
        ).toBe("narrowingAccess");
    });

    it("does not confirm a backend-enforced save that changes nothing", () => {
        expect(
            confirmationReason(
                change({
                    consumerClass: "BACKEND_ENFORCED",
                    storedMode: "USER_GROUPS",
                    storedGroupIds: ["g1"],
                    nextMode: "USER_GROUPS",
                    nextGroupIds: ["g1"],
                }),
            ),
        ).toBeNull();
    });

    /**
     * The group picker hands back its own selection order, so an admin who unchecks a group and
     * rechecks it can submit the same set in a different order. That is not a change, and raising a
     * dialog for it is exactly the click-through training this predicate avoids.
     */
    it("does not confirm the same group set in a different order", () => {
        expect(
            confirmationReason(
                change({
                    consumerClass: "BACKEND_ENFORCED",
                    storedMode: "USER_GROUPS",
                    storedGroupIds: ["g1", "g2"],
                    nextMode: "USER_GROUPS",
                    nextGroupIds: ["g2", "g1"],
                }),
            ),
        ).toBeNull();
    });
});
