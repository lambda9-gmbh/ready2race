import {GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {BaseEntityTableProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityTable, {ExtendedGridColDef} from '@components/EntityTable.tsx'
import {deleteEvent, deleteTimeslot, getEvents, getTimeslots} from '@api/sdk.gen.ts'
import {EventDto, TimeslotDto, TimeslotRequest} from '@api/types.gen.ts'
import {Check} from '@mui/icons-material'
import {readEventGlobal} from '@authorization/privileges.ts'
import {eventDayRoute, eventRoute} from '@routes'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 100,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const {eventId} = eventRoute.useParams()
const {eventDayId} = eventDayRoute.useParams()

const dataRequest = (signal: AbortSignal) => {
    return getTimeslots({
        signal,
        path: {
            eventDayId: eventDayId,
            eventId: eventId,
        },
    })
}

const deleteRequest = (dto: TimeslotDto) => {
    return deleteTimeslot({path: {timeslotId: dto.id, eventDayId: '', eventId: ''}})
}

const TimeslotTable = (props: BaseEntityTableProps<TimeslotDto>) => {
    const {t} = useTranslation()

    const columns: ExtendedGridColDef<TimeslotDto>[] = [
        {
            field: 'name',
            headerName: t('event.name'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'startTime',
            headerName: 'wambo',
            minWidth: 110,
            sortable: false,
            requiredPrivilege: readEventGlobal,
            renderCell: ({value}) => (value ? <Check /> : <></>),
        },
        {
            field: 'endTime',
            headerName: 'wambo',
            minWidth: 110,
            sortable: false,
            requiredPrivilege: readEventGlobal,
            renderCell: ({value}) => (value ? <Check /> : <></>),
        },
        {
            field: 'description',
            headerName: t('event.description'),
            flex: 2,
            sortable: false,
        },
    ]

    return (
        <EntityTable
            {...props}
            parentResource={'EVENT'}
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

export default TimeslotTable
