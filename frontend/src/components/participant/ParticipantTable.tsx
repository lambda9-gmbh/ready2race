import {useTranslation} from 'react-i18next'
import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {clubIndexRoute} from '@routes'
import {deleteClubParticipant, getClubParticipants, ParticipantDto} from '../../api'
import {BaseEntityTableProps} from '@utils/types.ts'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import EntityTable from '../EntityTable.tsx'
import {Check} from '@mui/icons-material'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'firstname', sort: 'asc'}]

const ParticipantTable = (props: BaseEntityTableProps<ParticipantDto>) => {
    const {t} = useTranslation()

    const {clubId} = clubIndexRoute.useParams()

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getClubParticipants({
            signal,
            path: {clubId},
            query: {...paginationParameters},
        })
    }

    const deleteRequest = (dto: ParticipantDto) => {
        return deleteClubParticipant({path: {clubId, participantId: dto.id}})
    }

    const columns: GridColDef<ParticipantDto>[] = [
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
            minWidth: 100,
            flex: 1,
        },
        {
            field: 'year',
            headerName: t('club.participant.year'),
            minWidth: 100,
            flex: 1,
        },
        {
            field: 'externalClubName',
            headerName: t('club.participant.externalClub'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'usedInRegistration',
            headerName: t('club.participant.usedInRegistration'),
            renderCell: ({value}) => (value ? <Check /> : <></>),
        },
    ]

    return (
        <EntityTable
            {...props}
            deletableIf={p => !p.usedInRegistration}
            resource={'PARTICIPANT'}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            entityName={t('club.participant.title')}
            deleteRequest={deleteRequest}
        />
    )
}

export default ParticipantTable
