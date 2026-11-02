// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Splitter } from "@ark-ui/react/splitter";
import { Badge, Box, Button, Flex, HStack, Input, Link, Spinner, Text } from "@chakra-ui/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Breadcrumbs, ConfirmDialog, Divider, Tooltip, type BreadcrumbItem } from "@opencop/ui";
import type { MapperProfileFullMetaRequest, MapperProfilePatch } from "@opencop/api";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { FaSave } from "react-icons/fa";
import { Link as RouterLink, useNavigate, useParams, useSearchParams } from "react-router";
import { v4 as uuid } from "uuid";
import { ErrorBoundaryWrapper } from "~/features/errors/ErrorBoundaryWrapper";
import { ModuleWrapper } from "~/features/sidebar/ModuleWrapper";
import { profileQueries } from "~/features/data-sources/data-source-profiles.queries";
import { ENTITY_ADMIN_PATH } from "~/features/data-sources/data-resources.util";
import { useToast } from "~/features/notifications/use-toast.hook";
import { CenterPanel } from "./CenterPanel";
import { mapperBuilderMutations } from "./mapper-builder.queries";
import { mapperMetadataSchema, type MapperMetadataFormValues } from "./mapper-builder.schemas";
import {
    MapperBuilderStoreProvider,
    useMapperBuilderActions,
    useMapperBuilderStore,
} from "./mapper-builder.store";
import {
    buildDynamicMapper,
    decodeGzipBase64,
    fileToGzipBase64,
    getExprFromSource,
} from "./mapper-builder.util";
import { getValueCountsForExpr } from "./mapper-builder.automap";
import { useMapperPreview } from "./use-mapper-preview.hook";
import { SourcePanel } from "./SourcePanel";
import { LivePreview } from "./workspace/LivePreview";

export function MapperBuilderPage() {
    return (
        <ModuleWrapper fixedHeight>
            <ErrorBoundaryWrapper componentName="MapperBuilderPage">
                <MapperBuilderStoreProvider>
                    <MapperBuilderContent />
                </MapperBuilderStoreProvider>
            </ErrorBoundaryWrapper>
        </ModuleWrapper>
    );
}

