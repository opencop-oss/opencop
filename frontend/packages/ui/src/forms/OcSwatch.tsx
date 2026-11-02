// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Box,
    ColorPicker,
    ColorPickerChannelSlider,
    HStack,
    parseColor,
    Portal,
    VStack,
    type ColorPickerRootProps,
} from "@chakra-ui/react";
import { type ReactElement } from "react";

const COLOR_PICKER_Z_INDEX = 9999;

export interface OcSwatchProps {
    defaultValue?: string;
    value?: string;
    onChange?: (color: string) => void;
    rootProps?: ColorPickerRootProps;
    alpha?: boolean;
    label?: string;
}

export function OcSwatch({ value, defaultValue, onChange, ...rest }: OcSwatchProps): ReactElement {
    return (
        <ColorPicker.Root
            w="full"
            h="10"
            defaultValue={defaultValue ? parseColor(defaultValue) : undefined}
            value={value ? parseColor(value) : undefined}
            format="rgba"
            onValueChange={(e) => {
                if (onChange) onChange(e.value.toString("hexa"));
            }}
            gap="1"
            {...rest.rootProps}
        >
            <ColorPicker.Control>
                <ColorPicker.Trigger
                    aria-label={rest.label}
                    css={{
                        "& .colorPicker__swatch": {
                            w: "4",
                            h: "4",
                            borderRadius: "sm",
                            border: "none",
                        },
                    }}
                />
                <ColorPicker.Input
                    w="full"
                    h="full"
                    css={{ "&:focus, &:focus-visible": { outline: "none", boxShadow: "none" } }}
                />
            </ColorPicker.Control>
            <Portal>
                <Box position="relative" zIndex={COLOR_PICKER_Z_INDEX}>
                    <ColorPicker.Positioner>
                        <ColorPicker.Content>
                            <ColorPicker.Area />
                            <HStack>
                                <ColorPicker.EyeDropper size="sm" variant="outline" />
                                <VStack w="full">
                                    <ColorPickerChannelSlider w="full" channel="hue" />
                                    {rest.alpha && (
                                        <ColorPickerChannelSlider w="full" channel="alpha" />
                                    )}
                                </VStack>
                                <ColorPicker.ValueSwatch />
                            </HStack>
                            <ColorPicker.Input w="full" />
                        </ColorPicker.Content>
                    </ColorPicker.Positioner>
                </Box>
            </Portal>
        </ColorPicker.Root>
    );
}
