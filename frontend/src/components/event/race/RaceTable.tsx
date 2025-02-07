import {PaginationParameters} from "../../../utils/ApiUtils.ts";
import {deleteRace, getRaces, RaceDto} from "../../../api";
import {useTranslation} from "react-i18next";
import {GridColDef, GridPaginationModel, GridSortModel} from "@mui/x-data-grid";
import EntityTable from "../../EntityTable.tsx";
import {BaseEntityTableProps} from "../../../utils/types.ts";
import {eventIndexRoute} from "../../../routes.tsx";


const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'identifier', sort: 'asc'}]


const RaceTable = (props: BaseEntityTableProps<RaceDto>) => {
    const {t} = useTranslation()

    const {eventId} = eventIndexRoute.useParams()

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getRaces({
            signal,
            path: {eventId: eventId},
            query: {...paginationParameters},
        })
    }

    const deleteRequest = (dto: RaceDto) => {
        return deleteRace({path: {eventId: dto.event, raceId: dto.id}})
    }

    const columns: GridColDef<RaceDto>[] = [
        {
            field: 'identifier',
            headerName: t('event.race.identifier'),
            minWidth: 200,
            flex: 0,
            valueGetter: (_, e) => e.properties.identifier
        },
        {
            field: 'shortName',
            headerName: t('event.race.shortName'),
            minWidth: 200,
            flex: 0,
            valueGetter: (_, e) => e.properties.shortName
        },
        {
            field: 'name',
            headerName: t('event.race.name'),
            minWidth: 200,
            flex: 1,
            valueGetter: (_, e) => e.properties.name
        },
        {
            field: 'raceCategory',
            headerName: t('event.race.category.category'),
            minWidth: 200,
            flex: 0,
            valueGetter: (_, row) => row.properties.raceCategory
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

export default RaceTable