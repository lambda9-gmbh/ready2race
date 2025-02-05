import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {BaseEntityTableProps} from '../../utils/types.ts'
import {deleteEvent, EventDto, getEvents} from '../../api'
import {useTranslation} from 'react-i18next'
import EntityTable from '../EntityTable.tsx'
import {PaginationParameters} from '../../utils/ApiUtils.ts'


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

    const columns: GridColDef<EventDto>[] = [
        {
            field: 'name',
            headerName: t('event.name'),
            minWidth: 200,
            flex: 1,
            valueGetter: (_, e) => e.properties.name
        },
        {
            field: 'description',
            headerName: t('event.description'),
            minWidth: 200,
            flex: 2,
            sortable: false,
            valueGetter: (_, row) => row.properties.description
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
            jumpToColumn={entity => ({
                to: '/event/$eventId',
                params: {eventId: entity.id},
            })}
            entityName={t('event.event')}
            deleteRequest={deleteRequest}
            onDelete={() => {}}
            changePermission={'EVENT_EDIT'}
            readPermission={'EVENT_VIEW'}
        />
    )
}

export default EventTable