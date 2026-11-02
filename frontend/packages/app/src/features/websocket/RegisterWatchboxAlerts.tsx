// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect } from "react";
import { Trans, useTranslation } from "react-i18next";
import {
    watchForAlerts,
    type WatchboxAlertEvent,
    type WatchboxAlertEventKind,
} from "../watchbox/watchbox.api";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { watchboxQueries } from "../watchbox/watchbox.queries";
import { recordDetection } from "../watchbox/watchbox-detections.cache";
import { useOCMPClient } from "./use-ocmp-client.hook";
import { useToast } from "../notifications/use-toast.hook";
import { useAuth } from "../auth/AuthContext";
import { useWatchboxMuteStore } from "../watchbox/watchbox-mute.store";
import type { Watchbox } from "../watchbox/watchbox.model";
import { Link } from "@chakra-ui/react";
import { dataSourceQueries } from "../data-sources/data-sources.queries";
import { useSelectAndFocusElement } from "../map/use-select-and-focus-element.hook";
import { TacticalText } from "@opencop/ui";

/** Full sentence per alert kind — word order and verb form differ per locale, so the
 *  verb and the linked object cannot be spliced into a shared template. */
const descriptionKey = {
    ENTER: "watchbox.alerts.enterDescription",
    LEAVE: "watchbox.alerts.leaveDescription",
} as const satisfies Partial<Record<WatchboxAlertEventKind, string>>;

/** Same rule, for an object the alert names but this client does not hold. */
const missingKey = {
    ENTER: "watchbox.alerts.enterMissing",
    LEAVE: "watchbox.alerts.leaveMissing",
} as const satisfies Partial<Record<WatchboxAlertEventKind, string>>;

type AlertDescriptionProps = {
    alert: WatchboxAlertEvent;
    watchbox: Watchbox;
};

/** Exported for `RegisterWatchboxAlerts.test.tsx`; rendered only as toast content. */
export function AlertDescription({ alert, watchbox }: AlertDescriptionProps) {
    const { t } = useTranslation();
    const { element, selectAndFocus } = useSelectAndFocusElement(alert);
    const { data: ds } = useQuery(dataSourceQueries.myList());
    const dataSourceName = ds?.find(
        (dataSource) => dataSource.serverId === alert.sourceId,
    )?.serverName;

    // Narrows `kind` for the key maps; notify() already filters to ENTER/LEAVE.
    if (alert.kind !== "ENTER" && alert.kind !== "LEAVE") return null;

    // Prefer the name captured with the alert so the live toast and retained
    // Notifications row identify the object consistently, even if the local
    // element has since been renamed or is not currently held.
    const objectName = alert.objectDisplayName ?? alert.objectId;

    // The data source's own name once the query lands; the raw id until then.
    //
    // NOT `watchbox.sourceStatuses`, which carries a name per source and is
    // already in hand: the API returns that list empty unless the caller
    // manages every layer the watchbox covers (`WatchboxResponseAssembler`
    // gates it on `decision.managesAll()`), so for most recipients of an alert
    // it is `[]`.
    const values = {
        watchboxName: watchbox.name,
        sourceName: dataSourceName ?? alert.sourceId,
    };

    // Told about an object we do not hold: name what happened, then say why
    // there is no link to follow. The display name still comes from the alert
    // payload, with the raw id as its backwards-compatible fallback.
    if (!element)
        return (
            <TacticalText fontSize="xs" color="type.warning" pl="5">
                {t(missingKey[alert.kind], { ...values, objectId: objectName })}{" "}
                {t("watchbox.alerts.missingHint")}
            </TacticalText>
        );

    // The object's name is a link INSIDE the sentence, so the sentence has to
    // stay one translation unit — `tw` puts the source ahead of the object,
    // `fr` puts it last, and neither survives being assembled from fragments.
    return (
        <TacticalText fontSize="xs" color="fg.muted" pl="5">
            <Trans
                i18nKey={descriptionKey[alert.kind]}
                values={{ ...values, objectName }}
                components={{
                    objectLink: (
                        <Link
                            fontFamily="mono"
                            as="button"
                            type="button"
                            fontWeight="bold"
                            color="active"
                            textDecoration="underline"
                            textDecorationColor="currentColor"
                            textUnderlineOffset="2px"
                            onClick={selectAndFocus}
                        />
                    ),
                }}
            />
        </TacticalText>
    );
}

export function RegisterWatchboxAlerts() {
    const { t } = useTranslation();
    const { data: watchboxes } = useQuery({
        ...watchboxQueries.watchboxes(),
    });
    const [client] = useOCMPClient();
    const { showToast } = useToast();
    const queryClient = useQueryClient();
    const { user } = useAuth();
    const userId = user?.user.id ?? "";

    useEffect(() => {
        if (!client) return;

        const subs = (watchboxes ?? []).map((watchbox) =>
            watchForAlerts(watchbox.id, {
                client,
                notify: (alert) => {
                    // Unconditional: the Notifications tab shows every kind, not just
                    // ENTER/LEAVE, and a muted watchbox still keeps a factual detection
                    // record — only the toast below is silenced. Writes straight into
                    // the cache instead of refetching — see watchbox-detections.cache.ts
                    // for why that's safe.
                    recordDetection(queryClient, alert);

                    if (alert.kind !== "ENTER" && alert.kind !== "LEAVE") return;
                    // Read imperatively rather than subscribing: a toggle mid-session
                    // should take effect on the next alert without tearing down and
                    // recreating every websocket subscription in this effect.
                    if (useWatchboxMuteStore.getState().actions.isMuted(userId, watchbox.id)) {
                        return;
                    }
                    showToast({
                        type: "important",
                        title: t("watchbox.alerts.title"),
                        content: <AlertDescription alert={alert} watchbox={watchbox} />,
                    });
                },
            }),
        );

        return () => subs.forEach((unsub) => unsub());
    }, [client, watchboxes, showToast, t, queryClient, userId]);

    return null;
}
