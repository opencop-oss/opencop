// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Button, HStack, Text, VStack } from "@chakra-ui/react";
import { useState } from "react";
import { ThemesSection } from "../ThemesSection";
import { ExampleSection } from "../ExampleSection";
import { FormDialog, ConfirmDialog, BaseDialog, DialogHeader, DialogActions } from "@opencop/ui";
import { Dialog } from "@chakra-ui/react";

export function DialogSection() {
    const [formOpen, setFormOpen] = useState(false);
    const [formSecondaryOpen, setFormSecondaryOpen] = useState(false);
    const [confirmOpen, setConfirmOpen] = useState(false);
    const [confirmSecondaryOpen, setConfirmSecondaryOpen] = useState(false);
    const [baseOpen, setBaseOpen] = useState(false);

    return (
        <ThemesSection title="Dialogs">
            <VStack align="start" gap="8" w="full">
                <ExampleSection
                    label="FormDialog"
                    code={`import { FormDialog } from "@opencop/ui";

const [open, setOpen] = useState(false);

<FormDialog
    open={open}
    onClose={() => setOpen(false)}
    title="Edit Item"
    onSubmit={() => setOpen(false)}
    submitLabel="Save"
    cancelLabel="Cancel"
>
    <Text>Form content goes here...</Text>
</FormDialog>`}
                >
                    <Button onClick={() => setFormOpen(true)}>Open Form Dialog</Button>
                    <FormDialog
                        open={formOpen}
                        onClose={() => setFormOpen(false)}
                        title="Edit Item"
                        onSubmit={() => setFormOpen(false)}
                        submitLabel="Save"
                        cancelLabel="Cancel"
                    >
                        <Text>
                            Form content goes here. Uses the &ldquo;admin&rdquo; variant by default.
                        </Text>
                    </FormDialog>
                </ExampleSection>

                <ExampleSection
                    label="FormDialog with Secondary Text"
                    code={`import { FormDialog } from "@opencop/ui";

const [open, setOpen] = useState(false);

<FormDialog
    open={open}
    onClose={() => setOpen(false)}
    title="Edit Layer"
    secondaryText="Buildings"
    onSubmit={() => setOpen(false)}
    submitLabel="Save"
>
    <Text>Form content goes here...</Text>
</FormDialog>`}
                >
                    <Button onClick={() => setFormSecondaryOpen(true)}>
                        Open Form Dialog (Secondary Text)
                    </Button>
                    <FormDialog
                        open={formSecondaryOpen}
                        onClose={() => setFormSecondaryOpen(false)}
                        title="Edit Layer"
                        secondaryText="Buildings"
                        onSubmit={() => setFormSecondaryOpen(false)}
                        submitLabel="Save"
                    >
                        <Text>
                            The secondary text appears next to the title separated by a pipe, styled
                            in the brand color. Useful for showing context like entity names.
                        </Text>
                    </FormDialog>
                </ExampleSection>

                <ExampleSection
                    label="ConfirmDialog (Destructive)"
                    code={`import { ConfirmDialog } from "@opencop/ui";

const [open, setOpen] = useState(false);

<ConfirmDialog
    open={open}
    onClose={() => setOpen(false)}
    onConfirm={() => setOpen(false)}
    title="Delete Item"
    message="Are you sure? This action cannot be undone."
    confirmLabel="Delete"
    isDestructive
/>`}
                >
                    <Button colorPalette="red" onClick={() => setConfirmOpen(true)}>
                        Delete Item
                    </Button>
                    <ConfirmDialog
                        open={confirmOpen}
                        onClose={() => setConfirmOpen(false)}
                        onConfirm={() => setConfirmOpen(false)}
                        title="Remove Item"
                        message="Are you sure you want to remove this item? This action cannot be undone."
                        confirmLabel="Remove"
                        isDestructive
                    />
                </ExampleSection>

                <ExampleSection
                    label="ConfirmDialog with Secondary Text"
                    code={`import { ConfirmDialog } from "@opencop/ui";

const [open, setOpen] = useState(false);

<ConfirmDialog
    open={open}
    onClose={() => setOpen(false)}
    onConfirm={() => setOpen(false)}
    title="Delete Layer"
    secondaryText="Buildings"
    message="Are you sure? This action cannot be undone."
    confirmLabel="Delete"
    isDestructive
/>`}
                >
                    <Button colorPalette="red" onClick={() => setConfirmSecondaryOpen(true)}>
                        Delete Layer
                    </Button>
                    <ConfirmDialog
                        open={confirmSecondaryOpen}
                        onClose={() => setConfirmSecondaryOpen(false)}
                        onConfirm={() => setConfirmSecondaryOpen(false)}
                        title="Remove Layer"
                        secondaryText="Buildings"
                        message="Are you sure you want to remove this layer? This action cannot be undone."
                        confirmLabel="Remove"
                        isDestructive
                    />
                </ExampleSection>

                <ExampleSection
                    label="BaseDialog with DialogHeader & DialogActions"
                    code={`import { BaseDialog, DialogHeader, DialogActions } from "@opencop/ui";
import { Dialog } from "@chakra-ui/react";

<BaseDialog open={open} onClose={onClose} variant="admin" size="md">
    <DialogHeader title="Custom Dialog" secondaryText="Details" />
    <Dialog.Body>
        <Text>Any content goes here.</Text>
    </Dialog.Body>
    <DialogActions
        onClose={onClose}
        cancelLabel="Cancel"
        onAction={onConfirm}
        actionLabel="Confirm"
    />
</BaseDialog>`}
                >
                    <HStack gap="3">
                        <Button onClick={() => setBaseOpen(true)}>Open Base Dialog</Button>
                    </HStack>
                    <BaseDialog
                        open={baseOpen}
                        onClose={() => setBaseOpen(false)}
                        variant="admin"
                        size="md"
                    >
                        <DialogHeader title="Custom Dialog" secondaryText="Details" />
                        <Dialog.Body>
                            <Text>
                                This BaseDialog composes DialogHeader and DialogActions directly.
                                Use this approach when you need full control over layout.
                            </Text>
                        </Dialog.Body>
                        <DialogActions
                            onClose={() => setBaseOpen(false)}
                            cancelLabel="Cancel"
                            onAction={() => setBaseOpen(false)}
                            actionLabel="Confirm"
                        />
                    </BaseDialog>
                </ExampleSection>
            </VStack>
        </ThemesSection>
    );
}
