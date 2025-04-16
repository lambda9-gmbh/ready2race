import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {deleteCompetitionSetupTemplate, getCompetitionSetupTemplates} from '@api/sdk.gen.ts'
import {CompetitionSetupTemplateOverviewDto} from '@api/types.gen.ts'
import {BaseEntityTableProps} from "@utils/types.ts";
import {useTranslation} from "react-i18next";
import {useFeedback} from "@utils/hooks.ts";
import EntityTable from "@components/EntityTable.tsx";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
    return getCompetitionSetupTemplates({
        signal,
        query: {...paginationParameters},
    })
}

const deleteRequest = (dto: CompetitionSetupTemplateOverviewDto) => {
    return deleteCompetitionSetupTemplate({path: {competitionSetupTemplateId: dto.id}})
}

const CompetitionSetupTemplateTable = (props: BaseEntityTableProps<CompetitionSetupTemplateOverviewDto>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const columns: GridColDef<CompetitionSetupTemplateOverviewDto>[] = [
        {
            field: 'name',
            headerName: '[todo] Name',
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'description',
            headerName: '[todo] Description',
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
            entityName={'[todo] Competition Setup Template'}
            deleteRequest={deleteRequest}
        />
    )
}

export default CompetitionSetupTemplateTable
