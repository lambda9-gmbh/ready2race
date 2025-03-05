import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {BaseEntityTableProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import EntityTable from '@components/EntityTable.tsx'
import {deleteCompetitionCategory, getCompetitionCategories} from '@api/sdk.gen.ts'
import {CompetitionCategoryDto, DeleteNamedParticipantError} from '@api/types.gen.ts'
import {useFeedback} from '@utils/hooks.ts'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
    return getCompetitionCategories({
        signal,
        query: {...paginationParameters},
    })
}

const deleteRequest = (dto: CompetitionCategoryDto) => {
    return deleteCompetitionCategory({path: {competitionCategoryId: dto.id}})
}

const CompetitionCategoryTable = (props: BaseEntityTableProps<CompetitionCategoryDto>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const columns: GridColDef<CompetitionCategoryDto>[] = [
        {
            field: 'name',
            headerName: t('event.competition.category.name'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'description',
            headerName: t('event.competition.category.description'),
            flex: 2,
            sortable: false,
        },
    ]

    const onDeleteError = (error: DeleteNamedParticipantError) => {
        if (error.status.value === 409) {
            feedback.error(t('event.competition.error.referenced', {entity: props.entityName}))
        } else {
            feedback.error(t('entity.delete.error', {entity: props.entityName}))
        }
        console.error(error)
    }

    return (
        <EntityTable
            {...props}
            parentResource={'EVENT'}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            entityName={t('event.competition.category.category')}
            deleteRequest={deleteRequest}
            onDeleteError={onDeleteError}
        />
    )
}

export default CompetitionCategoryTable
