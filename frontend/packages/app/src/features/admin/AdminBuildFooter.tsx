// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Text } from "@chakra-ui/react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { appMetadataQueries } from "./app-metadata.queries";
import { formatBuildIdentity, getBuildFooterDisplayState } from "./app-metadata-state";

export function AdminBuildFooter() {
    const { t } = useTranslation();
    const { data, isError, isPending } = useQuery(appMetadataQueries.detail());
    const unknownLabel = t("adminPanel.unknownVersion");
    const displayState = getBuildFooterDisplayState(
        {
            version: import.meta.env.VITE_APP_VERSION,
            revision: import.meta.env.VITE_BUILD_SHA,
        },
        {
            version: data?.appVersion,
            revision: data?.admin?.bff?.revision,
        },
    );
    const webBuild =
        displayState.status === "match"
            ? formatBuildIdentity(
                  {
                      version: displayState.version,
                      revision: displayState.webRevision,
                  },
                  unknownLabel,
              )
            : formatBuildIdentity(displayState.web, unknownLabel);

    let footerText: string | undefined;
    if (isError) {
        footerText = t("adminPanel.serverMetadataUnavailable");
    } else if (isPending) {
        footerText = t("adminPanel.webBuildSummary", { webBuild });
    } else if (displayState.status === "match") {
        footerText = t("adminPanel.versionSummary", {
            version: displayState.version,
            webRevision: displayState.webRevision ?? unknownLabel,
            serverRevision: displayState.serverRevision ?? unknownLabel,
        });
    } else if (displayState.status === "mismatch") {
        footerText = t("adminPanel.versionMismatchSummary", {
            webBuild,
            serverBuild: formatBuildIdentity(displayState.server, unknownLabel),
        });
    } else {
        footerText = t("adminPanel.versionMetadataMissingSummary", {
            webBuild,
            serverBuild: formatBuildIdentity(displayState.server, unknownLabel),
        });
    }

    return (
        <Flex
            as="footer"
            justify="start"
            align="center"
            wrap="wrap"
            mt="auto"
            py={2}
            columnGap={4}
            rowGap={1}
            color="fg.muted"
            fontSize="xs"
            opacity={0.6}
        >
            <Text fontFamily="mono">{footerText}</Text>
        </Flex>
    );
}
