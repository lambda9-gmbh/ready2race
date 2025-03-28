import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {getParticipantRequirementsForEvent} from '@api/sdk.gen.ts'
import {ParticipantRequirementForEventDto} from '@api/types.gen.ts'
import {BaseEntityTableProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityTable from '@components/EntityTable.tsx'
import {eventIndexRoute} from '@routes'
import {Cancel, Check, CheckCircle} from '@mui/icons-material'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const ParticipantRequirementForEventTable = (
    props: BaseEntityTableProps<ParticipantRequirementForEventDto>,
) => {
    const {t} = useTranslation()

    const {eventId} = eventIndexRoute.useParams()

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getParticipantRequirementsForEvent({
            signal,
            path: {eventId},
            query: {...paginationParameters},
        })
    }

    const columns: GridColDef<ParticipantRequirementForEventDto>[] = [
        {
            field: 'active',
            headerName: t('participantRequirement.active'),
            sortable: false,
            renderCell: ({value}) =>
                value ? <CheckCircle color={'success'} /> : <Cancel color={'error'} />,
        },
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
            sortable: false,
            renderCell: ({value}) => (value ? <Check /> : <></>),
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
            entityName={t('participantRequirement.participantRequirement')}
        />
    )
}

export default ParticipantRequirementForEventTable
