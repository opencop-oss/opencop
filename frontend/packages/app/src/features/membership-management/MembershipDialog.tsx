// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Alert, Box, Button, Flex, Grid, Input, Stack, Text } from "@chakra-ui/react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { FaExclamationCircle } from "react-icons/fa";
import { FormDialog, OcCombobox, OcSelect, Tooltip } from "@opencop/ui";
import { ConfirmDeleteDialog } from "./ConfirmDeleteDialog";
import { MemberRow } from "./MemberRow";

export type ItemRole = string;
export type GenericItem = {
    id: string;
    name?: string;
    label?: string;
    type?: ItemRole;
    typeColorPalette?: string;
    joinedAt?: string;
    kind?: "user" | "group";
    accessType?: "individual" | "group";
    adminEditHref?: string;
};

interface MembershipDialogProps<T extends GenericItem> {
    isOpen: boolean;
    onClose: () => void;
    title: string;
    target: string;
    targetText: string;
    targetTextCurrent: string;
    readOnly?: boolean;
    viewOnlyMessage?: string;
    items: T[];
    options: GenericSelectOption[];
    roleOptions?: GenericSelectOption[];
    onAdd: (selectedItems: GenericSelectOption[], selectedRole?: string) => Promise<void>;
    onRemove: (itemId: string) => Promise<void>;
    onGroupDeleteClick?: (item: T) => void;
}

export type GenericSelectOption<T = string> = { label: string; value: T };

type TypeFilter = "all" | "user" | "group";

function FilterLink({
    label,
    active,
    onClick,
}: {
    label: string;
    active: boolean;
    onClick: () => void;
}) {
    return (
        <Text
            as="button"
            fontSize="xs"
            fontWeight={active ? "bold" : "normal"}
            color={active ? "fg" : "fg.muted"}
            cursor="pointer"
            onClick={onClick}
            _hover={{ color: "fg" }}
        >
            {label}
        </Text>
    );
}

