import {BaseEntityTableProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import EntityTable from '@components/EntityTable.tsx'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {getInvitations} from "@api/sdk.gen.ts";
import {AppUserInvitationDto} from "@api/types.gen.ts";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'lastname', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) =>
    getInvitations({
        signal,
        query: paginationParameters,
    })

const UserInvitationTable = (props: BaseEntityTableProps<AppUserInvitationDto>) => {
    const {t} = useTranslation()

    const columns: GridColDef<AppUserInvitationDto>[] = [
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
            headerName: t('user.email.email'),
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
        />
    )
}

export default UserInvitationTable
