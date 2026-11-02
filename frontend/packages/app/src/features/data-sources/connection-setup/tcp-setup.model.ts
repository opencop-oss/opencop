// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type {
    AccessMode,
    ConnectionExtractor,
    CreateTcpConnectionRequest,
    EgressRule,
    MapperProfile,
    FramingMode,
    IntegrationFraming,
    ProtocolSetup,
    TcpFramingSelection,
    TcpIngressRule,
} from "@opencop/api";
import { invert, uniq } from "lodash";
import { ALL_ACCESS_MODES } from "~/features/data-sources/data-source-access.policy";
import {
    accessReads,
    accessWrites,
    extractorPayload,
    type MapperChoice,
} from "./protocol-setup.model";

export type TcpSetup = Extract<ProtocolSetup, { kind: "TCP" }>;

/** Form value of the framing picker for a delimiter the connection supplies itself. */
export const CUSTOM_FRAMING = "CUSTOM";

/** Every mode Core accepts for a custom framing (`TcpCustomFramingPolicy`). */
export const CUSTOM_FRAMING_MODES = [
    "delimiterInclude",
    "delimiterSplit",
    "passthrough",
    "binary",
] as const satisfies readonly FramingMode[];

export type CustomFramingMode = (typeof CUSTOM_FRAMING_MODES)[number];

const DELIMITER_ESCAPES: Readonly<Record<string, string>> = {
    n: "\n",
    r: "\r",
    t: "\t",
    "\\": "\\",
};

/**
 * The delimiter's real characters from what the user typed. Core stores the delimiter as sent and
 * Connect splits on its bytes, so a typed `\n` must become a newline here or the stream is split on
 * a backslash and an `n`. Only `\n`, `\r`, `\t` and `\\` are recognised; anything else is kept.
 */
export function unescapeDelimiter(typed: string): string {
    return typed.replace(/\\(.)/gs, (escaped, escape: string) =>
        Object.hasOwn(DELIMITER_ESCAPES, escape) ? DELIMITER_ESCAPES[escape] : escaped,
    );
}

const TYPED_ESCAPES: Readonly<Record<string, string>> = invert(DELIMITER_ESCAPES);

/**
 * A stored delimiter as the user would type it, which {@link unescapeDelimiter} reads back
 * unchanged.
 */
export function escapeDelimiter(delimiter: string): string {
    return Array.from(delimiter, (real) =>
        Object.hasOwn(TYPED_ESCAPES, real) ? `\\${TYPED_ESCAPES[real]}` : real,
    ).join("");
}

/** Modes whose framing is defined by a delimiter, which the request must then carry. */
export function modeNeedsDelimiter(mode: FramingMode): boolean {
    return mode === "delimiterInclude" || mode === "delimiterSplit";
}

/** One framing the picker offers: an integration framing by id, or the custom choice. */
export type FramingChoice =
    | { value: string; kind: "INTEGRATION"; framing: IntegrationFraming }
    | { value: typeof CUSTOM_FRAMING; kind: "CUSTOM" };

/**
 * Framings the ingress rules permit, integration framings first in rule order, then the custom
 * choice when any rule allows it. A framing id a rule names but `framings` does not resolve is
 * skipped rather than offered without a mode.
 */
export function framingChoices(setup: TcpSetup): FramingChoice[] {
    const framingIds = uniq(
        setup.ingress.flatMap((rule) =>
            rule.type === "ANY_MAPPER_CUSTOM_FRAMING" ? [] : [rule.framingId],
        ),
    );
    const choices: FramingChoice[] = framingIds.flatMap((id) => {
        const framing = setup.framings.find((candidate) => candidate.id === id);
        return framing ? [{ value: id, kind: "INTEGRATION" as const, framing }] : [];
    });
    if (setup.ingress.some((rule) => rule.type === "ANY_MAPPER_CUSTOM_FRAMING")) {
        choices.push({ value: CUSTOM_FRAMING, kind: "CUSTOM" });
    }
    return choices;
}

/**
 * Which ingress mappers may pair with a framing choice. A rule that opens the mapper for that
 * framing (`ANY_MAPPER_WITH`, or `ANY_MAPPER_CUSTOM_FRAMING` for custom) permits any mapper;
 * otherwise only the mappers named by its `EXACT_PAIR` rules.
 */
