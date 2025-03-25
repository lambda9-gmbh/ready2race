import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {deleteParticipantRequirement, getParticipantRequirementsForEvent} from '@api/sdk.gen.ts'
import {ParticipantRequirementForEventDto} from '@api/types.gen.ts'
import {BaseEntityTableProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityTable from '@components/EntityTable.tsx'
import {useFeedback} from '@utils/hooks.ts'
import {eventIndexRoute} from '@routes'
import {Cancel, CheckCircle} from '@mui/icons-material'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [
    {field: 'active', sort: 'desc'},
    {field: 'name', sort: 'asc'},
]

const ParticipantRequirementForEventTable = (
    props: BaseEntityTableProps<ParticipantRequirementForEventDto>,
) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventIndexRoute.useParams()

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getParticipantRequirementsForEvent({
            signal,
            path: {eventId},
            query: {...paginationParameters},
        })
    }

    const deleteRequest = (dto: ParticipantRequirementForEventDto) => {
        return deleteParticipantRequirement({path: {participantRequirementId: dto.id}})
    }

    const columns: GridColDef<ParticipantRequirementForEventDto>[] = [
        {
            field: 'active',
            headerName: t('event.participantRequirement.active'),
            sortable: false,
            renderCell: ({value}) =>
                value ? <CheckCircle color={'success'} /> : <Cancel color={'error'} />,
        },
        {
            field: 'name',
            headerName: t('event.participantRequirement.name'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'description',
            headerName: t('event.participantRequirement.description'),
            flex: 2,
            sortable: false,
        },
        {
            field: 'optional',
            headerName: t('event.participantRequirement.optional'),
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
            entityName={t('event.participantRequirement.participantRequirement')}
        />
    )
}

export default ParticipantRequirementForEventTable
