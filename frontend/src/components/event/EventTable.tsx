import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {BaseEntityTableProps} from '../../utils/types.ts'
import {deleteEvent, EventDto, getEvents} from '../../api'
import {useTranslation} from 'react-i18next'
import {useFeedback} from '../../utils/hooks.ts'
import EntityTable from '../EntityTable.tsx'
import {PaginationParameters} from '../../utils/ApiUtils.ts'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const EventTable = (props: BaseEntityTableProps<EventDto>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const columns: GridColDef<EventDto>[] = [
        {
            field: 'name',
            headerName: t('event.name'),
            minWidth: 200,
            flex: 1,
            valueGetter: (_value, row) => {
                row.properties.name
            },
        },
        {
            field: 'description',
            headerName: t('event.description'),
            minWidth: 200,
            flex: 2,
            sortable: false,
            valueGetter: (_value, row) => {
                row.properties.description
            },
        },
    ]

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getEvents({
            signal,
            query: {...paginationParameters},
        })
    }

    const deleteRequest = (dto: EventDto) => {
        return deleteEvent({path: {eventId: dto.id}})
    }

    return (
        <EntityTable
            {...props}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            jumpToColumn={entity => ({
                to: '/event',
                params: {regattaId: entity.id},
            })}
            addLabel={t('event.action.add')}
            deleteRequest={deleteRequest}
            onDelete={() => {
                feedback.success(t('event.action.delete.success'))
            }}
            changePermission={'EVENT_EDIT'}
            readPermission={'EVENT_VIEW'}
        />
    )
}

export default EventTable