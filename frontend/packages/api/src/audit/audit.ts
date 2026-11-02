// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";
import { buildPageParams, type Page } from "../common";

const ADMIN_BASE = "/admin/audit-history";

/** The resources the shared ledger covers. Watchboxes and runtime features keep their own. */
export type AuditEntityType = "DATA_SOURCE" | "LAYER" | "LAYER_GROUP" | "MAPPER_PROFILE";

/**
 * The shared operation vocabulary, one name per kind of change across every resource.
 *
 * Which subset a given resource may use is enforced by the database, not here: a row the server
 * sends is by definition one its constraints allowed.
 */
export type AuditOperation =
    | "CREATE"
    | "PATCH"
    | "ARCHIVE"
    | "DELETE"
    | "SETUP_REPLACED"
    | "CONNECT"
    | "DISCONNECT"
    | "MEMBERS_CHANGED"
    | "USER_GROUPS_CHANGED"
    | "LAYERS_CHANGED"
    | "DATA_SOURCES_CHANGED"
    | "MAPPER_CHANGED"
    | "CLONED_TO"
    | "CREATED_FROM_CLONE"
    | "MERGED_FROM"
    | "MERGED_INTO"
    | "EXPORTED"
    | "IMPORT_REQUESTED"
    /** A layer restored after its archive could not evict the geo cache; no user path produces it. */
    | "UNARCHIVE";

/** Actor facts as they read when the action happened. Either profile field may be absent. */
export interface AuditActor {
    userId: string;
    fullName?: string;
    email?: string;
    globalAdmin: boolean;
}

/** One field the action moved. A field recorded by name only carries no `before`/`after`. */
export interface AuditFieldChange {
    field: string;
    before?: string | number | boolean | null;
    after?: string | number | boolean | null;
}

/** One id in a delta list, with the role it was given. */
export interface AuditDeltaEntry {
    id: string;
    role: string;
}

/**
 * One delta list, capped at 100 ids with an exact `count`.
 *
 * `truncated` therefore means "this row knows how many but not which ones in full" — the count is
 * still the truth.
 */
export interface AuditDelta {
    count: number;
    /** Plain ids, for a list that carries no role (a revoke, an unlink). */
    ids?: string[];
    /** Id-and-role pairs, for a grant or a role change. */
    entries?: AuditDeltaEntry[];
    /** Client-supplied names, each bounded, for a list that names things rather than ids. */
    names?: string[];
    truncated: boolean;
}

/**
 * The allowlisted projection of one change — never a request body, and never the value of a
 * secret, credential, token or key.
 *
 * The structural keys below are shared by every operation; anything else an operation records is
 * a scalar detail (`format`, `transportKind`, `exportId`, cascade counts, …). The index signature
 * is what lets a new backend operation display without a web change, so keep it.
 */
export interface AuditChange {
    changed?: AuditFieldChange[];
    added?: AuditDelta;
    removed?: AuditDelta;
    roleChanged?: AuditDelta;
    [detail: string]: unknown;
}

/** One history row. `change` arrives as a JSON object, not a JSON-encoded string. */
export interface AuditHistoryItem {
    id: number;
    entityType: AuditEntityType;
    entityId: string;
    entityName?: string;
    operation: AuditOperation;
    occurredAt: string;
    actor: AuditActor;
    change: AuditChange;
    changeVersion: number;
}

export interface AuditHistoryPageParams {
    entityType?: AuditEntityType;
    entityId?: string;
    operation?: AuditOperation;
    actorUserId?: string;
    /** Inclusive lower bound on occurrence time, ISO-8601. */
    occurredFrom?: string;
    /** Inclusive upper bound on occurrence time, ISO-8601. */
    occurredTo?: string;
    page?: number;
    /** Server cap is 100; the default is 25. */
    size?: number;
}

/**
 * Reads the resource audit ledger. Global administrators only.
 *
 * Chronology is fixed newest-first and cannot be reordered by the caller. Paging is by offset and
 * is **not** snapshot consistent: rows written between requests shift every later offset, so a
 * reader paging through must detect that drift rather than assume stable pages.
 */
export async function getAuditHistory(
    params: AuditHistoryPageParams,
    options?: FetchOptions,
): Promise<Page<AuditHistoryItem>> {
    const query = buildPageParams({ ...params }).toString();
    return apiFetch<Page<AuditHistoryItem>>(`${ADMIN_BASE}${query ? `?${query}` : ""}`, {
        signal: options?.signal,
    });
}
