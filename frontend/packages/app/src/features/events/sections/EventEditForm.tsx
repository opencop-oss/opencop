// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo, useState } from "react";
import {
    Box,
    Button,
    Checkbox,
    Combobox,
    Flex,
    HStack,
    Icon,
    Input,
    Text,
    VStack,
} from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import * as Yup from "yup";
import { LuTriangleAlert } from "react-icons/lu";
import { ApiError, getErrorMessage, type EventDetails } from "@opencop/api";
import { ConfirmDialog, OcDateTimePicker } from "@opencop/ui";
import { LayerComboBox } from "~/features/layers/LayerComboBox";
import { DateTime } from "luxon";
import { useEventsEditingStore, useEventsEditingActions } from "../events-editing.store";
import { MoveRebroadcastError, useEventSave } from "../use-event-save.hook";
import { useToast } from "~/features/notifications/use-toast.hook";
import { useQuery } from "@tanstack/react-query";
import { layerQueries } from "~/features/layers/layers.queries";
import { cancel } from "~/features/map/interaction-runtime/session/commands";
import { useDismissedDialogs } from "~/features/preferences/use-dismissed-dialogs.hook";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { usePickedElement } from "~/features/map/use-picked-element.hook";
import { GeometryEditForms } from "./GeometryEditForms";
import { defaultStale, localEventSource, neverStale } from "@opencop/elements";
import { AttributesEditor } from "./AttributesEditor";
import { useSourcesWithState } from "@opencop/ingest";
import { getAllowedEventSourceIds } from "../event-edit-source.util";
import type { Editability } from "~/features/map/editability";
import Logger from "js-logger";

const logger = Logger.get("events");

const HIDDEN_LAYER_DISMISS_ID = "events/hiddenLayerBroadcast";
const PENDING_ATTR_FIELDS = new Set(["pendingAttrName", "pendingAttrValue"]);

function createEventEditSchema(allowedSourceIds: string[], t: TFunction, requireSource: boolean) {
    return Yup.object().shape(
        {
            // A remote event must always resolve to a source: clearing the
            // combobox emits "", which would otherwise slip past the oneOf guard
            // below and turn the save into a silent local no-op.
            source: (requireSource
                ? Yup.string().required(t("events.sourceLayerRequired"))
                : Yup.string()
            ).when({
                is: (val: string) => val && val !== "local",
                then: (schema) => schema.oneOf(allowedSourceIds, t("events.invalidSourceLayer")),
            }),
            displayName: Yup.string()
                .trim()
                .min(1, t("events.nameRequired"))
                .max(50, t("events.nameMaxLength")),
            timestampz: Yup.object().shape({
                time: Yup.number().positive(t("events.invalidTime")),
                start: Yup.number().positive(t("events.invalidStartTime")),
                stale: Yup.number().positive(t("events.invalidStaleTime")),
            }),
            pendingAttrName: Yup.string().when("pendingAttrValue", {
                is: (val: string) => val && val.trim().length > 0,
                then: (schema) => schema.trim().min(1, t("events.nameRequired")),
            }),
            pendingAttrValue: Yup.string().when("pendingAttrName", {
                is: (val: string) => val && val.trim().length > 0,
                then: (schema) => schema.trim().min(1, t("events.valueRequired" as never)),
            }),
            attributes: Yup.lazy((obj) =>
                Yup.object(
                    Object.fromEntries(
                        Object.keys((obj as Record<string, unknown>) ?? {}).map((key) => [
                            key,
                            Yup.string()
                                .trim()
                                .min(1, t("events.valueRequired" as never)),
                        ]),
                    ),
                ),
            ),
        },
        [["pendingAttrName", "pendingAttrValue"]],
    );
}

interface EventEditFormProps {
    details: EventDetails;
    editability: Editability;
}

