// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { INTEGRATION_UNAVAILABLE } from "@opencop/api";
import type { ConnectionState, DataSourceInfo } from "@opencop/api";
import {
    ConnectionIntegrationValue,
    IntegrationUnavailableAlert,
} from "./ConnectionIntegrationStatus";
import { useConnectionFramingLabel } from "./use-connection-framing-label.hook";
import { useConnectionIntegration } from "./use-connection-integration.hook";
import { DetailTable, DrawerSideNavLayout, useDefaultVisibleRow } from "@opencop/ui";
import type { DrawerSideNavSection } from "@opencop/ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { FaArchive, FaLayerGroup, FaPencilAlt, FaPlay, FaStop } from "react-icons/fa";
import { TbUserFilled } from "react-icons/tb";
import { ConnectionStatusIndicator } from "~/features/websocket/ConnectionStatusIndicator";
import { DrawerToolbarButton } from "~/features/drawers/DrawerToolbarButton";
import { createOverviewSection } from "~/features/drawers/relation-section.util";
import { useAuditHistorySection } from "~/features/drawers/use-audit-history-section.hook";
import { useAuditRows } from "~/features/drawers/use-audit-rows.hook";
import { useViewMode } from "~/features/drawers/use-view-mode.hook";
import { dataSourceMutations } from "../data-sources.queries";
import { DataSourceEditContent } from "./DataSourceEditContent";
import {
    ENTITY_BADGE_CONFIG,
    connectionEgressMapperId,
    connectionResources,
} from "../data-resources.util";
import { ResourceTypeIcon } from "../ResourceTypeIcon";
import { isConnected } from "../data-sources.util";
import type { ModalState } from "../DataSourceDrawer";
import { formatRelativeTime } from "~/date.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { RelatedEntityLink } from "./RelatedEntityLink";
import { DataSourceUsersSection } from "./DataSourceUsersSection";
import { DataSourceLayersSection } from "./DataSourceLayersSection";
import { DataSourceAccessModeStatus } from "../DataSourceAccessModeStatus";
import { resolveCapabilityCheck } from "../data-source-access.policy";

interface DataSourceDrawerContentProps {
    dataSource: DataSourceInfo;
    onOpenModal: (modal: ModalState) => void;
    mapperNames: Map<string, string>;
}

