import EntityTable, {ExtendedGridColDef} from "@components/EntityTable.tsx";
import {BaseEntityTableProps} from "@utils/types.ts";
import {EventRegistrationViewDto} from "@api/types.gen.ts";
import {GridPaginationModel, GridSortModel} from "@mui/x-data-grid";
import {useTranslation} from "react-i18next";
import {PaginationParameters} from "@utils/ApiUtils.ts";
import {deleteEventRegistration, getRegistrationsForEvent} from "@api/sdk.gen.ts";
import {format} from "date-fns";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'createdAt', sort: 'asc'}]

const deleteRequest = (dto: EventRegistrationViewDto) =>
    deleteEventRegistration({path: {eventId: dto.eventId, eventRegistrationId: dto.id}})

const EventRegistrationTable = ({eventId, ...props}: BaseEntityTableProps<EventRegistrationViewDto> & {eventId: string}) => {

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters)=>
        getRegistrationsForEvent({
            signal,
            path: {eventId},
            query: {...paginationParameters},
        })

    const {t} = useTranslation()

    const columns: ExtendedGridColDef<EventRegistrationViewDto>[] = [
        {
            field: 'createdAt',
            headerName: t('entity.createdAt'),
            valueGetter: (v: string) => v ? format(new Date(v), t('format.datetime')) : null,
            minWidth: 200,
            flex: 1
        },
        {
            field: 'clubName',
            headerName: t('club.club'),
            flex: 2,
        }
    ]

    return (
        <EntityTable
            {...props}
            resource={'REGISTRATION'}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            deleteRequest={deleteRequest}
            linkColumn={entity => ({
                to: '/event/$eventId/registration/$eventRegistrationId',
                params: {eventId: entity.eventId, eventRegistrationId: entity.id},
            })}
        />
    )
}

export default EventRegistrationTable