// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Badge,
    Field,
    Flex,
    IconButton,
    Input,
    Stack,
    Text,
    Textarea,
    Group,
    HStack,
} from "@chakra-ui/react";
import { Formik } from "formik";
import type { FormikHelpers } from "formik";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { FaEye, FaEyeSlash } from "react-icons/fa6";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import * as Yup from "yup";
import { FormDialog, OcSelect, OcTabSelect } from "@opencop/ui";
import type { SelectItem } from "@opencop/ui";
import { MAX_TILE_ATTRIBUTION_LENGTH, TileKind } from "@opencop/api";
import type { MapTileAccessScope } from "@opencop/api";
import type { CreateMapTile, TileConfig, TileType } from "@opencop/api";
import { tileMutations } from "~/features/imagery/imagery.queries";
import {
    accessScopeOptions,
    parseUrlToken,
    TILE_ACCESS_SCOPE_DESCRIPTIONS,
} from "~/features/imagery/imagery.util";
import { RoleInfoTooltip } from "~/features/layer-groups/RoleInfoTooltip";
import { buildMapTileConfig, getDefaultConfig } from "~/features/imagery/tile-config.util";
import { TileConfigFields } from "./TileConfigFields";

interface DialogAddTileProps {
    isOpen: boolean;
    onClose: () => void;
}

const TILE_TYPE_OPTIONS = [
    { label: "BASEMAP", value: "BASEMAP" },
    { label: "WEATHER", value: "WEATHER" },
];

interface FormValues {
    name: string;
    description: string;
    attribution: string;
    url: string;
    token: string;
    tileType: TileType;
    accessScope: MapTileAccessScope;
    config: TileConfig | null;
}

const INITIAL_VALUES: FormValues = {
    name: "",
    description: "",
    attribution: "",
    url: "",
    token: "",
    tileType: "BASEMAP",
    accessScope: "PRIVATE",
    config: null,
};

function createAddTileSchema(t: TFunction) {
    return Yup.object().shape({
        name: Yup.string().trim().required(t("adminImagery.validation.nameRequired")),
        description: Yup.string().trim().required(t("adminImagery.validation.descriptionRequired")),
        attribution: Yup.string()
            .trim()
            .max(
                MAX_TILE_ATTRIBUTION_LENGTH,
                t("adminImagery.validation.attributionTooLong", {
                    max: MAX_TILE_ATTRIBUTION_LENGTH,
                }),
            ),
        url: Yup.string().trim().required(t("adminImagery.validation.urlRequired")),
        config: Yup.mixed()
            .required(t("adminImagery.validation.kindRequired"))
            .test("not-null", t("adminImagery.validation.kindRequired"), (v) => v !== null),
    });
}

function getKindItems(t: TFunction, tileType: TileType): SelectItem[] {
    if (tileType === "WEATHER") {
        return [
            { label: t("adminImagery.kinds.raster"), value: "RASTER" },
            { label: t("adminImagery.kinds.wms"), value: "WMS", disabled: true },
            { label: t("adminImagery.kinds.wmts"), value: "WMTS" },
        ];
    }
    return [
        { label: t("adminImagery.kinds.vector"), value: "VECTOR" },
        { label: t("adminImagery.kinds.raster"), value: "RASTER" },
        { label: t("adminImagery.kinds.terrain_rgb"), value: "TERRAIN_MESH_TERRAIN_RGB" },
        { label: t("adminImagery.kinds.terrarium"), value: "TERRAIN_MESH_TERRARIUM" },
        { label: t("adminImagery.kinds.wms"), value: "WMS" },
        { label: t("adminImagery.kinds.wmts"), value: "WMTS" },
        { label: t("adminImagery.kinds.tile_3d"), value: "TILE_3D", disabled: true },
    ];
}

