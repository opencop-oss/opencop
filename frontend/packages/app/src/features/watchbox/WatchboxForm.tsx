// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Button,
    Combobox,
    Field,
    HStack,
    Input,
    Text,
    Textarea,
    VisuallyHidden,
    VStack,
} from "@chakra-ui/react";
import { zodResolver } from "@hookform/resolvers/zod";
import type { AccessScope } from "@opencop/api";
import { ConfirmDialog, OcCombobox, OcSelect, OcSwitch } from "@opencop/ui";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { difference, isEqual, sortBy } from "lodash";
import { useEffect, useMemo, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useSpinDelay } from "spin-delay";
import { layerQueries } from "~/features/layers/layers.queries";
import { ScopeIndicator } from "~/features/layers/ScopeIndicator";
import { useToast } from "~/features/notifications/use-toast.hook";
import { WatchboxSection } from "./WatchboxSection";
import { WatchboxTitle } from "./WatchboxTitle";
import {
    defaultPredicateDraft,
    draftFromFilter,
    FilterRulesBuilder,
    isInertUnderAll,
    parseFilter,
    useFilterFields,
} from "@opencop/ui";
import { isOffered } from "~/features/filters/panel-exclusion.util";
import { usePanelExcludedIds } from "~/features/filters/use-panel-excluded-ids.hook";
import {
    watchboxEditSchema,
    watchboxSchema,
    type Watchbox,
    type WatchboxFormValues,
    type WatchboxPolygon,
    type WatchboxResetReason,
} from "./watchbox.model";
import { watchboxMutations } from "./watchbox.queries";
import { watchboxErrorMessage, watchboxPolygonErrorMessageKey } from "./watchbox-error.util";
import { parseWatchboxError } from "./watchbox.api";
import type { CreateWatchboxRequest, PatchWatchboxRequest } from "./watchbox.api";

/** Create takes a drawn polygon; edit takes a saved watchbox. One or the other. */
export type WatchboxFormProps = (
    | { polygon: WatchboxPolygon; watchbox?: never }
    | { watchbox: Watchbox; polygon?: never }
) & {
    onSaved?: (watchbox: Watchbox) => void;
    onCancel?: () => void;
};

/** Spelled out rather than interpolated: a template-literal key is invisible to the extractor. */
const resetReasonKey = {
    POLYGON_CHANGED: "watchbox.resetReasons.POLYGON_CHANGED",
    LAYER_REMOVED: "watchbox.resetReasons.LAYER_REMOVED",
    REENABLED: "watchbox.resetReasons.REENABLED",
} as const satisfies Record<WatchboxResetReason, string>;

// Compares membership with multiplicity; layer order carries no meaning, but
// a length-plus-includes check would call ["1","1"] and ["1","2"] equal.
const sameLayers = (a: string[], b: string[]) => isEqual(sortBy(a), sortBy(b));

/** accessScope is optional: the layer-unavailable placeholder items don't carry it. */
type LayerPickerItem = { value: string; label: string; accessScope?: AccessScope };

