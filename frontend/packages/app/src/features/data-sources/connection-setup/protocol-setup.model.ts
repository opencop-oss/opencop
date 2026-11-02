// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type {
    AccessMode,
    ConnectionExtractor,
    CreateMapperConnectionRequest,
    MapperIngressRule,
    ProtocolSetup,
    TransportType,
} from "@opencop/api";
import { assertNever } from "assert-never";
import { uniq } from "lodash";
import { ALL_ACCESS_MODES } from "~/features/data-sources/data-source-access.policy";

export function accessReads(access: AccessMode): boolean {
    return access === "R" || access === "RW";
}

export function accessWrites(access: AccessMode): boolean {
    return access === "W" || access === "RW";
}

/**
 * Access a mapper-only request can carry. It names an ingress mapper and no egress mapper, so it can
 * only read; widen this when the request gains an egress mapper.
 */
const MAPPER_ONLY_REQUEST_ACCESS: readonly AccessMode[] = ["R"];

/**
 * Access modes a mapper-only setup offers: what the integration reports, limited to what the
 * request can carry, so a Write choice is never offered and then dropped.
 */
export function supportedMapperOnlyAccessModes(reported: readonly AccessMode[]): AccessMode[] {
    return ALL_ACCESS_MODES.filter(
        (mode) => reported.includes(mode) && MAPPER_ONLY_REQUEST_ACCESS.includes(mode),
    );
}

/** Mappers the rules permit: any eligible mapper, or only those named. */
export type MapperChoice = { kind: "ANY" } | { kind: "ONLY"; mapperIds: string[] };

/** Transports whose mapper is the whole mapping step: no framing, no egress. */
export type MapperOnlyProtocol = CreateMapperConnectionRequest["kind"];

export function isMapperOnly(protocol: TransportType): protocol is MapperOnlyProtocol {
    switch (protocol) {
        case "TCP":
            return false;
        case "UDP":
        case "RTSP":
        case "KAFKA":
            return true;
        default:
            return assertNever(protocol);
    }
}

/** One protocol's entry of a setup, or `undefined` when the integration doesn't offer it. */
export function findProtocolSetup<K extends TransportType>(
    protocols: readonly ProtocolSetup[],
    kind: K,
): Extract<ProtocolSetup, { kind: K }> | undefined {
    return protocols.find(
        (protocol): protocol is Extract<ProtocolSetup, { kind: K }> => protocol.kind === kind,
    );
}

/** Mappers a non-TCP protocol's rules permit: any mapper when a rule is open, else those named. */
export function ingressMapperChoice(rules: readonly MapperIngressRule[]): MapperChoice {
    if (rules.some((rule) => rule.type === "ANY_MAPPER")) return { kind: "ANY" };
    return {
        kind: "ONLY",
        mapperIds: uniq(rules.flatMap((rule) => (rule.type === "MAPPER" ? [rule.mapperId] : []))),
    };
}

/**
 * The selected extractor's declared values and file parts. Only declared fields are sent, since
 * Core rejects any other name; optional text fields left empty are omitted rather than sent blank.
 */
export function extractorPayload(
    extractor: ConnectionExtractor,
    typedValues: Readonly<Record<string, string | undefined>>,
    chosenFiles: Readonly<Record<string, readonly File[] | undefined>>,
): { values: Record<string, string>; files: Record<string, File[]> } {
    const values: Record<string, string> = {};
    const files: Record<string, File[]> = {};
    for (const field of extractor.fields) {
        if (field.kind === "FILE" || field.kind === "FILE_LIST") {
            const chosen = chosenFiles[field.name] ?? [];
            if (chosen.length > 0) files[field.name] = [...chosen];
        } else {
            const value = typedValues[field.name] ?? "";
            if (value !== "") values[field.name] = value;
        }
    }
    return { values, files };
}

/** Wizard values a mapper-only request is built from. */
export interface MapperOnlySelection {
    kind: MapperOnlyProtocol;
    name: string;
    access: AccessMode;
    integration: { id: string; schemaVersion: number };
    extractor: ConnectionExtractor;
    values: Readonly<Record<string, string | undefined>>;
    files: Readonly<Record<string, readonly File[] | undefined>>;
    mapperId: string;
}

/** The v2 request and its file parts for a mapper-only connection. */
export function buildMapperOnlyRequest(selection: MapperOnlySelection): {
    request: CreateMapperConnectionRequest;
    files: Record<string, File[]>;
} {
    const { values, files } = extractorPayload(
        selection.extractor,
        selection.values,
        selection.files,
    );
    return {
        request: {
            kind: selection.kind,
            name: selection.name,
            access: selection.access,
            integration: selection.integration,
            extractorId: selection.extractor.id,
            values,
            mapperId: selection.mapperId,
        },
        files,
    };
}