function MapperBuilderContent() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const { showToast } = useToast();
    const { id } = useParams<{ id?: string }>();
    const [searchParams] = useSearchParams();
    const duplicateSourceId = searchParams.get("from") ?? undefined;

    const isEditMode = !!id;
    const isDuplicateMode = !!duplicateSourceId;
    const sourceId = id ?? duplicateSourceId;

    const { data: sourceMeta, isPending: isSourceMetaPending } = useQuery({
        ...profileQueries.mapperProfileAdminDetail(sourceId ?? ""),
        enabled: !!sourceId,
    });

    const actions = useMapperBuilderActions();
    const lastHydratedIdRef = useRef<string | null>(null);

    const sampleFile = useMapperBuilderStore((s) => s.sampleFile);
    const rules = useMapperBuilderStore((s) => s.rules);
    const formatConfig = useMapperBuilderStore((s) => s.formatConfig);
    const recordSelector = useMapperBuilderStore((s) => s.recordSelector);
    const detectedFormat = useMapperBuilderStore((s) => s.detectedFormat);
    const parsedTree = useMapperBuilderStore((s) => s.parsedTree);
    const recordNodeId = useMapperBuilderStore((s) => s.recordNodeId);
    const objectIdRule = useMapperBuilderStore(
        (s) => s.rules.find((r) => r.target === "OBJECT_ID") ?? null,
    );
    const hasRules = useMapperBuilderStore((s) => s.rules.length > 0);
    const isStoreDirty = useMapperBuilderStore((s) => s.isDirty);
    const isSampleFileChanged = useMapperBuilderStore((s) => s.isSampleFileChanged);

    const createMutation = useMutation(mapperBuilderMutations.create(queryClient));
    const updateMutation = useMutation(mapperBuilderMutations.update(queryClient));
    const isSavePending = isEditMode ? updateMutation.isPending : createMutation.isPending;
    const isSaveError = isEditMode ? updateMutation.isError : createMutation.isError;

    const {
        register,
        handleSubmit,
        reset: resetForm,
        formState: { isDirty: isFormDirty, isValid: isFormValid },
    } = useForm<MapperMetadataFormValues>({
        resolver: zodResolver(mapperMetadataSchema),
        defaultValues: { name: "", description: "" },
        mode: "onChange",
    });

    useEffect(() => {
        if (!sourceMeta) return;
        if (lastHydratedIdRef.current === sourceMeta.profile.id) return;

        const isUnsupportedFormat =
            sourceMeta.profile.dynamicMapper?.formatConfig.type === "kml+kmz";
        if (!sourceMeta.profile.dynamicMapper || isUnsupportedFormat) {
            showToast({
                type: "error",
                title: t(
                    isDuplicateMode
                        ? "adminActions.duplicateMapperNotSupported"
                        : "adminActions.editMapperNotSupported",
                    { name: sourceMeta.profile.name },
                ),
                description: t("adminActions.editMapperNotSupportedDescription"),
            });
            navigate(ENTITY_ADMIN_PATH.mapper, { replace: true });
            return;
        }

        resetForm({
            name: isDuplicateMode
                ? t("mapperBuilder.duplicateName", { name: sourceMeta.profile.name })
                : sourceMeta.profile.name,
            description: sourceMeta.profile.description ?? "",
        });

        const { payload, filename } = sourceMeta;
        if (!payload) {
            showToast({
                type: "error",
                title: t(
                    isDuplicateMode
                        ? "adminActions.duplicateMapperNotSupported"
                        : "adminActions.editMapperNotSupported",
                    { name: sourceMeta.profile.name },
                ),
                description: t(
                    isDuplicateMode
                        ? "adminActions.duplicateMapperPayloadMissing"
                        : "adminActions.editMapperPayloadMissing",
                ),
            });
            navigate(ENTITY_ADMIN_PATH.mapper, { replace: true });
            return;
        }

        const decoded = decodeGzipBase64(payload);
        const syntheticFile = new File([decoded], filename ?? "sample");

        actions.loadForEdit(
            syntheticFile,
            decoded,
            sourceMeta.profile.dynamicMapper.formatConfig.type,
            sourceMeta.profile.dynamicMapper.formatConfig,
            sourceMeta.profile.dynamicMapper.recordSelector,
            sourceMeta.profile.dynamicMapper.rules,
        );
        // recordNodeId is the UI tree-path; reconstruct from recordSelector.source.expr once parsedTree is ready.

        lastHydratedIdRef.current = sourceMeta.profile.id;
        // eslint-disable-next-line react-hooks/exhaustive-deps -- actions is structurally stable (defined once in createStore)
    }, [sourceMeta, isDuplicateMode, resetForm, showToast, navigate, t]);

    const { previewResponse, previewError } = useMapperPreview();

    // Client-side OBJECT_ID warnings for the Live Preview panel
    const clientWarnings = useMemo(() => {
        const warnings: { code: string; message: string; ruleIds?: string[] }[] = [];
        if (!hasRules) return warnings;

        if (!objectIdRule) {
            warnings.push({
                code: "OBJECT_ID_NOT_MAPPED",
                message: t("mapperBuilder.validation.objectIdNotMapped"),
            });
        } else if (recordNodeId) {
            const expr = getExprFromSource(objectIdRule.source);
            if (expr) {
                const counts = getValueCountsForExpr(parsedTree, recordNodeId, expr);
                if (counts.total > 1 && counts.unique < counts.total) {
                    warnings.push({
                        code: "OBJECT_ID_NOT_UNIQUE",
                        message: t("mapperBuilder.validation.objectIdNotUnique", {
                            total: counts.total,
                            unique: counts.unique,
                        }),
                        ruleIds: objectIdRule.id ? [objectIdRule.id] : undefined,
                    });
                }
            }
        }
        return warnings;
    }, [hasRules, objectIdRule, parsedTree, recordNodeId, t]);

    const hasUnsavedWork = isFormDirty || isStoreDirty;

    // Warn on unsaved changes (browser tab close / page unload)
    useEffect(() => {
        function handleBeforeUnload(e: BeforeUnloadEvent) {
            if (hasUnsavedWork) {
                e.preventDefault();
            }
        }
        window.addEventListener("beforeunload", handleBeforeUnload);
        return () => window.removeEventListener("beforeunload", handleBeforeUnload);
    }, [hasUnsavedWork]);

    // Save confirmation dialog state
    const [saveConfirmOpen, setSaveConfirmOpen] = useState(false);
    const [pendingFormData, setPendingFormData] = useState<MapperMetadataFormValues | null>(null);
    // Cancel confirmation dialog state
    const [cancelConfirmOpen, setCancelConfirmOpen] = useState(false);

    function handleSaveClick(formData: MapperMetadataFormValues) {
        setPendingFormData(formData);
        setSaveConfirmOpen(true);
    }

    async function handleSaveConfirm() {
        setSaveConfirmOpen(false);
        const formData = pendingFormData;
        setPendingFormData(null);
        if (!formData) return;

        const mapper = buildDynamicMapper({ formatConfig, recordSelector, rules });

        try {
            if (isEditMode) {
                const skipPayload = !isSampleFileChanged;
                const payloadBase64 = skipPayload ? undefined : await fileToGzipBase64(sampleFile!);
                const meta: MapperProfilePatch = {
                    profile: {
                        id: sourceMeta!.profile.id,
                        name: formData.name,
                        description: formData.description ?? "",
                        editable: true,
                        dynamicMapper: mapper,
                        version: sourceMeta!.profile.version,
                    },
                    ...(skipPayload ? {} : { filename: sampleFile!.name, payload: payloadBase64 }),
                };
                await updateMutation.mutateAsync(meta);
            } else {
                const meta: MapperProfileFullMetaRequest = {
                    profile: {
                        id: uuid(),
                        name: formData.name,
                        description: formData.description ?? "",
                        editable: true,
                        dynamicMapper: mapper,
                    },
                    filename: sampleFile!.name,
                    payload: await fileToGzipBase64(sampleFile!),
                };
                await createMutation.mutateAsync(meta);
            }
            navigate(ENTITY_ADMIN_PATH.mapper, { replace: true });
        } catch {
            // Error state is tracked by createMutation.isError / updateMutation.isError
        }
    }

    function handleCancel() {
        if (hasUnsavedWork) {
            setCancelConfirmOpen(true);
            return;
        }
        navigate(ENTITY_ADMIN_PATH.mapper, { replace: true });
    }

    function handleCancelConfirm() {
        setCancelConfirmOpen(false);
        navigate(ENTITY_ADMIN_PATH.mapper, { replace: true });
    }

    if ((isEditMode || isDuplicateMode) && isSourceMetaPending) {
        return (
            <Flex h="100%" align="center" justify="center">
                <Spinner size="lg" />
            </Flex>
        );
    }

    const formatLabel = detectedFormat?.toUpperCase() ?? null;
    const canSave =
        sampleFile !== null &&
        rules.length > 0 &&
        formatConfig !== null &&
        recordSelector !== null &&
        isFormValid &&
        previewResponse !== null &&
        previewResponse.errors.length === 0;

    function getSaveDisabledReason(): string | null {
        if (!isFormValid) return t("mapperBuilder.saveDisabled.invalidForm");
        if (rules.length === 0) return t("mapperBuilder.saveDisabled.noRules");
        if (formatConfig === null) return t("mapperBuilder.saveDisabled.noFormat");
        if (recordSelector === null) return t("mapperBuilder.saveDisabled.noRecord");
        if (previewResponse === null) return t("mapperBuilder.saveDisabled.previewPending");
        if (previewResponse.errors.length > 0) return t("mapperBuilder.saveDisabled.previewErrors");
        return null;
    }
    const saveDisabledReason = canSave ? null : getSaveDisabledReason();

    const panelSizes = [22, 56, 22];

    const breadcrumbs: BreadcrumbItem[] = [
        { label: t("adminPanel.title"), href: "/admin" },
        { label: t("adminNav.mappers"), href: ENTITY_ADMIN_PATH.mapper },
        { label: t("mapperBuilder.pageTitle") },
    ];

    function renderLink({ href, children }: { href: string; children: ReactNode }) {
        return (
            <Link
                asChild
                fontSize="sm"
                color="active"
                cursor="pointer"
                _hover={{ textDecoration: "underline" }}
                display="inline-flex"
                alignItems="center"
                gap={1}
            >
                <RouterLink to={href}>{children}</RouterLink>
            </Link>
        );
    }

    return (
        <>
            <Flex direction="column" h="100%" bg="slate.surface.secondary" minW="900px">
                {/* Title bar */}
                <HStack
                    px={6}
                    py={3}
                    background="slate.surface.primary"
                    borderBottomWidth="1px"
                    borderBottomColor="slate.border.primary"
                    flexShrink={0}
                >
                    <Text fontSize="xl" fontWeight="bold" color="fg" textTransform="uppercase">
                        {t("adminPanel.title")}
                    </Text>
                    <Divider orientation="vertical" borderWidth="1px" height="1.5em" />
                    <Text fontSize="sm" color="active" textTransform="uppercase">
                        {t("mapperBuilder.pageTitle")}
                    </Text>

                    {/* Mapper name & description — inline in title bar */}
                    <Box flex="1" />
                    <Input
                        size="sm"
                        maxW="200px"
                        aria-label={t("mapperBuilder.namePlaceholder")}
                        placeholder={t("mapperBuilder.namePlaceholder")}
                        {...register("name")}
                    />
                    <Input
                        size="sm"
                        maxW="200px"
                        aria-label={t("mapperBuilder.descriptionPlaceholder")}
                        placeholder={t("mapperBuilder.descriptionPlaceholder")}
                        {...register("description")}
                    />

                    <Button variant="subtle" size="sm" onClick={handleCancel}>
                        {t("common.cancel")}
                    </Button>
                    <Tooltip content={saveDisabledReason} disabled={canSave} uppercase={false}>
                        <Box>
                            <Button
                                colorPalette="brand"
                                size="sm"
                                disabled={!canSave}
                                loading={isSavePending}
                                onClick={() => void handleSubmit(handleSaveClick)()}
                            >
                                <FaSave />
                                {t("common.save")}
                            </Button>
                        </Box>
                    </Tooltip>
                </HStack>

                {/* Breadcrumbs */}
                <Breadcrumbs items={breadcrumbs} renderLink={renderLink} />

                {/* Panel workspace */}
                <Box flex="1" display="flex" overflow="hidden" px="2" pb="2" gap="2">
                    <Splitter.Root
                        panels={[
                            { id: "source", minSize: 15 },
                            { id: "mapping", minSize: 30 },
                            { id: "preview", minSize: 15 },
                        ]}
                        defaultSize={panelSizes}
                        style={{ flex: 1, display: "flex" }}
                    >
                        {/* Left: Source panel */}
                        <Splitter.Panel id="source" asChild>
                            <Flex direction="column" overflow="hidden">
                                <PanelHeading>
                                    {t("mapperBuilder.panelSource")}
                                    {formatLabel && (
                                        <Badge colorPalette="brand" size="xs">
                                            {formatLabel}
                                        </Badge>
                                    )}
                                </PanelHeading>
                                <Box flex="1" overflow="hidden">
                                    <SourcePanel />
                                </Box>
                            </Flex>
                        </Splitter.Panel>

                        <ResizeHandle id="source:mapping" />

                        {/* Center: Mapping configuration */}
                        <Splitter.Panel id="mapping" asChild>
                            <Flex direction="column" overflow="hidden">
                                <PanelHeading>{t("mapperBuilder.panelMapping")}</PanelHeading>
                                <Box flex="1" overflow="hidden">
                                    <CenterPanel saveError={isSaveError} />
                                </Box>
                            </Flex>
                        </Splitter.Panel>

                        <ResizeHandle id="mapping:preview" />

                        {/* Right: Live preview */}
                        <Splitter.Panel id="preview" asChild>
                            <Flex direction="column" overflow="hidden">
                                <PanelHeading>{t("mapperBuilder.panelPreview")}</PanelHeading>
                                <Box flex="1" overflow="hidden" overscrollBehavior="contain">
                                    <LivePreview
                                        response={previewResponse}
                                        hasFile={sampleFile !== null}
                                        error={previewError}
                                        clientWarnings={clientWarnings}
                                    />
                                </Box>
                            </Flex>
                        </Splitter.Panel>
                    </Splitter.Root>
                </Box>

                <ConfirmDialog
                    open={saveConfirmOpen}
                    onClose={() => setSaveConfirmOpen(false)}
                    onConfirm={handleSaveConfirm}
                    title={t("mapperBuilder.saveConfirmTitle")}
                    message={t("mapperBuilder.savePayloadConfirm")}
                    confirmLabel={t("common.save")}
                    isDestructive={false}
                />

                <ConfirmDialog
                    open={cancelConfirmOpen}
                    onClose={() => setCancelConfirmOpen(false)}
                    onConfirm={handleCancelConfirm}
                    title={t("mapperBuilder.discardConfirmTitle")}
                    message={t("mapperBuilder.discardConfirm")}
                    confirmLabel={t("common.discard")}
                    isDestructive
                />
            </Flex>
        </>
    );
}

function ResizeHandle({ id }: { id: `${string}:${string}` }) {
    const { t } = useTranslation();
    return (
        <Splitter.ResizeTrigger id={id} asChild>
            <Box
                aria-label={t("mapperBuilder.resizePanels")}
                w="4px"
                bg="transparent"
                borderRadius="full"
                cursor="col-resize"
                transition="backgrounds"
                border="none"
                p="0"
                _hover={{ bg: "border" }}
            />
        </Splitter.ResizeTrigger>
    );
}

function PanelHeading({ children }: { children: React.ReactNode }) {
    return (
        <Flex
            align="center"
            gap="2"
            fontSize="xs"
            fontWeight="semibold"
            textTransform="uppercase"
            color="fg"
            px="3"
            py="2"
            bg="bg.glass"
            borderWidth="1px"
            borderColor="slate.border.primary"
            borderRadius="md"
            flexShrink={0}
            letterSpacing="wider"
            mb="1.5"
        >
            {children}
        </Flex>
    );
}