export function MembershipDialog<T extends GenericItem>({
    isOpen,
    onClose,
    title,
    target,
    targetText,
    targetTextCurrent,
    readOnly = false,
    viewOnlyMessage,
    items,
    options,
    roleOptions,
    onAdd,
    onRemove,
    onGroupDeleteClick,
}: MembershipDialogProps<T>) {
    const { t } = useTranslation();

    const [selectedValues, setSelectedValues] = useState<string[]>([]);
    const [selectedRole, setSelectedRole] = useState<string>("");
    const [search, setSearch] = useState("");
    const [page, setPage] = useState(1);
    const [typeFilter, setTypeFilter] = useState<TypeFilter>("all");
    const [error, setError] = useState<string | null>(null);
    const [itemToDelete, setItemToDelete] = useState<string | null>(null);
    const [isDeleteOpen, setIsDeleteOpen] = useState(false);

    const hasGroups = items.some((i) => i.kind === "group");

    interface ApiErrorBody {
        code?: string;
        message?: string;
        error?: { message?: string };
    }

    const comboboxItems = options.map((o) => ({ label: o.label, value: String(o.value) }));

    async function handleAdd() {
        if (!selectedValues.length) return;
        setError(null);
        try {
            const selectedItems = options.filter((o) => selectedValues.includes(String(o.value)));
            await onAdd(selectedItems, selectedRole || undefined);
            setSelectedValues([]);
            setSelectedRole("");
        } catch (err) {
            setError(getErrorMessage(err));
        }
    }

    function handleDeleteClick(id: string) {
        setItemToDelete(id);
        setIsDeleteOpen(true);
    }

    function handleFilterChange(filter: TypeFilter) {
        setTypeFilter(filter);
        setPage(1);
    }

    function handleRevokeClick(item: T) {
        if (item.kind === "group" && onGroupDeleteClick) {
            onGroupDeleteClick(item);
        } else {
            handleDeleteClick(item.id);
        }
    }

    async function handleDeleteConfirmed() {
        if (!itemToDelete) return;
        setError(null);
        try {
            await onRemove(itemToDelete);
            setIsDeleteOpen(false);
        } catch (err) {
            setError(getErrorMessage(err));
            setIsDeleteOpen(false);
        }
    }

    function getErrorMessage(err: unknown): string {
        if (typeof err === "string") return err;
        if (err instanceof Error) return err.message;
        if (err && typeof err === "object") {
            const e = err as ApiErrorBody;
            if (typeof e.message === "string") return e.message;
            if (typeof e.error?.message === "string") return e.error.message;
        }
        return t("adminDialogs.somethingWentWrong");
    }

    const PAGE_SIZE = 5;

    const filteredItems = items.filter((item) => {
        const kind = item.kind ?? "user";
        const matchesType = typeFilter === "all" || kind === typeFilter;
        const matchesSearch = item.label?.toLowerCase().includes(search.toLowerCase()) ?? true;
        return matchesType && matchesSearch;
    });

    const totalPages = Math.ceil(filteredItems.length / PAGE_SIZE);
    const paginatedItems = filteredItems.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

    const showRoleHint = !!roleOptions && selectedValues.length > 0 && !selectedRole;
    const buttonTooltip = !selectedValues.length
        ? t("adminDialogs.selectUsersFirst")
        : roleOptions && !selectedRole
          ? t("adminDialogs.selectRoleFirst")
          : null;

    return (
        <>
            <FormDialog
                open={isOpen}
                onClose={onClose}
                title={title}
                secondaryText={target}
                size="2xl"
                contentWidth="660px"
                primaryClose
            >
                <Stack>
                    {readOnly && (
                        <Alert.Root status="info" size="sm">
                            <Alert.Indicator />
                            <Alert.Content>
                                <Alert.Description>
                                    {viewOnlyMessage ?? t("adminDialogs.membersViewOnly")}
                                </Alert.Description>
                            </Alert.Content>
                        </Alert.Root>
                    )}

                    {/* Add users section */}
                    {!readOnly && (
                        <Flex
                            direction="column"
                            borderBottom="1px solid"
                            borderColor="border.muted"
                            pb={6}
                        >
                            <Box>
                                <Text
                                    fontSize="xs"
                                    fontWeight="bold"
                                    textTransform="uppercase"
                                    color="active"
                                >
                                    {targetText}
                                </Text>
                            </Box>
                            <Grid
                                gap={2}
                                alignItems="flex-end"
                                gridTemplateColumns="repeat(12, minmax(0, 1fr))"
                            >
                                {roleOptions && (
                                    <Box gridColumn="span 4">
                                        <Text
                                            py={2}
                                            color="fg.muted"
                                            fontSize="9px"
                                            fontWeight="bold"
                                            letterSpacing="0.1em"
                                            textTransform="uppercase"
                                        >
                                            {t("adminDialogs.accessRole")}
                                        </Text>
                                        <OcSelect
                                            items={roleOptions.map((o) => ({
                                                label: o.label,
                                                value: String(o.value),
                                            }))}
                                            value={selectedRole}
                                            onValueChange={setSelectedRole}
                                            placeholder={t("adminDialogs.rolePlaceholder")}
                                        />
                                    </Box>
                                )}
                                <Box gridColumn={roleOptions ? "span 5" : "span 9"}>
                                    <Text
                                        py={2}
                                        color="fg.muted"
                                        fontSize="9px"
                                        fontWeight="bold"
                                        letterSpacing="0.1em"
                                        textTransform="uppercase"
                                    >
                                        {t("adminDialogs.usersToBeAdded", { target })}
                                    </Text>
                                    <OcCombobox
                                        items={comboboxItems}
                                        value={selectedValues}
                                        onValueChange={(v) => setSelectedValues(v as string[])}
                                        multiple
                                        placeholder={t("adminDialogs.selectPlaceholder")}
                                        emptyMessage={t("common.noResults")}
                                    />
                                </Box>
                                <Tooltip
                                    content={buttonTooltip}
                                    disabled={!buttonTooltip}
                                    uppercase={false}
                                >
                                    <Box gridColumn="span 3">
                                        <Button
                                            w="full"
                                            textTransform="uppercase"
                                            fontSize="9px"
                                            fontWeight="bold"
                                            letterSpacing="0.1em"
                                            colorPalette="brand"
                                            onClick={handleAdd}
                                            disabled={
                                                !selectedValues.length ||
                                                (roleOptions && !selectedRole)
                                            }
                                        >
                                            {targetText}
                                        </Button>
                                    </Box>
                                </Tooltip>
                            </Grid>
                            {showRoleHint && (
                                <Flex align="center" gap={1} mt={2}>
                                    <FaExclamationCircle
                                        color="var(--chakra-colors-orange-400)"
                                        size={11}
                                    />
                                    <Text fontSize="11px" color="orange.400">
                                        {t("adminDialogs.selectRoleFirst")}
                                    </Text>
                                </Flex>
                            )}
                        </Flex>
                    )}

                    {/* Current members header: label + filter + search */}
                    <Flex alignItems="center" py={4}>
                        <Text
                            fontSize="xs"
                            fontWeight="bold"
                            textTransform="uppercase"
                            color="active"
                            flexShrink={0}
                        >
                            {targetTextCurrent}
                        </Text>

                        <Flex align="center" gap={3} ml="auto">
                            {hasGroups && (
                                <Flex align="center" gap={1} flexShrink={0}>
                                    <FilterLink
                                        label={t("resourceTable.filterAll")}
                                        active={typeFilter === "all"}
                                        onClick={() => handleFilterChange("all")}
                                    />
                                    <Text color="fg.subtle" fontSize="xs" px="2px">
                                        |
                                    </Text>
                                    <FilterLink
                                        label={t("resourceTable.filterUsers")}
                                        active={typeFilter === "user"}
                                        onClick={() => handleFilterChange("user")}
                                    />
                                    <Text color="fg.subtle" fontSize="xs" px="2px">
                                        |
                                    </Text>
                                    <FilterLink
                                        label={t("resourceTable.filterGroups")}
                                        active={typeFilter === "group"}
                                        onClick={() => handleFilterChange("group")}
                                    />
                                </Flex>
                            )}

                            <Input
                                size="sm"
                                w="180px"
                                placeholder={t("common.search")}
                                aria-label={t("common.search")}
                                value={search}
                                onChange={(e) => {
                                    setSearch(e.target.value);
                                    setPage(1);
                                }}
                            />
                        </Flex>
                    </Flex>

                    {/* Unified member list */}
                    <Stack>
                        {paginatedItems.map((item) => (
                            <MemberRow
                                key={item.id}
                                item={item}
                                readOnly={readOnly}
                                onGroupDeleteClick={onGroupDeleteClick}
                                onRevokeClick={handleRevokeClick}
                            />
                        ))}

                        {error && (
                            <Text color="red.emphasized" role="alert" aria-live="assertive">
                                {error}
                            </Text>
                        )}
                    </Stack>

                    {totalPages > 1 && (
                        <Flex gap={2} justify="flex-end">
                            <Button
                                variant="subtle"
                                onClick={() => setPage((p) => p - 1)}
                                disabled={page === 1}
                            >
                                {t("common.previous")}
                            </Button>
                            <Button
                                variant="subtle"
                                onClick={() => setPage((p) => p + 1)}
                                disabled={page === totalPages}
                            >
                                {t("common.next")}
                            </Button>
                        </Flex>
                    )}
                </Stack>
            </FormDialog>

            {itemToDelete && (
                <ConfirmDeleteDialog
                    isOpen={isDeleteOpen}
                    onClose={() => setIsDeleteOpen(false)}
                    userName={paginatedItems.find((i) => i.id === itemToDelete)?.label || "Unknown"}
                    onDelete={handleDeleteConfirmed}
                />
            )}
        </>
    );
}
