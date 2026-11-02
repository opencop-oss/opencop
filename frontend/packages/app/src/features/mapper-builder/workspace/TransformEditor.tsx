// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Box,
    Button,
    Flex,
    IconButton,
    Input,
    NativeSelectField,
    NativeSelectRoot,
    Text,
} from "@chakra-ui/react";
import type { TargetField, TransformOperation, TransformStep } from "@opencop/api";
import { Tooltip } from "@opencop/ui";
import {
    DndContext,
    closestCenter,
    KeyboardSensor,
    PointerSensor,
    useSensor,
    useSensors,
} from "@dnd-kit/core";
import type { DragEndEvent } from "@dnd-kit/core";
import {
    arrayMove,
    SortableContext,
    sortableKeyboardCoordinates,
    useSortable,
    verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { type ChangeEvent, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Duration } from "luxon";
import { LuGripVertical, LuPlus, LuX } from "react-icons/lu";
import { v4 as uuid } from "uuid";

const ALL_OPERATIONS: TransformOperation[] = [
    "TRIM",
    "LOWER",
    "UPPER",
    "REPLACE_REGEX",
    "TO_INT",
    "TO_DOUBLE",
    "PARSE_DATE_TIME",
    "DEFAULT_NOW",
    "ADD_DURATION",
    "DEFAULT_IF_BLANK",
    "MAP_VALUES",
    "MGRS_TO_POINT",
    "GEOMETRY",
    "KPH_TO_MPS",
    "MPH_TO_MPS",
    "KNOTS_TO_MPS",
    "FPS_TO_MPS",
    "RAD_TO_DEGREES",
    "GRAD_TO_DEGREES",
    "MILS_TO_DEGREES",
];

/** Speed conversion transforms — only available for ELEMENT_SPEED */
const SPEED_OPS: Set<TransformOperation> = new Set([
    "KPH_TO_MPS",
    "MPH_TO_MPS",
    "KNOTS_TO_MPS",
    "FPS_TO_MPS",
]);

/** Angle conversion transforms — only available for ELEMENT_COURSE and ELEMENT_HEADING */
const ANGLE_OPS: Set<TransformOperation> = new Set([
    "RAD_TO_DEGREES",
    "GRAD_TO_DEGREES",
    "MILS_TO_DEGREES",
]);

/** Returns the operations available for a given target field */
function getAvailableOperations(targetField?: TargetField): TransformOperation[] {
    return ALL_OPERATIONS.filter((op) => {
        if (SPEED_OPS.has(op)) return targetField === "ELEMENT_SPEED";
        if (ANGLE_OPS.has(op))
            return targetField === "ELEMENT_COURSE" || targetField === "ELEMENT_HEADING";
        return true;
    });
}

/** i18n keys for operations that benefit from a brief description */
const DESCRIBED_OPS: Partial<Record<TransformOperation, string>> = {
    DEFAULT_NOW: "mapperBuilder.transformDescription.DEFAULT_NOW",
    ADD_DURATION: "mapperBuilder.transformDescription.ADD_DURATION",
    GEOMETRY: "mapperBuilder.transformDescription.GEOMETRY",
};

/** Operations that require no user-provided parameters */
const ZERO_CONFIG_OPS: Set<TransformOperation> = new Set([
    "TRIM",
    "LOWER",
    "UPPER",
    "TO_INT",
    "TO_DOUBLE",
    "DEFAULT_NOW",
    "MGRS_TO_POINT",
    "GEOMETRY",
    "KPH_TO_MPS",
    "MPH_TO_MPS",
    "KNOTS_TO_MPS",
    "FPS_TO_MPS",
    "RAD_TO_DEGREES",
    "GRAD_TO_DEGREES",
    "MILS_TO_DEGREES",
]);

function buildDefaultStep(op: TransformOperation): TransformStep {
    switch (op) {
        case "REPLACE_REGEX":
            return { op, pattern: "", replacement: "" };
        case "PARSE_DATE_TIME":
            return { op, format: "", value: "" };
        case "ADD_DURATION":
            return { op, value: "" };
        case "DEFAULT_IF_BLANK":
            return { op, value: "" };
        case "MAP_VALUES":
            return { op, mapValues: {}, mapDefault: "" };
        default:
            return { op };
    }
}

type TransformItem = { _id: string; step: TransformStep };

interface TransformEditorProps {
    transforms: TransformStep[];
    onChange: (transforms: TransformStep[]) => void;
    targetField?: TargetField;
}

export function TransformEditor({ transforms, onChange, targetField }: TransformEditorProps) {
    const { t } = useTranslation();

    const [items, setItems] = useState<TransformItem[]>(() =>
        transforms.map((step) => ({ _id: uuid(), step })),
    );

    const sensors = useSensors(
        useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
        useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
    );

    const itemIds = useMemo(() => items.map((item) => item._id), [items]);
    const availableOps = useMemo(() => getAvailableOperations(targetField), [targetField]);

    function commit(next: TransformItem[]) {
        setItems(next);
        onChange(next.map((item) => item.step));
    }

    function handleDragEnd(event: DragEndEvent) {
        const { active, over } = event;
        if (!over || active.id === over.id) return;

        const oldIndex = items.findIndex((item) => item._id === active.id);
        const newIndex = items.findIndex((item) => item._id === over.id);
        commit(arrayMove(items, oldIndex, newIndex));
    }

    function handleAdd(e: ChangeEvent<HTMLSelectElement>) {
        const op = e.target.value as TransformOperation;
        if (!op) return;
        commit([...items, { _id: uuid(), step: buildDefaultStep(op) }]);
    }

    function handleRemove(index: number) {
        commit(items.filter((_, i) => i !== index));
    }

    function handleUpdate(index: number, step: TransformStep) {
        commit(items.map((s, i) => (i === index ? { _id: s._id, step } : s)));
    }

    return (
        <Box mt="2">
            <Flex align="center" justify="space-between" mb="1">
                <Text fontSize="xs" fontWeight="medium">
                    {t("mapperBuilder.transforms")}
                </Text>
                <Flex align="center" position="relative" color="active">
                    <LuPlus
                        size={12}
                        style={{ position: "absolute", left: "6px", pointerEvents: "none" }}
                    />
                    <NativeSelectRoot size="xs" w="auto" minW="120px" variant="plain">
                        <NativeSelectField
                            value=""
                            onChange={handleAdd}
                            aria-label={t("mapperBuilder.addTransform")}
                            pl="6"
                            cursor="pointer"
                            color="active"
                        >
                            <option value="" disabled>
                                {t("mapperBuilder.addTransform")}
                            </option>
                            {availableOps.map((op) => (
                                <option key={op} value={op}>
                                    {t(`mapperBuilder.transform.${op}` as never)}
                                </option>
                            ))}
                        </NativeSelectField>
                    </NativeSelectRoot>
                </Flex>
            </Flex>

            {items.length > 0 && (
                <DndContext
                    sensors={sensors}
                    collisionDetection={closestCenter}
                    onDragEnd={handleDragEnd}
                >
                    <SortableContext items={itemIds} strategy={verticalListSortingStrategy}>
                        {items.map((item, i) => (
                            <SortableTransformStep
                                key={item._id}
                                id={item._id}
                                step={item.step}
                                index={i}
                                onUpdate={handleUpdate}
                                onRemove={handleRemove}
                            />
                        ))}
                    </SortableContext>
                </DndContext>
            )}
        </Box>
    );
}

// --- Sortable step row --------------------------------------------------

interface SortableTransformStepProps {
    id: string;
    step: TransformStep;
    index: number;
    onUpdate: (index: number, step: TransformStep) => void;
    onRemove: (index: number) => void;
}

function SortableTransformStep({
    id,
    step,
    index,
    onUpdate,
    onRemove,
}: SortableTransformStepProps) {
    const { t } = useTranslation();
    const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
        id,
    });

    const style = {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.5 : 1,
    };

    return (
        <Box
            ref={setNodeRef}
            style={style}
            borderWidth="1px"
            borderRadius="sm"
            px="2"
            py="1.5"
            mb="1"
            bg="bg.subtle"
        >
            <Flex align="center" gap="1">
                <Box
                    {...attributes}
                    {...listeners}
                    aria-label={t("mapperBuilder.reorderTransform")}
                    cursor={isDragging ? "grabbing" : "grab"}
                    color="fg.subtle"
                    flexShrink={0}
                >
                    <LuGripVertical size={14} />
                </Box>

                <Text fontSize="xs" fontWeight="medium" flexShrink={0}>
                    {t(`mapperBuilder.transform.${step.op}` as never)}
                </Text>

                <Box flex="1" />

                <Tooltip content={t("mapperBuilder.removeTransform")}>
                    <IconButton
                        aria-label={t("mapperBuilder.removeTransform")}
                        size="2xs"
                        variant="ghost"
                        onClick={() => onRemove(index)}
                    >
                        <LuX />
                    </IconButton>
                </Tooltip>
            </Flex>

            {/* Brief description for operations that have one */}
            {DESCRIBED_OPS[step.op] && (
                <Text fontSize="2xs" color="fg.subtle" pl="5" mt="0.5">
                    {t(DESCRIBED_OPS[step.op] as never)}
                </Text>
            )}

            {/* Per-operation parameter form */}
            {!ZERO_CONFIG_OPS.has(step.op) && (
                <Box mt="1.5" pl="5">
                    <TransformStepForm step={step} index={index} onUpdate={onUpdate} />
                </Box>
            )}
        </Box>
    );
}

