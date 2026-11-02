// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Alert,
    Badge,
    Box,
    Button,
    Dialog,
    Field,
    Flex,
    Grid,
    HStack,
    IconButton,
    Input,
    Spinner,
    Stack,
    Steps,
    Text,
} from "@chakra-ui/react";
import { ApiError, getErrorMessage, isEmptyLayerMergeValidationError } from "@opencop/api";
import type { MergeValidateResponse } from "@opencop/api";
import { BaseDialog, DialogHeader, WizardDialogActions } from "@opencop/ui";
import {
    DndContext,
    closestCenter,
    PointerSensor,
    KeyboardSensor,
    useSensor,
    useSensors,
    type DragEndEvent,
} from "@dnd-kit/core";
import {
    SortableContext,
    arrayMove,
    sortableKeyboardCoordinates,
    verticalListSortingStrategy,
    useSortable,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { FaCheckCircle, FaTimesCircle } from "react-icons/fa";
import { LuArrowDown, LuArrowUp, LuGripVertical, LuX } from "react-icons/lu";
import { mergeMutations, mergeQueries } from "~/features/layers/merge.queries";
import { MERGE_MAX } from "~/features/layers/merge-selection.util";

// ── Types ──────────────────────────────────────────────────────────────────────

export interface MergeLayerItem {
    id: string;
    name: string;
}

interface MergeLayersDialogProps {
    isOpen: boolean;
    onClose: () => void;
    /** Pre-selected layers passed in from the table (ordered by selection). */
    layers: MergeLayerItem[];
    selectionLimitExceeded?: boolean;
}

type MergeStep = 0 | 1 | 2;
type MergePhase = "idle" | "pending";

// ── Sub-components ─────────────────────────────────────────────────────────────

function SortableLayerRow({
    layer,
    index,
    total,
    isLoading,
    onMoveUp,
    onMoveDown,
    onRemove,
}: {
    layer: MergeLayerItem;
    index: number;
    total: number;
    isLoading: boolean;
    onMoveUp: () => void;
    onMoveDown: () => void;
    onRemove: () => void;
}) {
    const { t } = useTranslation();
    const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
        id: layer.id,
        disabled: isLoading,
    });

    const style = {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.1 : 1,
    };

    return (
        <Flex
            ref={setNodeRef}
            style={style}
            align="center"
            gap={2}
            borderWidth="1px"
            borderRadius="md"
            bg="bg.subtle"
            overflow="hidden"
        >
            <Box
                {...attributes}
                {...listeners}
                cursor={isLoading ? "default" : isDragging ? "grabbing" : "grab"}
                color="fg.subtle"
                px={2}
                py={2}
                alignSelf="stretch"
                display="flex"
                alignItems="center"
                _hover={{ color: isLoading ? "fg.subtle" : "fg.muted" }}
                aria-label={t("mergeLayers.dragToReorder")}
            >
                <LuGripVertical size={14} />
            </Box>
            <Text flex={1} fontSize="sm" truncate py={2}>
                {layer.name}
            </Text>
            <HStack gap={0} pr={1}>
                <IconButton
                    aria-label={t("mergeLayers.moveUp")}
                    size="xs"
                    variant="ghost"
                    disabled={index === 0 || isLoading}
                    onClick={onMoveUp}
                >
                    <LuArrowUp />
                </IconButton>
                <IconButton
                    aria-label={t("mergeLayers.moveDown")}
                    size="xs"
                    variant="ghost"
                    disabled={index === total - 1 || isLoading}
                    onClick={onMoveDown}
                >
                    <LuArrowDown />
                </IconButton>
                <IconButton
                    aria-label={t("mergeLayers.removeLayer")}
                    size="xs"
                    variant="ghost"
                    colorPalette="red"
                    disabled={total <= 2 || isLoading}
                    onClick={onRemove}
                >
                    <LuX />
                </IconButton>
            </HStack>
        </Flex>
    );
}

