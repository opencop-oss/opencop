// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type {
    ExtractorField,
    FramingMode,
    IntegrationFraming,
    StandardExtractorField,
    TcpFramingSelection,
} from "@opencop/api";
import type { TFunction } from "i18next";
import { integrationText } from "~/features/data-sources/integration-text.util";
import { escapeDelimiter, type CustomFramingMode } from "./tcp-setup.model";

const MODE_LABEL_KEYS = {
    delimiterInclude: "dataSources.delimiterInclude",
    delimiterSplit: "dataSources.delimiterSplit",
    passthrough: "dataSources.passthrough",
    binary: "connectionSetup.binaryMode",
} as const satisfies Record<CustomFramingMode, `${"dataSources" | "connectionSetup"}.${string}`>;

/** Display text for a framing mode; a mode the wizard has no copy for shows its wire name. */
export function framingModeLabel(mode: FramingMode, t: TFunction): string {
    return mode in MODE_LABEL_KEYS
        ? t(MODE_LABEL_KEYS[mode as keyof typeof MODE_LABEL_KEYS])
        : mode;
}

/**
 * A TCP framing as text. A custom framing shows its mode, and its delimiter as the user would type
 * it when the framing carries one. An integration framing names its provider, with the mode the
 * integration declares for its id, or the provider alone when the declarations aren't known or
 * none has that id; with no provider to name, there is no text.
 */
export function describeFraming(
    framing: TcpFramingSelection,
    provider: string | undefined,
    declaredFramings: readonly IntegrationFraming[] | undefined,
    t: TFunction,
): string | undefined {
    if (framing.type === "CUSTOM") {
        const mode = framingModeLabel(framing.mode, t);
        return framing.delimiter ? `${mode} (${escapeDelimiter(framing.delimiter)})` : mode;
    }
    if (!provider) return undefined;
    const mode = declaredFramings?.find((declared) => declared.id === framing.framingId)?.mode;
    return mode
        ? t("dataSources.framingProvidedByWithMode", {
              integration: provider,
              mode: framingModeLabel(mode, t),
          })
        : t("dataSources.framingProvidedBy", { integration: provider });
}

/** True when the file's name ends with one of the declared extensions; no declaration accepts all. */
export function hasAcceptedExtension(
    file: File,
    extensions: readonly string[] | undefined,
): boolean {
    if (!extensions?.length) return true;
    const name = file.name.toLowerCase();
    return extensions.some((extension) => name.endsWith(extension.toLowerCase()));
}

export const MIN_PORT = 1;
export const MAX_PORT = 65535;

/**
 * True when the value, ignoring surrounding whitespace, is a whole number from `MIN_PORT` to
 * `MAX_PORT`. Empty input passes, so presence stays the `required` rule's to report.
 */
export function isValidPort(value: string | undefined): boolean {
    const trimmed = value?.trim() ?? "";
    if (trimmed === "") return true;
    if (!/^\d+$/.test(trimmed)) return false;
    const port = Number(trimmed);
    return port >= MIN_PORT && port <= MAX_PORT;
}

/** Standard fields that share a row with the one declared directly after them. */
const ROW_PARTNERS: Partial<Record<StandardExtractorField, StandardExtractorField>> = {
    HOST: "PORT",
    CLIENT_CERTIFICATE_STORE: "CLIENT_CERTIFICATE_PASSWORD",
    TRUSTSTORE: "TRUSTSTORE_PASSWORD",
};

/**
 * Fields grouped into form rows in declaration order. A field directly followed by its partner
 * shares one row with it: host with port, and each certificate store with its password. Every
 * other field takes its own row. Keyed on the shared `standardKey`, so any extractor declaring
 * those pairs gets the same layout.
 */
export function groupFieldRows(fields: readonly ExtractorField[]): ExtractorField[][] {
    const rows: ExtractorField[][] = [];
    for (let index = 0; index < fields.length; index += 1) {
        const field = fields[index];
        const next = fields.at(index + 1);
        const partner = field.standardKey && ROW_PARTNERS[field.standardKey];
        if (partner && next?.standardKey === partner) {
            rows.push([field, next]);
            index += 1;
        } else {
            rows.push([field]);
        }
    }
    return rows;
}

const STANDARD_LABEL_KEYS = {
    HOST: "connectionSetup.standardFields.host",
    PORT: "connectionSetup.standardFields.port",
    CLIENT_CERTIFICATE_STORE: "connectionSetup.standardFields.clientCertificateStore",
    CLIENT_CERTIFICATE_PASSWORD: "connectionSetup.standardFields.clientCertificatePassword",
    TRUSTSTORE: "connectionSetup.standardFields.truststore",
    TRUSTSTORE_PASSWORD: "connectionSetup.standardFields.truststorePassword",
} as const satisfies Record<StandardExtractorField, `connectionSetup.${string}`>;

/** A field's label: the standard-field translation, else the field's own key, else Core's label. */
export function descriptorFieldLabel(field: ExtractorField, t: TFunction): string {
    return field.standardKey && field.standardKey in STANDARD_LABEL_KEYS
        ? t(STANDARD_LABEL_KEYS[field.standardKey])
        : integrationText(field.label, field.labelKey, t);
}
