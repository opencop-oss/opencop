// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { BoxProps, TextProps } from "@chakra-ui/react";
import type { ReactElement } from "react";
import { DateTime } from "luxon";
import { TacticalText } from "@opencop/ui";
import { Card } from "./Card";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";

export interface TimestampCardProps {
    label: string;
    time: number;
    rootProps?: BoxProps;
    dateProps?: TextProps;
    timeProps?: TextProps;
}

export function TimestampCard(props: TimestampCardProps): ReactElement {
    const timezone = useUserTimezone();
    const dt = DateTime.fromMillis(props.time).setZone(timezone);

    return (
        <Card label={props.label} stackProps={{ align: "start" }} {...props.rootProps}>
            <TacticalText color="bg.contrast" {...props.dateProps}>
                {dt.toLocaleString(DateTime.DATE_SHORT)}
            </TacticalText>
            <TacticalText color="active" {...props.timeProps}>
                {dt.toLocaleString(DateTime.TIME_WITH_SECONDS)}
            </TacticalText>
        </Card>
    );
}