function StepArrange({
    orderedLayers,
    onReorder,
    onMoveUp,
    onMoveDown,
    onRemove,
    isLoading,
    emptyLayerError,
}: {
    orderedLayers: MergeLayerItem[];
    onReorder: (layers: MergeLayerItem[]) => void;
    onMoveUp: (index: number) => void;
    onMoveDown: (index: number) => void;
    onRemove: (index: number) => void;
    isLoading: boolean;
    emptyLayerError?: string;
}) {
    const { t } = useTranslation();

    const layerIds = useMemo(() => orderedLayers.map((l) => l.id), [orderedLayers]);

    const sensors = useSensors(
        useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
        useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
    );

    function handleDragEnd(event: DragEndEvent) {
        const { active, over } = event;
        if (!over || active.id === over.id) return;
        const oldIndex = layerIds.indexOf(String(active.id));
        const newIndex = layerIds.indexOf(String(over.id));
        if (oldIndex !== -1 && newIndex !== -1) {
            onReorder(arrayMove(orderedLayers, oldIndex, newIndex));
        }
    }

    return (
        <Stack gap={5}>
            <Box>
                <HStack mb={2} justify="space-between">
                    <Text fontWeight="medium">{t("mergeLayers.stepArrange")}</Text>
                    <Text fontSize="xs" color="fg.muted">
                        {t("mergeLayers.priorityHint")}
                    </Text>
                </HStack>

                <DndContext
                    sensors={sensors}
                    collisionDetection={closestCenter}
                    onDragEnd={handleDragEnd}
                >
                    <SortableContext items={layerIds} strategy={verticalListSortingStrategy}>
                        <Stack gap={2}>
                            {orderedLayers.map((layer, index) => (
                                <SortableLayerRow
                                    key={layer.id}
                                    layer={layer}
                                    index={index}
                                    total={orderedLayers.length}
                                    isLoading={isLoading}
                                    onMoveUp={() => onMoveUp(index)}
                                    onMoveDown={() => onMoveDown(index)}
                                    onRemove={() => onRemove(index)}
                                />
                            ))}
                        </Stack>
                    </SortableContext>
                </DndContext>
            </Box>

            {emptyLayerError && (
                <Alert.Root status="error">
                    <Alert.Indicator />
                    <Alert.Content>
                        <Alert.Description fontSize="sm">{emptyLayerError}</Alert.Description>
                    </Alert.Content>
                </Alert.Root>
            )}
        </Stack>
    );
}

function StepValidation({
    orderedLayers,
    validateResult,
    onReorder,
}: {
    orderedLayers: MergeLayerItem[];
    validateResult: MergeValidateResponse;
    onReorder: () => void;
}) {
    const { t } = useTranslation();
    const nameById = useMemo(
        () => Object.fromEntries(orderedLayers.map((l) => [l.id, l.name])),
        [orderedLayers],
    );
    const rankById = useMemo(
        () => Object.fromEntries(orderedLayers.map((l, i) => [l.id, i + 1])),
        [orderedLayers],
    );

    const { totalCollisions, pairwiseCounts } = validateResult.collisionSummary;

    return (
        <Stack gap={5}>
            {totalCollisions === 0 ? (
                <Flex direction="column" align="center" gap={4} py={8}>
                    <Box color="green.500" fontSize="3xl">
                        <FaCheckCircle />
                    </Box>
                    <Text fontWeight="semibold">{t("mergeLayers.validateSuccessHeading")}</Text>
                    <Text fontSize="sm" color="fg.muted" textAlign="center">
                        {t("mergeLayers.noCollisions")}
                    </Text>
                </Flex>
            ) : (
                <Box>
                    <HStack mb={2} justify="space-between">
                        <HStack>
                            <Text fontWeight="medium">{t("mergeLayers.collisionsHeading")}</Text>
                            <Badge colorPalette="orange" variant="subtle">
                                {t("mergeLayers.collisionsTotal", { count: totalCollisions })}
                            </Badge>
                        </HStack>
                        <Button size="xs" variant="ghost" colorPalette="brand" onClick={onReorder}>
                            {t("mergeLayers.changePriority")}
                        </Button>
                    </HStack>

                    <Alert.Root status="info" mb={3} size="sm">
                        <Alert.Indicator />
                        <Alert.Content>
                            <Alert.Description fontSize="xs">
                                {t("mergeLayers.collisionPriorityExplainer")}
                            </Alert.Description>
                        </Alert.Content>
                    </Alert.Root>

                    <Stack gap={2}>
                        {pairwiseCounts.map((pair) => (
                            <Flex
                                key={`${pair.firstLayerId}-${pair.secondLayerId}`}
                                align="center"
                                gap={2}
                                px={3}
                                py={2}
                                borderWidth="1px"
                                borderRadius="md"
                                bg="bg.subtle"
                            >
                                <Badge
                                    colorPalette="green"
                                    variant="subtle"
                                    minW="20px"
                                    textAlign="center"
                                >
                                    {rankById[pair.firstLayerId]}
                                </Badge>
                                <Text fontSize="sm" fontWeight="medium" flex={1} truncate>
                                    {nameById[pair.firstLayerId]}
                                </Text>
                                <Text fontSize="xs" color="fg.muted" flexShrink={0}>
                                    {t("mergeLayers.winsOver")}
                                </Text>
                                <Badge
                                    colorPalette="red"
                                    variant="subtle"
                                    minW="20px"
                                    textAlign="center"
                                >
                                    {rankById[pair.secondLayerId]}
                                </Badge>
                                <Text fontSize="sm" flex={1} truncate color="fg.muted">
                                    {nameById[pair.secondLayerId]}
                                </Text>
                                <Badge colorPalette="orange" variant="subtle" flexShrink={0}>
                                    {t("mergeLayers.collisionCount", { count: pair.count })}
                                </Badge>
                            </Flex>
                        ))}
                    </Stack>
                </Box>
            )}
        </Stack>
    );
}

