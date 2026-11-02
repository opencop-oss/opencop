// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Button, VStack } from "@chakra-ui/react";
import { ThemesSection } from "../ThemesSection";
import { ExampleSection } from "../ExampleSection";
import { DataTable, DetailTable } from "@opencop/ui";

interface SampleRow {
    id: number;
    name: string;
    status: string;
    role: string;
}

const sampleData: SampleRow[] = [
    { id: 1, name: "Alice", status: "Active", role: "Admin" },
    { id: 2, name: "Bob", status: "Inactive", role: "User" },
    { id: 3, name: "Charlie", status: "Active", role: "Editor" },
    { id: 4, name: "Diana", status: "Pending", role: "User" },
];

const columns = [
    { header: "ID", accessor: "id" as const },
    { header: "Name", accessor: "name" as const },
    { header: "Status", accessor: "status" as const },
    { header: "Role", accessor: "role" as const },
];

const stripedRows = [
    { key: "1", label: "Callsign", value: "ALPHA-01" },
    { key: "2", label: "Type", value: "Ground Vehicle" },
    { key: "3", label: "Status", value: "Active" },
    { key: "4", label: "Last Update", value: "2025-01-27T14:30:00Z" },
    { key: "5", label: "Affiliation", value: "Friendly" },
];

export function TableSection() {
    return (
        <ThemesSection title="Tables">
            <VStack align="start" gap="8" w="full">
                <ExampleSection
                    label="DataTable"
                    code={`import { DataTable } from "@opencop/ui";

const columns = [
    { header: "ID", accessor: "id" },
    { header: "Name", accessor: "name" },
    { header: "Status", accessor: "status" },
    { header: "Role", accessor: "role" },
];

<DataTable
    columns={columns}
    data={data}
    sortable
    onRowClick={(row) => console.log(row)}
/>`}
                >
                    <DataTable columns={columns} data={sampleData} sortable />
                </ExampleSection>

                <ExampleSection
                    label="DataTable with Actions"
                    code={`<DataTable
    columns={columns}
    data={data}
    actions={(row) => (
        <Button size="xs" variant="outline">
            Edit
        </Button>
    )}
/>`}
                >
                    <DataTable
                        columns={columns}
                        data={sampleData}
                        actions={() => (
                            <Button size="xs" variant="outline">
                                Edit
                            </Button>
                        )}
                    />
                </ExampleSection>

                <ExampleSection
                    label="DetailTable"
                    code={`import { DetailTable } from "@opencop/ui";

<DetailTable
    rows={[
        { key: "1", label: "Callsign", value: "ALPHA-01" },
        { key: "2", label: "Type", value: "Ground Vehicle" },
        { key: "3", label: "Status", value: "Active" },
    ]}
/>`}
                >
                    <DetailTable rows={stripedRows} />
                </ExampleSection>
            </VStack>
        </ThemesSection>
    );
}