// --- Per-operation forms -------------------------------------------------

interface TransformStepFormProps {
    step: TransformStep;
    index: number;
    onUpdate: (index: number, step: TransformStep) => void;
}

function TransformStepForm({ step, index, onUpdate }: TransformStepFormProps) {
    const { t } = useTranslation();

    switch (step.op) {
        case "REPLACE_REGEX":
            return (
                <Flex gap="2" flexWrap="wrap">
                    <Box flex="1" minW="100px">
                        <Text fontSize="2xs" color="fg.subtle" mb="0.5">
                            {t("mapperBuilder.transformPattern")}
                        </Text>
                        <Input
                            size="xs"
                            aria-label={t("mapperBuilder.transformPattern")}
                            value={step.pattern ?? ""}
                            onChange={(e) => onUpdate(index, { ...step, pattern: e.target.value })}
                            placeholder={t("mapperBuilder.transformPatternPlaceholder")}
                        />
                    </Box>
                    <Box flex="1" minW="100px">
                        <Text fontSize="2xs" color="fg.subtle" mb="0.5">
                            {t("mapperBuilder.transformReplacement")}
                        </Text>
                        <Input
                            size="xs"
                            aria-label={t("mapperBuilder.transformReplacement")}
                            value={step.replacement ?? ""}
                            onChange={(e) =>
                                onUpdate(index, { ...step, replacement: e.target.value })
                            }
                        />
                    </Box>
                </Flex>
            );

        case "PARSE_DATE_TIME":
            return (
                <Flex gap="2" flexWrap="wrap">
                    <Box flex="1" minW="100px">
                        <Text fontSize="2xs" color="fg.subtle" mb="0.5">
                            {t("mapperBuilder.transformFormat")}
                        </Text>
                        <Input
                            size="xs"
                            aria-label={t("mapperBuilder.transformFormat")}
                            value={step.format ?? ""}
                            onChange={(e) => onUpdate(index, { ...step, format: e.target.value })}
                            placeholder={t("mapperBuilder.transformFormatPlaceholder")}
                        />
                    </Box>
                    <Box flex="1" minW="100px">
                        <Text fontSize="2xs" color="fg.subtle" mb="0.5">
                            {t("mapperBuilder.transformTimezone")}
                        </Text>
                        <Input
                            size="xs"
                            aria-label={t("mapperBuilder.transformTimezone")}
                            value={step.value ?? ""}
                            onChange={(e) => onUpdate(index, { ...step, value: e.target.value })}
                            placeholder={t("mapperBuilder.transformTimezonePlaceholder")}
                        />
                    </Box>
                </Flex>
            );

        case "ADD_DURATION":
            return <AddDurationForm step={step} index={index} onUpdate={onUpdate} />;

        case "DEFAULT_IF_BLANK":
            return (
                <Box>
                    <Text fontSize="2xs" color="fg.subtle" mb="0.5">
                        {t("mapperBuilder.defaultValue")}
                    </Text>
                    <Input
                        size="xs"
                        aria-label={t("mapperBuilder.defaultValue")}
                        value={step.value ?? ""}
                        onChange={(e) => onUpdate(index, { ...step, value: e.target.value })}
                    />
                </Box>
            );

        case "MAP_VALUES":
            return <MapValuesForm step={step} index={index} onUpdate={onUpdate} />;

        default:
            return null;
    }
}