function StepMerge({
    orderedLayers,
    targetName,
    onTargetNameChange,
    nameError,
    validateResult,
    mergePhase,
    targetLayerId,
    onDone,
    onTryAgain,
}: {
    orderedLayers: MergeLayerItem[];
    targetName: string;
    onTargetNameChange: (v: string) => void;
    nameError?: string;
    validateResult: MergeValidateResponse;
    mergePhase: MergePhase;
    targetLayerId: string | null;
    onDone: () => void;
    onTryAgain: () => void;
}) {
    const { t } = useTranslation();
    const { totalCollisions } = validateResult.collisionSummary;

    const pollQuery = useQuery({
        ...mergeQueries.pollTarget(targetLayerId ?? ""),
        enabled: mergePhase === "pending" && !!targetLayerId,
    });

    const lifecycleState = pollQuery.data?.lifecycleState ?? pollQuery.data?.status;

    if (
        mergePhase === "pending" &&
        lifecycleState !== "ACTIVE" &&
        lifecycleState !== "MERGE_FAILED"
    ) {
        return (
            <Flex direction="column" align="center" gap={4} py={8}>
                <Spinner size="xl" colorPalette="brand" />
                <Text color="fg.muted">{t("mergeLayers.mergingMessage")}</Text>
            </Flex>
        );
    }

    if (lifecycleState === "ACTIVE") {
        return (
            <Flex direction="column" align="center" gap={4} py={8}>
                <Box color="green.500" fontSize="3xl">
                    <FaCheckCircle />
                </Box>
                <Text fontWeight="semibold">{t("mergeLayers.successHeading")}</Text>
                <Text fontSize="sm" color="fg.muted" textAlign="center">
                    {t("mergeLayers.successMessage", { name: targetName })}
                </Text>
                <Button colorPalette="brand" size="sm" onClick={onDone}>
                    {t("mergeLayers.done")}
                </Button>
            </Flex>
        );
    }

    if (lifecycleState === "MERGE_FAILED") {
        return (
            <Flex direction="column" align="center" gap={4} py={8}>
                <Box color="red.500" fontSize="3xl">
                    <FaTimesCircle />
                </Box>
                <Text fontWeight="semibold">{t("mergeLayers.failedHeading")}</Text>
                <Text fontSize="sm" color="fg.muted" textAlign="center">
                    {t("mergeLayers.failedMessage")}
                </Text>
                <HStack>
                    <Button variant="subtle" colorPalette="secondary" size="sm" onClick={onDone}>
                        {t("common.close")}
                    </Button>
                    <Button colorPalette="brand" size="sm" onClick={onTryAgain}>
                        {t("mergeLayers.tryAgain")}
                    </Button>
                </HStack>
            </Flex>
        );
    }

    return (
        <Stack gap={6}>
            <Stack gap={3}>
                <Field.Root invalid={!!nameError} required maxW={{ md: "420px" }}>
                    <Flex
                        direction={{ base: "column", md: "row" }}
                        align={{ base: "start", md: "center" }}
                        justify="space-between"
                        gap={3}
                        mb={2}
                    >
                        <Field.Label mb="0">
                            {t("mergeLayers.targetNameLabel")}
                            <Field.RequiredIndicator />
                        </Field.Label>
                        {totalCollisions > 0 && (
                            <Badge colorPalette="orange" variant="subtle">
                                {t("mergeLayers.collisionsTotal", { count: totalCollisions })}
                            </Badge>
                        )}
                    </Flex>
                    <Input
                        value={targetName}
                        onChange={(e) => onTargetNameChange(e.target.value)}
                        placeholder={t("mergeLayers.targetNamePlaceholder")}
                        disabled={mergePhase !== "idle"}
                    />
                    {nameError && <Field.ErrorText>{nameError}</Field.ErrorText>}
                </Field.Root>

                <Text fontSize="sm" color="fg.muted">
                    {t("mergeLayers.archiveWarning")}
                </Text>
            </Stack>

            <Stack gap={3}>
                <Text fontWeight="medium">{t("mergeLayers.sourceLayersHeading")}</Text>
                <Grid
                    templateColumns="72px minmax(0, 1fr) 120px"
                    gap={3}
                    px={3}
                    alignItems="center"
                >
                    <Text
                        fontSize="xs"
                        color="fg.muted"
                        fontWeight="medium"
                        textTransform="uppercase"
                    >
                        {t("mergeLayers.order")}
                    </Text>
                    <Text
                        fontSize="xs"
                        color="fg.muted"
                        fontWeight="medium"
                        textTransform="uppercase"
                    >
                        {t("layers.name")}
                    </Text>
                    <Text
                        fontSize="xs"
                        color="fg.muted"
                        fontWeight="medium"
                        textTransform="uppercase"
                        textAlign="right"
                    >
                        {t("mergeLayers.featureCount")}
                    </Text>
                </Grid>

                <Stack gap={2}>
                    {orderedLayers.map((layer, index) => (
                        <Flex
                            key={layer.id}
                            align="center"
                            gap={3}
                            px={3}
                            py={2.5}
                            borderWidth="1px"
                            borderRadius="md"
                            bg="bg.subtle"
                        >
                            <Text w="72px" fontSize="sm" fontWeight="medium" color="fg.muted">
                                {index + 1}
                            </Text>
                            <Text flex={1} fontSize="sm" fontWeight="medium" truncate>
                                {layer.name}
                            </Text>
                            <Text w="120px" textAlign="right" fontSize="sm" color="fg.muted">
                                {validateResult.activeFeatureCounts[layer.id] ?? "—"}
                            </Text>
                        </Flex>
                    ))}
                </Stack>
            </Stack>
        </Stack>
    );
}

