import {useTranslation} from 'react-i18next'
import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {eventIndexRoute} from '@routes'
import {getParticipantsForEvent, ParticipantForEventDto} from '../../api'
import {BaseEntityTableProps} from '@utils/types.ts'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import EntityTable from '../EntityTable.tsx'
import {VerifiedUser} from '@mui/icons-material'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'clubName', sort: 'asc'}]

const ParticipantForEventTable = (
    props: BaseEntityTableProps<ParticipantForEventDto> & {openRequirementsCheck: () => void},
) => {
    const {t} = useTranslation()

    const {eventId} = eventIndexRoute.useParams()

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getParticipantsForEvent({
            signal,
            path: {eventId},
            query: {...paginationParameters},
        })
    }

    const columns: GridColDef<ParticipantForEventDto>[] = [
        {
            field: 'clubName',
            headerName: t('club.club'),
            flex: 1,
        },
        {
            field: 'firstname',
            headerName: t('entity.firstname'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'lastname',
            headerName: t('entity.lastname'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'gender',
            headerName: t('entity.gender'),
        },
        {
            field: 'year',
            headerName: t('club.participant.year'),
        },
        {
            field: 'externalClubName',
            headerName: t('club.participant.externalClub'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'participantRequirementsChecked',
            headerName: t('event.participantRequirement.checked'),
            minWidth: 150,
            flex: 1,
        },
    ]

    return (
        <EntityTable
            {...props}
            customTableActions={[
                {
                    icon: <VerifiedUser />,
                    label: t('event.participantRequirement.check'),
                    onClick: props.openRequirementsCheck,
                },
            ]}
            gridProps={{getRowId: row => row.participantId}}
            parentResource={'EVENT'}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            entityName={t('club.participant.title')}
        />
    )
}

export default ParticipantForEventTable
