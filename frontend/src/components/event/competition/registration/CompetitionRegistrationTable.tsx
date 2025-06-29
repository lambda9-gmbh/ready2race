import {useTranslation} from 'react-i18next'
import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {competitionRoute, eventRoute} from '@routes'
import {
    CompetitionRegistrationTeamDto,
    deleteCompetitionRegistration,
    EventDto,
    getCompetitionRegistrations,
} from '../../../../api'
import {BaseEntityTableProps} from '@utils/types.ts'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {Fragment, useMemo} from 'react'
import EntityTable from '@components/EntityTable.tsx'
import {Grid2, Stack, Typography} from '@mui/material'
import {format} from "date-fns";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'clubName', sort: 'asc'}]

const CompetitionRegistrationTable = (
    props: BaseEntityTableProps<CompetitionRegistrationTeamDto>,
) => {
    const {t} = useTranslation()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getCompetitionRegistrations({
            signal,
            path: {eventId, competitionId},
            query: {...paginationParameters},
        })
    }

    const deleteRequest = (dto: EventDto) =>
        deleteCompetitionRegistration({
            path: {
                eventId: eventId,
                competitionId: competitionId,
                competitionRegistrationId: dto.id,
            },
        })

    const columns: GridColDef<CompetitionRegistrationTeamDto>[] = useMemo(
        () => [
            {
                field: 'clubName',
                headerName: t('club.club'),
                minWidth: 200,
            },
            {
                field: 'name',
                headerName: t('entity.name'),
                valueGetter: value => value ?? '-',
            },
            {
                field: 'namedParticipants',
                headerName: t('club.participant.title'),
                flex: 1,
                renderCell: ({row}) => (
                    <Grid2 container spacing={1} key={`participants-${row.id}`}>
                        {row.namedParticipants.map(np => (
                            <Fragment key={np.namedParticipantId}>
                                {row.namedParticipants.length > 1 && (
                                    <Grid2
                                        container
                                        direction={'row'}
                                        spacing={2}
                                        size={2}
                                        key={`name-${np.namedParticipantId}`}>
                                        <Typography variant={'subtitle2'}>
                                            {np.namedParticipantName}:
                                        </Typography>
                                    </Grid2>
                                )}
                                <Grid2
                                    container
                                    direction={'column'}
                                    spacing={0.5}
                                    size={10}
                                    key={`participants-${np.namedParticipantId}`}>
                                    {np.participants.map(p => (
                                        <Typography variant={'body2'} key={p.id}>
                                            {p.firstname} {p.lastname}{' '}
                                            {p.externalClubName && `(${p.externalClubName})`}
                                        </Typography>
                                    ))}
                                </Grid2>
                            </Fragment>
                        ))}
                    </Grid2>
                ),
            },
            {
                field: 'optionalFees',
                headerName: t('event.registration.optionalFee'),
                renderCell: ({row}) => (
                    <Stack>
                        {row.optionalFees.length >= 1
                            ? row.optionalFees.map(f => f.feeName).join(', ')
                            : '-'}
                    </Stack>
                ),
            },
            {
                field: 'updatedAt',
                headerName: t('entity.updatedAt'),
                minWidth: 100,
                maxWidth: 200,
                flex: 1,
                valueGetter: (v: string) => v ? format(new Date(v), t('format.datetime')) : null,
            },
        ],
        [],
    )

    return (
        <Fragment>
            <EntityTable
                {...props}
                withSearch={false}
                parentResource={'REGISTRATION'}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                dataRequest={dataRequest}
                deleteRequest={deleteRequest}
                entityName={t('event.registration.registration')}
            />
        </Fragment>
    )
}

export default CompetitionRegistrationTable
