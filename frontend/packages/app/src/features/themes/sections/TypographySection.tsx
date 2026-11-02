// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Heading, Text, VStack } from "@chakra-ui/react";
import { ThemesSection } from "../ThemesSection";
import { ExampleSection } from "../ExampleSection";
import {
    ActiveHeading,
    TacticalHeading,
    BoxedHeading,
    TacticalText,
    AccentHeading,
} from "@opencop/ui";
import { Label } from "~/features/events/detail/Label";

export function TypographySection() {
    return (
        <ThemesSection title="Typography">
            <VStack align="start" gap="8" w="full">
                <ExampleSection
                    label="Chakra Heading Sizes"
                    code={`<Heading size="xs">Heading xs</Heading>
<Heading size="sm">Heading sm</Heading>
<Heading size="md">Heading md</Heading>
<Heading size="lg">Heading lg</Heading>
<Heading size="xl">Heading xl</Heading>
<Heading size="2xl">Heading 2xl</Heading>
<Heading size="3xl">Heading 3xl</Heading>
<Heading size="4xl">Heading 4xl</Heading>
<Heading size="5xl">Heading 5xl</Heading>`}
                >
                    <VStack align="start" gap="2">
                        <Heading size="xs">Heading xs</Heading>
                        <Heading size="sm">Heading sm</Heading>
                        <Heading size="md">Heading md</Heading>
                        <Heading size="lg">Heading lg</Heading>
                        <Heading size="xl">Heading xl</Heading>
                        <Heading size="2xl">Heading 2xl</Heading>
                        <Heading size="3xl">Heading 3xl</Heading>
                        <Heading size="4xl">Heading 4xl</Heading>
                        <Heading size="5xl">Heading 5xl</Heading>
                    </VStack>
                </ExampleSection>

                <ExampleSection
                    label="Chakra Text Variants"
                    code={`<Text>Default body text</Text>
<Text color="fg.muted">Muted text (fg.muted)</Text>
<Text fontSize="2xs">Font size 2xs (10px)</Text>
<Text fontSize="xs">Font size xs (12px)</Text>
<Text fontSize="sm">Font size sm (14px)</Text>
<Text fontSize="md">Font size md (16px)</Text>
<Text fontSize="lg">Font size lg (18px)</Text>
<Text fontSize="xl">Font size xl (20px)</Text>`}
                >
                    <VStack align="start" gap="2">
                        <Text>Default body text</Text>
                        <Text color="fg.muted">Muted text (fg.muted)</Text>
                        <Text fontSize="2xs">Font size 2xs (10px)</Text>
                        <Text fontSize="xs">Font size xs (12px)</Text>
                        <Text fontSize="sm">Font size sm (14px)</Text>
                        <Text fontSize="md">Font size md (16px)</Text>
                        <Text fontSize="lg">Font size lg (18px)</Text>
                        <Text fontSize="xl">Font size xl (20px)</Text>
                    </VStack>
                </ExampleSection>

                <ExampleSection
                    label="ActiveHeading"
                    code={`import { ActiveHeading } from "@opencop/ui";

<ActiveHeading>Active Heading</ActiveHeading>`}
                >
                    <ActiveHeading>Active Heading</ActiveHeading>
                </ExampleSection>

                <ExampleSection
                    label="TacticalHeading"
                    code={`import { TacticalHeading } from "@opencop/ui";

<TacticalHeading>Tactical Heading</TacticalHeading>`}
                >
                    <TacticalHeading>Tactical Heading</TacticalHeading>
                </ExampleSection>

                <ExampleSection
                    label="BoxedHeading"
                    code={`import { BoxedHeading } from "@opencop/ui";

<BoxedHeading>Boxed Heading</BoxedHeading>`}
                >
                    <BoxedHeading>Boxed Heading</BoxedHeading>
                </ExampleSection>

                <ExampleSection
                    label="TacticalText"
                    code={`import { TacticalText } from "@opencop/ui";

<TacticalText>Tactical text content — smaller, tactical font</TacticalText>`}
                >
                    <TacticalText>Tactical text content — smaller, tactical font</TacticalText>
                </ExampleSection>

                <ExampleSection
                    label="AccentHeading"
                    code={`import AccentHeading from "~/modules/mapbar/events/components/AccentHeading";

<AccentHeading>Accent Heading</AccentHeading>`}
                >
                    <AccentHeading>Accent Heading</AccentHeading>
                </ExampleSection>

                <ExampleSection
                    label="Label"
                    code={`import Label from "~/features/events/detail/Label";

<Label>Field Label</Label>`}
                >
                    <Label>Field Label</Label>
                </ExampleSection>
            </VStack>
        </ThemesSection>
    );
}
