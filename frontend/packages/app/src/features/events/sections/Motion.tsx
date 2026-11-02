// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { HStack, Text } from "@chakra-ui/react";
import { Card } from "../detail/Card";
import { useTranslation } from "react-i18next";

export interface MotionProps {
    speed?: number | null;
    course?: number | null;
    heading?: number | null;
}

export function Motion({ speed, course, heading }: MotionProps) {
    const { t } = useTranslation();
    const notApplicable = t("events.notApplicable");
    const deg = t("events.degreesUnit");
    return (
        <HStack>
            <Card label={t("events.speed")}>
                <Text>
                    {speed != null
                        ? `${speed.toFixed(2)} ${t("events.metersPerSecondUnit")}`
                        : notApplicable}
                </Text>
            </Card>
            <Card label={t("events.course")}>
                <Text>{course != null ? `${course.toFixed(2)}${deg}` : notApplicable}</Text>
            </Card>
            <Card label={t("events.heading")}>
                <Text>{heading != null ? `${heading.toFixed(2)}${deg}` : notApplicable}</Text>
            </Card>
        </HStack>
    );
}
