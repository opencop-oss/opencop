// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex } from "@chakra-ui/react";
import { OcSwitch } from "../forms/OcSwitch.tsx";
import { LayerOpacityControl } from "../forms/LayerOpacityControl.tsx";

export interface LayerToggleBodyProps {
    name: string;
    checked: boolean;
    opacity: number;
    opacityLabel: string;
    onCheckedChange: (checked: boolean) => void;
    onOpacityChange: (value: number) => void;
}

/** Compact toggle + opacity row used in sortable overlay lists (imagery, weather). */
export function LayerToggleBody({
    name,
    checked,
    opacity,
    opacityLabel,
    onCheckedChange,
    onOpacityChange,
}: LayerToggleBodyProps) {
    return (
        <Flex direction="column" px="2" py="3" gap="1.5">
            <OcSwitch
                checked={checked}
                label={name}
                onCheckedChange={onCheckedChange}
                colorPalette="brand"
                rootProps={{ size: "sm" }}
            />
            <LayerOpacityControl
                value={opacity}
                onChange={onOpacityChange}
                label={opacityLabel}
                inputAriaLabel={`${name} ${opacityLabel.toLowerCase()} value`}
            />
        </Flex>
    );
}
