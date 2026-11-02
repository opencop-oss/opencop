// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type {
    ConnectionSetup,
    ConnectionStateReason,
    DataSourceInfo,
    MapperProfile,
} from "@opencop/api";
import type { ParseKeys, TFunction } from "i18next";
import { TYPE_PALETTE } from "@opencop/ui";
import type { TypePaletteKey } from "@opencop/ui";

/** Resource kinds an admin page lists, each linkable to its drawer there. */
export type ResourceEntityType = "source" | "mapper";

export interface RelatedEntity {
    id: string;
    name: string;
    type: ResourceEntityType;
}

export interface ResourceRow {
    id: string;
    name: string;
    entityType: ResourceEntityType;
    statusLabel: string;
    connectionState?: string;
    /** Why `connectionState` is `FAILED`, when Core reported a reason. */
    stateReason?: ConnectionStateReason | null;
    lastTelemetry?: string | null;
    usesLabel: string;
    uses: ConnectionResource[];
    usedBy: RelatedEntity[];
    archived: boolean;
    editable: boolean;
    createdAt?: string | null;
    updatedAt?: string | null;
    /** Last observed use. Absent for resource kinds that record no usage. */
    lastEventAt?: string | null;
}

// ── Shared config ──────────────────────────────────────────────────────

export const ENTITY_BADGE_CONFIG: Record<
    ResourceEntityType,
    {
        labelKey: ParseKeys;
        colorPalette: string;
        linkColor: string;
        color: TypePaletteKey;
        abbr: string;
    }
> = {
    source: {
        labelKey: "admin.dataSource",
        colorPalette: "yellow",
        linkColor: TYPE_PALETTE.sky,
        color: "sky",
        abbr: "DS",
    },
    mapper: {
        labelKey: "admin.mapper",
        colorPalette: "purple",
        linkColor: TYPE_PALETTE.blue,
        color: "blue",
        abbr: "M",
    },
};

/** Admin page that lists each resource kind. */
export const ENTITY_ADMIN_PATH: Record<ResourceEntityType, string> = {
    source: "/admin/data-sources",
    mapper: "/admin/mappers",
};

/** Mapper builder base route: `/new`, `/new?from=<id>`, `/<id>/edit`. */
export const MAPPER_BUILDER_PATH = ENTITY_ADMIN_PATH.mapper;

// ── Helpers ────────────────────────────────────────────────────────────

/** Link that opens a resource's drawer on its admin page. */
export function relatedEntityLocation(entity: { id: string; type: ResourceEntityType }) {
    return {
        pathname: ENTITY_ADMIN_PATH[entity.type],
        search: `?id=${entity.id}&type=${entity.type}`,
    };
}

export function stripArchiveSuffix(name: string): string {
    return name.replace(/-ARCHIVE$/, "");
}

export function buildMapperNameMap(mappers: MapperProfile[]): Map<string, string> {
    const map = new Map<string, string>();
    for (const m of mappers) map.set(m.id, stripArchiveSuffix(m.name));
    return map;
}

function formatRelatedLabel(entities: RelatedEntity[]): string {
    return entities.map((r) => r.name).join(", ") || "-";
}

/** A resource a connection runs with. `egress` marks the mapper a TCP connection writes with. */
export interface ConnectionResource extends RelatedEntity {
    egress?: true;
}

/** The egress mapper a TCP connection writes with, when it writes. */
export function connectionEgressMapperId(setup: ConnectionSetup | null | undefined) {
    return setup?.kind === "TCP" ? (setup.egressMapperId ?? undefined) : undefined;
}

/**
 * The connection's own mappers (ingress, then egress), for those whose names resolve. A connection
 * that only writes has no ingress mapper.
 */
export function connectionResources(
    setup: ConnectionSetup | null | undefined,
    mapperNames: Map<string, string>,
): ConnectionResource[] {
    const resources: ConnectionResource[] = [];
    const add = (
        id: string | null | undefined,
        resource: Omit<ConnectionResource, "id" | "name">,
    ) => {
        const name = id ? mapperNames.get(id) : undefined;
        if (id && name) resources.push({ id, name, ...resource });
    };
    add(setup?.mapperId, { type: "mapper" });
    add(connectionEgressMapperId(setup), { type: "mapper", egress: true });
    return resources;
}

// ── Normalizers ────────────────────────────────────────────────────────

export function normalizeSource(
    source: DataSourceInfo,
    mapperNames: Map<string, string>,
): ResourceRow {
    const uses = connectionResources(source.setup, mapperNames);

    return {
        id: source.id,
        name: source.name,
        entityType: "source",
        statusLabel: source.state,
        connectionState: source.state,
        stateReason: source.stateReason ?? null,
        lastTelemetry: source.lastMessageAt,
        usesLabel: formatRelatedLabel(uses),
        uses,
        usedBy: [],
        archived: !!source.archivedAt,
        editable: true,
        createdAt: source.createdAt,
        updatedAt: source.updatedAt,
        lastEventAt: source.lastEventAt,
    };
}

export function normalizeMapper(
    mapper: MapperProfile,
    usedBy: RelatedEntity[],
    t: TFunction,
): ResourceRow {
    return {
        id: mapper.id,
        name: stripArchiveSuffix(mapper.name),
        entityType: "mapper",
        statusLabel: mapper.archived ? t("resourceTable.filterArchived") : "-",
        usesLabel: "-",
        uses: [],
        usedBy,
        archived: mapper.archived === true,
        editable: mapper.editable !== false,
        createdAt: mapper.createdAt,
        updatedAt: mapper.updatedAt,
        lastEventAt: mapper.lastEventAt,
    };
}
