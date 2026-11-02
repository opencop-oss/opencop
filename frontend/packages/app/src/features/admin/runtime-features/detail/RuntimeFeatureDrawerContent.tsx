// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Alert, Button, Flex, Separator, Stack, Text } from "@chakra-ui/react";
import { getErrorMessage, isRuntimeFeatureConflict } from "@opencop/api";
import { assertNever } from "assert-never";
import { ConfirmDialog, DetailTable, DrawerSectionHeader, DrawerSideNavLayout } from "@opencop/ui";
import type { DrawerSideNavSection } from "@opencop/ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { DateTime } from "luxon";
import { type ReactElement, useState } from "react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { FaFlag, FaHistory } from "react-icons/fa";
import { createOverviewSection } from "~/features/drawers/relation-section.util";
import { DrawerEditActions } from "~/features/drawers/DrawerEditActions";
import { useDrawerBusy } from "~/features/drawers/DrawerContext";
import { useToast } from "~/features/notifications/use-toast.hook";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { AudienceModeSelect } from "../AudienceModeSelect";
import { AudienceGroupPicker } from "../AudienceGroupPicker";
import { StatusBadge } from "../StatusBadge";
import { RuntimeFeatureHistory } from "./RuntimeFeatureHistory";
import { EMPTY_CELL } from "../status.const";
import {
    type AudienceMode,
    audienceUserGroupIds,
    buildAudience,
    confirmationReason,
    type ConfirmationReason,
    consumerClassLabelKey,
} from "../audience.util";
import { findFeatureCopy } from "../runtime-features.manifest";
import type { RuntimeFeatureRow } from "../runtime-features.rows";
import { runtimeFeatureMutations } from "../runtime-features.queries";

interface RuntimeFeatureDrawerContentProps {
    row: RuntimeFeatureRow;
}

/** An audience the admin chose, held until they confirm it. */
interface PendingSave {
    mode: AudienceMode;
    groupIds: readonly string[];
    reason: ConfirmationReason;
}

/**
 * The audience a completed save replaced, with the revision that save produced.
 *
 * The revision is the one the server returned rather than the one in cache, so Undo carries an
 * `expectedRevision` that is exact even before the metadata refetch lands.
 */
interface UndoTarget {
    mode: AudienceMode;
    groupIds: readonly string[];
    revision: number;
}

