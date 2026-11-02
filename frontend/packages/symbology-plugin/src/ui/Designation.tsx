// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { OcSelect, Section } from "@opencop/ui";
import { WIZARD_SECTION } from "./wizard-section";
import { Checkbox, HStack } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { useSidc } from "./SidcContext";
import { LabeledInput } from "./LabeledInput";
import { Catalog } from "@opencop/symbology";
import { selectProps } from "./editor.utils";
import { translateByKey } from "./translate-sidc.util";

const hqtfFlags = [
    { bit: 1, labelKey: "symbology:symbolWizard.hqtfFeintDummy" },
    { bit: 2, labelKey: "symbology:symbolWizard.hqtfHeadquarters" },
    { bit: 4, labelKey: "symbology:symbolWizard.hqtfTaskForce" },
] as const;

export function Designation() {
    const { sidc, setSidc } = useSidc();
    const { t } = useTranslation(["translation", "symbology"]);

    const hqtfCode = Number.parseInt(sidc.hqtf, 10);

    const toggleFlag = (bit: number) => {
        const next = (hqtfCode ^ bit).toString();
        setSidc(sidc.withHqtf(next));
    };

    const amplifierItems = Catalog.AMPLIFIERS.array.map(([code, amp]) => ({
        value: code,
        label: translateByKey(amp.localeKey, amp.label, t),
    }));

    const currentAmplifier = Catalog.AMPLIFIERS.record[sidc.amplifier];
    const descriptorItems = currentAmplifier
        ? Object.entries(currentAmplifier.descriptors).map(([code, desc]) => ({
              value: code,
              label: translateByKey(desc.localeKey, desc.label, t),
          }))
        : [];

    return (
        <Section {...WIZARD_SECTION} heading={t("symbology:symbolWizard.designation")}>
            <HStack w="full">
                <LabeledInput label={t("symbology:symbolWizard.amplifier")}>
                    <OcSelect
                        value={sidc.amplifier}
                        items={amplifierItems}
                        onValueChange={(v) => setSidc(sidc.withAmplifier(v))}
                        {...selectProps}
                    />
                </LabeledInput>
                <LabeledInput label={t("symbology:symbolWizard.descriptor")}>
                    <OcSelect
                        value={sidc.descriptor}
                        items={descriptorItems}
                        disabled={descriptorItems.length <= 1}
                        onValueChange={(v) => setSidc(sidc.withDescriptor(v))}
                        placeholder={t("symbology:symbolWizard.unspecified")}
                        {...selectProps}
                    />
                </LabeledInput>
            </HStack>
            <HStack gap="1" w="full">
                {hqtfFlags.map(({ bit, labelKey }) => {
                    const active = (hqtfCode & bit) !== 0;
                    return (
                        <Checkbox.Root
                            key={bit}
                            checked={active}
                            onCheckedChange={() => toggleFlag(bit)}
                            size="sm"
                            cursor="pointer"
                            flex="1"
                            h="8"
                            borderWidth="1px"
                            borderColor="border"
                            borderRadius="2px"
                            bg="bg"
                            display="flex"
                            alignItems="center"
                            justifyContent="center"
                            gap="1.5"
                            px="2"
                        >
                            <Checkbox.HiddenInput />
                            <Checkbox.Control />
                            <Checkbox.Label textStyle="xs" color="fg.muted" fontWeight="medium">
                                {t(labelKey)}
                            </Checkbox.Label>
                        </Checkbox.Root>
                    );
                })}
            </HStack>
        </Section>
    );
}
