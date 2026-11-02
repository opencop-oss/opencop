// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { Box } from "@chakra-ui/react";
import type { TFunction } from "i18next";
import type { DrawerSideNavSection } from "@opencop/ui";
import { DrawerSectionHeader } from "@opencop/ui";
import { FaList } from "react-icons/fa";
import type { DrawerRelationSectionLabels } from "./drawer-relation.model";

interface RelationLabelOverrides {
    /**
     * The drawer's own entity, singular and capitalized — "Layer", "Data Source".
     * Feeds the derived panel headings, so it reads as the user's noun for the
     * thing the drawer is about, not the API's.
     */
    entityLabel: string;
    /** What this section lists, plural and capitalized — "Members", "Layers". */
    relationLabel: string;
    /** What the picker draws from, lowercase — "directory users", "layer groups". */
    poolLabel: string;
    /**
     * The drawer's entity is the grantee rather than the resource: this *user* gains a role
     * on the selected layers, not the other way round. Flips the assign copy, which would
     * otherwise describe the grant backwards. Read it off the section's own mutation —
     * `assignUser({ layerId, userId, role })` grants the user, so the user receives access.
     */
    entityReceivesAccess?: boolean;
    /** One line stating what the list holds and what being in it grants. */
    description: string;
    listAriaLabel: string;
    assignAriaLabel: string;
    revokeError: string;
    assignError: string;
    /** Omitted for no-role relations (members without a role). */
    rolePlaceholder?: string;
    /** Tooltip shown when the Revoke action is disabled (e.g. last-owner guard). */
    revokeDisabledReason?: string;
}

/**
 * Builds a {@link DrawerRelationSectionLabels} from a section's nouns.
 *
 * Panel headings are *derived* rather than written per section — "Layer Members",
 * "Assign Members to Layer", "Back to Members" all fall out of `entityLabel` and
 * `relationLabel` — so every drawer names itself the same way and a new section
 * cannot drift into its own phrasing. The prose `description` is the one piece
 * each section supplies, because what membership actually grants differs per
 * relation and cannot be templated.
 *
 * Whether the assign flow mentions choosing a role follows `rolePlaceholder`,
 * which is the same signal the picker uses to decide if a role select renders.
 */
export function baseRelationLabels(
    t: TFunction,
    overrides: RelationLabelOverrides,
): DrawerRelationSectionLabels {
    const { entityLabel, relationLabel, poolLabel, entityReceivesAccess, ...rest } = overrides;
    const nouns = { entity: entityLabel, relation: relationLabel, pool: poolLabel };
    const hasRole = overrides.rolePlaceholder != null;

    // "Layer" + "Layer Groups" would title the panel "Layer Layer Groups". Where the
    // relation noun already carries the entity, the prefix is pure stutter — and the
    // entity is named twice over anyway, by the drawer header and the active rail item.
    // Locales that join the nouns rather than prefixing them (fr's "{{relation}} —
    // {{entity}}") never overlap, so this simply doesn't fire for them.
    const title = relationLabel.startsWith(entityLabel)
        ? relationLabel
        : t("adminDialogs.sectionTitle", nouns);

    return {
        // Spread first so the derived headings below always win. A field later added to
        // RelationLabelOverrides that happened to reuse one of their names would otherwise
        // silently replace it at every call site, and type-check cleanly doing it — both
        // sides are string. Spread order is what makes this function's contract true.
        ...rest,
        title,
        assignTitle: t("adminDialogs.assignTitle", nouns),
        assignDescription: hasRole
            ? t(
                  entityReceivesAccess
                      ? "adminDialogs.assignDescriptionWithRoleToEntity"
                      : "adminDialogs.assignDescriptionWithRole",
                  nouns,
              )
            : t("adminDialogs.assignDescription", nouns),
        changeRoleTitle: t("adminDialogs.changeRoleTitle", nouns),
        changeRoleDescription: t("adminDialogs.changeRoleDescription", nouns),
        assign: t("adminDialogs.assign"),
        revoke: t("adminDialogs.revokeAccess"),
        revokeConfirm: {
            title: t("adminDialogs.revokeConfirmTitle"),
            message: (count) => t("adminDialogs.revokeConfirmMessage", { count }),
        },
        add: t("adminDialogs.add"),
        back: t("adminDialogs.backTo", nouns),
        changeRole: t("adminDialogs.changeRole"),
        changeRoleError: t("adminDialogs.changeRoleFailed"),
    };
}

/**
 * The standard "Overview" side-nav section. `content` is usually a compact
 * {@link DetailTable} but can be any node (e.g. the mapper drawer adds a
 * version-history table below its details).
 *
 * `entityLabel` is the drawer's own noun, singular and capitalized ("Layer"),
 * used to derive the panel heading the same way the relation sections do.
 */
export function createOverviewSection(
    t: TFunction,
    content: ReactNode,
    entityLabel: string,
): DrawerSideNavSection {
    return {
        key: "overview",
        label: t("adminDialogs.overview"),
        icon: <FaList />,
        content: (
            <>
                <DrawerSectionHeader
                    title={t("adminDialogs.overviewTitle", { entity: entityLabel })}
                    description={t("adminDialogs.overviewDescription", { entity: entityLabel })}
                />
                {/* Detail rows need more air under the heading than a relation panel does:
                    there the header runs straight into a toolbar, which carries its own
                    padding, so the shared header margin alone is enough. */}
                <Box mt={2}>{content}</Box>
            </>
        ),
    };
}

/**
 * Reads the ids an `onAssign` rejection reports as already committed.
 *
 * When an assign fans out to several targets, some can succeed while others fail. The picker stays
 * open so the user can retry, so it must drop the committed ids first — otherwise a retry re-sends
 * them, and if the user changed the role in between (the natural "fix and retry" move) the already
 * committed rows are silently rewritten to the new role.
 *
 * Duck-typed rather than tied to a specific error class: any rejection carrying a `succeededIds`
 * string array opts into pruning, and rejections that don't (the all-or-nothing case) read as `[]`.
 */
export function succeededAssignIds(error: unknown): string[] {
    const ids = (error as { succeededIds?: unknown } | null)?.succeededIds;
    if (!Array.isArray(ids)) return [];
    return ids.filter((id): id is string => typeof id === "string");
}
