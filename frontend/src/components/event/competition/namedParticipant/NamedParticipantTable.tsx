import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {BaseEntityTableProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityTable from '@components/EntityTable.tsx'
import {deleteNamedParticipant, getNamedParticipants} from '@api/sdk.gen.ts'
import {DeleteNamedParticipantError, NamedParticipantDto} from '@api/types.gen.ts'
import {useFeedback} from '@utils/hooks.ts'

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
    const feedback = useFeedback()

    const columns: GridColDef<NamedParticipantDto>[] = [
        {
            field: 'name',
            headerName: t('event.competition.namedParticipant.name'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'description',
            headerName: t('event.competition.namedParticipant.description'),
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
            entityName={t('event.competition.namedParticipant.namedParticipant')}
            deleteRequest={deleteRequest}
            onDeleteError={onDeleteError}
        />
    )
}

export default NamedParticipantTable
