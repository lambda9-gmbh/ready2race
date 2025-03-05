import {GridColDef, GridPaginationModel, GridSortModel} from "@mui/x-data-grid";
import {PaginationParameters} from "@utils/ApiUtils.ts";
import {BaseEntityTableProps} from "@utils/types.ts";
import {useTranslation} from "react-i18next";
import EntityTable from "@components/EntityTable.tsx";
import {deleteCompetitionTemplate, getCompetitionTemplates} from "@api/sdk.gen.ts";
import {CompetitionTemplateDto} from "@api/types.gen.ts";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]


const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
    return getCompetitionTemplates({
        signal,
        query: {...paginationParameters},
    })
}

const deleteRequest = (dto: CompetitionTemplateDto) => {
    return deleteCompetitionTemplate({path: {competitionTemplateId: dto.id}})
}


const CompetitionTemplateTable = (props: BaseEntityTableProps<CompetitionTemplateDto>) => {
    const {t} = useTranslation()

    const columns: GridColDef<CompetitionTemplateDto>[] = [
        {
            field: 'name',
            headerName: t('event.competition.template.name'),
            minWidth: 150,
            flex: 1,
            valueGetter: (_, e) => e.properties.name
        },
        {
            field: 'description',
            headerName: t('event.competition.template.description'),
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

export default CompetitionTemplateTable