export function AddTileDialog({ isOpen, onClose }: DialogAddTileProps) {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const createTileMutation = useMutation(tileMutations.create(queryClient));
    const [showToken, setShowToken] = useState(false);

    const validationSchema = useMemo(() => createAddTileSchema(t), [t]);

    const handleSubmit = async (values: FormValues, helpers: FormikHelpers<FormValues>) => {
        if (!values.config) return;

        try {
            const createData: CreateMapTile = {
                name: values.name.trim(),
                url: values.url.trim(),
                description: values.description.trim(),
                accessScope: values.accessScope,
                config: buildMapTileConfig(values.tileType, values.config),
            };

            if (values.token.trim()) {
                createData.token = values.token.trim();
            }
            if (values.attribution.trim()) {
                createData.attribution = values.attribution.trim();
            }

            await createTileMutation.mutateAsync(createData);
            helpers.resetForm();
            onClose();
        } catch (err) {
            helpers.setStatus(
                err instanceof Error ? err.message : t("adminImagery.failedToCreate"),
            );
        } finally {
            helpers.setSubmitting(false);
        }
    };

    return (
        <Formik
            initialValues={INITIAL_VALUES}
            validationSchema={validationSchema}
            onSubmit={handleSubmit}
            validateOnBlur={true}
            validateOnChange={false}
        >
            {({
                values,
                errors,
                touched,
                isSubmitting,
                setFieldValue,
                setFieldTouched,
                handleBlur,
                handleChange,
                resetForm,
                submitForm,
                status,
            }) => {
                const kindItems = getKindItems(t, values.tileType);
                return (
                    <FormDialog
                        open={isOpen}
                        onClose={() => {
                            resetForm();
                            onClose();
                        }}
                        title={t("adminImagery.title")}
                        secondaryText={t("adminImagery.addTile")}
                        onSubmit={() => void submitForm()}
                        submitLabel={t("common.create")}
                        cancelLabel={t("common.cancel")}
                        isSubmitting={isSubmitting}
                        size="lg"
                    >
                        <Stack gap={4}>
                            <Field.Root required invalid={!!touched.name && !!errors.name}>
                                <Field.Label>
                                    {t("adminImagery.name")}
                                    <Text as="span" color="red.500" ml={1}>
                                        *
                                    </Text>
                                </Field.Label>
                                <Input
                                    name="name"
                                    value={values.name}
                                    onChange={handleChange}
                                    onBlur={handleBlur}
                                    placeholder={t("adminImagery.namePlaceholder")}
                                />
                                {touched.name && errors.name && (
                                    <Field.ErrorText>{errors.name}</Field.ErrorText>
                                )}
                            </Field.Root>

                            <Field.Root
                                required
                                invalid={!!touched.description && !!errors.description}
                            >
                                <Field.Label>
                                    {t("adminImagery.description")}
                                    <Text as="span" color="red.500" ml={1}>
                                        *
                                    </Text>
                                </Field.Label>
                                <Textarea
                                    name="description"
                                    value={values.description}
                                    onChange={handleChange}
                                    onBlur={handleBlur}
                                    rows={3}
                                    placeholder={t("adminImagery.descriptionPlaceholder")}
                                />
                                {touched.description && errors.description && (
                                    <Field.ErrorText>{errors.description}</Field.ErrorText>
                                )}
                            </Field.Root>

                            <Field.Root invalid={!!touched.attribution && !!errors.attribution}>
                                <Field.Label>{t("adminImagery.attribution")}</Field.Label>
                                <Textarea
                                    name="attribution"
                                    value={values.attribution}
                                    onChange={(e) => {
                                        handleChange(e);
                                        // Keep the field neutral while it is being corrected;
                                        // blur re-checks it.
                                        void setFieldTouched("attribution", false, false);
                                    }}
                                    onBlur={handleBlur}
                                    rows={2}
                                    placeholder={t("adminImagery.attributionPlaceholder")}
                                />
                                {touched.attribution && errors.attribution ? (
                                    <Field.ErrorText>{errors.attribution}</Field.ErrorText>
                                ) : (
                                    <Field.HelperText>
                                        {t("adminImagery.attributionHelp")}
                                    </Field.HelperText>
                                )}
                            </Field.Root>

                            <Field.Root required invalid={!!touched.url && !!errors.url}>
                                <Field.Label>
                                    {t("adminImagery.url")}
                                    <Text as="span" color="red.500" ml={1}>
                                        *
                                    </Text>
                                </Field.Label>
                                <Input
                                    name="url"
                                    value={values.url}
                                    onChange={(e) => {
                                        const { url, token } = parseUrlToken(e.target.value);
                                        void setFieldValue("url", url);
                                        if (token && !values.token) {
                                            void setFieldValue("token", token);
                                        }
                                    }}
                                    onBlur={handleBlur}
                                    placeholder={t("adminImagery.urlPlaceholder")}
                                />
                                {touched.url && errors.url && (
                                    <Field.ErrorText>{errors.url}</Field.ErrorText>
                                )}
                            </Field.Root>

                            <Field.Root>
                                <Field.Label>{t("adminImagery.token")}</Field.Label>
                                <Group attached w="full">
                                    <Input
                                        name="token"
                                        type={showToken ? "text" : "password"}
                                        value={values.token}
                                        onChange={handleChange}
                                        placeholder="••••••••"
                                        flex={1}
                                    />
                                    <IconButton
                                        aria-label={
                                            showToken
                                                ? t("imagery.hideToken")
                                                : t("imagery.showToken")
                                        }
                                        variant="outline"
                                        onClick={() => setShowToken(!showToken)}
                                        size="md"
                                        color="active"
                                        borderColor="active"
                                        boxShadow="active"
                                    >
                                        {showToken ? <FaEyeSlash /> : <FaEye />}
                                    </IconButton>
                                </Group>
                            </Field.Root>

                            <Field.Root>
                                <Field.Label>{t("adminImagery.tileType")}</Field.Label>
                                <OcTabSelect
                                    defaultValue={values.tileType}
                                    value={values.tileType}
                                    options={TILE_TYPE_OPTIONS}
                                    onChange={(value) => {
                                        void setFieldValue("tileType", value);
                                        void setFieldValue("config", null);
                                    }}
                                />
                            </Field.Root>

                            <Field.Root>
                                <Field.Label>
                                    <HStack gap={1}>
                                        {t("adminImagery.accessScope")}
                                        <RoleInfoTooltip roles={TILE_ACCESS_SCOPE_DESCRIPTIONS} />
                                    </HStack>
                                </Field.Label>
                                <OcTabSelect
                                    defaultValue="PRIVATE"
                                    value={values.accessScope}
                                    options={accessScopeOptions(t)}
                                    onChange={(value) => void setFieldValue("accessScope", value)}
                                />
                            </Field.Root>

                            <Field.Root required invalid={!!touched.config && !!errors.config}>
                                <Field.Label>
                                    {t("adminImagery.kind")}
                                    <Text as="span" color="red.500" ml={1}>
                                        *
                                    </Text>
                                </Field.Label>
                                <OcSelect
                                    items={kindItems}
                                    value={
                                        values.config?.kind === "TERRAIN_MESH"
                                            ? `TERRAIN_MESH_${(values.config as { kind: string; encoding: string }).encoding}`
                                            : (values.config?.kind ?? "")
                                    }
                                    onValueChange={(value) => {
                                        let config: TileConfig;
                                        if (value === "TERRAIN_MESH_TERRAIN_RGB") {
                                            config = {
                                                kind: "TERRAIN_MESH",
                                                encoding: "TERRAIN_RGB",
                                                meshMaxError: 4,
                                            };
                                        } else if (value === "TERRAIN_MESH_TERRARIUM") {
                                            config = {
                                                kind: "TERRAIN_MESH",
                                                encoding: "TERRARIUM",
                                                meshMaxError: 4,
                                            };
                                        } else {
                                            config = getDefaultConfig(value as TileKind);
                                        }
                                        void setFieldValue("config", config, true);
                                    }}
                                    placeholder={t("adminImagery.kindPlaceholder")}
                                    renderItem={(item) => (
                                        <Flex
                                            align="center"
                                            justify="space-between"
                                            w="full"
                                            gap={2}
                                        >
                                            <span>{item.label}</span>
                                            {item.disabled && (
                                                <Badge
                                                    size="sm"
                                                    colorPalette="gray"
                                                    variant="subtle"
                                                >
                                                    {t("admin.unavailable")}
                                                </Badge>
                                            )}
                                        </Flex>
                                    )}
                                />
                                {touched.config && errors.config && (
                                    <Field.ErrorText>{errors.config as string}</Field.ErrorText>
                                )}
                            </Field.Root>

                            {values.config && (
                                <TileConfigFields
                                    config={values.config}
                                    onConfigChange={(config) =>
                                        void setFieldValue("config", config)
                                    }
                                />
                            )}

                            {status && (
                                <Field.Root invalid>
                                    <Field.ErrorText>{status as string}</Field.ErrorText>
                                </Field.Root>
                            )}
                        </Stack>
                    </FormDialog>
                );
            }}
        </Formik>
    );
}