export function RuntimeFeatureDrawerContent({
    row,
}: RuntimeFeatureDrawerContentProps): ReactElement {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const queryClient = useQueryClient();
    const { showToast } = useToast();
    const record = row.record;

    const storedMode: AudienceMode = record?.audience.mode ?? "NONE";
    const storedGroupIds = record ? audienceUserGroupIds(record.audience) : [];

    // Draft state exists only while an edit is in flight; null means "showing what's stored".
    const [draftMode, setDraftMode] = useState<AudienceMode | null>(null);
    const [draftGroupIds, setDraftGroupIds] = useState<readonly string[] | null>(null);
    /** The audience a concurrent-edit rejection left unsaved, held so it can be resubmitted. */
    const [conflict, setConflict] = useState<{
        mode: AudienceMode;
        groupIds: readonly string[];
    } | null>(null);
    const [pending, setPending] = useState<PendingSave | null>(null);
    const [undoTarget, setUndoTarget] = useState<UndoTarget | null>(null);

    const mode = draftMode ?? storedMode;
    const groupIds = draftGroupIds ?? storedGroupIds;
    // Audience is editable for every registered feature, backend-enforced ones included: the
    // endpoint has no consumer-class filter, and the drawer is the only surface an operator has —
    // Swagger is not deployed in production. What stays read-only is the feature's identity: its
    // name and its consumer class, which is bound in lockstep to the existence of enforcement code.
    const editable = record != null;

    const { mutate: patchAudience, isPending: isSaving } = useMutation(
        runtimeFeatureMutations.patchAudience(queryClient),
    );
    // Every result of this write lands in a mutate-time callback owned by this component, so the
    // enclosing drawer has to stay mounted until it settles.
    useDrawerBusy(isSaving);

    const reset = () => {
        setDraftMode(null);
        setDraftGroupIds(null);
        setConflict(null);
        setPending(null);
    };

    /**
     * Abandons a change the admin declined to confirm.
     *
     * For a mode the select commits on its own, the draft goes with it, so declining leaves the
     * control showing the stored audience rather than a mode that was never written. A group list
     * is different: it is deliberate work the admin may only have wanted to look at again, and the
     * picker already carries its own Cancel for abandoning it — so that draft survives and only
     * the dialog closes. A pending conflict is left standing either way: its Retry carries its own
     * attempted audience and is a separate decision.
     */
    const cancelPending = () => {
        const keepDraft = pending?.mode === "USER_GROUPS";
        setPending(null);
        if (keepDraft) return;
        setDraftMode(null);
        setDraftGroupIds(null);
    };

    /** Clears the post-save notice. */
    const dismissUndo = () => {
        setUndoTarget(null);
    };

    /**
     * Writes an audience and records what it replaced.
     *
     * `revert` marks the write as the Undo of an earlier one: it carries the revision that earlier
     * write returned, and it clears the notice rather than offering to undo the undo.
     */
    const save = (nextMode: AudienceMode, nextGroupIds: readonly string[], revert?: UndoTarget) => {
        if (!record) return;
        const replaced = { mode: storedMode, groupIds: storedGroupIds };
        patchAudience(
            {
                name: record.name,
                // The revision read alongside this audience; the server rejects the write if
                // someone else changed the feature in between rather than silently clobbering it.
                patch: {
                    audience: buildAudience(nextMode, nextGroupIds),
                    expectedRevision: revert?.revision ?? record.revision,
                },
            },
            // Mutate-time callbacks, so the mutation's own invalidation still runs first and this
            // drawer's UI state stays out of the shared mutation definition.
            {
                onSuccess: (updated) => {
                    reset();
                    setUndoTarget(revert ? null : { ...replaced, revision: updated.revision });
                    showToast({ type: "success", title: t("runtimeFeatures.audienceSaved") });
                },
                onError: (error) => {
                    if (isRuntimeFeatureConflict(error)) {
                        // The rejected revision is the one that was in cache, so a plain retry
                        // would fail identically forever. The mutation's `onSettled` policy has
                        // already invalidated both canonical views before this runs, so keep the
                        // attempted audience and let the admin resubmit against what came back —
                        // but do not invalidate again here: a second conflict-only refresh would
                        // duplicate the low-frequency reads that policy deliberately owns. The
                        // alert carries the explanation and the Retry, so a toast on top of it
                        // would be the same news twice.
                        setConflict({ mode: nextMode, groupIds: nextGroupIds });
                        // Both of the other affordances were decided against the revision the
                        // server just rejected. The confirmation dialog would sit over the alert
                        // and resubmit its original intent without the admin ever seeing what
                        // changed, and Undo pins the rejected revision, so it could never succeed.
                        // Retry replaces both: it carries this attempted audience against the
                        // revision the reread loaded.
                        setPending(null);
                        dismissUndo();
                        return;
                    }
                    showToast({ type: "error", title: getErrorMessage(error) });
                },
            },
        );
    };

    /** Routes a save through the confirmation dialog when the change is high-consequence. */
    const requestSave = (nextMode: AudienceMode, nextGroupIds: readonly string[]) => {
        if (!record) return;
        const reason = confirmationReason({
            consumerClass: record.consumerClass,
            storedMode,
            storedGroupIds,
            nextMode,
            nextGroupIds,
        });
        if (reason) {
            setPending({ mode: nextMode, groupIds: nextGroupIds, reason });
            return;
        }
        save(nextMode, nextGroupIds);
    };

    const handleModeChange = (nextMode: AudienceMode) => {
        setDraftMode(nextMode);
        // Nobody and Everyone carry no targets, so there is nothing left to fill in — commit
        // immediately. USER_GROUPS needs at least one group before the server will accept it.
        if (nextMode === "USER_GROUPS") {
            setDraftGroupIds(groupIds);
            return;
        }
        // Selecting the audience the flag already has is not an edit. Guarding it means a change
        // event the user did not cause — a remount re-emitting the control's first option — cannot
        // write to a live flag.
        if (nextMode === storedMode) return;
        requestSave(nextMode, []);
    };

    const declared = findFeatureCopy(row.name);
    const pickingGroups = editable && mode === "USER_GROUPS";
    const groupsDirty = draftGroupIds != null || draftMode === "USER_GROUPS";
    /**
     * The saved audience targets nobody, so the feature is genuinely off for everyone. The picker
     * blocks saving that state, so it can only arrive from an older build or a backend write.
     */
    const storedOff = storedMode === "USER_GROUPS" && storedGroupIds.length === 0;
    const previousModeLabel = undoTarget
        ? t(`runtimeFeatures.audienceModes.${undoTarget.mode}` as never)
        : "";

    const overviewRows = [
        { key: "name", label: t("runtimeFeatures.name"), value: row.name },
        {
            key: "status",
            label: t("runtimeFeatures.status"),
            value: (
                // align="start" or the Stack stretches the badge into a full-width bar.
                <Stack gap={1} align="start">
                    <StatusBadge status={row.status} />
                    {row.status === "archived" && (
                        // Archived is per-build, so say what it means here rather than letting the
                        // one-word label imply a global lifecycle claim.
                        <Text fontSize="xs" color="fg.muted">
                            {t("runtimeFeatures.archivedHelp")}
                        </Text>
                    )}
                </Stack>
            ),
        },
        {
            key: "description",
            label: t("runtimeFeatures.description"),
            value: declared ? t(declared.descriptionKey as never) : EMPTY_CELL,
        },
        {
            key: "consumerClass",
            label: t("runtimeFeatures.consumer"),
            value: record ? t(consumerClassLabelKey(record.consumerClass) as never) : EMPTY_CELL,
        },
        {
            key: "audience",
            label: t("runtimeFeatures.audience"),
            value: !record ? (
                EMPTY_CELL
            ) : (
                <AudienceModeSelect value={mode} onChange={handleModeChange} disabled={isSaving} />
            ),
        },
        {
            key: "revision",
            label: t("runtimeFeatures.revision"),
            value: String(record?.revision ?? EMPTY_CELL),
        },
        {
            key: "updatedAt",
            label: t("runtimeFeatures.updatedAt"),
            value: record
                ? DateTime.fromISO(record.updatedAt)
                      .setZone(timezone)
                      .toLocaleString(DateTime.DATETIME_MED)
                : EMPTY_CELL,
        },
    ];

    const overview = (
        <Stack gap={4}>
            <DetailTable rows={overviewRows} compact />
            {conflict && (
                // Chakra's Alert.Root renders a bare div. This one appears in response to the
                // admin's own Save and carries the only Retry/Discard affordance, so a screen
                // reader has to be told it arrived.
                <Alert.Root status="warning" role="alert">
                    <Alert.Indicator />
                    <Alert.Content>
                        <Alert.Title>{t("runtimeFeatures.conflictTitle")}</Alert.Title>
                        <Alert.Description>
                            {t("runtimeFeatures.conflictBody", {
                                attempted: t(
                                    `runtimeFeatures.audienceModes.${conflict.mode}` as never,
                                ),
                                current: t(`runtimeFeatures.audienceModes.${storedMode}` as never),
                            })}
                        </Alert.Description>
                        {/* USER_GROUPS already has a Save under the group picker, which resubmits
                            the same draft — a second button here would be two controls for one
                            action. */}
                        {!pickingGroups && (
                            <Flex gap={2} mt={3}>
                                <Button
                                    size="sm"
                                    colorPalette="brand"
                                    loading={isSaving}
                                    onClick={() => save(conflict.mode, conflict.groupIds)}
                                >
                                    {t("common.retry")}
                                </Button>
                                <Button
                                    size="sm"
                                    variant="subtle"
                                    // Without this the button inherits the alert's warning palette
                                    // and reads as destructive. Discarding an unsaved draft is the
                                    // mild choice here — the stored audience is untouched either
                                    // way. `secondary` is the app's cancel palette (DialogActions).
                                    colorPalette="secondary"
                                    disabled={isSaving}
                                    onClick={reset}
                                >
                                    {t("common.discard")}
                                </Button>
                            </Flex>
                        )}
                    </Alert.Content>
                </Alert.Root>
            )}
            {undoTarget != null && (
                // Inline rather than an action on the success toast: the revert has to outlive a
                // toast timeout, and this is already where the drawer puts "something needs your
                // attention". role="alert" for the same reason the conflict alert has it.
                <Alert.Root status="info" role="alert">
                    <Alert.Indicator />
                    <Alert.Content>
                        <Alert.Title>{t("runtimeFeatures.audienceSaved")}</Alert.Title>
                        <Alert.Description>
                            {t("runtimeFeatures.undoBody", { previous: previousModeLabel })}
                        </Alert.Description>
                        <Flex gap={2} mt={3}>
                            {undoTarget && (
                                // Reverting writes without confirming: it restores the audience the
                                // admin had a moment ago, and asking again would put a dialog in
                                // front of the affordance that exists to undo one.
                                <Button
                                    size="sm"
                                    colorPalette="brand"
                                    loading={isSaving}
                                    onClick={() =>
                                        save(undoTarget.mode, undoTarget.groupIds, undoTarget)
                                    }
                                >
                                    {t("runtimeFeatures.undoAction")}
                                </Button>
                            )}
                            <Button
                                size="sm"
                                variant="subtle"
                                colorPalette="secondary"
                                disabled={isSaving}
                                onClick={dismissUndo}
                            >
                                {t("common.close")}
                            </Button>
                        </Flex>
                    </Alert.Content>
                </Alert.Root>
            )}
            {pickingGroups && (
                <>
                    <Separator />
                    <form
                        onSubmit={(event) => {
                            event.preventDefault();
                            requestSave("USER_GROUPS", groupIds);
                        }}
                    >
                        <Stack gap={3}>
                            <AudienceGroupPicker
                                selectedIds={groupIds}
                                onChange={setDraftGroupIds}
                                disabled={isSaving}
                            />
                            {storedOff && (
                                <Text fontSize="xs" color="fg.muted">
                                    {t("runtimeFeatures.audienceOffNoGroups")}
                                </Text>
                            )}
                            {groupIds.length === 0 && (
                                <Text fontSize="xs" color="fg.muted">
                                    {t("runtimeFeatures.selectAtLeastOneGroup")}
                                </Text>
                            )}
                            {/* The server rejects USER_GROUPS with no targets, so block the submit
                                here rather than surfacing an error the admin can do nothing about. */}
                            <DrawerEditActions
                                onCancel={reset}
                                loading={isSaving}
                                canSave={groupIds.length > 0 && groupsDirty}
                            />
                        </Stack>
                    </form>
                </>
            )}
            {pending && (
                <ConfirmDialog
                    open
                    onClose={cancelPending}
                    onConfirm={() => save(pending.mode, pending.groupIds)}
                    title={confirmTitle(t, pending.reason, row.name)}
                    message={confirmBody(t, pending.reason, row.name)}
                    confirmLabel={confirmAction(t, pending.reason)}
                    // Only the two access-removing reasons are destructive. A backend-enforced
                    // change can just as easily be an enable, so a red confirm would misread it.
                    isDestructive={pending.reason !== "backendEnforced"}
                    loading={isSaving}
                />
            )}
        </Stack>
    );

    const sections: DrawerSideNavSection[] = [
        createOverviewSection(t, overview, t("drawerSections.entity.runtimeFeature")),
    ];

    if (record) {
        sections.push({
            key: "history",
            label: t("runtimeFeatures.history"),
            icon: <FaHistory />,
            content: (
                <>
                    <DrawerSectionHeader
                        title={t("runtimeFeatures.history")}
                        description={t("drawerSections.description.runtimeFeatureHistory")}
                    />
                    <RuntimeFeatureHistory name={record.name} />
                </>
            ),
        });
    }

    return (
        <DrawerSideNavLayout
            name={row.name}
            badgeLabel={t("runtimeFeatures.featureFlag")}
            navLabel={t("adminNav.runtimeFeatures")}
            icon={<FaFlag />}
            sections={sections}
        />
    );
}

