import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {BaseEntityTableProps} from '@utils/types.ts'
import {ClubDto, deleteClub, getClubs} from '../../api'
import {useTranslation} from 'react-i18next'
import EntityTable from '../EntityTable.tsx'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {format} from 'date-fns'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
    return getClubs({
        signal,
        query: {...paginationParameters},
    })
}

const deleteRequest = (dto: ClubDto) => {
    return deleteClub({path: {clubId: dto.id}})
}

const ClubTable = (props: BaseEntityTableProps<ClubDto>) => {
    const {t} = useTranslation()

    const columns: GridColDef<ClubDto>[] = [
        {
            field: 'name',
            headerName: t('entity.name'),
            minWidth: 150,
            flex: 2,
        },
        {
            field: 'createdAt',
            headerName: t('entity.createdAt'),
            flex: 1,
            sortable: true,
            valueGetter: (v: string) => (v ? format(new Date(v), t('format.datetime')) : null),
        },
    ]

    return (
        <EntityTable
            {...props}
            resource={'CLUB'}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            linkColumn={entity => ({
                to: '/club/$clubId',
                params: {clubId: entity.id},
            })}
            entityName={t('club.club')}
            deleteRequest={deleteRequest}
            onDelete={() => {}}
        />
    )
}

export default ClubTable
