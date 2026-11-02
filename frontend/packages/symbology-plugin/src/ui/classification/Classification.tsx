// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Section } from "@opencop/ui";
import { WIZARD_SECTION } from "../wizard-section";
import { useTranslation } from "react-i18next";
import { Context } from "./Context";
import { Identity } from "./Identity";
import { Status } from "./Status";

export function Classification() {
    const { t } = useTranslation(["translation", "symbology"]);
    return (
        <Section {...WIZARD_SECTION} heading={t("symbology:symbolWizard.classification")}>
            <Context />
            <Identity />
            <Status />
        </Section>
    );
}
