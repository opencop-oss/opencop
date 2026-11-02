// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Center, Flex, Text } from "@chakra-ui/react";
import { getErrorMessage, type MapperProfile } from "@opencop/api";
import {
    ConfirmDialog,
    DetailTable,
    DrawerSideNavLayout,
    ResourceTable,
    ResourceTypeBadge,
    Tooltip,
    createColumnHelper,
} from "@opencop/ui";
import type { DetailTableRow, DrawerSideNavSection } from "@opencop/ui";
import { useMemo, useState, type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { FaArchive, FaCopy, FaPencilAlt } from "react-icons/fa";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { profileMutations, profileQueries } from "../data-source-profiles.queries";
import {
    ENTITY_BADGE_CONFIG,
    MAPPER_BUILDER_PATH,
    stripArchiveSuffix,
} from "../data-resources.util";
import type { RelatedEntity } from "../data-resources.util";
import { ResourceTypeIcon } from "../ResourceTypeIcon";
import { useToast } from "~/features/notifications/use-toast.hook";
import { DrawerToolbarButton } from "~/features/drawers/DrawerToolbarButton";
import { createOverviewSection } from "~/features/drawers/relation-section.util";
import { useAuditHistorySection } from "~/features/drawers/use-audit-history-section.hook";
import { useAuditRows } from "~/features/drawers/use-audit-rows.hook";
import { useDrawerContext } from "~/features/drawers/DrawerContext";
import { UsedBySources } from "../UsedBySources";
import { UsedByLoadError } from "./UsedByLoadError";

interface VersionRow {
    name: string;
    description: string;
    version: number;
    isCurrent: boolean;
}

const columnHelper = createColumnHelper<VersionRow>();

interface MapperDrawerContentProps {
    mapper: MapperProfile;
    /** Data sources that use this mapper. */
    usedBy: RelatedEntity[];
    /** Archived data sources that use this mapper; named and counted, not linked. */
    archived: readonly RelatedEntity[];
    /** The data sources request failed, so which sources use this mapper is unknown. */
    isUsageError: boolean;
}

export function MapperDrawerContent({
    mapper,
    usedBy,
    archived,
    isUsageError,
}: MapperDrawerContentProps): ReactElement {
    const { onClose } = useDrawerContext();
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const { showToast } = useToast();
    const timezone = useUserTimezone();
    const [deleteOpen, setDeleteOpen] = useState(false);

    const { mutate: archiveMapper, isPending: isArchiving } = useMutation(
        profileMutations.deleteMapperProfile(queryClient),
    );

    const {
        data: versions,
        isLoading: isLoadingVersions,
        isError: isVersionsError,
    } = useQuery(profileQueries.mapperProfileVersions(mapper.id));

    const auditRows = useAuditRows(mapper, timezone, "mapper");

    const versionRows: VersionRow[] = useMemo(
        () =>
            (versions ?? []).map((v, i) => ({
                name: stripArchiveSuffix(v.name),
                description: v.description ?? "",
                version: v.version ?? 0,
                isCurrent: i === 0,
            })),
        [versions],
    );

    const versionColumns = useMemo(
        () => [
            columnHelper.accessor("name", {
                header: () => t("common.name"),
                cell: (info) => info.getValue(),
                enableSorting: false,
            }),
            columnHelper.accessor("description", {
                header: () => t("admin.description"),
                cell: (info) => {
                    const desc = info.getValue();
                    if (!desc) return "-";
                    if (desc.length <= 10) return desc;
                    return (
                        <Tooltip content={desc}>
                            <span>{`${desc.slice(0, 10)}…`}</span>
                        </Tooltip>
                    );
                },
                enableSorting: false,
            }),
            columnHelper.accessor("version", {
                header: () => t("admin.version"),
                cell: (info) => `v${info.getValue()}`,
                enableSorting: false,
                size: 70,
            }),
            columnHelper.accessor("isCurrent", {
                header: () => t("common.status"),
                cell: (info) =>
                    info.getValue() ? (
                        <ResourceTypeBadge label={t("adminUsers.active")} colorPalette="green" />
                    ) : (
                        <ResourceTypeBadge label={t("adminUsers.inactive")} colorPalette="gray" />
                    ),
                enableSorting: false,
                size: 90,
            }),
        ],
        [t],
    );

    const displayName = stripArchiveSuffix(mapper.name);
    const isArchived = mapper.archived === true;
    const isKmlKmz = mapper.dynamicMapper?.formatConfig?.type === "kml+kmz";
    const isEditable = mapper.editable !== false && !isKmlKmz;

    const tooltipContent = t("adminActions.editMapperNotSupportedDescription");

    const rows: DetailTableRow[] = [
        { key: "name", label: t("common.name"), value: displayName },
        { key: "description", label: t("admin.description"), value: mapper.description || "-" },
        { key: "version", label: t("admin.version"), value: `v${mapper.version}` },
        ...auditRows,
    ];

    if (isArchived) {
        rows.push({
            key: "status",
            label: t("common.status"),
            value: t("resourceTable.filterArchived"),
        });
    }

    if (isUsageError) {
        rows.push({
            key: "usedBy",
            label: t("admin.usedBy"),
            value: <UsedByLoadError />,
        });
    } else if (usedBy.length > 0 || archived.length > 0) {
        rows.push({
            key: "usedBy",
            label: t("admin.usedBy"),
            value: <UsedBySources active={usedBy} archived={archived} />,
        });
    }

    const mapperConfig = ENTITY_BADGE_CONFIG.mapper;

    const overviewContent = (
        <>
            <DetailTable rows={rows} compact />
            <Flex direction="column" gap={2} mt={4}>
                <Text fontSize="xs" fontWeight="bold" letterSpacing="1px" color="fg.muted">
                    {t("admin.versionHistory")}
                </Text>
                {isVersionsError ? (
                    <Center py={8}>
                        <Text fontSize="sm" color="red.500">
                            {t("admin.versionHistoryLoadError")}
                        </Text>
                    </Center>
                ) : (
                    <ResourceTable
                        data={versionRows}
                        columns={versionColumns}
                        isLoading={isLoadingVersions}
                        getRowId={(row) => String(row.version)}
                        aria-label={t("admin.versionHistory")}
                    />
                )}
            </Flex>
        </>
    );

    const auditSection = useAuditHistorySection("MAPPER_PROFILE", mapper.id);

    const sections: DrawerSideNavSection[] = [
        createOverviewSection(t, overviewContent, t("drawerSections.entity.mapper")),
    ];

    if (auditSection) sections.push(auditSection);

    return (
        <>
            <DrawerSideNavLayout
                name={displayName}
                badgeLabel={t(mapperConfig.labelKey)}
                navLabel={t("adminNav.mappers")}
                icon={<ResourceTypeIcon type="mapper" />}
                actions={
                    <>
                        {!isArchived && (
                            <>
                                <DrawerToolbarButton
                                    label={t("adminActions.edit")}
                                    icon={<FaPencilAlt />}
                                    to={`${MAPPER_BUILDER_PATH}/${mapper.id}/edit`}
                                    onClick={onClose}
                                    disabled={!isEditable}
                                    disabledReason={tooltipContent}
                                />
                                <DrawerToolbarButton
                                    label={t("adminActions.duplicate")}
                                    icon={<FaCopy />}
                                    to={`${MAPPER_BUILDER_PATH}/new?from=${mapper.id}`}
                                    onClick={onClose}
                                />
                            </>
                        )}
                        <DrawerToolbarButton
                            label={t("common.archive")}
                            icon={<FaArchive />}
                            disabled={isArchived}
                            loading={isArchiving}
                            onClick={() => setDeleteOpen(true)}
                        />
                    </>
                }
                sections={sections}
            />

            <ConfirmDialog
                open={deleteOpen}
                onClose={() => setDeleteOpen(false)}
                onConfirm={() =>
                    archiveMapper(
                        { id: mapper.id },
                        {
                            onSuccess: () => {
                                showToast({
                                    title: t("adminActions.deleteMapperSuccess"),
                                    type: "success",
                                });
                                setDeleteOpen(false);
                                onClose();
                            },
                            onError: (err) => {
                                setDeleteOpen(false);
                                showToast({
                                    title: t("adminActions.deleteMapperError"),
                                    description: getErrorMessage(err),
                                    type: "error",
                                });
                            },
                        },
                    )
                }
                title={t("adminActions.deleteMapperConfirmTitle")}
                message={t("adminActions.deleteMapperConfirmMessage", { name: displayName })}
                confirmLabel={t("common.archive")}
                isDestructive={false}
            />
        </>
    );
}
