// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Center,
    HStack,
    Field,
    Flex,
    Group,
    IconButton,
    Input,
    Spinner,
    Stack,
    Textarea,
    Button,
} from "@chakra-ui/react";
import { useState, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { FaEye, FaEyeSlash } from "react-icons/fa6";
import { isEqual } from "lodash";
import { MAX_TILE_ATTRIBUTION_LENGTH } from "@opencop/api";
import type { MapTileAccessScope, TileConfig, UpdateMapTile } from "@opencop/api";
import { OcTabSelect } from "@opencop/ui";
import { DrawerEditActions } from "~/features/drawers/DrawerEditActions";
import { tileQueries, tileMutations } from "~/features/imagery/imagery.queries";
import { TileConfigFields } from "~/features/imagery/dialogs/TileConfigFields";
import {
    buildMapTileConfigPatch,
    getTileConfig,
    getTileType,
    getWeatherFeatures,
} from "~/features/imagery/tile-config.util";
import { useToast } from "~/features/notifications/use-toast.hook";
import {
    accessScopeOptions,
    TILE_ACCESS_SCOPE_DESCRIPTIONS,
} from "~/features/imagery/imagery.util";
import { RoleInfoTooltip } from "~/features/layer-groups/RoleInfoTooltip";

export interface FormDeltas {
    name?: string;
    description?: string;
    attribution?: string;
    token?: string;
    accessScope?: MapTileAccessScope;
    config?: TileConfig;
}

export interface FormTouched {
    name?: boolean;
    description?: boolean;
    attribution?: boolean;
}

interface TileDrawerContentProps {
    tileId: string;
    deltas: FormDeltas;
    touched: FormTouched;
    onDeltasChange: (deltas: FormDeltas) => void;
    onTouchedChange: (touched: FormTouched) => void;
    /** Called on successful save or cancel — returns the drawer to view mode. */
    onDone: () => void;
}

export function TileDrawerContent({
    tileId,
    deltas,
    touched,
    onDeltasChange,
    onTouchedChange,
    onDone,
}: TileDrawerContentProps) {
    const { t } = useTranslation();
    const { showToast } = useToast();
    const queryClient = useQueryClient();
    const { data: tile, isLoading } = useQuery(tileQueries.detail(tileId));
    const updateMutation = useMutation(tileMutations.update(queryClient));

    const [showToken, setShowToken] = useState(false);
    const submitError = updateMutation.error
        ? updateMutation.error.message || t("adminImagery.failedToUpdate")
        : null;
    const currentToken = tile?.token ?? "";
    const form = useMemo(
        () => ({
            name: deltas.name ?? tile?.name ?? "",
            description: deltas.description ?? tile?.description ?? "",
            attribution: deltas.attribution ?? tile?.attribution ?? "",
            url: tile?.url ?? "",
            token: deltas.token ?? "",
            accessScope: deltas.accessScope ?? tile?.accessScope ?? "PRIVATE",
            config: deltas.config ?? (tile ? getTileConfig(tile) : undefined),
        }),
        [tile, deltas],
    );

    const errors = useMemo(
        () => ({
            name: form.name.trim() === "" ? t("adminImagery.validation.nameRequired") : undefined,
            description:
                form.description.trim() === ""
                    ? t("adminImagery.validation.descriptionRequired")
                    : undefined,
            attribution:
                form.attribution.trim().length > MAX_TILE_ATTRIBUTION_LENGTH
                    ? t("adminImagery.validation.attributionTooLong", {
                          max: MAX_TILE_ATTRIBUTION_LENGTH,
                      })
                    : undefined,
        }),
        [form.name, form.description, form.attribution, t],
    );

    const isValid = !errors.name && !errors.description && !errors.attribution;

    const hasChanges = useMemo(() => {
        if (!tile) return false;
        return (
            form.name !== tile.name ||
            form.description !== tile.description ||
            form.attribution.trim() !== (tile.attribution ?? "") ||
            (deltas.token !== undefined && deltas.token.trim() !== currentToken) ||
            form.accessScope !== tile.accessScope ||
            (deltas.config !== undefined && !isEqual(form.config, getTileConfig(tile)))
        );
    }, [form, tile, deltas.config, deltas.token, currentToken]);

    const handleFieldChange =
        (field: keyof Omit<FormDeltas, "config">) =>
        (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
            onDeltasChange({ ...deltas, [field]: e.target.value });
        };

    const handleBlur = (field: keyof FormTouched) => () => {
        onTouchedChange({ ...touched, [field]: true });
    };

    const handleConfigChange = (config: TileConfig) => {
        onDeltasChange({ ...deltas, config });
    };

    const handleSave = async () => {
        if (!isValid) {
            onTouchedChange({ name: true, description: true, attribution: true });
            return;
        }
        if (!tile) return;
        try {
            const updateData: UpdateMapTile = {};
            if (form.name !== tile.name) updateData.name = form.name;
            if (form.description !== tile.description) updateData.description = form.description;
            // An empty string clears the credit: the API reads null as an absent field.
            const nextAttribution = form.attribution.trim();
            if (nextAttribution !== (tile.attribution ?? "")) {
                updateData.attribution = nextAttribution;
            }
            if (form.accessScope !== tile.accessScope) {
                updateData.accessScope = form.accessScope;
            }
            if (deltas.token !== undefined) {
                const nextToken = deltas.token.trim();
                if (nextToken !== currentToken) {
                    // An empty string clears the token: the API reads null as an absent field.
                    updateData.token = nextToken;
                }
            }
            const configChanged =
                form.config !== undefined && !isEqual(form.config, getTileConfig(tile));
            if (configChanged) {
                updateData.config = buildMapTileConfigPatch(
                    getTileType(tile),
                    form.config,
                    getWeatherFeatures(tile.config),
                );
            }
            if (Object.keys(updateData).length > 0) {
                await updateMutation.mutateAsync({ id: tileId, data: updateData });
            }
            showToast({ type: "success", title: t("adminImagery.updateSuccess") });
            onDone();
        } catch {
            // Error is surfaced via updateMutation.error (see submitError above).
        }
    };

    const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        void handleSave();
    };

    if (isLoading || !tile) {
        return (
            <Center py={8}>
                <Spinner size="lg" color="brand.500" />
            </Center>
        );
    }

    return (
        <form noValidate onSubmit={handleSubmit}>
            <Stack gap={4}>
                <Field.Root required invalid={!!touched.name && !!errors.name}>
                    <Field.Label>
                        {t("adminImagery.name")}
                        <Field.RequiredIndicator />
                    </Field.Label>
                    <Input
                        value={form.name}
                        onChange={handleFieldChange("name")}
                        onBlur={handleBlur("name")}
                    />
                    {touched.name && errors.name && (
                        <Field.ErrorText>{errors.name}</Field.ErrorText>
                    )}
                </Field.Root>

                <Field.Root required invalid={!!touched.description && !!errors.description}>
                    <Field.Label>
                        {t("adminImagery.description")}
                        <Field.RequiredIndicator />
                    </Field.Label>
                    <Textarea
                        value={form.description}
                        onChange={handleFieldChange("description")}
                        onBlur={handleBlur("description")}
                        rows={3}
                    />
                    {touched.description && errors.description && (
                        <Field.ErrorText>{errors.description}</Field.ErrorText>
                    )}
                </Field.Root>

                <Field.Root invalid={!!touched.attribution && !!errors.attribution}>
                    <Field.Label>{t("adminImagery.attribution")}</Field.Label>
                    <Textarea
                        value={form.attribution}
                        onChange={(e) => {
                            onDeltasChange({ ...deltas, attribution: e.target.value });
                            // Keep the field neutral while it is being corrected; blur re-checks it.
                            if (touched.attribution) {
                                onTouchedChange({ ...touched, attribution: false });
                            }
                        }}
                        onBlur={handleBlur("attribution")}
                        rows={2}
                        placeholder={t("adminImagery.attributionPlaceholder")}
                    />
                    {touched.attribution && errors.attribution ? (
                        <Field.ErrorText>{errors.attribution}</Field.ErrorText>
                    ) : (
                        <Field.HelperText>{t("adminImagery.attributionHelp")}</Field.HelperText>
                    )}
                </Field.Root>

                <Field.Root>
                    <Field.Label>{t("adminImagery.url")}</Field.Label>
                    <Input value={form.url} disabled opacity={0.6} cursor="not-allowed" />
                </Field.Root>

                <Field.Root>
                    <Field.Label>
                        <HStack gap={1}>
                            {t("adminImagery.accessScope")}
                            <RoleInfoTooltip roles={TILE_ACCESS_SCOPE_DESCRIPTIONS} />
                        </HStack>
                    </Field.Label>
                    <OcTabSelect
                        defaultValue={form.accessScope}
                        value={form.accessScope}
                        options={accessScopeOptions(t)}
                        onChange={(value) =>
                            onDeltasChange({
                                ...deltas,
                                accessScope: value as MapTileAccessScope,
                            })
                        }
                    />
                </Field.Root>

                <Field.Root>
                    <Field.Label>{t("adminImagery.token")}</Field.Label>
                    {tile.token && (
                        <Flex align="center" justify="space-between" gap={2}>
                            <Field.HelperText>
                                {t("adminImagery.tokenReplaceHint")}
                            </Field.HelperText>
                            <Button
                                type="button"
                                variant="ghost"
                                size="xs"
                                flexShrink={0}
                                onClick={() => onDeltasChange({ ...deltas, token: "" })}
                                disabled={deltas.token === ""}
                            >
                                {t("adminImagery.clearToken")}
                            </Button>
                        </Flex>
                    )}
                    <Group attached w="full">
                        <Input
                            type={showToken ? "text" : "password"}
                            value={form.token}
                            onChange={(e) =>
                                onDeltasChange({
                                    ...deltas,
                                    // Only Clear token requests deletion; erasing a replacement keeps it.
                                    token:
                                        e.target.value.trim() === "" ? undefined : e.target.value,
                                })
                            }
                            placeholder="••••••••"
                            flex={1}
                        />
                        <IconButton
                            type="button"
                            aria-label={showToken ? t("imagery.hideToken") : t("imagery.showToken")}
                            variant="outline"
                            onClick={() => setShowToken(!showToken)}
                            size="md"
                            color="active"
                            borderColor="active"
                        >
                            {showToken ? <FaEyeSlash /> : <FaEye />}
                        </IconButton>
                    </Group>
                </Field.Root>

                {form.config && (
                    <TileConfigFields config={form.config} onConfigChange={handleConfigChange} />
                )}

                {submitError && (
                    <Field.Root invalid>
                        <Field.ErrorText>{submitError}</Field.ErrorText>
                    </Field.Root>
                )}

                <DrawerEditActions
                    onCancel={onDone}
                    loading={updateMutation.isPending}
                    canSave={hasChanges}
                />
            </Stack>
        </form>
    );
}
