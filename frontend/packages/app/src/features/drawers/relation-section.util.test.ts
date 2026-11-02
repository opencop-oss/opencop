// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { i18n } from "~/i18n/config";
import { baseRelationLabels } from "./relation-section.util";

/** The fields every section supplies beyond its nouns; irrelevant to the title rule. */
const REQUIRED = {
    description: "desc",
    listAriaLabel: "list",
    assignAriaLabel: "pool",
    revokeError: "revoke failed",
    assignError: "assign failed",
};

function titleFor(entityLabel: string, relationLabel: string) {
    return baseRelationLabels(i18n.t, {
        entityLabel,
        relationLabel,
        poolLabel: "things",
        ...REQUIRED,
    }).title;
}

function assignDescriptionFor(entityLabel: string, entityReceivesAccess: boolean) {
    return baseRelationLabels(i18n.t, {
        entityLabel,
        relationLabel: "Layers",
        poolLabel: "layers",
        // Present => the relation carries a role, which selects the role-bearing copy.
        rolePlaceholder: "Select Role",
        entityReceivesAccess,
        ...REQUIRED,
    }).assignDescription;
}

describe("baseRelationLabels — assign grant direction", () => {
    it("grants access TO the entity when it is the resource", () => {
        expect(assignDescriptionFor("Layer", false)).toContain("grant layer access");
    });

    // The Users drawer grants the *user* a role on the picked layers, not the reverse.
    it("grants the entity access when it is the grantee", () => {
        expect(assignDescriptionFor("User", true)).toContain("grant this user access to them");
    });
});

describe("baseRelationLabels — derived section title", () => {
    it("prefixes the entity onto the relation", () => {
        expect(titleFor("Layer", "Members")).toBe("Layer Members");
        expect(titleFor("Data Source", "Users")).toBe("Data Source Users");
    });

    // "Layer" + "Layer Groups" would otherwise read "Layer Layer Groups".
    it("drops the prefix when the relation noun already carries the entity", () => {
        expect(titleFor("Layer", "Layer Groups")).toBe("Layer Groups");
    });

    it("keeps the prefix when the entity merely shares a prefix with the relation", () => {
        // "Layer Group" is not a prefix of "Layers", so this must not collapse.
        expect(titleFor("Layer Group", "Layers")).toBe("Layer Group Layers");
    });
});
