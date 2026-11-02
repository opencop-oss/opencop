// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, HStack, IconButton, Text, Textarea } from "@chakra-ui/react";
import { Element, MAX_TEXT_LENGTH, TextBox, type TextBoxElement } from "@opencop/elements";
import { useToolSession, useToolSessionRevision } from "@opencop/tools";
import { OcColorPicker, OcSwitch } from "@opencop/ui";
import { useCallback, type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { clampTextSize, growToHoldText } from "./box-text.util";
import { TextBoxEditorShell } from "./TextBoxEditorShell";
import { TextSizeField } from "./TextSizeField";
import { readTextBoxParams, styleFromParams } from "./textbox-tools";

/**
 * Content, colour, size and visibility. Values stage in the session params only
 * until a draft exists (a preview appears on the first mousemove); after that
 * they go to the draft's style and do not carry to the next box, since
 * `setParams` mid-draw would re-arm the session and drop the draft.
 */
export function TextBoxToolEditor(): ReactElement {
    const { t } = useTranslation(["translation", "textbox"]);
    const { draft, params, setParams, invalidate } = useToolSession();

    // The session hands back a draft; this editor only works on textbox elements.
    const element = draft instanceof Element ? draft : null;
    const target: TextBoxElement | null = element && TextBox.is(element) ? element : null;
    const style = target?.getStyle() ?? styleFromParams(readTextBoxParams(params));
    // Re-render on any change to the draft: the edit model's in-place geometry
    // edits, and this editor's own style writes (each calls `invalidate`).
    useToolSessionRevision();

    const text = style.getText();

    /** Live element if there is one, otherwise stage in params. */
    const write = useCallback(
        (mutate: (target: TextBoxElement) => void, staged: Record<string, unknown>) => {
            if (target) {
                mutate(target);
                // Content and size both change how much room the text needs, so
                // the box is re-grown before the map is told anything changed.
                const grew = growToHoldText(target);
                invalidate(target, grew ? "geometry" : "style");
                return;
            }
            setParams({ ...params, ...staged });
        },
        [target, invalidate, setParams, params],
    );

    const onText = useCallback(
        (value: string) => write((box) => box.getStyle().setText(value), { text: value }),
        [write],
    );

    const onColor = useCallback(
        (value: string) => write((box) => box.getStyle().setTextColor(value), { textColor: value }),
        [write],
    );

    const onSize = useCallback(
        (value: number) => {
            // The field clamps too; this guards the params path and any future caller.
            const size = clampTextSize(value);
            write((box) => box.getStyle().setTextSize(size), { textSize: size });
        },
        [write],
    );

    // Per element only: staging this would silently hide the next box drawn.
    const onVisible = useCallback(
        (value: boolean) => {
            if (!target) return;
            target.getStyle().setTextVisible(value);
            invalidate(target, "style");
        },
        [target, invalidate],
    );

    return (
        <TextBoxEditorShell title={t("textbox:editor.title")}>
            <Textarea
                aria-label={t("textbox:editor.contentLabel")}
                placeholder={t("textbox:editor.contentPlaceholder")}
                value={text}
                onChange={(event) => onText(event.target.value)}
                rows={3}
                resize="vertical"
                maxLength={MAX_TEXT_LENGTH}
            />

            <HStack justify="space-between">
                <Text textStyle="sm">{t("textbox:editor.colorLabel")}</Text>
                <OcColorPicker value={style.getTextColor()} onChange={onColor}>
                    <IconButton
                        variant="plain"
                        size="xs"
                        aria-label={t("textbox:editor.colorLabel")}
                    >
                        <Box
                            w="5"
                            h="5"
                            borderWidth="1px"
                            borderRadius="sm"
                            bg={style.getTextColor()}
                        />
                    </IconButton>
                </OcColorPicker>
            </HStack>

            <TextSizeField value={style.getTextSize()} onChange={onSize} />

            <OcSwitch
                checked={style.isTextVisible()}
                label={t("textbox:editor.visibleLabel")}
                description={t("textbox:editor.visibleDescription")}
                disabled={!target}
                onCheckedChange={onVisible}
            />
        </TextBoxEditorShell>
    );
}
