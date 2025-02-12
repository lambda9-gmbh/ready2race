import {GridColDef, GridPaginationModel, GridSortModel} from "@mui/x-data-grid";
import {PaginationParameters} from "../../../../utils/ApiUtils.ts";
import {
    deleteNamedParticipant,
    getNamedParticipants,
    NamedParticipantDto,
} from "../../../../api";
import {BaseEntityTableProps} from "../../../../utils/types.ts";
import {useTranslation} from "react-i18next";
import EntityTable from "../../../EntityTable.tsx";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]


const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
    return getNamedParticipants({
        signal,
        query: {...paginationParameters},
    })
}

const deleteRequest = (dto: NamedParticipantDto) => {
    return deleteNamedParticipant({path: {namedParticipantId: dto.id}})
}


const NamedParticipantTable = (props: BaseEntityTableProps<NamedParticipantDto>) => {
    const {t} = useTranslation()

    const columns: GridColDef<NamedParticipantDto>[] = [
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
            entityName={t('event.race.template.template')}
            deleteRequest={deleteRequest}
            changePermission={'EVENT_EDIT'}
            readPermission={'EVENT_VIEW'}
        />
    )
}

export default NamedParticipantTable