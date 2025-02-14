import {GridColDef, GridPaginationModel, GridSortModel} from "@mui/x-data-grid";
import {PaginationParameters} from "../../../../utils/ApiUtils.ts";
import {deleteRaceTemplate, getRaceTemplates, RaceTemplateDto} from "../../../../api";
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
    return getRaceTemplates({
        signal,
        query: {...paginationParameters},
    })
}

const deleteRequest = (dto: RaceTemplateDto) => {
    return deleteRaceTemplate({path: {raceTemplateId: dto.id}})
}


const RaceTemplateTable = (props: BaseEntityTableProps<RaceTemplateDto>) => {
    const {t} = useTranslation()

    const columns: GridColDef<RaceTemplateDto>[] = [
        {
            field: 'name',
            headerName: t('entity.name'),
            minWidth: 200,
            flex: 1,
            valueGetter: (_, e) => e.properties.name
        },
        {
            field: 'description',
            headerName: t('entity.description'),
            minWidth: 200,
            flex: 2,
            sortable: false,
            valueGetter: (_, e) => e.properties.description
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
            deleteRequest={deleteRequest}
        />
    )
}

export default RaceTemplateTable