/**
 * Copy for features whose real-world effect is not obvious from "loses access".
 *
 * Keyed by catalog name rather than read from the frontend manifest, because a backend-enforced
 * feature is deliberately absent from that manifest. A feature with no entry falls back to the
 * reason-based copy, which is accurate but generic.
 */
const FEATURE_CONSEQUENCES = {
    watchbox: { turnOff: "runtimeFeatures.watchboxTurnOffConsequence" },
} as const satisfies Record<string, { turnOff: TranslationKey }>;

type TranslationKey = Parameters<TFunction>[0];

function featureConsequence(t: TFunction, name: string): string | undefined {
    const keys = FEATURE_CONSEQUENCES[name as keyof typeof FEATURE_CONSEQUENCES];
    return keys ? t(keys.turnOff) : undefined;
}

/** Joins the generic consequence with a feature-specific one when there is one to add. */
function withConsequence(body: string, consequence: string | undefined): string {
    return consequence ? `${body} ${consequence}` : body;
}

// Literal keys per reason rather than an interpolated key path, so `i18n:check` can see them.

function confirmTitle(t: TFunction, reason: ConfirmationReason, name: string): string {
    switch (reason) {
        case "turningOff":
            return t("runtimeFeatures.confirmTurnOffTitle", { name });
        case "narrowingAccess":
            return t("runtimeFeatures.confirmNarrowTitle", { name });
        case "backendEnforced":
            return t("runtimeFeatures.confirmBackendTitle", { name });
        default:
            return assertNever(reason);
    }
}

function confirmBody(t: TFunction, reason: ConfirmationReason, name: string): string {
    switch (reason) {
        case "turningOff":
            return withConsequence(
                t("runtimeFeatures.confirmTurnOffBody"),
                featureConsequence(t, name),
            );
        case "narrowingAccess":
            return t("runtimeFeatures.confirmNarrowBody");
        case "backendEnforced":
            return t("runtimeFeatures.confirmBackendBody");
        default:
            return assertNever(reason);
    }
}

function confirmAction(t: TFunction, reason: ConfirmationReason): string {
    switch (reason) {
        case "turningOff":
            return t("runtimeFeatures.confirmTurnOffAction");
        case "narrowingAccess":
            return t("runtimeFeatures.confirmNarrowAction");
        case "backendEnforced":
            return t("runtimeFeatures.confirmBackendAction");
        default:
            return assertNever(reason);
    }
}
