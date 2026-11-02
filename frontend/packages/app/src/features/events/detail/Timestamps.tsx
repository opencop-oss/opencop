// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { HStack, Text } from "@chakra-ui/react";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { DateTime } from "luxon";
import { DetailTable, type DetailTableRow } from "@opencop/ui";
import { isNeverStale } from "@opencop/elements";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";

export interface TimestampsProps {
    timestampz: {
        time?: number | null;
        start?: number | null;
        stale?: number | null;
    };
}

export function Timestamps(props: TimestampsProps): ReactElement {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const orderedTimestampKeys: Array<keyof TimestampsProps["timestampz"]> = ["time", "stale"];

    const rows: DetailTableRow[] = orderedTimestampKeys.map((key) => {
        const time = props.timestampz[key];
        const isStale = key === "stale";
        const displayValue =
            time && isStale && isNeverStale(time) ? (
                <Text
                    textStyle="xs"
                    letterSpacing="1.5px"
                    color={isStale ? "orange.400" : "active"}
                >
                    {t("common.never")}
                </Text>
            ) : time == null ? (
                <Text textStyle="xs" letterSpacing="1px" color={isStale ? "orange.400" : "active"}>
                    {t("events.notApplicable")}
                </Text>
            ) : (
                <HStack gap="2">
                    <Text
                        textStyle="xs"
                        letterSpacing="1.5px"
                        color={isStale ? "orange.400" : "active"}
                    >
                        {DateTime.fromMillis(time)
                            .setZone(timezone)
                            .toLocaleString(DateTime.DATE_SHORT)}
                    </Text>
                    <Text
                        textStyle="xs"
                        letterSpacing="1px"
                        color={isStale ? "orange.400" : "active"}
                    >
                        {DateTime.fromMillis(time)
                            .setZone(timezone)
                            .toLocaleString(DateTime.TIME_WITH_SECONDS)}
                    </Text>
                </HStack>
            );
        return {
            key,
            label: t(`events.field${key.charAt(0).toUpperCase() + key.slice(1)}` as never, {
                defaultValue: key.toUpperCase(),
            }),
            value: displayValue,
        };
    });

    return <DetailTable rows={rows} />;
}
