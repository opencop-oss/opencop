// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// Overlays
export { BaseDialog, DialogContainerContext } from "./overlays/BaseDialog.tsx";
export { ConfirmDialog } from "./overlays/ConfirmDialog.tsx";
export { FormDialog } from "./overlays/FormDialog.tsx";
export { DialogActions } from "./overlays/DialogActions.tsx";
export { DialogHeader } from "./overlays/DialogHeader.tsx";
export { WizardDialogActions } from "./overlays/WizardDialogActions.tsx";
export { Tooltip } from "./overlays/Tooltip.tsx";
export type { TooltipProps } from "./overlays/Tooltip.tsx";

// Forms
export { OcCombobox } from "./forms/OcCombobox.tsx";
export type { ComboboxItem } from "./forms/OcCombobox.tsx";
export { OcGroupCombobox, UNGROUPED_GROUP_ID } from "./forms/OcGroupCombobox.tsx";
export type { OcGroupComboboxItem } from "./forms/OcGroupCombobox.tsx";
export { OcRecentsComboBox } from "./forms/OcRecentsCombobox.tsx";
export type { OcRecentsComboboxItem, OcRecentsComboboxProps } from "./forms/OcRecentsCombobox.tsx";
export { OcSelect } from "./forms/OcSelect.tsx";
export type { SelectItem, SelectGroup } from "./forms/OcSelect.tsx";
export { OcSlider } from "./forms/OcSlider.tsx";
export { OcInputGroup } from "./forms/OcInputGroup.tsx";
export { OcInputLabel } from "./forms/OcInputLabel.tsx";
export { OcSwatch } from "./forms/OcSwatch.tsx";
export { FilterInput } from "./forms/FilterInput.tsx";
export { OcSwitch } from "./forms/OcSwitch.tsx";
export type { OcSwitchProps } from "./forms/OcSwitch.tsx";
export { OcTag } from "./forms/OcTag.tsx";
export { DefaultCheckbox } from "./forms/DefaultCheckbox.tsx";
export type { DefaultCheckboxProps } from "./forms/DefaultCheckbox.tsx";
export { OcDateTimePicker } from "./forms/OcDateTimePicker.tsx";
export { OptionColorPicker } from "./forms/OptionColorPicker.tsx";
export { OcColorPicker } from "./forms/OcColorPicker.tsx";
export type { OcColorPickerProps } from "./forms/OcColorPicker.tsx";
export { OptionSlider } from "./forms/OptionSlider.tsx";
export { SettingsPopover } from "./forms/SettingsPopover.tsx";
export { LayerOpacityControl } from "./forms/LayerOpacityControl.tsx";
export type { LayerOpacityControlProps } from "./forms/LayerOpacityControl.tsx";
export { useOvertype } from "./forms/use-overtype.hook.ts";
export { useCreatableCombobox } from "./forms/use-creatable-combobox.hook.ts";
export { InfoTooltip } from "./forms/InfoTooltip.tsx";
export type { InfoTooltipProps } from "./forms/InfoTooltip.tsx";

export { CoordFormatSelect } from "./forms/CoordFormatSelect.tsx";
export type { CoordFormatSelectProps } from "./forms/CoordFormatSelect.tsx";
export { CoordinateInputField } from "./forms/CoordinateInputField.tsx";
export type { CoordinateInputFieldProps } from "./forms/CoordinateInputField.tsx";
export { useCoordinateInput } from "./forms/use-coordinate-input.hook.ts";
export { CopyButton } from "./forms/CopyButton.tsx";
export type { CopyButtonProps } from "./forms/CopyButton.tsx";

// Preferences
export {
    DisplayPreferencesProvider,
    useDisplayPreferences,
} from "./preferences/DisplayPreferencesContext.tsx";
export type { DisplayPreferences } from "./preferences/DisplayPreferencesContext.tsx";

// Navigation
export { Breadcrumbs } from "./navigation/Breadcrumbs.tsx";
export type { BreadcrumbItem } from "./navigation/Breadcrumbs.tsx";
export { OcTabSelect } from "./navigation/OcTabSelect.tsx";
export { Pagination } from "./navigation/Pagination.tsx";

// Typography
export { AccentHeading } from "./typography/AccentHeading.tsx";
export { PanelSectionHeading } from "./typography/PanelSectionHeading.tsx";
export { ActiveHeading } from "./typography/ActiveHeading.tsx";
export { BoxedHeading } from "./typography/BoxedHeading.tsx";
export { TacticalHeading } from "./typography/TacticalHeading.tsx";
export { TacticalText } from "./typography/TacticalText.tsx";
export { Divider } from "./typography/Divider.tsx";

export { Section } from "./typography/Section.tsx";
export type { SectionProps } from "./typography/Section.tsx";