export function EventEditForm({ details, editability }: EventEditFormProps) {
    const { t } = useTranslation();
    const { showToast } = useToast();
    const timezone = useUserTimezone();
    const overlay = useEventsEditingStore((s) => s.overlay);
    const touchedFields = useEventsEditingStore((s) => s.touchedFields);
    const { setField, deleteField, markTouched, unmarkTouched } = useEventsEditingActions();
    const { data: layers } = useQuery(layerQueries.myList());
    const [isSaving, setIsSaving] = useState(false);
    const [confirmOpen, setConfirmOpen] = useState(false);
    const disabledSources = new Set(useSourcesWithState("disabled"));
    const { isDismissed, dismiss } = useDismissedDialogs();
    const pickedElement = usePickedElement();

    // Small change for now I think, component could use some re-organization in the future (along with calendar probably).
    const [never, setNever] = useState<boolean>(details.timestampz.stale === neverStale);

    // An event that already lives on a remote source keeps that source unless the
    // user opts into a move: without the opt-in, changing the field would silently
    // relocate the event on save. A move needs a route to delete the old copy
    // through *and* broadcast permission on this user — data-source events carry
    // a mutation target for property edits while `canBroadcast` stays false, so
    // checking the route alone would offer them a move the model forbids.
    const { mutationTarget } = editability;
    const isRemote = details.source !== localEventSource;
    const [move, setMove] = useState<boolean>(false);
    const canMove = isRemote && !!mutationTarget && editability.canBroadcast;
    // Whether the layer field itself is unlocked — distinct from the permission
    // flag `Editability.canBroadcast` above.
    const isLayerFieldEditable = !isRemote || move;

    const save = useEventSave(mutationTarget, move);

    // Pending "add attribute" row — local state, validated via Yup
    const [pendingAttrName, setPendingAttrName] = useState("");
    const [pendingAttrValue, setPendingAttrValue] = useState("");

    // Merge overlay on top of details for display and validation. The overlay is sparse —
    // it only carries `attributes` once the user edits them — so prefer the overlay's
    // attributes when present (reflecting in-progress add/delete/value edits) and fall back
    // to the committed event's attributes otherwise.
    const source = useMemo(() => {
        if (!overlay) return details;
        const overlayAttributes = overlay.attributes as Record<string, unknown> | undefined;
        return {
            ...details,
            ...overlay,
            attributes: overlayAttributes ?? details.attributes ?? {},
        } as EventDetails;
    }, [details, overlay]);

    const layerIds = useMemo(() => (layers ?? []).map((l) => l.layerId), [layers]);
    const allowedSourceIds = useMemo(
        () => getAllowedEventSourceIds(layerIds, details.source, isRemote),
        [isRemote, details.source, layerIds],
    );

    // Validate the entire form including the pending attribute row
    const validationErrors = useMemo(() => {
        if (!overlay) return {};
        const errors: Record<string, string> = {};
        const schema = createEventEditSchema(allowedSourceIds, t, isRemote);
        const toValidate = { ...source, pendingAttrName, pendingAttrValue };
        try {
            schema.validateSync(toValidate, { abortEarly: false });
        } catch (err) {
            if (err instanceof Yup.ValidationError) {
                if (err.inner) {
                    err.inner.forEach((e) => {
                        if (e.path) errors[e.path] = e.message;
                    });
                } else if (err.path) {
                    errors[err.path] = err.message;
                }
            }
        }
        return errors;
    }, [overlay, source, allowedSourceIds, t, isRemote, pendingAttrName, pendingAttrValue]);

    // Pending attribute errors only block save once their field has been
    // touched — this prevents the form from going invalid the instant the
    // user starts typing in one add-row field. Committed attribute errors
    // (from Yup.lazy) always block save immediately.
    const isFormValid = Object.keys(validationErrors).every(
        (key) => PENDING_ATTR_FIELDS.has(key) && !touchedFields.has(key),
    );

    const visibleErrors = Object.fromEntries(
        Object.entries(validationErrors).filter(([key]) => touchedFields.has(key)),
    );

    const selectedLayerId = source.source === "local" ? null : source.source;
    const isTargetLayerHidden = selectedLayerId ? disabledSources.has(selectedLayerId) : false;

    const staleStartTime = useMemo(
        () =>
            details.timestampz.stale == null
                ? undefined
                : DateTime.fromMillis(details.timestampz.stale, { zone: timezone }),
        [details.timestampz.stale, timezone],
    );
    const startStartTime = useMemo(
        () =>
            details.timestampz.start == null
                ? DateTime.now().setZone(timezone)
                : DateTime.fromMillis(details.timestampz.start, { zone: timezone }),
        [details.timestampz.start, timezone],
    );

    const stalePickerStartTime = useMemo(
        () =>
            !staleStartTime || staleStartTime.toMillis() === neverStale
                ? DateTime.fromMillis(defaultStale(), { zone: timezone })
                : staleStartTime,
        [staleStartTime, timezone],
    );
    // Domain floor ("not before now"), not a calendar bound — and not threaded
    // into `startTime`, so recomputing per render cannot reset the picker selection.
    const staleMin = DateTime.now();

    // Hint under the combobox for the locked / optional states. The move
    // consequence hint renders under the checkbox that triggers it instead —
    // once the field is unlocked, no hint sits between field and checkbox.
    const layerHint = !isLayerFieldEditable
        ? null
        : isRemote
          ? null
          : t("events.broadcastLayerHint" as never, "Optional — leave empty to keep local only");

    const doSave = async () => {
        setIsSaving(true);
        try {
            // A move is not a delete followed by a save: `useEventSave` owns both
            // halves so the delete is scoped to the server (the element has to
            // survive locally for the re-broadcast to re-key and re-select it)
            // and a failure anywhere aborts the whole save.
            await save();
            showToast({
                type: "success",
                title: t("events.eventSaved"),
                description: t("events.eventSavedDesc"),
            });
        } catch (error) {
            // Real API failures surface the server's message; internal invariant
            // errors (e.g. "No geojson to save") stay out of the UI behind a
            // translated generic fallback. Raw error is kept for DEV diagnosis.
            if (import.meta.env.DEV) logger.error("[events] save failed", error);
            showToast({
                type: "error",
                title: t("events.saveFailed"),
                description:
                    // A failed move already committed its delete — the message
                    // must say the event is gone from its old layer, not imply
                    // the save changed nothing.
                    error instanceof MoveRebroadcastError
                        ? t(
                              "events.moveRebroadcastFailed" as never,
                              "The event was removed from its old layer but could not be re-broadcast — pick a layer and save again to retry",
                          )
                        : error instanceof ApiError
                          ? getErrorMessage(error, t("events.failedToSave"))
                          : t("events.failedToSave"),
            });
        } finally {
            setIsSaving(false);
        }
    };

    const handleSave = () => {
        // Auto-commit the pending add row if both fields have content.
        // The user may type name + value and click Save without pressing
        // Enter or +. Since the overlay is read via getState() at save
        // time, this synchronous write is visible to the save callback.
        const trimmedName = pendingAttrName.trim();
        const trimmedValue = pendingAttrValue.trim();
        if (trimmedName && trimmedValue) {
            const currentAttrs =
                (useEventsEditingStore.getState().overlay?.attributes as Record<string, unknown>) ??
                (source.attributes as Record<string, unknown>) ??
                {};
            setField(["attributes"], { ...currentAttrs, [trimmedName]: trimmedValue });
            setPendingAttrName("");
            setPendingAttrValue("");
        }

        if (isTargetLayerHidden && !isDismissed(HIDDEN_LAYER_DISMISS_ID)) {
            setConfirmOpen(true);
            return;
        }
        doSave();
    };

    return (
        <>
            <ConfirmDialog
                open={confirmOpen}
                onClose={() => setConfirmOpen(false)}
                onConfirm={() => {
                    setConfirmOpen(false);
                    doSave();
                }}
                title={t("events.hiddenLayerConfirmTitle")}
                message={t("events.hiddenLayerConfirmMessage", {
                    layerName:
                        (layers ?? []).find((l) => l.layerId === selectedLayerId)?.layerName ??
                        selectedLayerId,
                })}
                confirmLabel={t("events.hiddenLayerConfirmAction")}
                isDestructive={false}
                dismissible
                onDismiss={() => dismiss(HIDDEN_LAYER_DISMISS_ID)}
            />
            <Flex direction="column" w="full" h="full">
                <VStack gap="5" w="full" flex="1" overflowY="auto" px="1" pb="4">
                    {/* Display Name */}
                    <Box w="full">
                        <Text fontWeight="bold" mb="2" color="fg.muted">
                            {t("events.displayName")}
                        </Text>
                        <Input
                            color="bg.contrast"
                            placeholder={details.displayName}
                            defaultValue={details.displayName}
                            onChange={(e) => {
                                markTouched("displayName");
                                setField(["displayName"], e.target.value);
                            }}
                            borderColor={visibleErrors.displayName ? "red.400" : undefined}
                            disabled={isSaving}
                        />
                        {visibleErrors.displayName && (
                            <Text color="red.400" fontSize="xs" mt="1">
                                {visibleErrors.displayName}
                            </Text>
                        )}
                    </Box>

                    {/* Broadcast Layer */}
                    <Box w="full">
                        <Text fontWeight="bold" mb="2" color="fg.muted">
                            {t("events.broadcastLayer" as never, "Broadcast to Layer")}
                        </Text>
                        <LayerComboBox
                            // Remount on lock-state change: the internal input text does not
                            // re-sync when the value is reset programmatically (unchecking the
                            // move box), which left a locked field rendering blank.
                            key={isLayerFieldEditable ? "unlocked" : "locked"}
                            storageKey="events:recent-layers"
                            value={source.source === "local" ? "" : source.source}
                            onValueChange={(v) => {
                                markTouched("source");
                                setField(["source"], v);
                            }}
                            disabled={isSaving || !isLayerFieldEditable}
                            renderItem={(item) => (
                                <Combobox.ItemText>
                                    {item.label}
                                    {disabledSources.has(item.value) && (
                                        <Text as="span" fontSize="2xs" color="fg.warning" ml="1">
                                            ({t("common.hidden")})
                                        </Text>
                                    )}
                                </Combobox.ItemText>
                            )}
                        />
                        {visibleErrors.source ? (
                            <Text color="red.400" fontSize="xs" mt="1">
                                {visibleErrors.source}
                            </Text>
                        ) : isTargetLayerHidden ? (
                            <HStack gap="1" mt="1" align="start">
                                <Icon color="fg.warning" mt="0.5">
                                    <LuTriangleAlert size={12} />
                                </Icon>
                                <Text color="fg.warning" fontSize="xs">
                                    {t("events.hiddenLayerWarning")}
                                </Text>
                            </HStack>
                        ) : layerHint ? (
                            <Text fontSize="xs" opacity={0.5} mt="1">
                                {layerHint}
                            </Text>
                        ) : null}
                        {canMove && (
                            <>
                                <Checkbox.Root
                                    mt="2"
                                    checked={move}
                                    disabled={isSaving}
                                    onCheckedChange={(v) => {
                                        const checked = !!v.checked;
                                        setMove(checked);
                                        // Unchecking re-locks the field, so drop any
                                        // target the user picked while it was open —
                                        // a leftover override would otherwise ride
                                        // along into the patch as a bogus source.
                                        if (!checked) {
                                            deleteField(["source"]);
                                            unmarkTouched(["source"]);
                                        }
                                    }}
                                >
                                    <Checkbox.HiddenInput />
                                    <Checkbox.Control />
                                    <Checkbox.Label>
                                        {t(
                                            "events.moveToLayer" as never,
                                            "Remove and re-broadcast to destination",
                                        )}
                                    </Checkbox.Label>
                                </Checkbox.Root>
                                <Text fontSize="xs" opacity={0.5} mt="1">
                                    {t(
                                        "events.layerMoveHint" as never,
                                        "The event is recreated on the chosen layer — remarks, attachments, and history do not transfer",
                                    )}
                                </Text>
                            </>
                        )}
                    </Box>

                    {pickedElement && (
                        <GeometryEditForms element={pickedElement} editability={editability} />
                    )}

                    {/* Start Time */}
                    <Box w="full">
                        <Text fontWeight="bold" mb="2" color="fg.muted">
                            {t("events.startTime")}
                        </Text>
                        <OcDateTimePicker
                            disabled={isSaving}
                            min={DateTime.local(2020, 1, 1)}
                            max={DateTime.local(2100, 1, 1)}
                            startTime={startStartTime}
                            onChange={(date) => setField(["timestampz", "start"], date.toMillis())}
                            timezone={timezone}
                        />
                    </Box>

                    {/* Stale Time */}
                    <Box w="full">
                        <Text fontWeight="bold" mb="2" color="fg.muted">
                            {t("events.staleTime")}
                        </Text>
                        <Box mb="2">
                            <OcDateTimePicker
                                key={never ? "disabled" : "active"}
                                min={staleMin}
                                max={DateTime.local(2100, 1, 1)}
                                startTime={stalePickerStartTime}
                                disabled={never}
                                onChange={(date) => {
                                    setField(["timestampz", "stale"], date.toMillis());
                                }}
                                timezone={timezone}
                            />
                        </Box>
                        <Checkbox.Root
                            checked={never}
                            onCheckedChange={(v) => {
                                if (v.checked) {
                                    setField(["timestampz", "stale"], neverStale);
                                } else {
                                    setField(["timestampz", "stale"], defaultStale());
                                }
                                setNever(v.checked as boolean);
                            }}
                        >
                            <Checkbox.HiddenInput />
                            <Checkbox.Control />
                            <Checkbox.Label>{t("events.neverGoesStale")}</Checkbox.Label>
                        </Checkbox.Root>
                    </Box>
                    {/* Attributes */}
                    <AttributesEditor
                        attributes={(source.attributes as Record<string, unknown>) ?? {}}
                        attributeErrors={validationErrors}
                        pendingName={pendingAttrName}
                        pendingValue={pendingAttrValue}
                        onPendingNameChange={setPendingAttrName}
                        onPendingValueChange={setPendingAttrValue}
                        pendingNameError={visibleErrors.pendingAttrName}
                        pendingValueError={visibleErrors.pendingAttrValue}
                        onMarkTouched={markTouched}
                    />
                </VStack>

                {/* Sticky Save/Cancel */}
                <HStack w="full" pt="4" flexShrink={0}>
                    <Button
                        onClick={() => cancel()}
                        variant="outline"
                        colorPalette="red"
                        size="sm"
                        flex="1"
                    >
                        {t("common.cancel")}
                    </Button>
                    <Button
                        onClick={handleSave}
                        variant="outline"
                        colorPalette="green"
                        size="sm"
                        flex="1"
                        loading={isSaving}
                        disabled={!isFormValid}
                    >
                        {t("common.save")}
                    </Button>
                </HStack>
            </Flex>
        </>
    );
}
