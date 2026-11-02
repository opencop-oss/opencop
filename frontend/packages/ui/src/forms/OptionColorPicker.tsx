// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, ColorPicker, Portal, HStack, parseColor } from "@chakra-ui/react";

interface OptionColorPickerProps {
    label: string;
    value: string;
    onChange: (value: string) => void;
    width?: string;
}

export function OptionColorPicker({
    label,
    value,
    onChange,
    width = "100%",
}: OptionColorPickerProps) {
    return (
        <Flex width={width} direction="column" gap="1">
            <ColorPicker.Root
                defaultValue={parseColor(value)}
                maxW="200px"
                onValueChange={(details) => onChange(details.valueAsString)}
            >
                <ColorPicker.HiddenInput />
                <ColorPicker.Label fontSize="xs">{label}</ColorPicker.Label>
                <ColorPicker.Control>
                    <ColorPicker.Trigger border="none">
                        <ColorPicker.ValueSwatch boxSize="10" borderRadius="lg" />
                    </ColorPicker.Trigger>
                </ColorPicker.Control>
                <Portal>
                    <ColorPicker.Positioner border="none">
                        <ColorPicker.Content border="none">
                            <ColorPicker.Area border="none" />
                            <HStack border="none">
                                <ColorPicker.EyeDropper color="fg" size="lg" />
                                <ColorPicker.Sliders />
                                <ColorPicker.ValueSwatch />
                            </HStack>
                        </ColorPicker.Content>
                    </ColorPicker.Positioner>
                </Portal>
            </ColorPicker.Root>
        </Flex>
    );
}
