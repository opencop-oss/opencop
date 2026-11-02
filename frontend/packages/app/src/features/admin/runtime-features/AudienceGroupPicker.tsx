// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field, Stack, Text } from "@chakra-ui/react";
import { OcCombobox } from "@opencop/ui";
import { useQuery } from "@tanstack/react-query";
import { type ReactElement, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { groupQueries } from "~/features/groups/groups.queries";
import { audienceGroupLabel, isUnresolved, resolveAudienceTargets } from "./audience.util";

interface AudienceGroupPickerProps {
    selectedIds: readonly string[];
    onChange: (userGroupIds: string[]) => void;
    disabled?: boolean;
}

/**
 * Group targets for the `USER_GROUPS` mode.
 *
 * Selected ids are the state of record, not resolved group objects, so an id the directory no
 * longer returns survives every edit until an administrator removes it explicitly. Such an id is
 * offered back as its own option so it still renders as a removable tag instead of vanishing.
 */
export function AudienceGroupPicker({
    selectedIds,
    onChange,
    disabled,
}: AudienceGroupPickerProps): ReactElement {
    const { t } = useTranslation();
    const { data: groups } = useQuery({ ...groupQueries.adminList(), enabled: !disabled });

    const unresolved = useMemo(
        () => resolveAudienceTargets(selectedIds, groups).filter(isUnresolved),
        [selectedIds, groups],
    );

    const selectedValues = useMemo(() => [...selectedIds], [selectedIds]);

    const items = useMemo(() => {
        const nameCounts = new Map<string, number>();
        const scimCounts = new Map<string, number>();
        for (const group of groups ?? []) {
            nameCounts.set(group.name, (nameCounts.get(group.name) ?? 0) + 1);
            if (group.source === "SCIM") {
                scimCounts.set(group.name, (scimCounts.get(group.name) ?? 0) + 1);
            }
        }

        return [
            ...(groups ?? []).map((group) => ({
                // Same-named SCIM and application-managed groups are schema-legal. The source
                // separates the usual local-vs-provisioned pair; the id is still needed when two
                // groups of the *same* source collide, which a combobox label cannot show as a badge.
                label: audienceGroupLabel(group, {
                    total: nameCounts.get(group.name) ?? 0,
                    scim: scimCounts.get(group.name) ?? 0,
                    scimLabel: t("adminGroups.scimManaged"),
                }),
                value: group.id,
            })),
            ...unresolved.map((target) => ({
                label: `${t("runtimeFeatures.unresolvedGroup")} (${target.id})`,
                value: target.id,
            })),
        ];
    }, [groups, unresolved, t]);

    return (
        <Stack gap={2}>
            <Field.Root>
                <Field.Label>{t("runtimeFeatures.audienceUserGroups")}</Field.Label>
                <OcCombobox
                    items={items}
                    // Copied because the widget's value type is mutable; the ids themselves come
                    // from the cached record and must not be handed out for in-place edits.
                    value={selectedValues}
                    onValueChange={(value) => onChange(value as string[])}
                    multiple
                    placeholder={t("runtimeFeatures.searchGroups")}
                    emptyMessage={t("common.noResults")}
                    disabled={disabled}
                />
            </Field.Root>

            {unresolved.length > 0 && (
                <Text fontSize="xs" color="fg.muted">
                    {t("runtimeFeatures.unresolvedGroupHelp")}
                </Text>
            )}
        </Stack>
    );
}
