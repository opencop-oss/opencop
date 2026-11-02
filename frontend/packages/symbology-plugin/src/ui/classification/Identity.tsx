// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo } from "react";
import { AffiliationFrameIcon, OcSelect } from "@opencop/ui";
import { LegacyC } from "@opencop/symbology";
import { useTranslation } from "react-i18next";
import { LabeledInput } from "../LabeledInput";
import { useSidc } from "../SidcContext";
import { selectProps } from "../editor.utils";

/** {@link LegacyC.STANDARD_IDENTITIES}'s `localeKey` → this plugin's locale key suffix.
 *  Mostly a capitalize, but Suspect and Hostile carry their 2525C alias here too. */
const LABEL_KEY_SUFFIX: Readonly<Record<string, string>> = {
    unknown: "Unknown",
    assumedFriend: "AssumedFriend",
    friend: "Friend",
    neutral: "Neutral",
    suspect: "SuspectJoker",
    hostile: "HostileFaker",
};

const identity = LegacyC.STANDARD_IDENTITIES.filter(({ code }) => code !== "0").map(
    ({ code, localeKey, color, frame }) => ({
        value: code,
        labelKey: `symbology:symbolWizard.identity${LABEL_KEY_SUFFIX[localeKey]}` as const,
        color,
        icon: <AffiliationFrameIcon frame={frame} />,
    }),
);

export function Identity() {
    const { sidc, setSidc } = useSidc();
    const { t } = useTranslation(["translation", "symbology"]);

    const items = useMemo(() => identity.map((i) => ({ ...i, label: t(i.labelKey) })), [t]);

    return (
        <LabeledInput label={t("symbology:symbolWizard.standardIdentity")}>
            <OcSelect
                value={sidc.identity}
                items={items}
                onValueChange={(v) => setSidc(sidc.withIdentity(v))}
                {...selectProps}
            />
        </LabeledInput>
    );
}