export function mapperChoice(setup: TcpSetup, framingValue: string): MapperChoice {
    const matches = (rule: TcpIngressRule): boolean =>
        framingValue === CUSTOM_FRAMING
            ? rule.type === "ANY_MAPPER_CUSTOM_FRAMING"
            : rule.type !== "ANY_MAPPER_CUSTOM_FRAMING" && rule.framingId === framingValue;
    const rules = setup.ingress.filter(matches);
    if (rules.some((rule) => rule.type !== "EXACT_PAIR")) return { kind: "ANY" };
    return {
        kind: "ONLY",
        mapperIds: uniq(
            rules.flatMap((rule) => (rule.type === "EXACT_PAIR" ? [rule.mapperId] : [])),
        ),
    };
}

/**
 * The built-in Native integration (`NativeIntegration.NATIVE_ID` in opencop-java). The wizard
 * offers it Read alone: creating a writable TCP source is a product choice reserved for
 * integrations built for it, such as TAK. Core deliberately keeps an open egress rule for Native
 * TCP, so its `accessModes` still lists Write; this is a wizard rule rather than a backend one,
 * and the backend still accepts a Native Write sent another way.
 */
export const NATIVE_INTEGRATION_ID = "5b96caf4-20dd-4a8a-8f1b-09fb594265be";

/**
 * Access modes the wizard lets the user pick for one integration's TCP setup: the `accessModes`
 * Core reports, in the order R, W, RW, without any mode this build does not know.
 */
export function supportedTcpAccessModes(setup: TcpSetup, integrationId: string): AccessMode[] {
    const reported = ALL_ACCESS_MODES.filter((mode) => setup.accessModes.includes(mode));
    return integrationId === NATIVE_INTEGRATION_ID
        ? reported.filter((mode) => mode === "R")
        : reported;
}

/**
 * Egress mappers the integration permits among those Core offers for egress. An open rule permits
 * every one; a closed rule only those it names, kept in the rule's order.
 */
export function egressMapperIds(
    rule: EgressRule | null,
    egressMappers: readonly Pick<MapperProfile, "id">[],
): string[] {
    if (!rule) return [];
    if (rule.anyMapper) return egressMappers.map((mapper) => mapper.id);
    return rule.allowedMappers;
}

/** Wizard values the request is built from. Field values are keyed by the extractor's field names. */
export interface TcpSelection {
    name: string;
    access: AccessMode;
    integration: { id: string; schemaVersion: number };
    extractor: ConnectionExtractor;
    values: Readonly<Record<string, string | undefined>>;
    files: Readonly<Record<string, readonly File[] | undefined>>;
    mapperId: string;
    egressMapperId: string;
    framing: string;
    customMode: CustomFramingMode;
    customDelimiter: string;
}

/**
 * The framing a TCP request sends for the wizard's choice. A custom framing carries its delimiter
 * only when its mode splits on one.
 */
export function tcpFramingSelection(
    choice: Pick<TcpSelection, "framing" | "customMode" | "customDelimiter">,
): TcpFramingSelection {
    if (choice.framing !== CUSTOM_FRAMING)
        return { type: "INTEGRATION", framingId: choice.framing };
    return modeNeedsDelimiter(choice.customMode)
        ? {
              type: "CUSTOM",
              mode: choice.customMode,
              delimiter: unescapeDelimiter(choice.customDelimiter),
          }
        : { type: "CUSTOM", mode: choice.customMode };
}

/**
 * The v2 request and its file parts for a TCP connection, with only the selected extractor's
 * declared fields (see {@link extractorPayload}). The ingress mapper is sent only when the
 * connection reads and the egress mapper only when it writes, since Core rejects either otherwise.
 */
export function buildTcpRequest(selection: TcpSelection): {
    request: CreateTcpConnectionRequest;
    files: Record<string, File[]>;
} {
    const { values, files } = extractorPayload(
        selection.extractor,
        selection.values,
        selection.files,
    );
    const framing = tcpFramingSelection(selection);
    return {
        request: {
            kind: "TCP",
            name: selection.name,
            access: selection.access,
            integration: selection.integration,
            extractorId: selection.extractor.id,
            values,
            ...(accessReads(selection.access) ? { mapperId: selection.mapperId } : {}),
            ...(accessWrites(selection.access) ? { egressMapperId: selection.egressMapperId } : {}),
            framing,
        },
        files,
    };
}