export function WatchboxForm({ polygon, watchbox, onSaved, onCancel }: WatchboxFormProps) {
    const { t, i18n } = useTranslation();
    const { showToast } = useToast();
    const queryClient = useQueryClient();
    const createMutation = useMutation(watchboxMutations.create(queryClient));
    const patchMutation = useMutation(watchboxMutations.patch(queryClient));
    const isPending = createMutation.isPending || patchMutation.isPending;
    const layers = useQuery(layerQueries.myList());
    const excludedIds = usePanelExcludedIds();

    // The form seeds from the watchbox once, but the prop behind it keeps
    // polling. Everything the diff compares against has to come from this frozen
    // snapshot: measuring against the live value would re-send a field the user
    // never touched at its stale value, reverting whoever changed it meanwhile.
    const [seed] = useState(watchbox);

    // The area is drawn on the map, so an edit keeps the saved polygon.
    const activePolygon = seed?.polygon ?? (polygon as WatchboxPolygon);

    const [infoOpen, setInfoOpen] = useState(true);
    const [advancedOpen, setAdvancedOpen] = useState(true);
    const [pendingReset, setPendingReset] = useState<{
        request: PatchWatchboxRequest;
        reasons: string[];
    } | null>(null);
    const [saveErrorAnnouncement, setSaveErrorAnnouncement] = useState("");
    const { fields, fieldFor, defaultFieldId } = useFilterFields();
    // Resolved once, with the fields present when the editor opened: the rows it seeds and
    // the saved filter a submit is compared against must read the same, even if a field
    // registers while the editor is open.
    const [seedDraft] = useState(() =>
        seed ? draftFromFilter(seed.filter, fields, fieldFor) : null,
    );

    const { control, register, handleSubmit, setError, setValue, formState } =
        useForm<WatchboxFormValues>({
            // The polygon can't change in edit mode, so don't re-validate it.
            resolver: zodResolver(seed ? watchboxEditSchema : watchboxSchema),
            mode: "onBlur",
            reValidateMode: "onChange",
            defaultValues: {
                name: seed?.name ?? "",
                description: seed?.description ?? "",
                direction: seed?.direction ?? "BOTH",
                enabled: seed?.enabled ?? true,
                layerIds: seed?.layerIds ?? [],
                polygon: activePolygon,
                filter: seedDraft
                    ? seedDraft
                    : {
                          combinator: "ALL",
                          predicates: [
                              defaultPredicateDraft({ field: defaultFieldId, operator: "EQUALS" }),
                          ],
                      },
            },
        });
    // Live title: reflects the name field as the user types, defaulting back
    // to the same placeholder name if the field is cleared.
    const nameValue = useWatch({ control, name: "name" });
    const title = nameValue.trim() || t("watchbox.newWatchboxName");

    // Same rule the builder's own banner uses — a condition that needs Any matches
    // nothing under All, so this blocks Create/Save rather than letting it through.
    const filterDraft = useWatch({ control, name: "filter" });
    const isUnsavable = isInertUnderAll(filterDraft, fields);

    // The polygon is edited outside the form; carry its latest
    // result into form state so submit validation sees it.
    useEffect(() => {
        setValue("polygon", activePolygon, {
            shouldValidate: formState.isSubmitted,
        });
    }, [activePolygon, setValue, formState.isSubmitted]);

    const keptLayerIds = seed?.layerIds;
    // Require OWNER/ADMIN for every scope, including GLOBAL. Layers removed from
    // the map panel are offered only if this watchbox already watches them.
    const layerCandidates = useMemo(
        () =>
            (layers.data ?? []).filter(
                (layer) =>
                    (layer.role === "OWNER" || layer.role === "ADMIN") &&
                    isOffered(excludedIds, layer.layerId, keptLayerIds),
            ),
        [layers.data, excludedIds, keptLayerIds],
    );

    // A layer with no data source can never produce a matching object, so it
    // can never be watched — drop it from the picker instead of letting the
    // user pick it and hit WATCHBOX_NO_ELIGIBLE_SOURCES on submit.
    //
    // One sources query per layer the caller manages (now including GLOBAL,
    // uncapped); revisit if that set grows large.
    const dataSourceQueries = useQueries({
        queries: layerCandidates.map((layer) => layerQueries.dataSources(layer.layerId)),
    });
    const layersLoading = layers.isLoading || dataSourceQueries.some((q) => q.isLoading);
    const layersError = layers.isError || dataSourceQueries.some((q) => q.isError);
    // The layers query and the per-layer data-source fan-out routinely take
    // 500ms-2s (and longer on retries after a transient failure) — without
    // this, the combobox just sits disabled with static helper text for that
    // whole window, reading as a broken form rather than one that's checking
    // something. See docs/best-practices/data-fetching/loading-indicators.md.
    const showLayersLoading = useSpinDelay(layersLoading, {
        delay: 500,
        minDuration: 200,
        ssr: false,
    });

    // Not memoized: useQueries returns a new array each render, so a memo keyed
    // on it would never skip recomputation anyway. accessScope rides along so
    // the picker can flag a GLOBAL layer's shared-visibility implication.
    const layerItems: LayerPickerItem[] = layerCandidates
        .filter((_, i) => (dataSourceQueries[i]?.data?.length ?? 0) > 0)
        .map((layer) => ({
            value: layer.layerId,
            label: layer.layerName,
            accessScope: layer.accessScope,
        }));

    // A watchbox names layers by id and the lookup supplies the names. A saved
    // layer id with no matching candidate — the whole lookup failed, or the
    // caller specifically no longer manages that layer (role changed, or it
    // drifted to a state the picker excludes) — gets this placeholder instead
    // of silently dropping out, which would otherwise leave its chip
    // rendering the raw UUID rather than reading as a degraded lookup. While
    // the lookup is still in flight, neither of those applies yet — not
    // resolved is not the same as unavailable.
    const pickerItems: LayerPickerItem[] = seed
        ? [
              ...layerItems,
              ...difference(
                  seed.layerIds,
                  layerItems.map((item) => item.value),
              ).map((id) => ({
                  value: id,
                  label: layersLoading ? t("common.loading") : t("watchbox.layerUnavailable"),
              })),
          ]
        : layerItems;

    const directions = useMemo(
        () => [
            { label: t("watchbox.directionEnter"), value: "ENTER" },
            { label: t("watchbox.directionLeave"), value: "LEAVE" },
            { label: t("watchbox.directionBoth"), value: "BOTH" },
        ],
        [t],
    );

    const reportSaved = (saved: Watchbox) => {
        showToast({
            type: "success",
            title: seed ? t("watchbox.updated") : t("watchbox.created"),
        });
        onSaved?.(saved);
    };

    const reportFailure = (error: unknown) => {
        const title = seed ? t("watchbox.updateError") : t("watchbox.createError");
        const description = watchboxErrorMessage(error, t, i18n.resolvedLanguage);
        setSaveErrorAnnouncement(description || title);
        showToast({
            type: "error",
            title,
            description,
        });
    };

    /** Only a patch can be refused for a tracking reset, so only it holds one. */
    const savePatch = async (req: PatchWatchboxRequest) => {
        try {
            reportSaved(await patchMutation.mutateAsync(req));
        } catch (error) {
            // A patch that resets tracking is refused until the user has seen
            // why. Hold it so confirming retries the same request.
            const response = parseWatchboxError(error);
            if (response?.code === "WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED") {
                setPendingReset({ request: req, reasons: response.reasons });
                return;
            }
            reportFailure(error);
        }
    };

    const onSubmit = handleSubmit(
        async (values) => {
            // zod only validates each predicate row's own shape (it can't see the
            // live fields without rebuilding the schema whenever they change).
            // parseFilter re-checks field existence against the current fields as
            // the authoritative gate: it can still fail here if a custom field a
            // row references was removed via a different row's dropdown after
            // this row was filled in.
            const filter = parseFilter(values.filter, fields);
            if (!filter.ok) {
                Object.entries(filter.errors).forEach(([id, error]) => {
                    const index = values.filter.predicates.findIndex((p) => p.id === id);
                    if (index === -1) return;
                    const path = `filter.predicates.${index}` as const;
                    if (error.field)
                        setError(`${path}.field`, { type: "custom", message: error.field });
                    if (error.operator)
                        setError(`${path}.operator`, { type: "custom", message: error.operator });
                    if (typeof error.value === "string")
                        setError(`${path}.value`, { type: "custom", message: error.value });
                    else if (error.value) {
                        if (error.value.value)
                            setError(`${path}.value.value`, {
                                type: "custom",
                                message: error.value.value,
                            });
                        if (error.value.start)
                            setError(`${path}.value.start`, {
                                type: "custom",
                                message: error.value.start,
                            });
                        if (error.value.end)
                            setError(`${path}.value.end`, {
                                type: "custom",
                                message: error.value.end,
                            });
                    }
                });
                setAdvancedOpen(true);
                return;
            }

            // Branch here rather than picking a mutation at runtime: each keeps
            // its own lifecycle, and the request stays typed to the call.
            if (seed) {
                // Both sides go through the same parse before comparison. parseFilter
                // writes negate/caseInsensitive defaults the stored filter omits, so a
                // raw comparison reports a filter change on every save.
                const parsedSeed = parseFilter(
                    seedDraft ?? draftFromFilter(seed.filter, fields, fieldFor),
                    fields,
                );
                const savedFilter = parsedSeed.ok ? parsedSeed.result : seed.filter;
                // Absent fields are left unchanged, so only send what moved.
                // The polygon is never sent: the server compares any it
                // receives, and this form cannot change it.
                const req: PatchWatchboxRequest = {
                    id: seed.id,
                    ...(values.name !== seed.name && { name: values.name }),
                    ...(values.description !== (seed.description ?? "") && {
                        description: values.description || null,
                    }),
                    ...(values.direction !== seed.direction && {
                        direction: values.direction,
                    }),
                    ...(values.enabled !== seed.enabled && { enabled: values.enabled }),
                    ...(!sameLayers(values.layerIds, seed.layerIds) && {
                        layerIds: values.layerIds,
                    }),
                    ...(!isEqual(filter.result, savedFilter) && {
                        filter: filter.result,
                    }),
                };
                // `id` travels in the URL, so a request carrying nothing else is
                // an empty body: a round trip and an audit row for no change.
                if (Object.keys(req).length === 1) {
                    onSaved?.(seed);
                    return;
                }
                await savePatch(req);
                return;
            }

            const req: CreateWatchboxRequest = {
                name: values.name,
                description: values.description || null,
                polygon: values.polygon,
                direction: values.direction,
                layerIds: values.layerIds,
                filter: filter.result,
            };
            try {
                reportSaved(await createMutation.mutateAsync(req));
            } catch (error) {
                reportFailure(error);
            }
        },
        (errors) => {
            if (errors.name || errors.description || errors.direction || errors.layerIds) {
                setInfoOpen(true);
            }
            if (errors.filter?.predicates) {
                setAdvancedOpen(true);
            }
            if (errors.polygon) {
                // The area is drawn on the map, so it has no field to render an error under.
                showToast({
                    type: "error",
                    title: t("watchbox.createError"),
                    description: t(watchboxPolygonErrorMessageKey(errors.polygon.message)),
                });
            }
        },
    );

    const retryLayerEligibility = () => {
        if (layers.isError) void layers.refetch();
        dataSourceQueries.forEach((query) => {
            if (query.isError) void query.refetch();
        });
    };

    // Create-only, like the gate below. A failed lookup leaves nothing to
    // create against; an existing watchbox keeps its layers and stays editable,
    // with the picker reporting the failure in place.
    if (!watchbox && !layersLoading && layersError) {
        return (
            <VStack w="full" h="full" align="stretch" gap="4" py="4">
                <WatchboxTitle title={title} />
                <VStack w="full" flex="1" justify="center" align="center" gap="4" px="2">
                    <Text role="alert" color="fg.muted" fontSize="sm" textAlign="center">
                        {t("watchbox.layerEligibilityError")}
                    </Text>
                    <HStack>
                        <Button size="sm" colorPalette="brand" onClick={retryLayerEligibility}>
                            {t("common.retry")}
                        </Button>
                        {onCancel && (
                            <Button
                                size="sm"
                                variant="subtle"
                                colorPalette="secondary"
                                onClick={onCancel}
                            >
                                {t("common.cancel")}
                            </Button>
                        )}
                    </HStack>
                </VStack>
            </VStack>
        );
    }

    // Create-only: nothing eligible means nothing to create against. An
    // existing watchbox already holds its layers, so this must not block
    // editing them. It only means the picker has nothing new to offer.
    if (!watchbox && !layersLoading && layerItems.length === 0) {
        return (
            <VStack w="full" h="full" align="stretch" gap="4" py="4">
                <WatchboxTitle title={title} />
                <VStack w="full" flex="1" justify="center" align="center" gap="4" px="2">
                    <Text color="fg.muted" fontSize="sm" textAlign="center">
                        {t("watchbox.noEligibleLayers")}
                    </Text>
                    {onCancel && (
                        <Button
                            size="sm"
                            variant="subtle"
                            colorPalette="secondary"
                            onClick={onCancel}
                        >
                            {t("common.cancel")}
                        </Button>
                    )}
                </VStack>
            </VStack>
        );
    }

    return (
        <form
            noValidate
            onSubmit={(e) => {
                // Clearing first makes an identical failure on a retry a new live-region update.
                setSaveErrorAnnouncement("");
                void onSubmit(e);
            }}
            onKeyDown={(e) => {
                // A plain <input> (the filter builder's value field, the
                // field/operator comboboxes, ...) submits its nearest <form>
                // on Enter by default — surprising here, since it's easy to
                // finish typing one condition's value, hit Enter out of habit,
                // and submit the whole multi-section form early. Buttons keep
                // their native Enter-activates-click behavior, and the
                // description textarea keeps Enter as a newline.
                if (
                    e.key === "Enter" &&
                    !(e.target instanceof HTMLTextAreaElement) &&
                    !(e.target instanceof HTMLButtonElement)
                ) {
                    e.preventDefault();
                }
            }}
            style={{ width: "100%", height: "100%", display: "flex", flexDirection: "column" }}
        >
            {/* Stay mounted so a screen reader observes the failure text being populated. */}
            <VisuallyHidden role="alert" aria-atomic="true">
                {saveErrorAnnouncement}
            </VisuallyHidden>
            <VStack gap="4" align="stretch" w="full" h="full">
                <WatchboxTitle title={title} />
                <VStack w="full" h="full" overflowY="auto" align="stretch" gap="2" px="2">
                    <WatchboxSection
                        title={t("watchbox.infoTitle")}
                        open={infoOpen}
                        onOpenChange={setInfoOpen}
                    >
                        <VStack align="stretch" gap="4" pt="2">
                            <Field.Root invalid={!!formState.errors.name} required>
                                <Field.Label>
                                    {t("watchbox.name")}
                                    <Field.RequiredIndicator />
                                </Field.Label>
                                <Input
                                    {...register("name")}
                                    maxLength={64}
                                    placeholder={t("watchbox.newWatchboxName")}
                                    // Entering edit replaces the read-only body,
                                    // so focus needs somewhere to land. Create
                                    // opens with the map still in play, and
                                    // stealing focus there would be wrong.
                                    autoFocus={!!seed}
                                />
                                <Field.ErrorText>{t("watchbox.nameError")}</Field.ErrorText>
                            </Field.Root>

                            <Field.Root invalid={!!formState.errors.description}>
                                <Field.Label>{t("watchbox.description")}</Field.Label>
                                <Textarea
                                    {...register("description")}
                                    maxLength={256}
                                    placeholder={t("watchbox.descriptionPlaceholder")}
                                />
                                <Field.ErrorText>{t("watchbox.descriptionError")}</Field.ErrorText>
                            </Field.Root>

                            <Field.Root invalid={!!formState.errors.direction} required>
                                <Field.Label>
                                    {t("watchbox.direction")}
                                    <Field.RequiredIndicator />
                                </Field.Label>
                                <Controller
                                    name="direction"
                                    control={control}
                                    render={({ field }) => (
                                        <OcSelect
                                            items={directions}
                                            value={field.value}
                                            onValueChange={field.onChange}
                                            triggerProps={{ onBlur: field.onBlur }}
                                        />
                                    )}
                                />
                            </Field.Root>

                            <Field.Root
                                invalid={!!formState.errors.layerIds || layersError}
                                required
                            >
                                <Field.Label>
                                    {t("watchbox.layers")}
                                    <Field.RequiredIndicator />
                                </Field.Label>
                                <Controller
                                    name="layerIds"
                                    control={control}
                                    render={({ field }) => (
                                        <OcCombobox
                                            multiple
                                            // Selections already show as removable
                                            // tags, so the list offers only what
                                            // can still be added.
                                            hideSelectedOptions
                                            emptyMessage={t("watchbox.allLayersSelected")}
                                            disabled={layersLoading}
                                            items={pickerItems}
                                            value={field.value}
                                            onValueChange={(value) =>
                                                field.onChange(
                                                    Array.isArray(value) ? value : [value],
                                                )
                                            }
                                            onBlur={field.onBlur}
                                            placeholder={t("watchbox.layersPlaceholder")}
                                            renderItem={(item) => (
                                                <HStack justify="space-between" w="full" gap="2">
                                                    <Combobox.ItemText>
                                                        {item.label}
                                                    </Combobox.ItemText>
                                                    {/* Options never take focus (aria-activedescendant), so only the hidden label names the scope. */}
                                                    {item.accessScope && (
                                                        <ScopeIndicator
                                                            scope={item.accessScope}
                                                            iconOnly
                                                        />
                                                    )}
                                                </HStack>
                                            )}
                                        />
                                    )}
                                />
                                <Field.HelperText
                                    color={layersError ? "type.error" : undefined}
                                    // An edit reaches here with the lookup failed.
                                    // Say so; an empty picker reads as "no layers".
                                    role={layersError ? "alert" : undefined}
                                >
                                    {layersError
                                        ? t("watchbox.layerEligibilityError")
                                        : showLayersLoading
                                          ? t("watchbox.checkingLayerEligibility")
                                          : t("watchbox.layersHelper")}
                                </Field.HelperText>
                                <Field.ErrorText>
                                    {formState.errors.layerIds?.type === "too_big"
                                        ? t("watchbox.errors.WATCHBOX_LAYER_LIMIT_EXCEEDED")
                                        : t("watchbox.layersError")}
                                </Field.ErrorText>
                            </Field.Root>

                            {watchbox && (
                                <Controller
                                    name="enabled"
                                    control={control}
                                    render={({ field }) => (
                                        <OcSwitch
                                            checked={field.value}
                                            label={t("watchbox.enabled")}
                                            onCheckedChange={field.onChange}
                                            fullWidth={false}
                                            rootProps={{ size: "sm" }}
                                        />
                                    )}
                                />
                            )}
                        </VStack>
                    </WatchboxSection>

                    <WatchboxSection
                        title={t("filter.rulesTitle")}
                        open={advancedOpen}
                        onOpenChange={setAdvancedOpen}
                    >
                        <FilterRulesBuilder control={control} />
                    </WatchboxSection>
                </VStack>

                <HStack w="full" pt="4" px="2" flexShrink={0}>
                    {onCancel && (
                        <Button
                            type="button"
                            variant="subtle"
                            colorPalette="secondary"
                            size="sm"
                            flex="1"
                            disabled={isPending}
                            onClick={onCancel}
                        >
                            {t("common.cancel")}
                        </Button>
                    )}
                    <Button
                        type="submit"
                        colorPalette="brand"
                        size="sm"
                        flex="1"
                        disabled={isUnsavable}
                        loading={isPending}
                        loadingText={t("common.saving")}
                    >
                        {watchbox ? t("common.save") : t("watchbox.create")}
                    </Button>
                </HStack>
            </VStack>

            <ConfirmDialog
                open={!!pendingReset}
                isDestructive={false}
                title={t("watchbox.confirmResetTitle")}
                message={
                    <VStack align="stretch" gap="2">
                        <Text>{t("watchbox.confirmResetIntro")}</Text>
                        <VStack as="ul" align="stretch" gap="1" pl="4">
                            {(pendingReset?.reasons ?? []).map((reason) => (
                                <Text as="li" key={reason} listStyleType="disc">
                                    {reason in resetReasonKey
                                        ? t(resetReasonKey[reason as keyof typeof resetReasonKey])
                                        : reason}
                                </Text>
                            ))}
                        </VStack>
                    </VStack>
                }
                confirmLabel={t("common.save")}
                loading={isPending}
                onClose={() => setPendingReset(null)}
                onConfirm={() => {
                    if (!pendingReset) return;
                    const { request } = pendingReset;
                    setSaveErrorAnnouncement("");
                    void (async () => {
                        try {
                            reportSaved(
                                await patchMutation.mutateAsync({
                                    ...request,
                                    confirmTrackingReset: true,
                                }),
                            );
                        } catch (error) {
                            reportFailure(error);
                        } finally {
                            setPendingReset(null);
                        }
                    })();
                }}
            />
        </form>
    );
}
