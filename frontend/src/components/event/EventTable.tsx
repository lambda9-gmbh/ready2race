import {GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {BaseEntityTableProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityTable, {ExtendedGridColDef} from '@components/EntityTable.tsx'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {deleteEvent, getEvents} from '@api/sdk.gen.ts'
import {EventDto} from '@api/types.gen.ts'
import {Check} from '@mui/icons-material'
import {readEventGlobal} from '@authorization/privileges.ts'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
    return getEvents({
        signal,
        query: {...paginationParameters},
    })
}

const deleteRequest = (dto: EventDto) => {
    return deleteEvent({path: {eventId: dto.id}})
}

const EventTable = (props: BaseEntityTableProps<EventDto>) => {
    const {t} = useTranslation()

    const columns: ExtendedGridColDef<EventDto>[] = [
        {
            field: 'name',
            headerName: t('event.name'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'description',
            headerName: t('event.description'),
            flex: 2,
            sortable: false,
        },
        {
            field: 'published',
            headerName: t('event.published'),
            minWidth: 110,
            sortable: false,
            requiredPrivilege: readEventGlobal,
            renderCell: ({value}) => (value ? <Check /> : <></>),
        },
    ]

    return (
        <EntityTable
            {...props}
            resource={'EVENT'}
            publicRead={true}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            linkColumn={entity => ({
                to: '/event/$eventId',
                params: {eventId: entity.id},
            })}
            deleteRequest={deleteRequest}
        />
    )
}

export default EventTable