// Data
export { DataTable } from "./data/DataTable.tsx";
export { SearchInput } from "./data/SearchInput.tsx";
export { ResourceTable } from "./data/ResourceTable.tsx";
export { ColumnPicker } from "./data/ColumnPicker.tsx";
export type { ColumnPickerItem, ColumnPickerProps } from "./data/ColumnPicker.tsx";
export type {
    ResourceTableProps,
    ToolbarAction,
    ToolbarMenuItem,
    ResourceTableToolbar,
} from "./data/ResourceTable.tsx";
export { OC_FILTER_FNS } from "./data/oc-filter-fns.ts";
export {
    usernameColumn,
    nameColumn,
    groupNameColumn,
    roleColumn,
    clickableRoleColumn,
} from "./data/table-columns.util.tsx";
export type { RoleColumnOption } from "./data/table-columns.util.tsx";
export { ResourceTypeBadge } from "./data/ResourceTypeBadge.tsx";
export type { ResourceTypeBadgeProps } from "./data/ResourceTypeBadge.tsx";
export { ResourceTypeFilterTab } from "./data/ResourceTypeFilterTab.tsx";
export type {
    ResourceTypeFilterTabProps,
    ResourceTypeFilterOption,
} from "./data/ResourceTypeFilterTab.tsx";
export { DetailTable } from "./data/DetailTable.tsx";
export type { DetailTableRow, DetailTableProps } from "./data/DetailTable.tsx";
export { useDefaultVisibleRow } from "./data/DefaultVisibleRow.tsx";
export type { DefaultVisibleRowProps } from "./data/DefaultVisibleRow.tsx";
export { JsonTreeView } from "./data/JsonTreeView.tsx";
export { ListPanelItem } from "./data/ListPanelItem.tsx";
export type { ListPanelItemProps } from "./data/ListPanelItem.tsx";
export { VirtualList } from "./data/VirtualList.tsx";
export type { VirtualListProps } from "./data/VirtualList.tsx";
export { LayerToggleBody } from "./data/LayerToggleBody.tsx";
export type { LayerToggleBodyProps } from "./data/LayerToggleBody.tsx";
export { SortableLayerEntry } from "./data/SortableLayerEntry.tsx";
export type { SortableLayerEntryProps } from "./data/SortableLayerEntry.tsx";
export { SortableLayerList } from "./data/SortableLayerList.tsx";
export type { SortableLayerListProps } from "./data/SortableLayerList.tsx";
export { ColumnFilterBuilder } from "./data/ColumnFilterBuilder.tsx";
export type { ColumnFilterBuilderProps, FilterableColumn } from "./data/ColumnFilterBuilder.tsx";
export { toFilterableColumns } from "./data/column-filter.util.ts";
export type {
    ColumnFilterDef,
    ColumnFilterType,
    ColumnFilterEnumOption,
    ActiveColumnFilterValue,
    ColumnFilterOperator,
    ColumnStringOperator,
    ColumnEnumOperator,
    ColumnNumericOperator,
    ColumnBooleanOperator,
    ColumnDateOperator,
} from "./data/column-filter.model.ts";
export { Stat } from "./data/Stat.tsx";
export { ArchivedBadge } from "./data/ArchivedBadge.tsx";
export { RingBuffer, higherIsBetter, lowerIsBetter } from "./data/stat.utils.ts";
export { TypeColorLabel } from "./data/TypeColorLabel.tsx";
export type { TypeColorLabelProps } from "./data/TypeColorLabel.tsx";
export { TYPE_PALETTE } from "./data/type-colors.ts";
export type { TypePaletteKey } from "./data/type-colors.ts";
export { TreeView } from "./data/TreeView.tsx";

export { DrawerSectionHeader } from "./data/DrawerSectionHeader.tsx";
export type { DrawerSectionHeaderProps } from "./data/DrawerSectionHeader.tsx";

// Data — filter rules: the generic predicate language and builder; fields come from the provider.
export {
    FilterRulesBuilder,
    type FilterRulesBuilderHandle,
    type FilterRulesBuilderProps,
    type FilterRulesFormValues,
} from "./data/FilterRules/FilterRulesBuilder.tsx";
export { PredicateSummary } from "./data/FilterRules/PredicateSummary.tsx";
export { PredicateField } from "./data/FilterRules/PredicateField.tsx";
export {
    FilterFieldsProvider,
    useFilterFields,
    type FilterFields,
} from "./data/FilterRules/FilterFieldsContext.tsx";
export {
    defaultPredicateDraft,
    operators,
    predicateDraftSchema,
    type Filter,
    type FilterCombinator,
    type FilterDraft,
    type FilterError,
    type Predicate,
    type PredicateDraft,
    type PredicateOperator,
    type PredicateValueError,
    type SubstringInPredicate,
    type SubstringInPredicateDraft,
} from "./data/FilterRules/predicate.model.ts";
export {
    draftFromFilter,
    isInertUnderAll,
    parseFilter,
    resolveFilter,
    retargetDraftToField,
    type ContributedFilterField,
    type FilterField,
    type FilterFieldLabel,
    type FilterValueEditorProps,
    type ParseFilterResult,
    type ResolvedRow,
} from "./data/FilterRules/filter-field.model.ts";
export {
    summarizePredicate,
    type PredicateSummaryValue,
} from "./data/FilterRules/predicate-summary.util.ts";

