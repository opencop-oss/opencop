// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";
import type { AccessMode, TransportType } from "./data-sources";

/** GET /v1/integrations → one entry per integration schema version. */
export interface IntegrationCatalogEntry {
    integrationId: string;
    schemaVersion: number;
    name: string;
    description: string;
    /** Translation key for `name`. Older Cores don't send it. */
    nameKey?: string;
    /** False when the catalog records the version but this runtime registers no implementation. */
    available: boolean;
}

/** What one extractor field carries; selects the form component. `FILE_LIST` is the only multi-valued kind. */
export type ExtractorFieldKind = "TEXT" | "SECRET" | "FILE" | "FILE_LIST";

/** Meaning shared by fields that several integrations declare the same way. */
export type StandardExtractorField =
    | "HOST"
    | "PORT"
    | "CLIENT_CERTIFICATE_STORE"
    | "CLIENT_CERTIFICATE_PASSWORD"
    | "TRUSTSTORE"
    | "TRUSTSTORE_PASSWORD";

/**
 * One input slot of an extractor. `extensions` and `mediaTypes` are present only for the file kinds;
 * `standardKey` only when the field carries a shared meaning.
 */
export interface ExtractorField {
    name: string;
    label: string;
    /** Translation key for `label`. A known `standardKey` wins over it. */
    labelKey?: string;
    required: boolean;
    kind: ExtractorFieldKind;
    extensions?: string[];
    mediaTypes?: string[];
    standardKey?: StandardExtractorField;
}

/** One way a user may supply connection input, with its fields in render order. */
export interface ConnectionExtractor {
    id: string;
    label: string;
    /** Translation key for `label`. */
    labelKey?: string;
    fields: ExtractorField[];
}

/** A TCP ingress rule: which mapper and framing a connection may decode with. */
export type TcpIngressRule =
    | { type: "EXACT_PAIR"; mapperId: string; framingId: string }
    | { type: "ANY_MAPPER_WITH"; framingId: string }
    | { type: "ANY_MAPPER_CUSTOM_FRAMING" };

/** A non-TCP ingress rule: which mappers a connection may decode with. */
export type MapperIngressRule = { type: "ANY_MAPPER" } | { type: "MAPPER"; mapperId: string };

/** Which egress mappers a writing connection may use; `allowedMappers` is empty when `anyMapper`. */
export interface EgressRule {
    anyMapper: boolean;
    allowedMappers: string[];
}

/** How a framing splits a stream. */
export type FramingMode = "delimiterInclude" | "delimiterSplit" | "passthrough" | "binary";

/** A framing an integration provides in code, referenced by id from the ingress rules. */
export interface IntegrationFraming {
    id: string;
    mode: FramingMode;
}

/** What every transport's setup carries, whatever its `kind`. */
interface ProtocolSetupBase {
    /**
     * The access modes a connection over this transport may take, in the order R, W, RW. Core
     * enforces the same list on creation, so it is the one source for what the wizard offers.
     */
    accessModes: AccessMode[];
    extractors: ConnectionExtractor[];
}

/** One transport's connection setup, discriminated by `kind` like a v2 creation request. */
export type ProtocolSetup =
    | (ProtocolSetupBase & {
          kind: "TCP";
          ingress: TcpIngressRule[];
          /** `null` means the integration cannot write over TCP. */
          egress: EgressRule | null;
          framings: IntegrationFraming[];
      })
    | (ProtocolSetupBase & { kind: "UDP"; ingress: MapperIngressRule[] })
    | (ProtocolSetupBase & { kind: "RTSP"; ingress: MapperIngressRule[] })
    | (ProtocolSetupBase & { kind: "KAFKA"; ingress: MapperIngressRule[] });

/** GET /v1/integrations/{id}/versions/{v}/connection/setup */
export interface IntegrationConnectionSetup {
    integrationId: string;
    schemaVersion: number;
    /** Only the transports the integration offers, in the order TCP, UDP, RTSP, KAFKA. */
    protocols: ProtocolSetup[];
}

/**
 * GET /v1/integrations — with `protocol`, only integrations declaring that transport; without it,
 * the whole catalog, unavailable versions included.
 */
export async function getIntegrations(
    protocol?: TransportType,
    options?: FetchOptions,
): Promise<IntegrationCatalogEntry[]> {
    const query = protocol ? `?protocol=${protocol}` : "";
    return apiFetch<IntegrationCatalogEntry[]>(`/integrations${query}`, {
        signal: options?.signal,
    });
}

/** GET /v1/integrations/{id}/versions/{v}/connection/setup */
export async function getIntegrationConnectionSetup(
    integrationId: string,
    schemaVersion: number,
    options?: FetchOptions,
): Promise<IntegrationConnectionSetup> {
    return apiFetch<IntegrationConnectionSetup>(
        `/integrations/${integrationId}/versions/${schemaVersion}/connection/setup`,
        { signal: options?.signal },
    );
}
