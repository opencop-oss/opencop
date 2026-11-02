// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DataSourceRoles, INTEGRATION_UNAVAILABLE } from "@opencop/api";
import {
    ConnectionIntegrationValue,
    IntegrationUnavailableAlert,
} from "./ConnectionIntegrationStatus";
import { useConnectionIntegration } from "./use-connection-integration.hook";
import type { ConnectionState, DataSourceRole, MapperProfile, MyDataSource } from "@opencop/api";
import {
    DetailTable,
    DrawerSideNavLayout,
    ResourceTypeBadge,
    useDefaultVisibleRow,
} from "@opencop/ui";
import type { DrawerSideNavSection } from "@opencop/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { FaArchive, FaLayerGroup, FaPencilAlt, FaPlay, FaStop } from "react-icons/fa";
import { TbUserFilled } from "react-icons/tb";
import { useIsAdmin } from "~/features/auth/use-is-admin.hook";
import { ConnectionStatusIndicator } from "~/features/websocket/ConnectionStatusIndicator";
import { PanelExclusionDrawerButton } from "~/features/filters/PanelExclusionDrawerButton";
import { DrawerToolbarButton } from "~/features/drawers/DrawerToolbarButton";
import { createOverviewSection } from "~/features/drawers/relation-section.util";
import { useViewMode } from "~/features/drawers/use-view-mode.hook";
import { useAuditRows } from "~/features/drawers/use-audit-rows.hook";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import {
    ENTITY_BADGE_CONFIG,
    connectionEgressMapperId,
    stripArchiveSuffix,
} from "../data-resources.util";
import { ResourceTypeIcon } from "../ResourceTypeIcon";
import { dataSourceMutations } from "../data-sources.queries";
import { dataSourceQueries } from "../data-sources.queries";
import { profileQueries } from "../data-source-profiles.queries";
import { DataSourceAccessModeStatus } from "../DataSourceAccessModeStatus";
import { DATA_SOURCE_ROLE_CONFIG, isConnected } from "../data-sources.util";
import type { MyDataSourceModalState } from "../MyDataSourceDrawer";
import { MyDataSourceEditContent } from "./MyDataSourceEditContent";
import { DataSourceUsersSection } from "./DataSourceUsersSection";
import { DataSourceLayersSection } from "./DataSourceLayersSection";
import { resolveCapabilityCheck } from "../data-source-access.policy";

const SOURCE_BADGE_CONFIG = ENTITY_BADGE_CONFIG.source;

interface MyDataSourceDrawerContentProps {
    dataSource: MyDataSource;
    onOpenModal: (modal: MyDataSourceModalState) => void;
    isArchived: boolean;
}

interface ResolvedMapper {
    name?: string;
    /** The source names a mapper the loaded list leaves out. */
    unavailable: boolean;
}

/**
 * A source's mapper from a list this viewer can load. Those lists leave out archived mappers, and
 * plugin reconciliation can archive one a source still uses, so a missing mapper is named as such.
 */
function resolveMapper(
    id: string | null | undefined,
    mappers: readonly MapperProfile[] | undefined,
): ResolvedMapper {
    const mapper = id ? mappers?.find((candidate) => candidate.id === id) : undefined;
    return {
        name: mapper && stripArchiveSuffix(mapper.name),
        unavailable: mappers != null && !!id && !mapper,
    };
}

/**
 * User-facing drawer for a single data source. Mirrors the admin equivalent
 * (`DataSourceDrawerContent.tsx`) by design — same side-nav sections, icons, and
 * state derivation — so future changes to the admin drawer are easy to spot here.
 */