// --- ADD_DURATION with validation ----------------------------------------

function isValidIsoDuration(value: string): boolean {
    if (!value) return true; // empty is not an error (yet) — just not filled in
    return Duration.fromISO(value).isValid;
}

function AddDurationForm({ step, index, onUpdate }: TransformStepFormProps) {
    const { t } = useTranslation();
    const [touched, setTouched] = useState(false);
    const value = step.value ?? "";
    const showError = touched && !isValidIsoDuration(value);

    return (
        <Box>
            <Text fontSize="2xs" color="fg.subtle" mb="0.5">
                {t("mapperBuilder.transformDuration")}
            </Text>
            <Input
                size="xs"
                aria-label={t("mapperBuilder.transformDuration")}
                value={value}
                onChange={(e) => onUpdate(index, { ...step, value: e.target.value })}
                onBlur={() => setTouched(true)}
                placeholder={t("mapperBuilder.transformDurationPlaceholder")}
                borderColor={showError ? "border.error" : undefined}
            />
            {showError && (
                <Text fontSize="2xs" color="fg.error" mt="0.5">
                    {t("mapperBuilder.validation.invalidDuration")}
                </Text>
            )}
        </Box>
    );
}

// --- MAP_VALUES key-value table ------------------------------------------

function MapValuesForm({ step, index, onUpdate }: TransformStepFormProps) {
    const { t } = useTranslation();
    const [entries, setEntries] = useState<Array<{ id: string; key: string; value: string }>>(() =>
        Object.entries(step.mapValues ?? {}).map(([key, value]) => ({
            id: uuid(),
            key,
            value,
        })),
    );

    function commitEntries(next: Array<{ id: string; key: string; value: string }>) {
        setEntries(next);
        const mapValues: Record<string, string> = {};
        for (const entry of next) {
            mapValues[entry.key] = entry.value;
        }
        onUpdate(index, { ...step, mapValues });
    }

    function handleKeyChange(id: string, newKey: string) {
        commitEntries(entries.map((e) => (e.id === id ? { ...e, key: newKey } : e)));
    }

    function handleValueChange(id: string, newValue: string) {
        commitEntries(entries.map((e) => (e.id === id ? { ...e, value: newValue } : e)));
    }

    function handleRemoveEntry(id: string) {
        commitEntries(entries.filter((e) => e.id !== id));
    }

    function handleAddEntry() {
        commitEntries([...entries, { id: uuid(), key: "", value: "" }]);
    }

    return (
        <Box>
            {entries.map(({ id, key, value }) => (
                <Flex key={id} gap="1" mb="1" align="center">
                    <Input
                        size="xs"
                        flex="1"
                        placeholder={t("mapperBuilder.transformMapKey")}
                        value={key}
                        onChange={(e) => handleKeyChange(id, e.target.value)}
                    />
                    <Text fontSize="xs" color="fg.subtle">
                        →
                    </Text>
                    <Input
                        size="xs"
                        flex="1"
                        placeholder={t("mapperBuilder.transformMapValue")}
                        value={value}
                        onChange={(e) => handleValueChange(id, e.target.value)}
                    />
                    <Tooltip content={t("mapperBuilder.removeEntry")}>
                        <IconButton
                            aria-label={t("mapperBuilder.removeEntry")}
                            size="2xs"
                            variant="ghost"
                            onClick={() => handleRemoveEntry(id)}
                        >
                            <LuX />
                        </IconButton>
                    </Tooltip>
                </Flex>
            ))}
            <Flex gap="2" align="center" mt="1">
                <Button size="xs" variant="ghost" onClick={handleAddEntry}>
                    <LuPlus />
                    {t("mapperBuilder.transformAddMapEntry")}
                </Button>
            </Flex>
            <Box mt="1">
                <Text fontSize="2xs" color="fg.subtle" mb="0.5">
                    {t("mapperBuilder.transformMapDefault")}
                </Text>
                <Input
                    size="xs"
                    value={step.mapDefault ?? ""}
                    onChange={(e) => onUpdate(index, { ...step, mapDefault: e.target.value })}
                    placeholder={t("mapperBuilder.transformMapDefaultPlaceholder")}
                />
            </Box>
        </Box>
    );
}
