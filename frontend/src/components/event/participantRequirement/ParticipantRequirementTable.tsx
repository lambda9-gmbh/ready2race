import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {deleteParticipantRequirement, getParticipantRequirements} from '@api/sdk.gen.ts'
import {DeleteNamedParticipantError, ParticipantRequirementDto} from '@api/types.gen.ts'
import {BaseEntityTableProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityTable from '@components/EntityTable.tsx'
import {useFeedback} from '@utils/hooks.ts'
import {Check} from '@mui/icons-material'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
    return getParticipantRequirements({
        signal,
        query: {...paginationParameters},
    })
}

const deleteRequest = (dto: ParticipantRequirementDto) => {
    return deleteParticipantRequirement({path: {participantRequirementId: dto.id}})
}

const ParticipantRequirementTable = (props: BaseEntityTableProps<ParticipantRequirementDto>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const columns: GridColDef<ParticipantRequirementDto>[] = [
        {
            field: 'name',
            headerName: t('entity.name'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'description',
            headerName: t('entity.description'),
            flex: 2,
            sortable: false,
        },
        {
            field: 'optional',
            headerName: t('entity.optional'),
            flex: 1,
            sortable: false,
            renderCell: ({value}) => (value ? <Check /> : <></>),
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
            entityName={t('participantRequirement.participantRequirement')}
            deleteRequest={deleteRequest}
            onDeleteError={onDeleteError}
        />
    )
}

export default ParticipantRequirementTable