export function MyDataSourceDrawerContent({
    dataSource,
    onOpenModal,
    isArchived,
}: MyDataSourceDrawerContentProps): ReactElement {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const queryClient = useQueryClient();
    const { mode, toView, toEdit } = useViewMode();
    const editing = mode === "edit";
    const {
        data: connection,
        isLoadingError: connectionFailed,
        refetch: retryConnection,
    } = useQuery(dataSourceQueries.detail(dataSource.serverId));
    const { data: mappers } = useQuery(profileQueries.mapperProfileList());
    const egressMapperId = connectionEgressMapperId(connection?.setup);
    const { data: egressMappers } = useQuery({
        ...profileQueries.egressMapperProfileList(),
        enabled: !!egressMapperId,
    });
    const mapper = resolveMapper(connection?.setup?.mapperId, mappers);
    const egressMapper = resolveMapper(egressMapperId, egressMappers);
    const mapperRowValue = ({ name, unavailable }: ResolvedMapper) =>
        name ?? (unavailable ? t("dataSources.mapperUnavailable") : "-");

    const isAdmin = useIsAdmin();
    const isOwner = dataSource.role === DataSourceRoles.OWNER;
    const isDirectOwner = dataSource.directRole === DataSourceRoles.OWNER;
    const canEditMetadata = isAdmin || isDirectOwner;
    const canManageConnection = isAdmin || isDirectOwner;
    const canManageRelations = isAdmin || isOwner;
    const relationsReadOnly = !canManageRelations || isArchived;
    const connected = isConnected(dataSource.serverState);
    // `/me/sources` carries neither field, so both come from the connection detail.
    const integration = useConnectionIntegration(connection?.integration, connection?.stateReason);
    const integrationUnavailable = connection?.stateReason === INTEGRATION_UNAVAILABLE;

    const { mutateAsync: disconnect, isPending: isDisconnecting } = useMutation(
        dataSourceMutations.disconnect(queryClient),
    );
    const { mutateAsync: reconnect, isPending: isReconnecting } = useMutation(
        dataSourceMutations.reconnect(queryClient),
    );
    const { mutate: update } = useMutation(dataSourceMutations.update(queryClient));
    const roleConfig = DATA_SOURCE_ROLE_CONFIG[dataSource.role as DataSourceRole] ?? {
        colorPalette: "gray",
    };
    const access = connection?.access ?? dataSource.access;
    const transportType = connection?.transportType ?? dataSource.transportType;
    // Only a request that never loaded fails the check; a failed refresh keeps the last answer.
    const capabilityCheck = resolveCapabilityCheck({
        transportType,
        access,
        setup: connection ? (connection.setup ?? null) : undefined,
        loadFailed: connectionFailed,
    });

    const defaultVisibleRow = useDefaultVisibleRow({
        checked: dataSource.defaultVisible,
        disabled: !canEditMetadata || isArchived,
        onCheckedChange: (checked) =>
            update({ id: dataSource.serverId, data: { defaultVisible: checked } }),
    });

    // The connection detail is fresher than the list row once it has loaded.
    const auditRows = useAuditRows(connection ?? dataSource, timezone, "dataSource");

    const rows = [
        { key: "name", label: t("common.name"), value: dataSource.serverName },
        {
            key: "status",
            label: t("common.status"),
            value: (
                <ConnectionStatusIndicator
                    // State and reason from one payload, so a stale list state can't pair with a fresh reason.
                    state={(connection?.state ?? dataSource.serverState) as ConnectionState}
                    reason={connection?.stateReason}
                />
            ),
        },
        ...(integration
            ? [
                  {
                      key: "integration",
                      label: t("dataSources.integration"),
                      value: <ConnectionIntegrationValue integration={integration} />,
                  },
              ]
            : []),
        {
            key: "protocol",
            label: t("admin.protocol"),
            value: transportType,
        },
        {
            key: "accessMode",
            label: t("admin.accessMode"),
            value: (
                <DataSourceAccessModeStatus
                    access={access}
                    transportType={transportType}
                    capabilityCheck={capabilityCheck}
                    onRetryCapability={() => void retryConnection()}
                />
            ),
        },
        {
            key: "mapper",
            label: t("admin.mapper"),
            value: mapperRowValue(mapper),
        },
        ...(egressMapperId
            ? [
                  {
                      key: "egressMapper",
                      label: t("connectionSetup.egressMapper"),
                      value: mapperRowValue(egressMapper),
                  },
              ]
            : []),
        {
            key: "role",
            label: t("dataSources.role"),
            value: (
                <ResourceTypeBadge label={dataSource.role} colorPalette={roleConfig.colorPalette} />
            ),
        },
        ...auditRows,
        defaultVisibleRow,
    ];

    const connectionAction = connected
        ? {
              icon: <FaStop />,
              label: t("adminActions.disconnect"),
              run: () => disconnect(dataSource.serverId),
          }
        : {
              icon: <FaPlay />,
              label: t("adminActions.start"),
              run: () => reconnect(dataSource.serverId),
          };
    const isConnectionPending = isDisconnecting || isReconnecting;

    const essentials = (
        <>
            <IntegrationUnavailableAlert
                integration={integration}
                unavailable={integrationUnavailable}
            />
            <DetailTable rows={rows} compact />
        </>
    );

    const sections: DrawerSideNavSection[] = [
        createOverviewSection(t, essentials, t("drawerSections.entity.dataSource")),
        {
            key: "users",
            label: t("dataSources.users"),
            icon: <TbUserFilled />,
            content: (
                <DataSourceUsersSection
                    serverId={dataSource.serverId}
                    readOnly={relationsReadOnly}
                />
            ),
        },
        {
            key: "layers",
            label: t("dataSources.layers"),
            icon: <FaLayerGroup />,
            content: (
                <DataSourceLayersSection
                    serverId={dataSource.serverId}
                    layerScope="my"
                    readOnly={relationsReadOnly}
                />
            ),
        },
    ];

    return (
        <DrawerSideNavLayout
            name={dataSource.serverName}
            badgeLabel={t(SOURCE_BADGE_CONFIG.labelKey)}
            navLabel={t("adminNav.dataSource")}
            icon={<ResourceTypeIcon type="source" />}
            actions={
                !isArchived && !editing ? (
                    <>
                        <PanelExclusionDrawerButton id={dataSource.serverId} kind="dataSource" />
                        <DrawerToolbarButton
                            label={connectionAction.label}
                            icon={connectionAction.icon}
                            loading={isConnectionPending}
                            disabled={!canManageConnection}
                            disabledReason={t("dataSources.ownerOnlyConnection")}
                            onClick={connectionAction.run}
                        />
                        <DrawerToolbarButton
                            label={t("adminActions.editDataSource")}
                            icon={<FaPencilAlt />}
                            disabled={!canEditMetadata}
                            disabledReason={t("dataSources.ownerOnlyEdit")}
                            onClick={toEdit}
                        />
                        <DrawerToolbarButton
                            label={t("common.archive")}
                            icon={<FaArchive />}
                            disabled={!isOwner}
                            disabledReason={t("dataSources.ownerOnlyDelete")}
                            onClick={() => onOpenModal({ type: "deleteDataSource", dataSource })}
                        />
                    </>
                ) : undefined
            }
            sections={sections}
            editContent={
                editing ? (
                    <MyDataSourceEditContent
                        dataSource={dataSource}
                        connection={connection}
                        mapperName={mapper.name}
                        egressMapperName={egressMapper.name}
                        onDone={toView}
                    />
                ) : undefined
            }
        />
    );
}
