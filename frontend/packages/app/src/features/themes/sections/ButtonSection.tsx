// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Button, HStack, IconButton, Text, VStack } from "@chakra-ui/react";
import { TbEdit, TbTrash, TbPlus } from "react-icons/tb";
import { FaPlay, FaPlus, FaStop, FaSync, FaTrash } from "react-icons/fa";
import { ThemesSection } from "../ThemesSection";
import { ExampleSection } from "../ExampleSection";
import {
    GlassButton as CustomButton,
    ActionButton,
    IconButton as CustomIconButton,
} from "@opencop/ui";

export function ButtonSection() {
    return (
        <ThemesSection title="Buttons">
            <VStack align="start" gap="8" w="full">
                <ExampleSection
                    label="Chakra Button Variants"
                    code={`<Button colorPalette="brand" variant="solid">Solid</Button>
<Button colorPalette="brand" variant="subtle">Subtle</Button>
<Button colorPalette="brand" variant="surface">Surface</Button>
<Button colorPalette="brand" variant="outline">Outline</Button>
<Button colorPalette="brand" variant="ghost">Ghost</Button>
<Button colorPalette="brand" variant="plain">Plain</Button>`}
                >
                    <HStack gap="3" flexWrap="wrap">
                        <Button colorPalette="brand" variant="solid">
                            Solid
                        </Button>
                        <Button colorPalette="brand" variant="subtle">
                            Subtle
                        </Button>
                        <Button colorPalette="brand" variant="surface">
                            Surface
                        </Button>
                        <Button colorPalette="brand" variant="outline">
                            Outline
                        </Button>
                        <Button colorPalette="brand" variant="ghost">
                            Ghost
                        </Button>
                        <Button colorPalette="brand" variant="plain">
                            Plain
                        </Button>
                    </HStack>
                </ExampleSection>

                <ExampleSection
                    label="Color Palettes"
                    code={`<Button colorPalette="brand">Brand</Button>
<Button colorPalette="red">Red</Button>
<Button colorPalette="accent">Accent</Button>
<Button colorPalette="purple">Purple</Button>
<Button colorPalette="success">Success</Button>
<Button colorPalette="warning">Warning</Button>`}
                >
                    <HStack gap="3" flexWrap="wrap">
                        <Button colorPalette="brand">Brand</Button>
                        <Button colorPalette="red">Red</Button>
                        <Button colorPalette="accent">Accent</Button>
                        <Button colorPalette="purple">Purple</Button>
                        <Button colorPalette="success">Success</Button>
                        <Button colorPalette="warning">Warning</Button>
                    </HStack>
                </ExampleSection>

                <ExampleSection
                    label="Button Sizes"
                    code={`<Button colorPalette="brand" size="xs">Extra Small</Button>
<Button colorPalette="brand" size="sm">Small</Button>
<Button colorPalette="brand" size="md">Medium</Button>
<Button colorPalette="brand" size="lg">Large</Button>`}
                >
                    <HStack gap="3" flexWrap="wrap" align="center">
                        <Button colorPalette="brand" size="xs">
                            Extra Small
                        </Button>
                        <Button colorPalette="brand" size="sm">
                            Small
                        </Button>
                        <Button colorPalette="brand" size="md">
                            Medium
                        </Button>
                        <Button colorPalette="brand" size="lg">
                            Large
                        </Button>
                    </HStack>
                </ExampleSection>

                <ExampleSection
                    label="Loading State"
                    code={`<Button colorPalette="brand" loading loadingText="Saving...">Save</Button>
<Button colorPalette="brand" loading>Save</Button>`}
                >
                    <HStack gap="3">
                        <Button colorPalette="brand" loading loadingText="Saving...">
                            Save
                        </Button>
                        <Button colorPalette="brand" loading>
                            Save
                        </Button>
                    </HStack>
                </ExampleSection>

                <ExampleSection
                    label="Chakra IconButton — Variants"
                    code={`<IconButton aria-label="action" colorPalette="brand" variant="solid"><FaPlus /></IconButton>
<IconButton aria-label="action" colorPalette="brand" variant="subtle"><FaPlus /></IconButton>
<IconButton aria-label="action" colorPalette="brand" variant="outline"><FaPlus /></IconButton>
<IconButton aria-label="action" colorPalette="brand" variant="ghost"><FaPlus /></IconButton>
<IconButton aria-label="action" colorPalette="brand" variant="plain"><FaPlus /></IconButton>`}
                >
                    <VStack align="start" gap="3">
                        {(["solid", "subtle", "outline", "ghost", "plain"] as const).map(
                            (variant) => (
                                <HStack key={variant} gap="3" align="center">
                                    <Text fontSize="xs" color="fg.muted" w="60px">
                                        {variant}
                                    </Text>
                                    {(["2xs", "xs", "sm", "md", "lg"] as const).map((size) => (
                                        <IconButton
                                            key={size}
                                            aria-label={`${variant} ${size}`}
                                            colorPalette="brand"
                                            variant={variant}
                                            size={size}
                                        >
                                            <FaPlus />
                                        </IconButton>
                                    ))}
                                </HStack>
                            ),
                        )}
                        <HStack gap="1" mt="1">
                            <Text fontSize="xs" color="fg.muted" w="60px" />
                            {["2xs", "xs", "sm", "md", "lg"].map((size) => (
                                <Text
                                    key={size}
                                    fontSize="2xs"
                                    color="fg.muted"
                                    textAlign="center"
                                    flex="0 0 auto"
                                    w="32px"
                                >
                                    {size}
                                </Text>
                            ))}
                        </HStack>
                    </VStack>
                </ExampleSection>

                <ExampleSection
                    label="Chakra IconButton — Color Palettes (ghost)"
                    code={`<IconButton variant="ghost" colorPalette="brand"><FaSync /></IconButton>
<IconButton variant="ghost" colorPalette="red"><FaTrash /></IconButton>
<IconButton variant="ghost" colorPalette="gray"><FaStop /></IconButton>`}
                >
                    <HStack gap="3" flexWrap="wrap">
                        {(["brand", "red", "gray", "accent", "green", "purple"] as const).map(
                            (cp) => (
                                <VStack key={cp} gap="1">
                                    <IconButton
                                        aria-label={cp}
                                        variant="ghost"
                                        size="sm"
                                        colorPalette={cp}
                                    >
                                        <FaPlus />
                                    </IconButton>
                                    <Text fontSize="2xs" color="fg.muted">
                                        {cp}
                                    </Text>
                                </VStack>
                            ),
                        )}
                    </HStack>
                </ExampleSection>

                <ExampleSection
                    label="Toolbar Button Example"
                    code={`// Dense toolbar with plain/ghost icon buttons
<HStack gap="1.5">
  <IconButton size="xs" variant="plain" colorPalette="brand"><FaSync /></IconButton>
  <IconButton size="xs" variant="plain" colorPalette="brand"><FaPlus /></IconButton>
  <IconButton size="xs" variant="ghost" colorPalette="brand"><FaSync /></IconButton>
  <IconButton size="xs" variant="ghost" colorPalette="brand"><FaPlus /></IconButton>
</HStack>`}
                >
                    <VStack align="start" gap="3">
                        <HStack gap="1.5" align="center">
                            <Text fontSize="xs" color="fg.muted" w="50px">
                                plain
                            </Text>
                            <IconButton
                                aria-label="refresh"
                                size="xs"
                                variant="plain"
                                colorPalette="brand"
                            >
                                <FaSync />
                            </IconButton>
                            <IconButton
                                aria-label="add"
                                size="xs"
                                variant="plain"
                                colorPalette="brand"
                            >
                                <FaPlus />
                            </IconButton>
                            <IconButton
                                aria-label="play"
                                size="xs"
                                variant="plain"
                                colorPalette="brand"
                            >
                                <FaPlay />
                            </IconButton>
                            <IconButton
                                aria-label="stop"
                                size="xs"
                                variant="plain"
                                colorPalette="brand"
                            >
                                <FaStop />
                            </IconButton>
                            <IconButton
                                aria-label="delete"
                                size="xs"
                                variant="plain"
                                colorPalette="red"
                            >
                                <FaTrash />
                            </IconButton>
                        </HStack>
                        <HStack gap="1.5" align="center">
                            <Text fontSize="xs" color="fg.muted" w="50px">
                                ghost
                            </Text>
                            <IconButton
                                aria-label="refresh"
                                size="xs"
                                variant="ghost"
                                colorPalette="brand"
                            >
                                <FaSync />
                            </IconButton>
                            <IconButton
                                aria-label="add"
                                size="xs"
                                variant="ghost"
                                colorPalette="brand"
                            >
                                <FaPlus />
                            </IconButton>
                            <IconButton
                                aria-label="play"
                                size="xs"
                                variant="ghost"
                                colorPalette="brand"
                            >
                                <FaPlay />
                            </IconButton>
                            <IconButton
                                aria-label="stop"
                                size="xs"
                                variant="ghost"
                                colorPalette="brand"
                            >
                                <FaStop />
                            </IconButton>
                            <IconButton
                                aria-label="delete"
                                size="xs"
                                variant="ghost"
                                colorPalette="red"
                            >
                                <FaTrash />
                            </IconButton>
                        </HStack>
                        <HStack gap="1.5" align="center">
                            <Text fontSize="xs" color="fg.muted" w="50px">
                                subtle
                            </Text>
                            <IconButton
                                aria-label="refresh"
                                size="xs"
                                variant="subtle"
                                colorPalette="brand"
                            >
                                <FaSync />
                            </IconButton>
                            <IconButton
                                aria-label="add"
                                size="xs"
                                variant="subtle"
                                colorPalette="brand"
                            >
                                <FaPlus />
                            </IconButton>
                            <IconButton
                                aria-label="play"
                                size="xs"
                                variant="subtle"
                                colorPalette="brand"
                            >
                                <FaPlay />
                            </IconButton>
                            <IconButton
                                aria-label="stop"
                                size="xs"
                                variant="subtle"
                                colorPalette="brand"
                            >
                                <FaStop />
                            </IconButton>
                            <IconButton
                                aria-label="delete"
                                size="xs"
                                variant="subtle"
                                colorPalette="red"
                            >
                                <FaTrash />
                            </IconButton>
                        </HStack>
                        <HStack gap="1.5" align="center">
                            <Text fontSize="xs" color="fg.muted" w="50px">
                                outline
                            </Text>
                            <IconButton
                                aria-label="refresh"
                                size="xs"
                                variant="outline"
                                colorPalette="brand"
                            >
                                <FaSync />
                            </IconButton>
                            <IconButton
                                aria-label="add"
                                size="xs"
                                variant="outline"
                                colorPalette="brand"
                            >
                                <FaPlus />
                            </IconButton>
                            <IconButton
                                aria-label="play"
                                size="xs"
                                variant="outline"
                                colorPalette="brand"
                            >
                                <FaPlay />
                            </IconButton>
                            <IconButton
                                aria-label="stop"
                                size="xs"
                                variant="outline"
                                colorPalette="brand"
                            >
                                <FaStop />
                            </IconButton>
                            <IconButton
                                aria-label="delete"
                                size="xs"
                                variant="outline"
                                colorPalette="red"
                            >
                                <FaTrash />
                            </IconButton>
                        </HStack>
                    </VStack>
                </ExampleSection>

                <ExampleSection
                    label="Glass Button (styled)"
                    code={`import { GlassButton } from "@opencop/ui";

<Button size="sm">Small</Button>
<Button size="md">Medium</Button>
<Button size="lg">Large</Button>`}
                >
                    <HStack gap="3" flexWrap="wrap" align="center">
                        <CustomButton size="sm">Small</CustomButton>
                        <CustomButton size="md">Medium</CustomButton>
                        <CustomButton size="lg">Large</CustomButton>
                    </HStack>
                </ExampleSection>

                <ExampleSection
                    label="ActionButton (styled)"
                    code={`import { ActionButton } from "@opencop/ui";

<ActionButton>Action</ActionButton>`}
                >
                    <HStack gap="3">
                        <ActionButton>Action</ActionButton>
                    </HStack>
                </ExampleSection>

                <ExampleSection
                    label="IconButton (styled)"
                    code={`import { IconButton } from "@opencop/ui";
import { TbEdit, TbTrash, TbPlus } from "react-icons/tb";

<IconButton size="sm"><TbEdit /></IconButton>
<IconButton size="md"><TbTrash /></IconButton>
<IconButton size="lg"><TbPlus /></IconButton>`}
                >
                    <HStack gap="4" align="center">
                        <CustomIconButton size="sm">
                            <TbEdit />
                        </CustomIconButton>
                        <CustomIconButton size="md">
                            <TbTrash />
                        </CustomIconButton>
                        <CustomIconButton size="lg">
                            <TbPlus />
                        </CustomIconButton>
                    </HStack>
                </ExampleSection>
            </VStack>
        </ThemesSection>
    );
}