export function DataSourceDrawerContent({
    dataSource,
    onOpenModal,
    mapperNames,
}: DataSourceDrawerContentProps): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const timezone = useUserTimezone();
    const { mode, toView, toEdit } = useViewMode();
    const editing = mode === "edit";
    const { mutateAsync: disconnect, isPending: isDisconnecting } = useMutation(
        dataSourceMutations.disconnect(queryClient),
    );
    const { mutateAsync: reconnect, isPending: isReconnecting } = useMutation(
        dataSourceMutations.reconnect(queryClient),
    );
    const { mutate: update } = useMutation(dataSourceMutations.update(queryClient));

    const connected = isConnected(dataSource.state);
    const isArchived = dataSource.archivedAt != null;

    const auditRows = useAuditRows(dataSource, timezone, "dataSource");
    const integration = useConnectionIntegration(dataSource.integration, dataSource.stateReason);
    const integrationUnavailable = dataSource.stateReason === INTEGRATION_UNAVAILABLE;
    const framingLabel = useConnectionFramingLabel(dataSource.setup, integration);

    // No role check needed — this drawer is reachable only via the admin-guarded route.
    const defaultVisibleRow = useDefaultVisibleRow({
        checked: dataSource.defaultVisible,
        disabled: isArchived,
        onCheckedChange: (checked) =>
            update({ id: dataSource.id, data: { defaultVisible: checked } }),
    });

    const rows = [
        { key: "name", label: t("common.name"), value: dataSource.name },
        {
            key: "status",
            label: t("common.status"),
            value: (
                <ConnectionStatusIndicator
                    state={dataSource.state as ConnectionState}
                    reason={dataSource.stateReason}
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
            value: dataSource.transportType,
        },
        {
            key: "security",
            label: t("admin.securityType"),
            value: dataSource.securityType ?? "-",
        },
        {
            key: "lastTelemetry",
            label: t("resourceTable.lastTelemetry"),
            value: dataSource.lastMessageAt
                ? formatRelativeTime(new Date(dataSource.lastMessageAt), timezone)
                : "-",
        },
        ...auditRows,
        defaultVisibleRow,
    ];

    const sourceConfig = ENTITY_BADGE_CONFIG.source;
    const mapperId = dataSource.setup?.mapperId;
    const egressMapperId = connectionEgressMapperId(dataSource.setup);

    rows.splice(rows.findIndex((row) => row.key === "protocol") + 1, 0, {
        key: "accessMode",
        label: t("admin.accessMode"),
        value: (
            <DataSourceAccessModeStatus
                access={dataSource.access}
                transportType={dataSource.transportType}
                capabilityCheck={resolveCapabilityCheck({
                    transportType: dataSource.transportType,
                    access: dataSource.access,
                    setup: dataSource.setup ?? null,
                })}
            />
        ),
    });

    for (const resource of connectionResources(dataSource.setup, mapperNames)) {
        rows.push({
            key: `${resource.type}:${resource.id}`,
            label: resource.egress
                ? t("connectionSetup.egressMapper")
                : t(ENTITY_BADGE_CONFIG[resource.type].labelKey),
            value: (
                <RelatedEntityLink
                    id={resource.id}
                    name={resource.name}
                    type={resource.type}
                    showTypePrefix={false}
                />
            ),
        });
    }
    if (framingLabel) {
        rows.push({ key: "framing", label: t("connectionSetup.framing"), value: framingLabel });
    }

    const essentials = (
        <>
            <IntegrationUnavailableAlert
                integration={integration}
                unavailable={integrationUnavailable}
            />
            <DetailTable rows={rows} compact />
        </>
    );

    const auditSection = useAuditHistorySection("DATA_SOURCE", dataSource.id);

    const sections: DrawerSideNavSection[] = [
        createOverviewSection(t, essentials, t("drawerSections.entity.dataSource")),
        {
            key: "users",
            label: t("adminActions.users"),
            icon: <TbUserFilled />,
            content: <DataSourceUsersSection serverId={dataSource.id} />,
        },
        {
            key: "layers",
            label: t("adminActions.layers"),
            icon: <FaLayerGroup />,
            content: <DataSourceLayersSection serverId={dataSource.id} />,
        },
    ];

    if (auditSection) sections.push(auditSection);

    return (
        <DrawerSideNavLayout
            name={dataSource.name}
            badgeLabel={t(sourceConfig.labelKey)}
            navLabel={t("adminNav.dataSource")}
            icon={<ResourceTypeIcon type="source" />}
            actions={
                !isArchived && !editing ? (
                    <>
                        <DrawerToolbarButton
                            label={t("adminActions.editDataSource")}
                            icon={<FaPencilAlt />}
                            onClick={toEdit}
                        />
                        {connected ? (
                            <DrawerToolbarButton
                                label={t("adminActions.disconnect")}
                                icon={<FaStop />}
                                loading={isDisconnecting}
                                onClick={() => disconnect(dataSource.id)}
                            />
                        ) : (
                            <DrawerToolbarButton
                                label={t("adminActions.start")}
                                icon={<FaPlay />}
                                loading={isReconnecting}
                                onClick={() => reconnect(dataSource.id)}
                            />
                        )}
                        <DrawerToolbarButton
                            label={t("common.archive")}
                            icon={<FaArchive />}
                            onClick={() => onOpenModal({ type: "deleteSource", dataSource })}
                        />
                    </>
                ) : undefined
            }
            sections={sections}
            editContent={
                editing ? (
                    <DataSourceEditContent
                        dataSource={dataSource}
                        mapperName={mapperId ? mapperNames.get(mapperId) : undefined}
                        egressMapperName={
                            egressMapperId ? mapperNames.get(egressMapperId) : undefined
                        }
                        onDone={toView}
                    />
                ) : undefined
            }
        />
    );
}
