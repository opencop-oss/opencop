// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Box,
    Checkbox,
    Field,
    FileUpload,
    Flex,
    HStack,
    Icon,
    Input,
    RadioGroup,
    Textarea,
    VStack,
} from "@chakra-ui/react";
import { useState } from "react";
import { FaSearch } from "react-icons/fa";
import { LuUpload } from "react-icons/lu";
import { TbStar } from "react-icons/tb";
import { OcCombobox, OcSelect, OcTag } from "@opencop/ui";
import { ExampleSection } from "../ExampleSection";
import { ThemesSection } from "../ThemesSection";
const frameworkItems = [
    { label: "React", value: "react" },
    { label: "Vue", value: "vue" },
    { label: "Angular", value: "angular" },
    { label: "Svelte", value: "svelte" },
    { label: "Solid", value: "solid" },
    { label: "Ember", value: "ember" },
];

export function InputSection() {
    const [tags, setTags] = useState(["Alpha", "Bravo", "Charlie"]);
    const [checkA, setCheckA] = useState(true);
    const [checkB, setCheckB] = useState(false);
    const [radio, setRadio] = useState("tcp");
    const [priority, setPriority] = useState("");
    const [singleFramework, setSingleFramework] = useState("");
    const [multiFrameworks, setMultiFrameworks] = useState<string[]>([]);

    return (
        <ThemesSection title="Inputs & Controls">
            <VStack align="start" gap="8" w="full">
                <ExampleSection
                    label="Tag"
                    code={`import { OcTag } from "@opencop/ui";
import { TbStar } from "react-icons/tb";

<OcTag>Basic Tag</OcTag>
<OcTag closable onClose={() => {}}>Closable Tag</OcTag>
<OcTag startElement={<TbStar />}>With Icon</OcTag>
<OcTag colorPalette="brand">Brand</OcTag>
<OcTag colorPalette="red">Red</OcTag>
<OcTag colorPalette="accent">Accent</OcTag>`}
                >
                    <HStack gap="3" flexWrap="wrap">
                        <OcTag>Basic Tag</OcTag>
                        {tags.map((t) => (
                            <OcTag
                                key={t}
                                closable
                                onClose={() => setTags((prev) => prev.filter((x) => x !== t))}
                            >
                                {t}
                            </OcTag>
                        ))}
                        <OcTag startElement={<TbStar />}>With Icon</OcTag>
                        <OcTag colorPalette="brand">Brand</OcTag>
                        <OcTag colorPalette="red">Red</OcTag>
                        <OcTag colorPalette="accent">Accent</OcTag>
                    </HStack>
                </ExampleSection>

                <ExampleSection
                    label="Text Input"
                    code={`import { Field, Input } from "@chakra-ui/react";

<Field.Root>
    <Field.Label>Server Name</Field.Label>
    <Input placeholder="Enter server name" />
</Field.Root>

<Field.Root invalid={!!error}>
    <Field.Label>Email Address</Field.Label>
    <Input placeholder="Enter email" />
    <Field.ErrorText>Invalid email address</Field.ErrorText>
</Field.Root>

<Field.Root disabled>
    <Field.Label>Read Only</Field.Label>
    <Input placeholder="Disabled input" />
</Field.Root>`}
                >
                    <VStack gap="4" w="full" maxW="sm">
                        <Field.Root>
                            <Field.Label>Server Name</Field.Label>
                            <Input placeholder="Enter server name" />
                        </Field.Root>
                        <Field.Root invalid>
                            <Field.Label>Email Address</Field.Label>
                            <Input placeholder="Enter email" defaultValue="not-an-email" />
                            <Field.ErrorText>Invalid email address</Field.ErrorText>
                        </Field.Root>
                        <Field.Root disabled>
                            <Field.Label>Read Only</Field.Label>
                            <Input placeholder="Disabled input" />
                        </Field.Root>
                    </VStack>
                </ExampleSection>

                <ExampleSection
                    label="Text Input with Icon"
                    code={`import { Box, Flex, Input } from "@chakra-ui/react";
import { FaSearch } from "react-icons/fa";

<Flex
    width="200px"
    height="40px"
    borderWidth="1px"
    alignItems="center"
    bg="bg.glass"
>
    <Box ml={2}>
        <FaSearch color="var(--chakra-colors-fg-muted)" size={14} />
    </Box>
    <Input
        border="none"
        _focus={{ outline: "none" }}
        placeholder="Search..."
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
    />
</Flex>`}
                >
                    <VStack gap="4" w="full" maxW="sm">
                        <Field.Root>
                            <Field.Label>Search</Field.Label>
                            <Flex
                                width="full"
                                height="40px"
                                borderWidth="1px"
                                alignItems="center"
                                bg="bg.glass"
                            >
                                <Box ml={2}>
                                    <FaSearch color="var(--chakra-colors-fg-muted)" size={14} />
                                </Box>
                                <Input
                                    border="none"
                                    _focus={{ outline: "none" }}
                                    placeholder="Search layers..."
                                />
                            </Flex>
                        </Field.Root>
                    </VStack>
                </ExampleSection>

                <ExampleSection
                    label="Textarea"
                    code={`import { Field, Textarea } from "@chakra-ui/react";

<Field.Root>
    <Field.Label>Description</Field.Label>
    <Textarea placeholder="Enter a description..." />
</Field.Root>`}
                >
                    <VStack gap="4" w="full" maxW="sm">
                        <Field.Root>
                            <Field.Label>Description</Field.Label>
                            <Textarea placeholder="Enter a description..." />
                        </Field.Root>
                        <Field.Root disabled>
                            <Field.Label>Notes</Field.Label>
                            <Textarea placeholder="Disabled textarea" />
                        </Field.Root>
                    </VStack>
                </ExampleSection>

                {/* TODO: REPAIR WITH NEW UI ITEMS
                <ExampleSection
                    label="Number Input"
                    code={`import NumberInput from "~/components/ui/OcNumberInput";
import { Field } from "@chakra-ui/react";

<Field.Root>
    <Field.Label>Port Number</Field.Label>
    <OcNumberInput min={1} max={65535} step={1} defaultValue={8080}
        onChange={(v) => console.log(v)} />
</Field.Root>`}
                >
                    <VStack gap="4" w="full" maxW="sm">
                        <Field.Root>
                            <Field.Label>Port Number</Field.Label>
                            <OcNumberInput min={1} max={65535} step={1} defaultValue={8080} />
                        </Field.Root>
                        <Field.Root disabled>
                            <Field.Label>Timeout (ms)</Field.Label>
                            <OcNumberInput
                                min={0}
                                max={60000}
                                step={100}
                                defaultValue={5000}
                                disabled
                            />
                        </Field.Root>
                    </VStack>
                </ExampleSection>
                */}

                <ExampleSection
                    label="Select"
                    code={`import { OcSelect } from "@opencop/ui";
import { Field } from "@chakra-ui/react";

const options = [
    { label: "Low", value: "low" },
    { label: "Medium", value: "medium" },
    { label: "High", value: "high" },
    { label: "Critical", value: "critical" },
];

const [priority, setPriority] = useState("");

<Field.Root>
    <Field.Label>Priority</Field.Label>
    <OcSelect
        items={options}
        value={priority}
        onValueChange={setPriority}
        placeholder="Select priority..."
    />
</Field.Root>`}
                >
                    <VStack gap="4" w="full" maxW="sm">
                        <Field.Root>
                            <Field.Label>Priority (OcSelect)</Field.Label>
                            <OcSelect
                                items={[
                                    { label: "Low", value: "low" },
                                    { label: "Medium", value: "medium" },
                                    { label: "High", value: "high" },
                                    { label: "Critical", value: "critical" },
                                ]}
                                value={priority}
                                onValueChange={setPriority}
                                placeholder="Select priority..."
                            />
                        </Field.Root>
                        <Field.Root disabled>
                            <Field.Label>Disabled Select</Field.Label>
                            <OcSelect items={frameworkItems} defaultValue="react" disabled />
                        </Field.Root>
                    </VStack>
                </ExampleSection>

                <ExampleSection
                    label="OcCombobox (Wrapper)"
                    code={`import { OcCombobox } from "@opencop/ui";
import { Field } from "@chakra-ui/react";

const items = [
    { label: "React", value: "react" },
    { label: "Vue", value: "vue" },
    { label: "Angular", value: "angular" },
];

// Single-select (searchable)
const [framework, setFramework] = useState("");

<Field.Root>
    <Field.Label>Framework</Field.Label>
    <OcCombobox
        items={items}
        value={framework}
        onValueChange={setFramework}
        placeholder="Search frameworks..."
    />
</Field.Root>

// Multi-select with tags
const [selected, setSelected] = useState<string[]>([]);

<Field.Root>
    <Field.Label>Frameworks</Field.Label>
    <OcCombobox
        items={items}
        value={selected}
        onValueChange={setSelected}
        multiple
        placeholder="Select frameworks..."
    />
</Field.Root>`}
                >
                    <VStack gap="4" w="full" maxW="sm">
                        <Field.Root>
                            <Field.Label>Framework (Single)</Field.Label>
                            <OcCombobox
                                items={frameworkItems}
                                value={singleFramework}
                                onValueChange={(v) => setSingleFramework(v as string)}
                                placeholder="Search frameworks..."
                            />
                        </Field.Root>
                        <Field.Root>
                            <Field.Label>Frameworks (Multi with Tags)</Field.Label>
                            <OcCombobox
                                items={frameworkItems}
                                value={multiFrameworks}
                                onValueChange={(v) => setMultiFrameworks(v as string[])}
                                multiple
                                placeholder="Select frameworks..."
                            />
                        </Field.Root>
                    </VStack>
                </ExampleSection>

                <ExampleSection
                    label="Checkbox"
                    code={`import { Checkbox } from "@chakra-ui/react";

<Checkbox.Root
    checked={checked}
    onCheckedChange={(e) => setChecked(!!e.checked)}
>
    <Checkbox.HiddenInput />
    <Checkbox.Control>
        <Checkbox.Indicator />
    </Checkbox.Control>
    <Checkbox.Label>Visible</Checkbox.Label>
</Checkbox.Root>`}
                >
                    <VStack align="start" gap="3">
                        <Checkbox.Root
                            checked={checkA}
                            onCheckedChange={(e) => setCheckA(!!e.checked)}
                        >
                            <Checkbox.HiddenInput />
                            <Checkbox.Control>
                                <Checkbox.Indicator />
                            </Checkbox.Control>
                            <Checkbox.Label>Visible</Checkbox.Label>
                        </Checkbox.Root>
                        <Checkbox.Root
                            checked={checkB}
                            onCheckedChange={(e) => setCheckB(!!e.checked)}
                        >
                            <Checkbox.HiddenInput />
                            <Checkbox.Control>
                                <Checkbox.Indicator />
                            </Checkbox.Control>
                            <Checkbox.Label>Apply Style</Checkbox.Label>
                        </Checkbox.Root>
                        <Checkbox.Root disabled defaultChecked>
                            <Checkbox.HiddenInput />
                            <Checkbox.Control>
                                <Checkbox.Indicator />
                            </Checkbox.Control>
                            <Checkbox.Label>Disabled</Checkbox.Label>
                        </Checkbox.Root>
                    </VStack>
                </ExampleSection>

                <ExampleSection
                    label="Radio Group"
                    code={`import { RadioGroup } from "@chakra-ui/react";

<RadioGroup.Root
    colorPalette="brand"
    variant="outline"
    value={protocol}
    onValueChange={(d) => setProtocol(d.value)}
>
    {options.map((item) => (
        <RadioGroup.Item key={item.value} value={item.value}>
            <RadioGroup.ItemHiddenInput />
            <RadioGroup.ItemIndicator />
            <RadioGroup.ItemText>{item.label}</RadioGroup.ItemText>
        </RadioGroup.Item>
    ))}
</RadioGroup.Root>`}
                >
                    <Field.Root>
                        <Field.Label>Protocol</Field.Label>
                        <RadioGroup.Root
                            colorPalette="brand"
                            variant="outline"
                            value={radio}
                            onValueChange={(d) => d.value && setRadio(d.value)}
                        >
                            <HStack gap="4">
                                {[
                                    { label: "TCP", value: "tcp" },
                                    { label: "UDP", value: "udp" },
                                    { label: "Bucket", value: "bucket" },
                                ].map((item) => (
                                    <RadioGroup.Item key={item.value} value={item.value}>
                                        <RadioGroup.ItemHiddenInput />
                                        <RadioGroup.ItemIndicator />
                                        <RadioGroup.ItemText>{item.label}</RadioGroup.ItemText>
                                    </RadioGroup.Item>
                                ))}
                            </HStack>
                        </RadioGroup.Root>
                    </Field.Root>
                </ExampleSection>

                {/* TODO: REPAIR WITH NEW UI ITEMS
                <ExampleSection
                    label="Slider"
                    code={`import { OcSlider as ValueSlider } from "@opencop/ui";
import { Field } from "@chakra-ui/react";

<Field.Root>
    <Field.Label>Opacity</Field.Label>
    <OcSlider
        defaultValue={[0.8]}
        min={0}
        max={1}
        step={0.1}
        onValueChange={(e) => console.log(e.value)}
    />
</Field.Root>`}
                >
                    <VStack gap="4" w="full" maxW="sm">
                        <Field.Root>
                            <Field.Label>Opacity</Field.Label>
                            <OcSlider defaultValue={[0.8]} min={0} max={1} step={0.1} />
                        </Field.Root>
                        <Field.Root>
                            <Field.Label>Stroke Weight</Field.Label>
                            <OcSlider defaultValue={[3]} min={1} max={10} step={1} />
                        </Field.Root>
                    </VStack>
                </ExampleSection>

                */}

                <ExampleSection
                    label="File Dropzone"
                    code={`import { Field, FileUpload, Icon } from "@chakra-ui/react";
import { LuUpload } from "react-icons/lu";

<Field.Root>
    <Field.Label>Upload TAK Package</Field.Label>
    <FileUpload.Root accept={{ "application/zip": [".zip"] }}>
        <FileUpload.HiddenInput />
        <FileUpload.Dropzone>
            <Icon size="md" color="fg.muted"><LuUpload /></Icon>
            <FileUpload.DropzoneContent>
                <Box>Drop TAK zip package here</Box>
                <Box color="fg.muted">.zip up to 50MB</Box>
            </FileUpload.DropzoneContent>
        </FileUpload.Dropzone>
        <FileUpload.List />
    </FileUpload.Root>
</Field.Root>`}
                >
                    <Field.Root w="full">
                        <Field.Label>Upload TAK Package</Field.Label>
                        <FileUpload.Root
                            accept={{
                                "application/zip": [".zip"],
                                "application/x-zip-compressed": [".zip"],
                            }}
                        >
                            <FileUpload.HiddenInput />
                            <FileUpload.Dropzone>
                                <Icon size="md" color="fg.muted">
                                    <LuUpload />
                                </Icon>
                                <FileUpload.DropzoneContent>
                                    <Box>Drop TAK zip package here</Box>
                                    <Box color="fg.muted">.zip up to 50MB</Box>
                                </FileUpload.DropzoneContent>
                            </FileUpload.Dropzone>
                            <FileUpload.List />
                        </FileUpload.Root>
                    </Field.Root>
                </ExampleSection>
            </VStack>
        </ThemesSection>
    );
}
