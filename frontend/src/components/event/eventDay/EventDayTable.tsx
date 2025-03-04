import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {BaseEntityTableProps} from '@utils/types.ts'
import {deleteEventDay, getEventDays} from '@api/sdk.gen.ts'
import {EventDayDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {eventIndexRoute} from '@routes'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import EntityTable from '@components/EntityTable.tsx'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'date', sort: 'asc'}]

const EventDayTable = (props: BaseEntityTableProps<EventDayDto>) => {
    const {t} = useTranslation()

    const {eventId} = eventIndexRoute.useParams()

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getEventDays({
            signal,
            path: {eventId: eventId},
            query: {...paginationParameters},
        })
    }

    const deleteRequest = (dto: EventDayDto) => {
        return deleteEventDay({path: {eventId: dto.event, eventDayId: dto.id}})
    }

    const columns: GridColDef<EventDayDto>[] = [
        {
            field: 'date',
            headerName: t('event.eventDay.date'),
            minWidth: 120,
            flex: 0,
        },
        {
            field: 'name',
            headerName: t('event.eventDay.name'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'description',
            headerName: t('event.eventDay.description'),
            flex: 2,
            sortable: false,
        },
    ]

    return (
        <EntityTable
            {...props}
            parentResource={'EVENT'}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            linkColumn={entity => ({
                to: '/event/$eventId/eventDay/$eventDayId',
                params: {eventId: entity.event, eventDayId: entity.id},
            })}
            entityName={t('event.eventDay.eventDay')}
            deleteRequest={deleteRequest}
        />
    )
}

export default EventDayTable