// ── Main dialog ────────────────────────────────────────────────────────────────

export function MergeLayersDialog({
    isOpen,
    onClose,
    layers,
    selectionLimitExceeded = false,
}: MergeLayersDialogProps) {
    const { t } = useTranslation();
    const queryClient = useQueryClient();

    const validateMutation = useMutation(mergeMutations.validate());
    const commitMutation = useMutation(mergeMutations.merge(queryClient));

    const [step, setStep] = useState<MergeStep>(0);
    const [mergePhase, setMergePhase] = useState<MergePhase>("idle");
    const [orderedLayers, setOrderedLayers] = useState<MergeLayerItem[]>(layers);
    const [targetName, setTargetName] = useState("");
    const [nameError, setNameError] = useState<string | undefined>();
    const [apiError, setApiError] = useState<string | undefined>();
    const [validateResult, setValidateResult] = useState<MergeValidateResponse | null>(null);
    const [targetLayerId, setTargetLayerId] = useState<string | null>(null);
    const [emptyLayerError, setEmptyLayerError] = useState<string | undefined>();

    const clearEmptyState = () => {
        setEmptyLayerError(undefined);
    };

    const handleReorder = (reordered: MergeLayerItem[]) => {
        clearEmptyState();
        setOrderedLayers(reordered);
    };

    const handleMoveUp = (index: number) => {
        if (index === 0) return;
        clearEmptyState();
        setOrderedLayers((prev) => arrayMove(prev, index, index - 1));
    };

    const handleMoveDown = (index: number) => {
        clearEmptyState();
        setOrderedLayers((prev) =>
            index === prev.length - 1 ? prev : arrayMove(prev, index, index + 1),
        );
    };

    const handleRemove = (index: number) => {
        clearEmptyState();
        setOrderedLayers((prev) => prev.filter((_, i) => i !== index));
    };

    const handleTargetNameChange = (value: string) => {
        setTargetName(value);
        if (nameError) setNameError(undefined);
    };

    const handleNext = async () => {
        if (step === 0) {
            setNameError(undefined);
            setApiError(undefined);
            setEmptyLayerError(undefined);

            try {
                const result = await validateMutation.mutateAsync({
                    layerIds: orderedLayers.map((l) => l.id),
                });

                setValidateResult(result);
                setStep(1);
            } catch (err) {
                if (err instanceof ApiError && err.status === 403) {
                    setApiError(t("mergeLayers.validatePermissionError"));
                } else if (isEmptyLayerMergeValidationError(err)) {
                    setEmptyLayerError(t("mergeLayers.emptyLayersError"));
                } else {
                    setApiError(getErrorMessage(err, t("mergeLayers.validateError")));
                }
            }
        } else if (step === 1) {
            setNameError(undefined);
            setApiError(undefined);
            setStep(2);
        } else if (step === 2) {
            if (!targetName.trim()) {
                setNameError(t("mergeLayers.targetNameRequired"));
                return;
            }

            setNameError(undefined);
            setApiError(undefined);

            try {
                const result = await commitMutation.mutateAsync({
                    name: targetName.trim(),
                    layerIds: orderedLayers.map((l) => l.id),
                });
                setTargetLayerId(result.targetLayer.id);
                setMergePhase("pending");
            } catch (err) {
                if (err instanceof ApiError && err.status === 409) {
                    setNameError(t("mergeLayers.targetNameTaken"));
                } else {
                    setApiError(t("mergeLayers.mergeError"));
                }
            }
        }
    };

    const handleClose = () => {
        setStep(0);
        setMergePhase("idle");
        setTargetName("");
        setNameError(undefined);
        setApiError(undefined);
        setEmptyLayerError(undefined);
        setValidateResult(null);
        setTargetLayerId(null);
        setOrderedLayers(layers);
        onClose();
    };

    const handleTryAgain = () => {
        setValidateResult(null);
        setTargetLayerId(null);
        setMergePhase("idle");
        setApiError(undefined);
        setEmptyLayerError(undefined);
        setStep(0);
    };

    const stepTitles = [
        t("mergeLayers.stepArrange"),
        t("mergeLayers.stepValidate"),
        t("mergeLayers.stepMerge"),
    ];

    const isValidating = validateMutation.isPending;
    const isMerging = commitMutation.isPending;
    const isLastStep = step === 2;
    const hideActions = mergePhase !== "idle";

    return (
        <BaseDialog open={isOpen} onClose={handleClose} size="lg">
            <Steps.Root
                colorPalette="brand"
                step={step}
                count={3}
                display="flex"
                flexDirection="column"
                flex="1"
                minH="0"
            >
                <DialogHeader
                    title={t("mergeLayers.title")}
                    secondaryText={t("mergeLayers.selectedSummary", {
                        total: orderedLayers.length,
                    })}
                />

                <Dialog.Body>
                    {selectionLimitExceeded && (
                        <Alert.Root status="info" mb={5}>
                            <Alert.Indicator />
                            <Alert.Content>
                                <Alert.Description fontSize="sm">
                                    {t("mergeLayers.selectionLimitInfo", {
                                        limit: MERGE_MAX,
                                    })}
                                </Alert.Description>
                            </Alert.Content>
                        </Alert.Root>
                    )}

                    {apiError && (
                        <Alert.Root status="error" mb={5}>
                            <Alert.Indicator />
                            <Alert.Content>
                                <Alert.Description fontSize="sm">{apiError}</Alert.Description>
                            </Alert.Content>
                        </Alert.Root>
                    )}

                    <Steps.List mb={5}>
                        {stepTitles.map((title, index) => (
                            <Steps.Item key={title} index={index} title={title}>
                                <Steps.Trigger>
                                    <Steps.Indicator />
                                    <Steps.Title>{title}</Steps.Title>
                                </Steps.Trigger>
                                <Steps.Separator />
                            </Steps.Item>
                        ))}
                    </Steps.List>

                    <Box display={step === 0 ? "block" : "none"}>
                        <StepArrange
                            orderedLayers={orderedLayers}
                            onReorder={handleReorder}
                            onMoveUp={handleMoveUp}
                            onMoveDown={handleMoveDown}
                            onRemove={handleRemove}
                            isLoading={isValidating}
                            emptyLayerError={emptyLayerError}
                        />
                    </Box>

                    <Box display={step === 1 ? "block" : "none"}>
                        {validateResult && (
                            <StepValidation
                                orderedLayers={orderedLayers}
                                validateResult={validateResult}
                                onReorder={() => {
                                    setApiError(undefined);
                                    setStep(0);
                                }}
                            />
                        )}
                    </Box>

                    <Box display={step === 2 ? "block" : "none"}>
                        {validateResult && (
                            <StepMerge
                                orderedLayers={orderedLayers}
                                targetName={targetName}
                                onTargetNameChange={handleTargetNameChange}
                                nameError={nameError}
                                validateResult={validateResult}
                                mergePhase={mergePhase}
                                targetLayerId={targetLayerId}
                                onDone={handleClose}
                                onTryAgain={handleTryAgain}
                            />
                        )}
                    </Box>
                </Dialog.Body>

                {!hideActions && (
                    <WizardDialogActions
                        onClose={handleClose}
                        isFirstStep={step === 0}
                        isLastStep={isLastStep}
                        onNext={handleNext}
                        onSubmit={handleNext}
                        submitLabel={t("mergeLayers.merge")}
                        loading={isValidating || isMerging}
                    />
                )}
            </Steps.Root>
        </BaseDialog>
    );
}
