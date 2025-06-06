import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {deleteCompetitionSetupTemplate, getCompetitionSetupTemplates} from '@api/sdk.gen.ts'
import {BaseEntityTableProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityTable from '@components/EntityTable.tsx'
import {CompetitionSetupTemplateDto} from '@api/types.gen.ts'

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

const deleteRequest = (dto: CompetitionSetupTemplateDto) => {
    return deleteCompetitionSetupTemplate({path: {competitionSetupTemplateId: dto.id}})
}

const CompetitionSetupTemplateTable = (
    props: BaseEntityTableProps<CompetitionSetupTemplateDto>,
) => {
    const {t} = useTranslation()

    const columns: GridColDef<CompetitionSetupTemplateDto>[] = [
        {
            field: 'name',
            headerName: t('event.competition.setup.template.name'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'description',
            headerName: t('event.competition.setup.template.description'),
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
            entityName={t('event.competition.setup.template.template')}
            deleteRequest={deleteRequest}
        />
    )
}

export default CompetitionSetupTemplateTable
