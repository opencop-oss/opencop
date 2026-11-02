// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement, ReactNode } from "react";
import { Box, Flex, Menu } from "@chakra-ui/react";
import { MdCheck } from "react-icons/md";
import { useTranslation } from "react-i18next";
import { Angle } from "@opencop/elements";
import type { ContextMenuContentProps } from "@opencop/contributions";
import { useDisplayPreferences } from "@opencop/ui";
import type { AngleUnit } from "@opencop/utils";
import { formatAngleValue } from "./angle-format.util";

const ANGLE_UNITS: readonly AngleUnit[] = ["degrees", "mils", "both"];

/**
 * The angle's block in the map's right-click menu: its reading in the operator's
 * angle unit, and a choice of that unit (which every angle on the map follows).
 */
export function AngleMenuBlock({ target, close }: ContextMenuContentProps): ReactElement | null {
    const { t } = useTranslation();
    const { angleUnit, setAngleUnit, angleOrientation } = useDisplayPreferences();
    const { element } = target;
    if (!element || !Angle.is(element)) return null;

    const labels: Record<AngleUnit, string> = {
        degrees: t("mapSettings.angleUnits.degrees"),
        mils: t("mapSettings.angleUnits.mils"),
        both: t("mapSettings.angleUnits.both"),
    };

    return (
        <>
            <Box my="1" borderTopWidth="1px" borderColor="border" />
            <Box px="3" py="1" fontSize="xs" color="fg.muted" fontWeight="semibold">
                {formatAngleValue(
                    Angle.getDirectionalAngle(element, angleOrientation),
                    angleUnit,
                    t,
                )}
            </Box>
            <Menu.RadioItemGroup
                value={angleUnit}
                onValueChange={(details) => {
                    setAngleUnit(details.value as AngleUnit);
                    close();
                }}
            >
                {ANGLE_UNITS.map((unit) => (
                    <AngleUnitMenuItem key={unit} value={unit} selected={angleUnit === unit}>
                        {labels[unit]}
                    </AngleUnitMenuItem>
                ))}
            </Menu.RadioItemGroup>
        </>
    );
}

/** An angle-unit option with a check for the selected one. */
function AngleUnitMenuItem({
    children,
    selected,
    value,
}: {
    children: ReactNode;
    selected: boolean;
    value: AngleUnit;
}): ReactElement {
    return (
        <Menu.RadioItem
            value={value}
            px="3"
            py="1.5"
            fontSize="sm"
            color={selected ? "active" : "fg.muted"}
            fontWeight={selected ? "semibold" : "normal"}
            _highlighted={{ bg: "whiteAlpha.200" }}
        >
            <Flex align="center" gap="2" width="100%">
                <Box w="4" flexShrink={0}>
                    <Menu.ItemIndicator>
                        <MdCheck />
                    </Menu.ItemIndicator>
                </Box>
                {children}
            </Flex>
        </Menu.RadioItem>
    );
}