// Re-exports from TanStack Table for consumer convenience
export { createColumnHelper } from "@tanstack/react-table";
export type {
    ColumnDef,
    ColumnFiltersState,
    OnChangeFn,
    RowSelectionState,
} from "@tanstack/react-table";

// Charts
export { Histogram } from "./charts/Histogram/Histogram.tsx";
export type {
    HistogramProps,
    HistogramBucket,
    HistogramChunkRange,
    HistogramSourceSeries,
} from "./charts/Histogram/histogram.types.ts";
export {
    CHUNK_STATUS,
    HISTOGRAM_METRIC,
    DEFAULT_SOURCE_COLOR,
    GAP_COLOR,
    HISTOGRAM_SOURCE_COLORS,
} from "./charts/Histogram/histogram.constants.ts";
export type {
    HistogramChunkStatus,
    HistogramMetric,
} from "./charts/Histogram/histogram.constants.ts";
export { SeriesSwatch } from "./charts/Histogram/SeriesSwatch.tsx";
export type { SeriesSwatchVariant } from "./charts/Histogram/SeriesSwatch.tsx";

// Surfaces
export { DrawerSideNavLayout } from "./surfaces/DrawerSideNavLayout.tsx";
export type {
    DrawerSideNavLayoutProps,
    DrawerSideNavSection,
} from "./surfaces/DrawerSideNavLayout.tsx";
export { SideDrawer } from "./surfaces/SideDrawer.tsx";
export { ResizeHandle } from "./surfaces/ResizeHandle.tsx";
export { computeFlexSizes } from "./surfaces/resize-utils.ts";
export type { ResizeHandleProps } from "./surfaces/ResizeHandle.tsx";
export { CollapsibleSection } from "./surfaces/CollapsibleSection.tsx";
export type { CollapsibleSectionProps } from "./surfaces/CollapsibleSection.tsx";
export { Hub } from "./surfaces/Hub.tsx";
export { HubCard } from "./surfaces/HubCard.tsx";
export { ScrollArea } from "./surfaces/ScrollArea.tsx";
export { Glow } from "./surfaces/Glow.tsx";
export { Panel } from "./surfaces/Panel.tsx";
export { PopoverPanel } from "./surfaces/PopoverPanel.tsx";
export { scrollbarStyles } from "./surfaces/scrollbar.util.ts";
export { Square, Circle } from "./surfaces/Shapes.tsx";
export { OverflowContainer } from "./surfaces/OverflowContainer.tsx";
export { useOverflowCount } from "./surfaces/use-overflow-count.hook.ts";
export { AffiliationFrameIcon } from "./surfaces/AffiliationFrameIcon.tsx";

export { EntityIconBox } from "./surfaces/EntityIconBox.tsx";
export { OverflowPopover } from "./surfaces/OverflowPopover.tsx";

// Media
export { FileTypeIcon } from "./media/FileTypeIcon.tsx";
export { Logo } from "./media/Logo.tsx";
export {
    createThumbnail,
    THUMBNAIL_MAX_DIMENSION,
    THUMBNAIL_FORMAT,
    THUMBNAIL_QUALITY,
} from "./media/createThumbnail.ts";

export { cachedImage, CACHED_IMAGE_LIMIT } from "./media/cached-image.ts";

// Buttons
export { RefreshButton } from "./buttons/RefreshButton.tsx";
export { ActionButton } from "./buttons/ActionButton.tsx";
export { GlassButton } from "./buttons/GlassButton.tsx";
export { IconButton } from "./buttons/IconButton.tsx";

// Feedback
export { StatusBox } from "./feedback/StatusBox.tsx";

// Theme
export {
    ColorModeProvider,
    useColorMode,
    useColorModeValue,
    ColorModeIcon,
    ColorModeButton,
    LightMode,
    DarkMode,
} from "./theme/ColorMode.tsx";
export type { ColorMode, UseColorModeReturn } from "./theme/ColorMode.tsx";
export { getDefaultTransition } from "./theme/transition.ts";
export { OcThemeProvider } from "./theme/OcThemeProvider.tsx";
export { system as ocSystem } from "./theme/theme.ts";
/** Raw theme config for dev tooling (the /themes showcase); its shape is not a supported contract. */
export { themeConfig as ocThemeConfig } from "./theme/theme.ts";

// Icons
export { Trash, Broadcast, Edit } from "./icons/icons.tsx";
export { addHostFormatters } from "./i18n/host-formatters.ts";
