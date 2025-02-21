import {BaseEntityTableProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityTable from '@components/EntityTable.tsx'
import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {getUsers} from "@api/sdk.gen.ts";
import {AppUserDto} from "@api/types.gen.ts";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'lastname', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) =>
    getUsers({
        signal,
        query: paginationParameters,
    })

const UserTable = (props: BaseEntityTableProps<AppUserDto>) => {
    const {t} = useTranslation()

    const columns: GridColDef<AppUserDto>[] = [
        {
            field: 'firstname',
            headerName: t('user.firstname'),
            minWidth: 200,
            flex: 1,
        },
        {
            field: 'lastname',
            headerName: t('user.lastname'),
            minWidth: 200,
            flex: 1,
        },
        {
            field: 'email',
            headerName: t('user.email'),
            minWidth: 200,
            flex: 1,
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
            resource={'USER'}
            linkColumn={entity => ({
                to: '/user/$userId',
                params: {userId: entity.id}
            })}
        />
    )
}

export default UserTable
