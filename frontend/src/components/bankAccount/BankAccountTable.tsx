import EntityTable from "@components/EntityTable.tsx";
import {BaseEntityTableProps} from "@utils/types.ts";
import {BankAccountDto} from "@api/types.gen.ts";
import {GridColDef, GridPaginationModel, GridSortModel} from "@mui/x-data-grid";
import {PaginationParameters} from "@utils/ApiUtils.ts";
import {deleteBankAccount, getBankAccounts} from "@api/sdk.gen.ts";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'holder', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) =>
    getBankAccounts({
        signal,
        query: paginationParameters
    })

const deleteRequest = (entity: BankAccountDto) =>
    deleteBankAccount({path: {bankAccountId: entity.id}})

const BankAccountTable = (props: BaseEntityTableProps<BankAccountDto>) => {

    const columns: GridColDef<BankAccountDto>[] = [
        {
            field: 'holder',
            headerName: '[todo] holder',
            minWidth: 200,
            flex: 1
        },
        {
            field: 'iban',
            headerName: '[todo] iban',
            minWidth: 200,
            flex: 1
        },
        {
            field: 'bic',
            headerName: '[todo] bic',
            minWidth: 200,
            flex: 1
        },
        {
            field: 'bank',
            headerName: '[todo] bank',
            minWidth: 200,
            flex: 1
        },
    ]

    return (
        <EntityTable
            {...props}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            deleteRequest={deleteRequest}
            parentResource={'EVENT'}
        />
    )
}

export default BankAccountTable