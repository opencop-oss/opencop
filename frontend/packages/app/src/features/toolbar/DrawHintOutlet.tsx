// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Text, VStack } from "@chakra-ui/react";
import { Panel } from "@opencop/ui";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { getTool } from "~/features/contributions/tool-registry";
import { sessionDrawHints } from "~/features/map/interaction-runtime/session/derive/draw-hints";
import type { Session } from "~/features/map/interaction-runtime/session/session";
import { useInteraction } from "~/features/map/interaction-runtime/session/interaction.store";
import { ContributionBoundary } from "~/features/contributions/ContributionBoundary";
import { ownerOfToolId } from "~/features/plugin-manager/plugin-tool-id";
import { toolOrigin } from "@opencop/contributions/containment";

/**
 * Where the active session's draw hints are shown — one host slot for every
 * tool, separate from the tool's `editor`, so a tool can have both.
 *
 * The hints come from the session alone (phase, locked count, custody), never
 * from the geometry, so the outlet re-renders when the session changes and not
 * on every pointer move. The list is a polite live region: a screen reader
 * hears each new instruction without being interrupted.
 */
export function DrawHintOutlet(): ReactElement {
    const session = useInteraction((s) => s.session);
    const descriptorId = session.kind === "idle" ? null : session.descriptorId;

    return (
        <VStack align="center" aria-live="polite" pointerEvents="none">
            {/* A tool's `hints` is contributed code; contained, a throwing one
                costs only its hints. `resetKeys` on the descriptor id means the
                next tool's hints show cleanly. */}
            {session.kind !== "idle" ? (
                <ContributionBoundary
                    origin={toolOrigin(
                        descriptorId ? ownerOfToolId(descriptorId) : undefined,
                        descriptorId ?? "edit",
                        "hints",
                    )}
                >
                    <DrawHintList session={session} />
                </ContributionBoundary>
            ) : null}
        </VStack>
    );
}

function DrawHintList({ session }: { session: Session }): ReactElement | null {
    const { t } = useTranslation();
    const hints = sessionDrawHints(session, getTool);
    if (hints.length === 0) return null;

    return (
        <Panel>
            <VStack align="start" p="2" gap="0.5">
                {hints.map((hint) => (
                    <Text key={hint.key} fontSize="xs" color="fg.subtle">
                        {t(hint.key as never, hint.values)}
                    </Text>
                ))}
            </VStack>
        </Panel>
    );
}
