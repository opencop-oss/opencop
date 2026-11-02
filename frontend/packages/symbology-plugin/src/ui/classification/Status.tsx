// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo } from "react";
import { OcSelect } from "@opencop/ui";
import { useTranslation } from "react-i18next";
import { LabeledInput } from "../LabeledInput";
import { HStack } from "@chakra-ui/react/stack";
import { useSidc } from "../SidcContext";
import { selectProps } from "../editor.utils";

const statuses = [
    { value: "0", labelKey: "symbology:symbolWizard.statusPresent" },
    { value: "1", labelKey: "symbology:symbolWizard.statusAnticipated" },
] as const;

const conditions = [
    { value: "0", labelKey: "symbology:symbolWizard.conditionUnspecified" },
    { value: "2", labelKey: "symbology:symbolWizard.conditionFullyCapable" },
    { value: "3", labelKey: "symbology:symbolWizard.conditionDamaged" },
    { value: "4", labelKey: "symbology:symbolWizard.conditionDestroyed" },
    { value: "5", labelKey: "symbology:symbolWizard.conditionFullToCapacity" },
] as const;

export function Status() {
    const { sidc, setSidc } = useSidc();
    const { t } = useTranslation(["translation", "symbology"]);

    const statusItems = useMemo(
        () => statuses.map((s) => ({ value: s.value, label: t(s.labelKey) })),
        [t],
    );

    const conditionItems = useMemo(
        () => conditions.map((c) => ({ value: c.value, label: t(c.labelKey) })),
        [t],
    );

    // "1" = anticipated, anything else = present (0, 2, 3, 4, 5)
    const isAnticipated = sidc.status === "1";

    return (
        <HStack w="full">
            <LabeledInput label={t("symbology:symbolWizard.status")}>
                <OcSelect
                    value={isAnticipated ? "1" : "0"}
                    items={statusItems}
                    onValueChange={(v) => setSidc(sidc.withStatus(v))}
                    {...selectProps}
                />
            </LabeledInput>
            <LabeledInput label={t("symbology:symbolWizard.condition")}>
                <OcSelect
                    value={isAnticipated ? "0" : sidc.status}
                    items={conditionItems}
                    disabled={isAnticipated}
                    onValueChange={(v) => setSidc(sidc.withStatus(v))}
                    {...selectProps}
                />
            </LabeledInput>
        </HStack>
    );
}
