import {GridColDef, GridPaginationModel, GridSortModel} from "@mui/x-data-grid";
import {BaseEntityTableProps} from "../../../utils/types.ts";
import {deleteEventDay, EventDayDto, getEventDays} from "../../../api";
import {useTranslation} from "react-i18next";
import {eventIndexRoute} from "../../../routes.tsx";
import {PaginationParameters} from "../../../utils/ApiUtils.ts";
import EntityTable from "../../EntityTable.tsx";

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
            minWidth: 200,
            flex: 0,
        },
        {
            field: 'name',
            headerName: t('entity.name'),
            minWidth: 200,
            flex: 1,
        },
        {
            field: 'description',
            headerName: t('entity.description'),
            minWidth: 200,
            flex: 2,
            sortable: false,
        }
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
                to: '/event/$eventId/eventDay/$eventDayId',
                params: {eventId: entity.event, eventDayId: entity.id}
            })}
            entityName={t('event.eventDay.eventDay')}
            deleteRequest={deleteRequest}
            onDelete={() => {}}
            changePermission={'EVENT_EDIT'}
            readPermission={'EVENT_VIEW'}
        />
    )
}

export default EventDayTable