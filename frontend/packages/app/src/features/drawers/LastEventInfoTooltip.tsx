// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Text } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { InfoTooltip } from "@opencop/ui";

/**
 * Which resource's rules to explain. "Last Event" means something different for each — received
 * data, produced features, or was looked at — and the column header alone doesn't say which.
 */
export type LastEventSubject = "dataSource" | "mapper" | "layer";

// The "about 15 seconds" / "around 5 for Kafka" timings quoted in these translation keys are
// prose copies of the backend's LastEventAtCoalescer.DEFAULT_FLUSH_INTERVAL — nothing ties them
// together, so re-quote them here if that constant ever changes.
const SUBJECT_EXPLANATION_KEYS: Record<LastEventSubject, readonly string[]> = {
    dataSource: ["lastEventInfo.dataSource.trigger", "lastEventInfo.dataSource.frequency"],
    mapper: ["lastEventInfo.mapper.trigger", "lastEventInfo.mapper.frequency"],
    layer: ["lastEventInfo.layer.trigger", "lastEventInfo.layer.frequency"],
};

interface LastEventInfoTooltipProps {
    subject: LastEventSubject;
}

/** Explains what advances a resource's "Last Event" time, and how often it can move. */
export function LastEventInfoTooltip({ subject }: LastEventInfoTooltipProps) {
    const { t } = useTranslation();

    const content = (
        <Box minW="260px">
            {SUBJECT_EXPLANATION_KEYS[subject].map((key) => (
                <Text key={key} fontSize="xs" color="fg.muted" lineHeight="short" py={1}>
                    {t(key as never)}
                </Text>
            ))}
            <Text fontSize="xs" color="fg.muted" lineHeight="short" py={1}>
                {t("lastEventInfo.blank")}
            </Text>
        </Box>
    );

    return <InfoTooltip content={content} aria-label={t("common.lastEventAt")} />;
}
