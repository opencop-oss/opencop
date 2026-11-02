// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ConnectionSetup } from "@opencop/api";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { integrationQueries } from "~/features/data-sources/integrations.queries";
import { describeFraming } from "../connection-setup/connection-setup.util";
import { findProtocolSetup } from "../connection-setup/protocol-setup.model";
import {
    connectionIntegrationLabel,
    type ConnectionIntegration,
} from "./use-connection-integration.hook";

/**
 * How a TCP connection frames its stream, as {@link describeFraming} words it; `undefined` for any
 * other transport or a TCP connection with no framing on record. An integration framing carries
 * only its id, so the connection's integration is named as provider, and its setup supplies the
 * declared modes: until it loads, or when it can't be, the provider is named alone. It does not
 * request the setup of an integration already known to be unavailable.
 */
export function useConnectionFramingLabel(
    setup: ConnectionSetup | null | undefined,
    integration: ConnectionIntegration | undefined,
): string | undefined {
    const { t } = useTranslation();
    const framing = setup?.kind === "TCP" ? setup.framing : null;
    const { data: integrationSetup } = useQuery({
        ...integrationQueries.connectionSetup(
            integration?.integrationId ?? "",
            integration?.schemaVersion ?? 0,
        ),
        enabled:
            framing?.type === "INTEGRATION" &&
            !!integration &&
            integration.availability !== "unavailable",
    });

    if (!framing) return undefined;
    return describeFraming(
        framing,
        integration && connectionIntegrationLabel(integration, t),
        integrationSetup && findProtocolSetup(integrationSetup.protocols, "TCP")?.framings,
        t,
    );
}
