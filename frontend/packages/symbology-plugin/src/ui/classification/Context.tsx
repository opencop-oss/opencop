// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo } from "react";
import { OcTabSelect } from "@opencop/ui";
import { useTranslation } from "react-i18next";
import { useSidc } from "../SidcContext";
import { LabeledInput } from "../LabeledInput";

const context = [
    { value: "0", labelKey: "symbology:symbolWizard.contextReality" },
    { value: "1", labelKey: "symbology:symbolWizard.contextExercise" },
    { value: "2", labelKey: "symbology:symbolWizard.contextSimulation" },
] as const;

export function Context() {
    const { sidc, setSidc } = useSidc();
    const { t } = useTranslation(["translation", "symbology"]);

    const options = useMemo(
        () => context.map((c) => ({ value: c.value, label: t(c.labelKey) })),
        [t],
    );

    return (
        <LabeledInput label={t("symbology:symbolWizard.context")}>
            <OcTabSelect
                defaultValue="0"
                options={options}
                onChange={(v) => setSidc(sidc.withContext(v))}
                rootProps={{ size: "sm", h: "8" }}
                listProps={{
                    bg: "bg",
                    borderWidth: "1px",
                    borderRadius: "2px",
                    overflow: "hidden",
                }}
                triggerProps={{
                    color: "fg.muted",
                    // This control keeps the subtle slate indicator below rather than the active
                    // pill, so selection reads as full-strength text on it. The shared `bg`
                    // default would put near-black on near-black.
                    _selected: { color: "fg" },
                }}
                indicatorProps={{
                    bg: "slate.elevated",
                    border: "none",
                    boxShadow: "none",
                    outline: "none",
                }}
            />
        </LabeledInput>
    );
}
